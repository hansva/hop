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

package org.apache.hop.databases.redshift;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Map;
import org.apache.hop.core.database.DatabaseMeta;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for RedshiftDatabaseMeta */
public class RedshiftDatabaseMetaTest {

  private RedshiftDatabaseMeta dbMeta;

  @Before
  public void setUp() {
    dbMeta = new RedshiftDatabaseMeta();
    dbMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
  }

  @Test
  public void testExtraOption() {
    Map<String, String> opts = dbMeta.getExtraOptions();
    assertNotNull(opts);
    assertEquals("true", opts.get("REDSHIFT.tcpKeepAlive"));
  }

  @Test
  public void testGetDefaultDatabasePort() {
    assertEquals(5439, dbMeta.getDefaultDatabasePort());
  }

  @Test
  public void testGetDriverClass() {
    assertEquals("com.amazon.redshift.jdbc42.Driver", dbMeta.getDriverClass());
  }

  @Test
  public void testGetURL() {
    assertEquals("jdbc:redshift://:/", dbMeta.getURL("", "", ""));
    assertEquals(
        "jdbc:redshift://rs.project-hop.org:4444/myDB",
        dbMeta.getURL("rs.project-hop.org", "4444", "myDB"));
  }

  @Test
  public void testGetExtraOptionsHelpText() {
    assertEquals(
        "http://docs.aws.amazon.com/redshift/latest/mgmt/configure-jdbc-connection.html",
        dbMeta.getExtraOptionsHelpText());
  }

  @Test
  public void testIsFetchSizeSupported() {
    assertFalse(dbMeta.isFetchSizeSupported());
  }

  @Test
  public void testSupportsSetMaxRows() {
    assertFalse(dbMeta.isSupportsSetMaxRows());
  }
}
