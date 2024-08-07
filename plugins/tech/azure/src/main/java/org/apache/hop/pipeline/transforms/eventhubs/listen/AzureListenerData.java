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

package org.apache.hop.pipeline.transforms.eventhubs.listen;

import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.RowProducer;
import org.apache.hop.pipeline.SingleThreadedPipelineExecutor;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

@SuppressWarnings("java:S1104")
public class AzureListenerData extends BaseTransformData implements ITransformData {

  public IRowMeta outputRowMeta;

  public ConnectionStringBuilder connectionStringBuilder;
  public ScheduledExecutorService executorService;
  public EventHubClient eventHubClient;
  public int batchSize;
  public int prefetchSize;
  public LinkedList<EventData> list;
  public String outputField;
  public String partitionIdField;
  public String offsetField;
  public String sequenceNumberField;
  public String hostField;
  public String enqueuedTimeField;

  public PipelineMeta sttPipelineMeta;
  public Pipeline sttPipeline;
  public SingleThreadedPipelineExecutor sttExecutor;
  public boolean stt = false;
  public RowProducer sttRowProducer;
  public long sttMaxWaitTime;
}
