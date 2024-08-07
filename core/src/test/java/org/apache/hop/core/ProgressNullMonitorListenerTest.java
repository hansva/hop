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

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ProgressNullMonitorListenerTest {

  @Test
  public void testClass() {
    ProgressNullMonitorListener listener = new ProgressNullMonitorListener();
    listener.beginTask("", 0);
    listener.subTask("");
    assertFalse(listener.isCanceled());
    listener.worked(0);
    listener.done();
    listener.setTaskName("");
  }
}
