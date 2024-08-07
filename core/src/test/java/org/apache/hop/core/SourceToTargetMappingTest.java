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
package org.apache.hop.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SourceToTargetMappingTest {

  @Test
  public void testClass() {
    SourceToTargetMapping mapping = new SourceToTargetMapping(2, 3);
    assertEquals(2, mapping.getSourcePosition());
    assertEquals(3, mapping.getTargetPosition());
    mapping.setSourcePosition(0);
    mapping.setTargetPosition(1);
    assertEquals(0, mapping.getSourcePosition());
    assertEquals(1, mapping.getTargetPosition());
    assertEquals("foo", mapping.getSourceString(new String[] {"foo", "bar"}));
    assertEquals("bar", mapping.getTargetString(new String[] {"foo", "bar"}));
  }
}
