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

package org.apache.hop.pipeline.transforms.uniquerowsbyhashset;

import java.util.HashSet;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

@SuppressWarnings("java:S1104")
public class UniqueRowsByHashSetData extends BaseTransformData implements ITransformData {
  public IRowMeta outputRowMeta;
  public IRowMeta inputRowMeta;
  public boolean storeValues;
  public int[] fieldnrs;
  public String compareFields;
  public String realErrorDescription;
  boolean sendDuplicateRows;

  public HashSet<RowKey> seen = new HashSet<>();

  public UniqueRowsByHashSetData() {
    super();
  }

  public void clearHashSet() {
    sendDuplicateRows = false;
    compareFields = null;
    realErrorDescription = null;
  }
}
