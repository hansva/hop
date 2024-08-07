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
 *
 */
package org.apache.hop.pipeline.transforms.cassandrasstableoutput.writer;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import org.junit.Test;

public class AbstractSSTableWriterTest extends AbstractSSTableWriter {

  @Test
  public void testGetDirectory() {
    AbstractSSTableWriter writer = new AbstractSSTableWriterTest();
    assertEquals(System.getProperty("java.io.tmpdir"), writer.getDirectory());
    writer.setDirectory("some_dir");
    assertEquals("some_dir", writer.getDirectory());
  }

  @Test
  public void testGetKeyspace() {
    AbstractSSTableWriter writer = new AbstractSSTableWriterTest();
    assertEquals(null, writer.getKeyspace());
    writer.setKeyspace("some_keyspace");
    assertEquals("some_keyspace", writer.getKeyspace());
  }

  @Test
  public void testGetTable() {
    AbstractSSTableWriter writer = new AbstractSSTableWriterTest();
    assertEquals(null, writer.getTable());
    writer.setTable("some_table");
    assertEquals("some_table", writer.getTable());
  }

  @Test
  public void testGetBufferSize() {
    AbstractSSTableWriter writer = new AbstractSSTableWriterTest();
    assertEquals(16, writer.getBufferSize());
    writer.setBufferSize(10);
    assertEquals(10, writer.getBufferSize());
  }

  @Test
  public void testGetKeyField() {
    AbstractSSTableWriter writer = new AbstractSSTableWriterTest();
    assertEquals(null, writer.getPrimaryKey());
    writer.setPrimaryKey("some_keyField");
    assertEquals("some_keyField", writer.getPrimaryKey());
  }

  @Override
  public void init() {
    // Do nothing
  }

  @Override
  public void processRow(Map<String, Object> record) {
    // Do nothing
  }

  @Override
  public void close() {
    // Do nothing
  }
}
