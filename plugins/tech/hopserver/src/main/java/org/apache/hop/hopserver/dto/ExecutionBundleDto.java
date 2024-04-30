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

package org.apache.hop.hopserver.dto;

public class ExecutionBundleDto {
  private Long executionId;
  private String filename;
  private String startAction;
  private String logLevel;
  private String runConfiguration;
  private byte[] bundle;

  public ExecutionBundleDto(
      Long executionId,
      String filename,
      String startAction,
      String logLevel,
      String runConfiguration,
      byte[] bundle) {
    this.executionId = executionId;
    this.filename = filename;
    this.startAction = startAction;
    this.logLevel = logLevel;
    this.runConfiguration = runConfiguration;
    this.bundle = bundle;
  }

  public ExecutionBundleDto() {}

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public String getRunConfiguration() {
    return runConfiguration;
  }

  public void setRunConfiguration(String runConfiguration) {
    this.runConfiguration = runConfiguration;
  }

  public byte[] getBundle() {
    return bundle;
  }

  public void setBundle(byte[] bundle) {
    this.bundle = bundle;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public void setExecutionId(Long executionId) {
    this.executionId = executionId;
  }

  public String getStartAction() {
    return startAction;
  }

  public void setStartAction(String startAction) {
    this.startAction = startAction;
  }
}
