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
package org.apache.hop.core.row.value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.junit.rules.RestoreHopEnvironment;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/** User: Dzmitry Stsiapanau Date: 3/20/2014 Time: 11:51 AM */
public class ValueMetaTimestampTest {
  @ClassRule public static RestoreHopEnvironment env = new RestoreHopEnvironment();

  @Test
  public void testSetPreparedStatementValue() throws Exception {
    ValueMetaTimestamp vm = new ValueMetaTimestamp();
    PreparedStatement ps = mock(PreparedStatement.class);
    doAnswer(
            (Answer<Object>)
                invocationOnMock -> {
                  Object ts = invocationOnMock.getArguments()[1];
                  return ts.toString();
                })
        .when(ps)
        .setTimestamp(anyInt(), (Timestamp) any());

    try {
      vm.setPreparedStatementValue(mock(DatabaseMeta.class), ps, 0, null);
    } catch (HopDatabaseException ex) {
      fail("Error setting value on prepared statement ");
    }
  }

  @Test
  public void testCompare() throws Exception {
    ValueMetaTimestamp vm = new ValueMetaTimestamp();
    Timestamp earlier = Timestamp.valueOf("2012-12-12 12:12:12.121212");
    Timestamp later = Timestamp.valueOf("2013-12-12 12:12:12.121212");
    assertTrue(vm.isSortedAscending());
    assertFalse(vm.isSortedDescending());
    assertEquals(0, vm.compare(null, null));
    assertEquals(-1, vm.compare(null, earlier));
    assertEquals(1, vm.compare(earlier, null));
    assertEquals(0, vm.compare(earlier, earlier));
    assertEquals(-1, vm.compare(earlier, later));
    assertEquals(1, vm.compare(later, earlier));

    // Check Descending comparison
    vm.setSortedDescending(true);
    assertFalse(vm.isSortedAscending());
    assertTrue(vm.isSortedDescending());
    assertEquals(0, vm.compare(null, null));
    assertEquals(1, vm.compare(null, earlier));
    assertEquals(-1, vm.compare(earlier, null));
    assertEquals(0, vm.compare(earlier, earlier));
    assertEquals(1, vm.compare(earlier, later));
    assertEquals(-1, vm.compare(later, earlier));
  }

  @Test
  public void testConvertStringToTimestamp() throws Exception {
    ValueMetaTimestamp valueMetaTimestamp = new ValueMetaTimestamp();
    assertEquals(
        Timestamp.valueOf("2012-04-05 04:03:02.123456"),
        valueMetaTimestamp.convertStringToTimestamp("2012/4/5 04:03:02.123456"));
    assertEquals(
        Timestamp.valueOf("2012-04-05 04:03:02.123"),
        valueMetaTimestamp.convertStringToTimestamp("2012/4/5 04:03:02.123"));
    assertEquals(
        Timestamp.valueOf("2012-04-05 04:03:02.123456789"),
        valueMetaTimestamp.convertStringToTimestamp("2012/4/5 04:03:02.123456789"));
  }

  @Test
  public void testConvertTimestampToString() {
    ValueMetaTimestamp valueMetaTimestamp = new ValueMetaTimestamp();
    assertEquals(
        "2012/04/05 04:03:02.123456000",
        valueMetaTimestamp.convertTimestampToString(
            Timestamp.valueOf("2012-04-05 04:03:02.123456")));
    assertEquals(
        "2012/04/05 04:03:02.123000000",
        valueMetaTimestamp.convertTimestampToString(Timestamp.valueOf("2012-04-05 04:03:02.123")));
    assertEquals(
        "2012/04/05 04:03:02.123456789",
        valueMetaTimestamp.convertTimestampToString(
            Timestamp.valueOf("2012-04-05 04:03:02.123456789")));
  }

  @Test
  public void testConvertDateToTimestamp() {
    ValueMetaTimestamp valueMetaTimestamp = new ValueMetaTimestamp();
    // Converting date to timestamp
    Date date = new Date();
    assertEquals(valueMetaTimestamp.convertDateToTimestamp(date).getTime(), date.getTime());

    // Converting timestamp to timestamp
    Timestamp timestamp = Timestamp.valueOf("2014-04-05 04:03:02.123456789");
    Timestamp convertedTimestamp = valueMetaTimestamp.convertDateToTimestamp(timestamp);
    assertEquals(convertedTimestamp.getTime(), timestamp.getTime());
    assertEquals(convertedTimestamp.getNanos(), timestamp.getNanos());
  }
}
