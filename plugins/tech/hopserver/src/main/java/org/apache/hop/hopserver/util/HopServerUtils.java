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

public final class HopServerUtils {

  public static String hopExecutionId;
  public static String hopServerToken;
  public static String hopServerUrl;
  private static HopServerUtils INSTANCE = new HopServerUtils();

  private HopServerUtils() {
    if (INSTANCE != null) {
      throw new IllegalStateException("Already instantiated");
    }
  }

  public static synchronized HopServerUtils getInstance() {
    return INSTANCE;
  }

  public boolean isEnabled() {
    return !this.hopServerUrl.isEmpty() && !this.hopExecutionId.isEmpty();
  }

  public static String getExecutionId() {
    return hopExecutionId;
  }

  public static void setExecutionId(String hopExecutionId) {
    HopServerUtils.hopExecutionId = hopExecutionId;
  }

  public static String getServerToken() {
    return hopServerToken;
  }

  public static void setServerToken(String hopServerToken) {
    HopServerUtils.hopServerToken = hopServerToken;
  }

  public static String getHopServerUrl() {
    return hopServerUrl;
  }

  public static void setHopServerUrl(String hopServerUrl) {
    HopServerUtils.hopServerUrl = hopServerUrl;
  }
}
