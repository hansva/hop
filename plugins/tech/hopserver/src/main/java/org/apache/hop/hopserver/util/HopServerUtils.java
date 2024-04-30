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

package org.apache.hop.hopserver.util;

import org.apache.hop.core.variables.IVariables;

public class HopServerUtils {

  private static String HOP_EXECUTION_ID;
  private static String HOP_SERVER_TOKEN;
  private static String HOP_SERVER_URL;

  public static boolean isEnabled(IVariables variables) {
    if (HOP_EXECUTION_ID == null) {
      return false;
    }
    return !HOP_SERVER_URL.isEmpty();
  }

  public static String getExecutionId() {
    return HOP_EXECUTION_ID;
  }

  public static void setExecutionId(String executionId) {
    if (HOP_EXECUTION_ID == null) {
      HOP_EXECUTION_ID = executionId;
    }
  }

  public static String getServerToken() {
    return HOP_SERVER_TOKEN;
  }

  public static void setServerToken(String serverToken) {
    if (HOP_SERVER_TOKEN == null) {
      HOP_SERVER_TOKEN = serverToken;
    }
  }

  public static String getHopServerUrl() {
    return HOP_SERVER_URL;
  }

  public static void setHopServerUrl(String hopServerUrl) {
    if (HOP_SERVER_URL == null) {
      HOP_SERVER_URL = hopServerUrl;
    }
  }
}
