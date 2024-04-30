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

package org.apache.hop.hopserver.logging.xp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.HopLoggingEvent;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.hopserver.util.HopServerUtils;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaDataCombi;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

@ExtensionPoint(
    id = "HopServerPipelineLoggingExtensionPoint",
    extensionPointId = "PipelineStartThreads",
    description = "Handle logging to Hop Server for a pipeline")
public class PipelineLoggingExtensionPoint
    implements IExtensionPoint<IPipelineEngine<PipelineMeta>> {

  private static final String PIPELINE_START_DATE = "PIPELINE_START_DATE";
  private static final String PIPELINE_END_DATE = "PIPELINE_END_DATE";
  private static final String EXECUTION_TYPE_PIPELINE = LoggingObjectType.PIPELINE.name();
  private static final String EXECUTION_TYPE_TRANSFORM = LoggingObjectType.TRANSFORM.name();
  private static final String DATE_FORMAT = "yyyy/MM/dd'T'HH:mm:ss.SSSZ";

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, IPipelineEngine<PipelineMeta> pipeline)
      throws HopException {

    // See if logging is enabled
    //
    if (!HopServerUtils.isEnabled(pipeline)) {
      return;
    }

    // Is the pipeline doing a preview? We don't want to log in that case.
    //
    String previewVariable = pipeline.getVariable(IPipelineEngine.PIPELINE_IN_PREVIEW_MODE, "N");
    if (Const.toBoolean(previewVariable)) {
      return;
    }

    // Keep the start date
    //
    pipeline.getExtensionDataMap().put(PIPELINE_START_DATE, new Date());

    String serverHost = HopServerUtils.getHopServerUrl();
    String endpoint = serverHost + "/v1/worker/";

    try {

      // Log pipeline at the beginning

      pipeline.addExecutionStartedListener(
          pipelineEngine -> logPipeline(log, pipeline, endpoint, Pipeline.STRING_INITIALIZING));

      // Periodic logging
      final Timer timer = new Timer();
      TimerTask timerTask =
          new TimerTask() {
            @Override
            public void run() {
              try {
                logPipeline(log, pipeline, endpoint, Pipeline.STRING_RUNNING);
              } catch (Exception e) {
                throw new RuntimeException(
                    "Unable to do interval logging for Pipeline Log object", e);
              }
            }
          };
      timer.schedule(timerTask, 5 * 1000L, 5 * 1000L);

      // Log pipeline Finished listener at the end
      pipeline.addExecutionFinishedListener(
          pipelineEngine -> {
            try {
              timer.cancel();
              pipeline.getExtensionDataMap().put(PIPELINE_END_DATE, new Date());
              logPipeline(log, pipeline, endpoint, Pipeline.STRING_FINISHED);
            } catch (Exception e) {
              throw new RuntimeException(
                  "Unable to do interval logging for Pipeline Log object", e);
            }
          });

      pipeline.addExecutionStoppedListener(
          pipelineEngine -> {
            try {
              timer.cancel();
              pipeline.getExtensionDataMap().put(PIPELINE_END_DATE, new Date());
              logPipeline(log, pipeline, endpoint, Pipeline.STRING_FINISHED);
            } catch (Exception e) {
              throw new RuntimeException(
                  "Unable to do interval logging for Pipeline Log object", e);
            }
          });

    } catch (Exception e) {
      // Let's not kill the pipeline just yet, just log the error
      // otherwise: throw new HopException(...)
      //
      log.logError("Error logging to Hop Server:", e);
    }
  }

  private void logPipeline(
      final ILogChannel log,
      final IPipelineEngine<PipelineMeta> pipeline,
      final String endpoint,
      final String pipelineStatus) {
    final PipelineMeta pipelineMeta = pipeline.getPipelineMeta();
    final ILogChannel channel = pipeline.getLogChannel();
    final ILoggingObject parent = pipeline.getParent();

    try {

      // Pipeline information
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode rootNode = mapper.createObjectNode();
      int lastNrInLogStore = HopLogStore.getLastBufferLineNr();

      rootNode.put("executionId", HopServerUtils.getExecutionId());
      rootNode.put("type", EXECUTION_TYPE_PIPELINE);
      rootNode.put("name", pipelineMeta.getName());
      rootNode.put("description", pipelineMeta.getDescription());
      rootNode.put("filename", pipelineMeta.getFilename());
      rootNode.put("workflowPipelineLogChannelId", channel.getLogChannelId());
      rootNode.put("status", pipelineStatus);
      rootNode.put("lastLoggingLineNr", lastNrInLogStore);
      ArrayNode logLinesNode = mapper.createArrayNode();
      for (int i = 0; i < lastNrInLogStore; i++) {
        ObjectNode logLineNode = mapper.createObjectNode();
        logLineNode.put("loggingLineNr", i);
        List<HopLoggingEvent> loggingEvents =
            HopLogStore.getLogBufferFromTo(channel.getLogChannelId(), true, i, i + 1);
        for (HopLoggingEvent loggingEvent : loggingEvents) {
          logLineNode.put(
              "logDate", new SimpleDateFormat(DATE_FORMAT).format(loggingEvent.getTimeStamp()));
          logLineNode.put("logLevel", loggingEvent.getLevel().getDescription());
          logLineNode.put("message", loggingEvent.getMessage().toString());
        }
        logLinesNode.add(logLineNode);
      }
      rootNode.put("log", logLinesNode.toPrettyString());
      if (parent != null) {
        rootNode.put("parentChannelId", parent.getLogChannelId());
      }
      rootNode.put(
          "startDate",
          new SimpleDateFormat(DATE_FORMAT)
              .format(pipeline.getExtensionDataMap().get(PIPELINE_START_DATE)));
      if (pipeline.getExtensionDataMap().get(PIPELINE_END_DATE) != null) {
        rootNode.put(
            "endDate",
            new SimpleDateFormat(DATE_FORMAT)
                .format(pipeline.getExtensionDataMap().get(PIPELINE_END_DATE)));
      }
      rootNode.put("logDate", new SimpleDateFormat(DATE_FORMAT).format(new Date()));
      // Transform information
      ArrayNode transformNodes = mapper.createArrayNode();

      for (TransformMetaDataCombi transformMetaDataCombi : ((Pipeline) pipeline).getTransforms()) {
        String transformLoggingText =
            HopLogStore.getAppender()
                .getBuffer(
                    transformMetaDataCombi.transform.getLogChannel().getLogChannelId(), false)
                .toString();
        TransformMeta transformMeta = transformMetaDataCombi.transformMeta;
        ObjectNode transformNode = mapper.createObjectNode();
        transformNode.put("type", EXECUTION_TYPE_TRANSFORM);
        transformNode.put("name", transformMeta.getName());
        transformNode.put("description", transformMeta.getDescription());
        transformNode.put("pluginId", transformMeta.getPluginId());
        transformNode.put("copies", transformMeta.getCopies(pipeline));
        transformNode.put(
            "actionTransformChannelId",
            transformMetaDataCombi.transform.getLogChannel().getLogChannelId());
        transformNode.put("copy", transformMetaDataCombi.copy);
        transformNode.put("status", transformMetaDataCombi.transform.getStatus().getDescription());
        transformNode.put("errors", transformMetaDataCombi.transform.getErrors());
        transformNode.put("linesRead", transformMetaDataCombi.transform.getLinesRead());
        transformNode.put("linesWritten", transformMetaDataCombi.transform.getLinesWritten());
        transformNode.put("linesInput", transformMetaDataCombi.transform.getLinesInput());
        transformNode.put("linesOutput", transformMetaDataCombi.transform.getLinesOutput());
        transformNode.put("linesRejected", transformMetaDataCombi.transform.getLinesRejected());
        transformNode.put("paused", transformMetaDataCombi.transform.isPaused());
        transformNode.put("stopped", transformMetaDataCombi.transform.isStopped());

        if (transformMetaDataCombi.transform.getExecutionStartDate() != null) {
          transformNode.put(
              "startDate",
              new SimpleDateFormat(DATE_FORMAT)
                  .format(transformMetaDataCombi.transform.getExecutionStartDate()));
        }
        if (transformMetaDataCombi.transform.getExecutionEndDate() != null) {
          transformNode.put(
              "endDate",
              new SimpleDateFormat(DATE_FORMAT)
                  .format(transformMetaDataCombi.transform.getExecutionEndDate()));
        }
        transformNode.put("durationMs", transformMetaDataCombi.transform.getExecutionDuration());
        transformNode.put("logDate", new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        transformNode.put("log", transformLoggingText);

        transformNodes.add(transformNode);
      }

      if (transformNodes.isEmpty()) {
        return;
      }

      rootNode.put("children", transformNodes);

      String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

      HttpClient httpclient = HttpClients.createDefault();
      HttpPost httppost = new HttpPost(endpoint);

      HttpEntity requestEntity = new StringEntity(jsonString, ContentType.APPLICATION_JSON);
      httppost.setEntity(requestEntity);
      httppost.setHeader("Authorization", "Bearer " + HopServerUtils.getServerToken());

      // Execute and get the response.
      HttpResponse response = httpclient.execute(httppost);

      if (response.getStatusLine().getStatusCode() >= 400) {
        log.logError(
            "The server returned an error status code : "
                + response.getStatusLine().getStatusCode()
                + " - "
                + response.getStatusLine().getReasonPhrase());
      }

    } catch (Exception e) {
      log.logError("Unexpected error occurred: ", e);
    }
  }
}
