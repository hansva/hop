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
 */

package org.apache.hop.pipeline.transforms.getfilenames;

import java.util.Objects;
import org.apache.hop.metadata.api.HopMetadataProperty;

public class FilterItem {

  /** The file filter type selection */
  @HopMetadataProperty(
      key = "filterfiletype",
      injectionKeyDescription = "GetFileNames.Injection.FilterItemTypeSelected.Label")
  private String fileTypeFilterSelection;

  public FilterItem() {}

  public FilterItem(String fileTypeFilterSelection) {
    this.fileTypeFilterSelection = fileTypeFilterSelection;
  }

  public String getFileTypeFilterSelection() {
    return fileTypeFilterSelection;
  }

  public void setFileTypeFilterSelection(String fileTypeFilterSelection) {
    this.fileTypeFilterSelection = fileTypeFilterSelection;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilterItem that = (FilterItem) o;
    return Objects.equals(fileTypeFilterSelection, that.fileTypeFilterSelection);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileTypeFilterSelection);
  }
}
