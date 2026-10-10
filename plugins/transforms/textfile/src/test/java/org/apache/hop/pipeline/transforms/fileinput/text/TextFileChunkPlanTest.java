/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.fileinput.text;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.pipeline.transforms.fileinput.text.TextFileRecordSplitter.RecordFormat;
import org.apache.hop.ui.pipeline.transform.common.TextFileLineUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextFileChunkPlanTest {

  private static final long MB = 1024L * 1024;

  @TempDir Path dir;

  @Test
  void splitSizeIsTheChunkSizeForLotsOfData() {
    assertEquals(128 * MB, TextFileChunkPlan.splitSize(128 * MB, 10_000 * MB, 4));
  }

  @Test
  void splitSizeShrinksSoEveryCopyGetsWork() {
    // 200 MB over 4 copies: 50 MB pieces rather than 128 MB ones
    assertEquals(50 * MB, TextFileChunkPlan.splitSize(128 * MB, 200 * MB, 4));
  }

  @Test
  void splitSizeDoesNotShrinkBelowFourMegabytes() {
    assertEquals(4 * MB, TextFileChunkPlan.splitSize(128 * MB, 6 * MB, 8));
  }

  @Test
  void anExplicitSmallChunkSizeIsKept() {
    assertEquals(1024, TextFileChunkPlan.splitSize(1024, 100 * MB, 4));
  }

  @Test
  void splitSizeIsEven() {
    assertEquals(0, TextFileChunkPlan.splitSize(1001, 100 * MB, 4) % 2);
  }

  @Test
  void parsesChunkSizes() throws HopException {
    assertEquals(TextFileChunkPlan.DEFAULT_CHUNK_SIZE, TextFileChunkPlan.parseChunkSize(null));
    assertEquals(TextFileChunkPlan.DEFAULT_CHUNK_SIZE, TextFileChunkPlan.parseChunkSize(" "));
    assertEquals(64 * MB, TextFileChunkPlan.parseChunkSize("64MB"));
    assertEquals(512 * 1024, TextFileChunkPlan.parseChunkSize("512 kb"));
    assertEquals(2 * 1024 * MB, TextFileChunkPlan.parseChunkSize("2GB"));
    assertEquals(4096, TextFileChunkPlan.parseChunkSize("4096"));
    assertEquals(100, TextFileChunkPlan.parseChunkSize("100B"));
  }

  @Test
  void refusesInvalidChunkSizes() {
    assertThrows(HopException.class, () -> TextFileChunkPlan.parseChunkSize("lots"));
    assertThrows(HopException.class, () -> TextFileChunkPlan.parseChunkSize("0"));
    assertThrows(HopException.class, () -> TextFileChunkPlan.parseChunkSize("-5MB"));
  }

  @Test
  void findsLinesByTheirLineFeedByte() {
    for (String name : new String[] {"UTF-8", "ISO-8859-1", "windows-1252", "Shift_JIS", "GBK"}) {
      assertArrayEquals(new byte[] {0x0A}, TextFileChunkPlan.lineFeed(Charset.forName(name)), name);
    }
  }

  @Test
  void findsUtf16LineFeeds() {
    assertArrayEquals(
        new byte[] {0x0A, 0x00}, TextFileChunkPlan.lineFeed(Charset.forName("UTF-16LE")));
    assertArrayEquals(
        new byte[] {0x00, 0x0A}, TextFileChunkPlan.lineFeed(Charset.forName("UTF-16BE")));
  }

  @Test
  void refusesEncodingsWhereLinesCannotBeFoundByBytes() {
    assertNull(TextFileChunkPlan.lineFeed(Charset.forName("UTF-32")));
    assertNull(TextFileChunkPlan.lineFeed(Charset.forName("ISO-2022-JP")));
    assertNull(TextFileChunkPlan.lineFeed(Charset.forName("IBM037")), "EBCDIC");
  }

  // ------------------------------------------------------------ splitting real files

  private FileObject write(String name, String text) throws Exception {
    Path path = dir.resolve(name);
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return HopVfs.getFileObject(path.toString());
  }

  private static List<TextFileChunk> drain(TextFileChunkPlan plan) {
    List<TextFileChunk> chunks = new ArrayList<>();
    for (TextFileChunk chunk = plan.next(); chunk != null; chunk = plan.next()) {
      chunks.add(chunk);
    }
    return chunks;
  }

  /** The chunks follow each other without gap or overlap, from the start to the end of the file. */
  private static void assertTiles(List<TextFileChunk> chunks, long size) {
    long expected = 0;
    for (TextFileChunk chunk : chunks) {
      assertEquals(expected, chunk.start(), "chunk starts where the previous one ended");
      assertTrue(chunk.end() > chunk.start(), "no empty chunk");
      expected = chunk.end();
    }
    assertEquals(size, expected, "the last chunk ends at the end of the file");
  }

  private static String lines(int count, boolean enclosedBreaks) {
    StringBuilder text = new StringBuilder();
    for (int i = 1; i <= count; i++) {
      text.append(i).append(',');
      text.append(enclosedBreaks && i % 3 == 0 ? "\"spans\nlines " + i + "\"" : "row-" + i);
      text.append('\n');
    }
    return text.toString();
  }

  @Test
  void aLargeFileGivesMoreChunksThanCopies() throws Exception {
    String text = lines(5000, false);
    FileObject file = write("lines.csv", text);
    int copies = 4;

    TextFileChunkPlan plan =
        TextFileChunkPlan.build(
            List.of(file), 1024, copies, false, 0, "UTF-8", null, mock(ILogChannel.class));

    List<TextFileChunk> chunks = drain(plan);
    assertTrue(
        chunks.size() > copies * 10, chunks.size() + " chunks for " + text.length() + " bytes");
    assertEquals(plan.size(), chunks.size());
    assertTiles(chunks, text.length());
  }

  @Test
  void aLargeFileWithBreaksInEnclosedFieldsGivesMoreChunksThanCopies() throws Exception {
    String text = lines(5000, true);
    FileObject file = write("breaks.csv", text);
    int copies = 4;
    RecordFormat records = new RecordFormat("\"", "", TextFileLineUtil.FILE_FORMAT_UNIX, false);

    TextFileChunkPlan plan =
        TextFileChunkPlan.build(
            List.of(file), 1024, copies, false, 0, "UTF-8", records, mock(ILogChannel.class));

    List<TextFileChunk> chunks = drain(plan);
    assertTrue(
        chunks.size() > copies * 10, chunks.size() + " chunks for " + text.length() + " bytes");
    assertTiles(chunks, text.length());
    for (TextFileChunk chunk : chunks) {
      assertTrue(chunk.exact());
      // An exact chunk starts at a record: never right after the line feed inside "spans\nlines"
      if (chunk.start() > 0) {
        assertTrue(
            !text.startsWith("lines ", (int) chunk.start()), "split inside a record: " + chunk);
      }
    }
  }

  /**
   * Fetching ahead on storage other than local disk, here the in-memory file system, which reads at
   * an offset like object storage does: the chunks read in plan order give back the file, every
   * line exactly once, whatever the number of blocks in flight.
   */
  @Test
  void chunksFetchedAheadGiveBackTheFile() throws Exception {
    for (boolean enclosedBreaks : new boolean[] {false, true}) {
      String text = lines(30_000, enclosedBreaks);
      FileObject file =
          HopVfs.getFileObject("ram:///chunk-plan-test/read-ahead-" + enclosedBreaks + ".csv");
      file.getParent().createFolder();
      try (OutputStream out = HopVfs.getOutputStream(file, false)) {
        out.write(text.getBytes(StandardCharsets.UTF_8));
      }
      RecordFormat records =
          enclosedBreaks
              ? new RecordFormat("\"", "", TextFileLineUtil.FILE_FORMAT_UNIX, false)
              : null;
      List<TextFileChunk> chunks =
          drain(
              TextFileChunkPlan.build(
                  List.of(file),
                  150 * 1024,
                  3,
                  false,
                  0,
                  "UTF-8",
                  records,
                  mock(ILogChannel.class)));
      assertTrue(chunks.size() > 1, "the file is split: " + chunks);

      for (int blocks : new int[] {1, 3}) {
        TextFileReadAhead readAhead = new TextFileReadAhead(blocks);
        // Reading the file once first to find the record starts, fetched ahead too
        List<TextFileChunk> fetchedAhead =
            drain(
                TextFileChunkPlan.buildFromSources(
                    List.of(new TextFileChunkPlan.Source(file, null)),
                    150 * 1024,
                    3,
                    false,
                    0,
                    "UTF-8",
                    records,
                    readAhead,
                    mock(ILogChannel.class)));
        assertEquals(
            chunks.stream().map(c -> c.start() + "-" + c.end()).toList(),
            fetchedAhead.stream().map(c -> c.start() + "-" + c.end()).toList(),
            "the same chunks with " + blocks + " blocks in flight");
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (TextFileChunk chunk : chunks) {
          try (InputStream in = TextFileChunkPlan.open(chunk, readAhead)) {
            in.transferTo(all);
          }
        }
        assertEquals(text, all.toString(StandardCharsets.UTF_8), blocks + " blocks in flight");
      }
    }
  }

  @Test
  void parsesReadAheadSettings() throws HopException {
    // Blank: a pipeline saved before the setting existed reads as it always did
    assertEquals(TextFileReadAhead.NONE, TextFileReadAhead.parse(null));
    assertEquals(TextFileReadAhead.NONE, TextFileReadAhead.parse(" "));
    assertEquals(new TextFileReadAhead(0), TextFileReadAhead.parse("0"));
    assertEquals(new TextFileReadAhead(16), TextFileReadAhead.parse(" 16 "));
    assertThrows(HopException.class, () -> TextFileReadAhead.parse("-1"));
    assertThrows(HopException.class, () -> TextFileReadAhead.parse("65"));
    assertThrows(HopException.class, () -> TextFileReadAhead.parse("four"));
  }
}
