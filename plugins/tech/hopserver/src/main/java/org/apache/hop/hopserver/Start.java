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

package org.apache.hop.hopserver;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hop.IExecutionConfiguration;
import org.apache.hop.core.Const;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.metadata.SerializableMetadataProvider;
import org.apache.hop.core.parameters.INamedParameterDefinitions;
import org.apache.hop.core.parameters.INamedParameters;
import org.apache.hop.core.parameters.UnknownParamException;
import org.apache.hop.core.plugins.JarCache;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.hopserver.dto.ExecutionBundleDto;
import org.apache.hop.hopserver.util.HopServerUtils;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.apache.hop.metadata.util.HopMetadataUtil;
import org.apache.hop.pipeline.PipelineExecutionConfiguration;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.workflow.WorkflowExecutionConfiguration;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.config.WorkflowRunConfiguration;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.apache.hop.workflow.engine.WorkflowEngineFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class Start implements Runnable, IHasHopMetadataProvider {
  private IVariables variables;
  private MultiMetadataProvider metadataProvider;
  private boolean finishedWithoutError;
  private String filename;
  private ILogChannel log;
  private String logLevel;
  private String realFilename;
  private String projectFolder;
  private String hopServerUrl;
  private String jwtToken;
  private ExecutionBundleDto executionBundleDto;
  private String realRunConfigurationName;

  private enum ExecutionStatus {
    CREATED,
    INITIALIZING,
    RUNNING,
    FAILED,
    FINISHED,
    FINISHED_WITH_ERRORS;
  }

  public Start() {
    this.variables = new Variables();
  }

  public static void main(String[] args) {
    Start start = new Start();

    if (args != null && args.length == 3) {
      start.hopServerUrl = args[0];
      start.jwtToken = args[1];
      start.logLevel = args[2];
    } else {
      System.err.println("Missing mandatory arguments");
      System.exit(2);
    }

    try {
      // Initialize the Hop environment: load plugins and more
      //
      HopEnvironment.init();

      // Picks up the system settings in the variables
      //
      start.buildVariableSpace();

      // Initialize the logging backend
      //
      HopLogStore.init();

      // Clear the jar file cache so that we don't waste memory...
      //
      JarCache.getInstance().clear();

      // Set up the metadata to use
      //
      start.metadataProvider = HopMetadataUtil.getStandardHopMetadataProvider(start.variables);

      // Start execution
      start.run();

    } catch (Exception e) {
      System.exit(2);
    }
  }

  /**
   * Calculate the new path of the file, this is done to avoid escaping the toplevel of the folder.
   *
   * @param destinationDir where the zipfile content needs to go
   * @param zipEntry to calculate the new filepath for
   * @return File to save the ZipEntry content to
   * @throws java.io.IOException when the filesystem location can not be reached
   */
  public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
    // We also drop the top level folder from the zipfile
    File destFile =
        new File(destinationDir, zipEntry.getName().substring(zipEntry.getName().indexOf("/", 1)));

    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }

  @Override
  public MultiMetadataProvider getMetadataProvider() {
    return metadataProvider;
  }

  @Override
  public void setMetadataProvider(MultiMetadataProvider multiMetadataProvider) {
    this.metadataProvider = multiMetadataProvider;
  }

  private void buildVariableSpace() {
    // Also grabs the system properties from hop.config.
    //
    variables = Variables.getADefaultVariableSpace();
  }

  public boolean isFinishedWithoutError() {
    return finishedWithoutError;
  }

  public void setFinishedWithoutError(boolean finishedWithoutError) {
    this.finishedWithoutError = finishedWithoutError;
  }

  @Override
  public void run() {
    log = new LogChannel("Start");
    log.setLogLevel(determineLogLevel());
    log.logDetailed("Start of execution");
    log.logDetailed("Server that started the process: " + hopServerUrl);

    if (!getBundle()) {
      updateExecutionStatus(ExecutionStatus.FAILED);
      System.exit(1);
    }

    filename = executionBundleDto.getFilename();

    if (!updateExecutionStatus(ExecutionStatus.INITIALIZING)) {
      System.exit(1);
    }

    if (isPipeline()) {
      runPipeline(log);
    }
    if (isWorkflow()) {
      runWorkflow(log);
    }

    // Cleanup directory
    try {
      FileUtils.deleteDirectory(new File(projectFolder));
    } catch (IOException e) {
      log.logError("Could not delete project directory: " + projectFolder);
    }
  }

  private boolean isPipeline() {
    if (StringUtils.isEmpty(filename)) {
      return false;
    }
    return filename.toLowerCase().endsWith(".hpl");
  }

  private boolean isWorkflow() {
    if (StringUtils.isEmpty(filename)) {
      return false;
    }
    return filename.toLowerCase().endsWith(".hwf");
  }

  private LogLevel determineLogLevel() {
    return LogLevel.getLogLevelForCode(variables.resolve(logLevel));
  }

  private void calculateRealFilename() {
    realFilename = variables.resolve(filename);
    realFilename = projectFolder + File.separator + realFilename;
    log.logBasic("Starting following filename: " + realFilename);
  }

  private boolean updateExecutionStatus(ExecutionStatus status) {
    String endpoint = hopServerUrl + "/v1/worker/updateStatus";
    log.logDetailed("updating execution status to: " + status + "on endpoint" + endpoint);

    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpPost httppost = new HttpPost(endpoint);
      List<NameValuePair> parameterPairs = new ArrayList<>();
      NameValuePair statusValuePair = new BasicNameValuePair("status", status.name());
      parameterPairs.add(statusValuePair);
      HttpEntity requestEntity = new UrlEncodedFormEntity(parameterPairs, StandardCharsets.UTF_8);
      httppost.setEntity(requestEntity);
      httppost.setHeader("Authorization", "Bearer " + jwtToken);

      // Execute and get the response.
      HttpResponse response = httpclient.execute(httppost);

      if (response.getStatusLine().getStatusCode() >= 400) {
        return false;
      }
    } catch (Exception e) {
      log.logError("Error updating status in server: ", e);
      return false;
    }
    return true;
  }

  private boolean getBundle() {
    String endpoint = hopServerUrl + "/v1/worker/executionBundle";
    log.logDetailed("Fetching Bundle from : " + endpoint);

    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(endpoint);
      httpGet.setHeader("Authorization", "Bearer " + jwtToken);

      // Execute and get the response.
      HttpResponse response = httpclient.execute(httpGet);

      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      executionBundleDto =
          objectMapper.readValue(response.getEntity().getContent(), ExecutionBundleDto.class);

      UUID uuid = UUID.randomUUID();
      String tmpDir = System.getProperty("java.io.tmpdir") + "/" + uuid.toString();
      setProjectFolder(tmpDir);
      log.logDetailed("project location: " + tmpDir);

      byte[] buffer = new byte[1024];
      ZipInputStream zis =
          new ZipInputStream(new ByteArrayInputStream(executionBundleDto.getBundle()) {});
      ZipEntry zipEntry = zis.getNextEntry();
      while (zipEntry != null) {
        File newFile = newFile(new File(tmpDir), zipEntry);
        if (zipEntry.isDirectory()) {
          if (!newFile.isDirectory() && !newFile.mkdirs()) {
            // Skip
          }
        } else {
          try (FileOutputStream stream = FileUtils.openOutputStream(newFile)) {
            int length;
            while ((length = zis.read(buffer)) > 0) {
              stream.write(buffer, 0, length);
            }
          } catch (Exception e) {
            log.logError("something has gone wrong:", e);
          }
        }
        zipEntry = zis.getNextEntry();
      }
      zis.closeEntry();
      zis.close();

      try (InputStream inputStream = HopVfs.getInputStream(projectFolder + "/metadata.json")) {
        String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        SerializableMetadataProvider exportedProvider = new SerializableMetadataProvider(json);
        metadataProvider.getProviders().add(exportedProvider);

        log.logBasic("Metadata provider is now: " + metadataProvider.getDescription());
      }

      if (response.getStatusLine().getStatusCode() >= 400) {
        return false;
      }
    } catch (Exception e) {
      log.logError("Error unzipping files: ", e);
      return false;
    }
    return true;
  }

  public String getHopServerUrl() {
    return hopServerUrl;
  }

  public void setHopServerUrl(String hopServerUrl) {
    this.hopServerUrl = hopServerUrl;
  }

  public String getJwtToken() {
    return jwtToken;
  }

  public void setJwtToken(String jwtToken) {
    this.jwtToken = jwtToken;
  }

  public String getProjectFolder() {
    return projectFolder;
  }

  public void setProjectFolder(String projectFolder) {
    this.projectFolder = projectFolder;
  }

  public IVariables getVariables() {
    return variables;
  }

  public void setVariables(IVariables variables) {
    this.variables = variables;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public ILogChannel getLog() {
    return log;
  }

  public void setLog(ILogChannel log) {
    this.log = log;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public String getRealFilename() {
    return realFilename;
  }

  public void setRealFilename(String realFilename) {
    this.realFilename = realFilename;
  }

  private void runPipeline(ILogChannel log) {

    try {
      calculateRealFilename();

      // Run the pipeline with the given filename
      //
      PipelineMeta pipelineMeta = new PipelineMeta(realFilename, metadataProvider, variables);

      // Configure the basic execution settings
      //
      PipelineExecutionConfiguration configuration = new PipelineExecutionConfiguration();

      // Overwrite if the user decided this
      //
      realRunConfigurationName = variables.resolve(executionBundleDto.getRunConfiguration());
      configuration.setRunConfiguration(realRunConfigurationName);
      configuration.setLogLevel(determineLogLevel());

      // Parse Variables and parameters
      parseParametersAndVariables(configuration, pipelineMeta);

      // Do we have a default run configuration?
      // That way the user doesn't have to specify the run configuration name
      //
      if (StringUtils.isEmpty(configuration.getRunConfiguration())) {
        PipelineRunConfiguration defaultRunConfiguration =
            PipelineRunConfiguration.findDefault(metadataProvider);
        if (defaultRunConfiguration != null) {
          configuration.setRunConfiguration(defaultRunConfiguration.getName());
        }
      }

      // Now run the pipeline using the run configuration
      //
      try {
        String pipelineRunConfigurationName =
            variables.resolve(configuration.getRunConfiguration());
        IPipelineEngine<PipelineMeta> pipeline =
            PipelineEngineFactory.createPipelineEngine(
                variables, pipelineRunConfigurationName, metadataProvider, pipelineMeta);
        pipeline.getPipelineMeta().setInternalHopVariables(pipeline);
        pipeline.initializeFrom(null);
        pipeline.setVariables(configuration.getVariablesMap());

        // configure the variables and parameters
        //
        pipeline.copyParametersFromDefinitions(pipelineMeta);
        configureParametersAndVariables(configuration, pipeline, pipeline);

        pipeline.setLogLevel(configuration.getLogLevel());
        pipeline.setMetadataProvider(metadataProvider);

        pipeline.activateParameters(pipeline);

        log.logMinimal("Starting pipeline: " + pipelineMeta.getFilename());
        // Run it!
        //
        updateExecutionStatus(ExecutionStatus.RUNNING);
        pipeline.prepareExecution();
        pipeline.startThreads();
        pipeline.waitUntilFinished();
        setFinishedWithoutError(pipeline.getResult().getNrErrors() == 0L);
        if (isFinishedWithoutError()) {
          updateExecutionStatus(ExecutionStatus.FINISHED);
        } else {
          updateExecutionStatus(ExecutionStatus.FINISHED_WITH_ERRORS);
        }
      } catch (Exception e) {
        updateExecutionStatus(ExecutionStatus.FAILED);
        log.logError("Error running pipeline locally", e);
      }
    } catch (Exception e) {
      updateExecutionStatus(ExecutionStatus.FAILED);
      log.logError("There was an error during execution of pipeline '" + filename + "'", e);
    }
  }

  private void parseParametersAndVariables(
      IExecutionConfiguration configuration, INamedParameterDefinitions namedParams) {
    try {
      String[] availableParameters = namedParams.listParameters();
      ObjectMapper mapper = new ObjectMapper();
      HashMap<String, String> map =
          mapper.readValue(new File(projectFolder + "/variables.json"), HashMap.class);

      for (Map.Entry<String, String> entry : map.entrySet())
        if (entry.getKey() != null) {
          // We can work with this.
          //
          if (Const.indexOfString(entry.getKey(), availableParameters) < 0) {
            // A variable
            //
            configuration.getVariablesMap().put(entry.getKey(), entry.getValue());
          } else {
            // A parameter
            //
            configuration.getParametersMap().put(entry.getKey(), entry.getValue());
          }
        }

      // Overwrite PROJECT_HOME
      configuration.getVariablesMap().put("PROJECT_HOME", projectFolder);
      configuration.getVariablesMap().put("HOP_SERVER_URL", hopServerUrl);
      HopServerUtils.setHopServerUrl(hopServerUrl);
      HopServerUtils.setExecutionId(executionBundleDto.getExecutionId().toString());
      HopServerUtils.setServerToken(jwtToken);

    } catch (Exception e) {
      log.logError("Error parsing parameters", e);
    }
  }

  private void configureParametersAndVariables(
      IExecutionConfiguration configuration, IVariables variables, INamedParameters namedParams) {

    // Copy variables over to the pipeline or workflow
    //
    variables.setVariables(configuration.getVariablesMap());

    // By default, we use the value from the current variables map:
    //
    for (String key : namedParams.listParameters()) {
      String value = variables.getVariable(key);
      if (StringUtils.isNotEmpty(value)) {
        try {
          namedParams.setParameterValue(key, value);
        } catch (UnknownParamException e) {
          log.logError("Unable to set parameter '" + key + "'", e);
        }
      }
    }

    // Possibly override with the parameter values set by the user (-p option)
    //
    for (String key : configuration.getParametersMap().keySet()) {
      String value = configuration.getParametersMap().get(key);
      try {
        namedParams.setParameterValue(key, value);
      } catch (UnknownParamException e) {
        log.logError("Unable to set parameter '" + key + "'", e);
      }
    }
  }

  private void runWorkflow(ILogChannel log) {
    try {
      calculateRealFilename();

      // Run the workflow with the given filename
      //
      WorkflowMeta workflowMeta = new WorkflowMeta(variables, realFilename, metadataProvider);

      // Configure the basic execution settings
      //
      WorkflowExecutionConfiguration configuration = new WorkflowExecutionConfiguration();

      // Overwrite the run configuration with optional command line options
      //
      realRunConfigurationName = variables.resolve(executionBundleDto.getRunConfiguration());
      configuration.setRunConfiguration(realRunConfigurationName);
      configuration.setLogLevel(determineLogLevel());

      // Do we have a default run configuration?
      // That way the user doesn't have to specify the run configuration name
      //
      if (StringUtils.isEmpty(configuration.getRunConfiguration())) {
        WorkflowRunConfiguration defaultRunConfiguration =
            WorkflowRunConfiguration.findDefault(metadataProvider);
        if (defaultRunConfiguration != null) {
          configuration.setRunConfiguration(defaultRunConfiguration.getName());
        }
      }

      // Start workflow at action
      if (!executionBundleDto.getStartAction().isEmpty()) {
        configuration.setStartActionName(executionBundleDto.getStartAction());
      }

      // Certain Hop plugins rely on this.
      //
      ExtensionPointHandler.callExtensionPoint(
          log,
          variables,
          HopExtensionPoint.HopGuiWorkflowBeforeStart.id,
          new Object[] {configuration, null, workflowMeta, null});

      // Parse Variables and parameters
      parseParametersAndVariables(configuration, workflowMeta);

      try {
        String runConfigurationName = variables.resolve(configuration.getRunConfiguration());
        // Create a logging object to push down the correct loglevel to the Workflow
        //
        LoggingObject workflowLog = new LoggingObject(log);
        workflowLog.setLogLevel(configuration.getLogLevel());

        IWorkflowEngine<WorkflowMeta> workflow =
            WorkflowEngineFactory.createWorkflowEngine(
                variables, runConfigurationName, metadataProvider, workflowMeta, workflowLog);
        workflow.getWorkflowMeta().setInternalHopVariables(workflow);
        workflow.setVariables(configuration.getVariablesMap());

        // Copy the parameter definitions from the metadata, with empty values
        //
        workflow.copyParametersFromDefinitions(workflowMeta);
        configureParametersAndVariables(configuration, workflow, workflow);

        // Also copy the parameter values over to the variables...
        //
        workflow.activateParameters(workflow);

        // If there is an alternative start action, pass it to the workflow
        //
        if (!Utils.isEmpty(configuration.getStartActionName())) {
          ActionMeta startActionMeta = workflowMeta.findAction(configuration.getStartActionName());

          if (startActionMeta == null) {
            log.logError("Error running workflow, specified start action not found");
          }
          workflow.setStartActionMeta(startActionMeta);
        }

        updateExecutionStatus(ExecutionStatus.RUNNING);
        log.logMinimal("Starting workflow: " + workflowMeta.getFilename());
        workflow.startExecution();
        setFinishedWithoutError(workflow.getResult().getResult());
        if (isFinishedWithoutError()) {
          updateExecutionStatus(ExecutionStatus.FINISHED);
        } else {
          updateExecutionStatus(ExecutionStatus.FINISHED_WITH_ERRORS);
        }
      } catch (Exception e) {
        updateExecutionStatus(ExecutionStatus.FAILED);
        log.logError("Error running workflow locally", e);
      }

    } catch (Exception e) {
      updateExecutionStatus(ExecutionStatus.FAILED);
      log.logError("There was an error during execution of workflow '" + filename + "'", e);
    }
  }
}
