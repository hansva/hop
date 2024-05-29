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

  public String hopExecutionId;
  public String hopServerToken;
  public String hopServerUrl;
  private static HopServerUtils INSTANCE = new HopServerUtils();
  ;

  private HopServerUtils() {
    if (INSTANCE != null) {
      throw new IllegalStateException("Already instantiated");
    }
  }

  public static synchronized HopServerUtils getInstance() {
    return INSTANCE;
  }

  //  public  void setParameters(
  //      String HOP_EXECUTION_ID, String HOP_SERVER_TOKEN, String HOP_SERVER_URL) {
  //    HOP_EXECUTION_ID = HOP_EXECUTION_ID;
  //    instance.HOP_SERVER_TOKEN = HOP_SERVER_TOKEN;
  //    instance.HOP_SERVER_URL = HOP_SERVER_URL;
  //  }

  public boolean isEnabled() {
    return !this.hopServerUrl.isEmpty() && !this.hopExecutionId.isEmpty();
  }

  public String getExecutionId() {
    return hopExecutionId;
  }

  public void setExecutionId(String hopExecutionId) {
    this.hopExecutionId = hopExecutionId;
  }

  public String getServerToken() {
    return hopServerToken;
  }

  public void setServerToken(String hopServerToken) {
    this.hopServerToken = hopServerToken;
  }

  public String getHopServerUrl() {
    return hopServerUrl;
  }

  public void setHopServerUrl(String hopServerUrl) {
    this.hopServerUrl = hopServerUrl;
  }
}
