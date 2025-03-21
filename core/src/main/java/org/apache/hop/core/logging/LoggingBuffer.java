/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.core.logging;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;

/** This class keeps the last N lines in a buffer */
public class LoggingBuffer {
  @Getter @Setter private String name;

  private List<BufferLine> buffer;
  private ReadWriteLock lock = new ReentrantReadWriteLock();

  private int bufferSize;

  @Getter @Setter private HopLogLayout layout;

  private List<IHopLoggingEventListener> eventListeners;

  private LoggingRegistry loggingRegistry = LoggingRegistry.getInstance();

  public LoggingBuffer(int bufferSize) {
    this.bufferSize = bufferSize;
    // The buffer overflow protection allows it to be overflowed for 1 item within a single thread.
    // Considering a possible high contention, let's set it's max overflow size to be 10%.
    // Anyway, even an overflow goes higher than 10%, it wouldn't cost us too much.
    buffer = new ArrayList<>((int) (bufferSize * 1.1));
    layout = new HopLogLayout(true);
    eventListeners = new CopyOnWriteArrayList<>();
  }

  /**
   * @return the number (sequence, 1..N) of the last log line. If no records are present in the
   *     buffer, 0 is returned.
   */
  public int getLastBufferLineNr() {
    lock.readLock().lock();
    try {
      if (!buffer.isEmpty()) {
        return buffer.get(buffer.size() - 1).getNr();
      } else {
        return 0;
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * @param channelId channel IDs to grab
   * @param includeGeneral include general log lines
   * @param from
   * @param to
   * @return
   */
  public List<HopLoggingEvent> getLogBufferFromTo(
      List<String> channelId, boolean includeGeneral, int from, int to) {
    lock.readLock().lock();
    try {
      Stream<BufferLine> bufferStream =
          buffer.stream().filter(line -> line.getNr() > from && line.getNr() <= to);
      if (channelId != null) {
        bufferStream =
            bufferStream.filter(
                line -> {
                  String logChannelId = getLogChId(line);
                  return includeGeneral
                      ? isGeneral(logChannelId) || channelId.contains(logChannelId)
                      : channelId.contains(logChannelId);
                });
      }
      return bufferStream.map(BufferLine::getEvent).collect(Collectors.toList());
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * @param parentLogChannelId the parent log channel ID to grab
   * @param includeGeneral include general log lines
   * @param from
   * @param to
   * @return
   */
  public List<HopLoggingEvent> getLogBufferFromTo(
      String parentLogChannelId, boolean includeGeneral, int from, int to) {

    // Typically, the log channel id is the one from the pipeline or workflow running currently.
    // However, we also want to see the details of the transforms etc.
    // So we need to look at the parents all the way up if needed...
    //
    List<String> childIds = loggingRegistry.getLogChannelChildren(parentLogChannelId);

    return getLogBufferFromTo(childIds, includeGeneral, from, to);
  }

  public StringBuffer getBuffer(
      String parentLogChannelId, boolean includeGeneral, int startLineNr, int endLineNr) {
    StringBuilder eventBuffer = new StringBuilder(10000);

    List<HopLoggingEvent> events =
        getLogBufferFromTo(parentLogChannelId, includeGeneral, startLineNr, endLineNr);
    for (HopLoggingEvent event : events) {
      eventBuffer.append(layout.format(event)).append(Const.CR);
    }

    return new StringBuffer(eventBuffer);
  }

  public StringBuffer getBuffer(String parentLogChannelId, boolean includeGeneral) {
    return getBuffer(parentLogChannelId, includeGeneral, 0);
  }

  public StringBuffer getBuffer(
      String parentLogChannelId, boolean includeGeneral, int startLineNr) {
    return getBuffer(parentLogChannelId, includeGeneral, startLineNr, getLastBufferLineNr());
  }

  public StringBuffer getBuffer() {
    return getBuffer(null, true);
  }

  public void close() {
    // Do nothing
  }

  public void doAppend(HopLoggingEvent event) {
    if (event.getMessage() instanceof LogMessage) {
      lock.writeLock().lock();
      try {
        buffer.add(new BufferLine(event));
        while (bufferSize > 0 && buffer.size() > bufferSize) {
          buffer.remove(0);
        }
      } finally {
        lock.writeLock().unlock();
      }
    }
  }

  public boolean requiresLayout() {
    return true;
  }

  public void clear() {
    lock.writeLock().lock();
    try {
      buffer.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * @return the maximum number of lines that this buffer contains, 0 or lower means: no limit
   */
  public int getMaxNrLines() {
    return bufferSize;
  }

  /**
   * @param maxNrLines the maximum number of lines that this buffer should contain, 0 or lower
   *     means: no limit
   */
  public void setMaxNrLines(int maxNrLines) {
    this.bufferSize = maxNrLines;
  }

  /**
   * @return the nrLines
   */
  public int getNrLines() {
    return buffer.size();
  }

  /**
   * Removes all rows for the channel with the specified id
   *
   * @param id the id of the logging channel to remove
   */
  public void removeChannelFromBuffer(String id) {
    lock.writeLock().lock();
    try {
      buffer.removeIf(line -> id.equals(getLogChId(line)));
    } finally {
      lock.writeLock().unlock();
    }
  }

  public int size() {
    return buffer.size();
  }

  public void removeGeneralMessages() {
    lock.writeLock().lock();
    try {
      buffer.removeIf(line -> isGeneral(getLogChId(line)));
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void removeBufferLinesBefore(long minTimeBoundary) {
    // Using HashSet even though BufferLine does not implement hashcode and equals,
    // we just need to remove the exact objects we have found and put in the set.
    Set<BufferLine> linesToRemove = new HashSet<>();
    lock.writeLock().lock();
    try {
      for (BufferLine bufferLine : buffer) {
        if (bufferLine.getEvent().timeStamp < minTimeBoundary) {
          linesToRemove.add(bufferLine);
        } else {
          break;
        }
      }
      // removeAll should run fast against a HashSet,
      // since ArrayList.batchRemove check for each element of a collection given if it is in the
      // ArrayList.
      // Thus, removeAll should run in a linear time.
      buffer.removeAll(linesToRemove);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void addLogggingEvent(HopLoggingEvent loggingEvent) {
    doAppend(loggingEvent);
    eventListeners.forEach(event -> event.eventAdded(loggingEvent));
  }

  public void addLoggingEventListener(IHopLoggingEventListener listener) {
    eventListeners.add(listener);
  }

  public void removeLoggingEventListener(IHopLoggingEventListener listener) {
    eventListeners.remove(listener);
  }

  private boolean isGeneral(String logChannelId) {
    ILoggingObject loggingObject = loggingRegistry.getLoggingObject(logChannelId);
    return loggingObject != null && LoggingObjectType.GENERAL.equals(loggingObject.getObjectType());
  }

  private static String getLogChId(BufferLine bufferLine) {
    return ((LogMessage) bufferLine.getEvent().getMessage()).getLogChannelId();
  }
}
