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
import org.apache.hop.workflow.ActionResult;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

@ExtensionPoint(
    id = "HopServerWorkflowLoggingExtensionPoint",
    extensionPointId = "WorkflowStart",
    description = "Handle logging to Hop Server for a workflow")
public class WorkflowLoggingExtensionPoint
    implements IExtensionPoint<IWorkflowEngine<WorkflowMeta>> {

  private static final String WORKFLOW_START_DATE = "WORKFLOW_START_DATE";
  private static final String WORKFLOW_END_DATE = "WORKFLOW_END_DATE";

  private static final String EXECUTION_TYPE_WORKFLOW = LoggingObjectType.WORKFLOW.name();
  private static final String EXECUTION_TYPE_ACTION = LoggingObjectType.ACTION.name();
  private static final String DATE_FORMAT = "yyyy/MM/dd'T'HH:mm:ss.SSSZ";
  private static HopServerUtils hopServerUtils = HopServerUtils.getInstance();

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, IWorkflowEngine<WorkflowMeta> workflow)
      throws HopException {

    // See if logging is enabled
    //
    if (!hopServerUtils.isEnabled()) {
      return;
    }

    // Keep the start date
    //
    workflow.getExtensionDataMap().put(WORKFLOW_START_DATE, new Date());
    String serverHost = hopServerUtils.getHopServerUrl();
    String endpoint = serverHost + "/v1/worker/";

    try {
      workflow.addWorkflowStartedListener(
          workflowMetaIWorkflowEngine -> logWorkflow(log, workflow, endpoint, "Initializing"));

      // Periodic logging
      final Timer timer = new Timer();
      TimerTask timerTask =
          new TimerTask() {
            @Override
            public void run() {
              try {
                logWorkflow(log, workflow, endpoint, "Running");
              } catch (Exception e) {
                throw new RuntimeException(
                    "Unable to do interval logging for Workflow Log object", e);
              }
            }
          };
      timer.schedule(timerTask, 5 * 1000L, 5 * 1000L);

      workflow.addWorkflowFinishedListener(
          workflowMetaIWorkflowEngine -> {
            timer.cancel();
            workflow.getExtensionDataMap().put(WORKFLOW_END_DATE, new Date());
            logWorkflow(log, workflow, endpoint, "Finished");
          });

    } catch (Exception e) {
      // Let's not kill the workflow just yet, just log the error
      // otherwise: throw new HopException(...)
      //
      log.logError("Error logging to Hop Server:", e);
    }
  }

  private void logWorkflow(
      final ILogChannel log,
      final IWorkflowEngine<WorkflowMeta> workflow,
      final String endpoint,
      final String workflowStatus) {
    final WorkflowMeta workflowMeta = workflow.getWorkflowMeta();
    final ILogChannel channel = workflow.getLogChannel();
    final ILoggingObject parent = workflow.getParent();

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode rootNode = mapper.createObjectNode();
      int lastNrInLogStore = HopLogStore.getLastBufferLineNr();

      // Workflow information
      rootNode.put("executionId", hopServerUtils.getExecutionId());
      rootNode.put("type", EXECUTION_TYPE_WORKFLOW);
      rootNode.put("name", workflowMeta.getName());
      rootNode.put("status", workflowStatus);
      rootNode.put("description", workflowMeta.getDescription());
      rootNode.put("filename", workflowMeta.getFilename());
      rootNode.put("workflowPipelineLogChannelId", channel.getLogChannelId());
      ArrayNode logLinesNode = mapper.createArrayNode();
      List<HopLoggingEvent> loggingEvents =
          HopLogStore.getLogBufferFromTo(channel.getLogChannelId(), true, 0, lastNrInLogStore);
      for (int i = 0; i < lastNrInLogStore; i++) {
        ObjectNode logLineNode = mapper.createObjectNode();
        logLineNode.put("loggingLineNr", i);
        HopLoggingEvent loggingEvent = loggingEvents.get(i);
        logLineNode.put(
            "logDate", new SimpleDateFormat(DATE_FORMAT).format(loggingEvent.getTimeStamp()));
        logLineNode.put("logLevel", loggingEvent.getLevel().getDescription());
        logLineNode.put("message", loggingEvent.getMessage().toString());
        logLinesNode.add(logLineNode);
      }
      rootNode.put("log", logLinesNode.toPrettyString());
      if (parent != null) {
        rootNode.put("parentChannelId", parent.getLogChannelId());
      }
      rootNode.put(
          "startDate",
          new SimpleDateFormat(DATE_FORMAT)
              .format(workflow.getExtensionDataMap().get(WORKFLOW_START_DATE)));
      if (workflow.getExtensionDataMap().get(WORKFLOW_END_DATE) != null) {
        rootNode.put(
            "endDate",
            new SimpleDateFormat(DATE_FORMAT)
                .format(workflow.getExtensionDataMap().get(WORKFLOW_END_DATE)));
      }
      rootNode.put("logDate", new SimpleDateFormat(DATE_FORMAT).format(new Date()));

      // Action information
      ArrayNode actionNodes = mapper.createArrayNode();

      // Get Active Action Meta
      for (ActionMeta actionMeta : workflow.getActiveActions()) {
        ObjectNode actionNode = mapper.createObjectNode();
        IAction action = actionMeta.getAction();
        String actionLoggingText =
            HopLogStore.getAppender()
                .getBuffer(action.getLogChannel().getLogChannelId(), false)
                .toString();
        actionNode.put("type", EXECUTION_TYPE_ACTION);
        actionNode.put("name", action.getName());
        actionNode.put("pluginId", action.getPluginId());
        actionNode.put("description", action.getDescription());
        actionNode.put("actionTransformChannelId", action.getLogChannel().getLogChannelId());
        actionNode.put("status", "Running");
        actionNode.put("log", actionLoggingText);
        actionNode.put(
            "startDate",
            new SimpleDateFormat(DATE_FORMAT)
                .format(workflow.getExtensionDataMap().get(WORKFLOW_START_DATE)));
        if (workflow.getExtensionDataMap().get(WORKFLOW_END_DATE) != null) {
          actionNode.put(
              "endDate",
              new SimpleDateFormat(DATE_FORMAT)
                  .format(workflow.getExtensionDataMap().get(WORKFLOW_END_DATE)));
        }
        actionNode.put("logDate", new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        actionNodes.add(actionNode);
      }

      // Add finished Actions
      for (ActionResult actionResult : workflow.getActionResults()) {
        ObjectNode actionNode = mapper.createObjectNode();
        // Find Action Meta
        ActionMeta foundActionMeta =
            workflowMeta.getActions().stream()
                .filter(actionMeta -> actionResult.getActionName().equals(actionMeta.getName()))
                .findFirst()
                .orElse(null);
        IAction action = foundActionMeta.getAction();

        String actionLoggingText =
            HopLogStore.getAppender().getBuffer(actionResult.getLogChannelId(), false).toString();
        actionNode.put("type", EXECUTION_TYPE_ACTION);
        actionNode.put("name", actionResult.getActionName());
        actionNode.put("pluginId", action.getPluginId());
        actionNode.put("description", action.getDescription());
        actionNode.put("actionTransformChannelId", actionResult.getLogChannelId());
        if (actionResult.getResult().getNrErrors() > 0) {
          actionNode.put("status", "Stopped");
        } else {
          actionNode.put("status", "Finished");
        }
        actionNode.put("errors", actionResult.getResult().getNrErrors());
        actionNode.put("linesRead", actionResult.getResult().getNrLinesRead());
        actionNode.put("linesWritten", actionResult.getResult().getNrLinesWritten());
        actionNode.put("linesInput", actionResult.getResult().getNrLinesInput());
        actionNode.put("linesOutput", actionResult.getResult().getNrLinesOutput());
        actionNode.put("linesRejected", actionResult.getResult().getNrLinesRejected());
        actionNode.put("log", actionLoggingText);
        actionNode.put(
            "startDate",
            new SimpleDateFormat(DATE_FORMAT)
                .format(workflow.getExtensionDataMap().get(WORKFLOW_START_DATE)));
        if (workflow.getExtensionDataMap().get(WORKFLOW_END_DATE) != null) {
          actionNode.put(
              "endDate",
              new SimpleDateFormat(DATE_FORMAT)
                  .format(workflow.getExtensionDataMap().get(WORKFLOW_END_DATE)));
        }
        actionNode.put(
            "logDate", new SimpleDateFormat(DATE_FORMAT).format(actionResult.getLogDate()));
        actionNode.put("durationMs", actionResult.getResult().getElapsedTimeMillis());
        actionNodes.add(actionNode);
      }

      rootNode.put("children", actionNodes);

      String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

      HttpClient httpclient = HttpClients.createDefault();
      HttpPost httppost = new HttpPost(endpoint);
      httppost.setHeader("Authorization", "Bearer " + hopServerUtils.getServerToken());

      HttpEntity requestEntity = new StringEntity(jsonString, ContentType.APPLICATION_JSON);
      httppost.setEntity(requestEntity);

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
