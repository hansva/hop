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

  private static String hopExecutionId;
  private static String hopServerToken;
  private static String hopServerUrl;
  private static final HopServerUtils instance = new HopServerUtils();

  private HopServerUtils() {
    if (instance != null) {
      throw new IllegalStateException("Already instantiated");
    }
  }

  public static synchronized HopServerUtils getInstance() {
    return instance;
  }

  public boolean isEnabled() {
    if (hopExecutionId == null || hopServerToken == null || hopServerUrl == null) {
      return false;
    }
    return true;
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
