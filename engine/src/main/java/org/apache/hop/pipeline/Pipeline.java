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

package org.apache.hop.pipeline;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.BIT_STATUS_SUM;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.FINISHED;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.INITIALIZING;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.PAUSED;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.PREPARING;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.RUNNING;
import static org.apache.hop.pipeline.Pipeline.BitMaskStatus.STOPPED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.BlockingBatchingRowSet;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.Const;
import org.apache.hop.core.IExecutor;
import org.apache.hop.core.IExtensionData;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.QueueRowSet;
import org.apache.hop.core.Result;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.exception.HopPipelineException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.IHasLogChannel;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LoggingHierarchy;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.LoggingRegistry;
import org.apache.hop.core.logging.Metrics;
import org.apache.hop.core.parameters.DuplicateParamException;
import org.apache.hop.core.parameters.INamedParameterDefinitions;
import org.apache.hop.core.parameters.INamedParameters;
import org.apache.hop.core.parameters.NamedParameters;
import org.apache.hop.core.parameters.UnknownParamException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowBuffer;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.util.ExecutorUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.execution.sampler.IExecutionDataSampler;
import org.apache.hop.execution.sampler.IExecutionDataSamplerStore;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.partition.PartitionSchema;
import org.apache.hop.pipeline.config.IPipelineEngineRunConfiguration;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.engine.EngineComponent.ComponentExecutionStatus;
import org.apache.hop.pipeline.engine.EngineMetric;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IEngineMetric;
import org.apache.hop.pipeline.engine.IPipelineComponentRowsReceived;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engines.EmptyPipelineRunConfiguration;
import org.apache.hop.pipeline.performance.PerformanceSnapShot;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.ITransformFinishedListener;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transform.RunThread;
import org.apache.hop.pipeline.transform.TransformInitThread;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaDataCombi;
import org.apache.hop.pipeline.transform.TransformPartitioningMeta;
import org.apache.hop.pipeline.transform.TransformStatus;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/**
 * This class represents the information and operations associated with the execution of a Pipeline.
 * It loads, instantiates, initializes, runs, and monitors the execution of the pipeline contained
 * in the specified PipelineMeta object.
 */
public abstract class Pipeline
    implements IVariables,
        INamedParameters,
        IHasLogChannel,
        ILoggingObject,
        IExecutor,
        IExtensionData,
        IPipelineEngine<PipelineMeta> {

  public static final String METRIC_NAME_INPUT = "input";
  public static final String METRIC_NAME_OUTPUT = "output";
  public static final String METRIC_NAME_ERROR = "error";
  public static final String METRIC_NAME_READ = "read";
  public static final String METRIC_NAME_WRITTEN = "written";
  public static final String METRIC_NAME_UPDATED = "updated";
  public static final String METRIC_NAME_REJECTED = "rejected";
  public static final String METRIC_NAME_BUFFER_IN = "buffer_in";
  public static final String METRIC_NAME_BUFFER_OUT = "buffer_out";
  public static final String METRIC_NAME_FLUSH_BUFFER = "flush_buffer";
  public static final String METRIC_NAME_INIT = "init";

  /** The package name, used for internationalization of messages. */
  private static final Class<?> PKG = Pipeline.class;

  protected String pluginId;
  protected PipelineRunConfiguration pipelineRunConfiguration;

  /** The log channel interface. */
  protected ILogChannel log;

  /** The log level. */
  protected LogLevel logLevel = LogLevel.BASIC;

  /** The container object id. */
  protected String containerObjectId;

  /** The log commit size. */
  protected int logCommitSize = 10;

  /** The pipeline metadata to execute. */
  protected PipelineMeta pipelineMeta;

  /** The MetaStore to use */
  protected IHopMetadataProvider metadataProvider;

  /**
   * The workflow that's launching this pipeline. This gives us access to the whole chain, including
   * the parent variables, etc.
   */
  private IWorkflowEngine<WorkflowMeta> parentWorkflow;

  /** The pipeline that is executing this pipeline in case of mappings. */
  private IPipelineEngine<PipelineMeta> parentPipeline;

  /** The parent logging object interface (this could be a pipeline or a workflow). */
  private ILoggingObject parent;

  /** Indicates that we want to do a topological sort of the transforms in a GUI. */
  private boolean sortingTransformsTopologically;

  /** Indicates that we are running in preview mode... */
  private boolean preview;

  /** Keeps track of when this pipeline started preparation */
  private Date executionStartDate;

  /** Keeps track of when this pipeline ended preparation */
  private Date executionEndDate;

  /** The variable bindings for the pipeline. */
  private IVariables variables = new Variables();

  /** A list of all the row sets. */
  public List<IRowSet> rowsets;

  /** A list of all the transforms. */
  private List<TransformMetaDataCombi> transforms;

  /** Constant indicating a dispatch type of 1-to-1. */
  public static final int TYPE_DISP_1_1 = 1;

  /** Constant indicating a dispatch type of 1-to-N. */
  public static final int TYPE_DISP_1_N = 2;

  /** Constant indicating a dispatch type of N-to-1. */
  public static final int TYPE_DISP_N_1 = 3;

  /** Constant indicating a dispatch type of N-to-N. */
  public static final int TYPE_DISP_N_N = 4;

  /** Constant indicating a dispatch type of N-to-M. */
  public static final int TYPE_DISP_N_M = 5;

  /** Constant indicating a pipeline status of Finished. */
  public static final String STRING_FINISHED = "Finished";

  /** Constant indicating a pipeline status of Finished (with errors). */
  public static final String STRING_FINISHED_WITH_ERRORS = "Finished (with errors)";

  /** Constant indicating a pipeline status of Running. */
  public static final String STRING_RUNNING = "Running";

  /** Constant indicating a pipeline status of Paused. */
  public static final String STRING_PAUSED = "Paused";

  /** Constant indicating a pipeline status of Preparing for execution. */
  public static final String STRING_PREPARING = "Preparing executing";

  /** Constant indicating a pipeline status of Initializing. */
  public static final String STRING_INITIALIZING = "Initializing";

  /** Constant indicating a pipeline status of Waiting. */
  public static final String STRING_WAITING = "Waiting";

  /** Constant indicating a pipeline status of Stopped. */
  public static final String STRING_STOPPED = "Stopped";

  /** Constant indicating a pipeline status of Stopped (with errors). */
  public static final String STRING_STOPPED_WITH_ERRORS = "Stopped (with errors)";

  /** Constant indicating a pipeline status of Halting. */
  public static final String STRING_HALTING = "Halting";

  /**
   * Constant specifying a filename containing XML to inject into a ZIP file created during resource
   * export.
   */
  public static final String CONFIGURATION_IN_EXPORT_FILENAME =
      "__pipeline_execution_configuration__.xml";

  /** Whether safe mode is enabled. */
  private boolean safeModeEnabled;

  /** Int value for storage pipeline statuses */
  private final AtomicInteger status;

  /** Boolean to check if pipeline is already stopped */
  private final AtomicBoolean isAlreadyStopped = new AtomicBoolean(false);

  /** Plugins can use this to add additional data samplers to the pipeline. */
  protected List<IExecutionDataSampler<? extends IExecutionDataSamplerStore>> dataSamplers;

  /**
   * This enum stores bit masks which are used to manipulate with statuses over field {@link
   * Pipeline#status}
   */
  enum BitMaskStatus {
    RUNNING(1),
    INITIALIZING(2),
    PREPARING(4),
    STOPPED(8),
    FINISHED(16),
    PAUSED(32);

    private final int mask;
    // the sum of status masks
    public static final int BIT_STATUS_SUM = 63;

    BitMaskStatus(int mask) {
      this.mask = mask;
    }
  }

  /** The number of errors that have occurred during execution of the pipeline. */
  private final AtomicInteger errors;

  /** Whether the pipeline is ready to start. */
  private boolean readyToStart;

  /** Transform performance snapshots. */
  private Map<String, List<PerformanceSnapShot>> transformPerformanceSnapShots;

  /** The transform performance snapshot timer. */
  private Timer transformPerformanceSnapShotTimer;

  /** A list of started listeners attached to the pipeline. */
  private List<IExecutionStartedListener<IPipelineEngine<PipelineMeta>>> executionStartedListeners;

  /** A list of finished listeners attached to the pipeline. */
  private List<IExecutionFinishedListener<IPipelineEngine<PipelineMeta>>>
      executionFinishedListeners;

  /** A list of stop-event listeners attached to the pipeline. */
  private List<IExecutionStoppedListener<IPipelineEngine<PipelineMeta>>> executionStoppedListeners;

  /** The number of finished transforms. */
  private int nrOfFinishedTransforms;

  /** The named parameters. */
  private final INamedParameters namedParams = new NamedParameters();

  /** The pipeline log table database connection. */
  private Database pipelineLogTableDatabaseConnection;

  /** The transform performance snapshot sequence number. */
  private AtomicInteger transformPerformanceSnapshotSeqNr;

  /** The last written transform performance sequence number. */
  private final int lastWrittenTransformPerformanceSequenceNr;

  /** The last transform performance snapshot sequence number added. */
  private int lastTransformPerformanceSnapshotSeqNrAdded;

  /** The active sub-pipelines. */
  private Map<String, IPipelineEngine> activeSubPipelines;

  /** The active subjobs */
  private Map<String, IWorkflowEngine<WorkflowMeta>> activeSubWorkflows;

  /** The transform performance snapshot size limit. */
  private int transformPerformanceSnapshotSizeLimit;

  /** The wait until finished method need this blocking queue. */
  private ArrayBlockingQueue<Object> pipelineWaitUntilFinishedBlockingQueue;

  /** The name of the executing server */
  private String executingServer;

  /** The name of the executing user */
  private String executingUser;

  private Result previousResult;

  protected List<RowMetaAndData> resultRows;

  protected List<ResultFile> resultFiles;

  /** The command line arguments for the pipeline. */
  protected String[] arguments;

  private HttpServletResponse servletResponse;

  private HttpServletRequest servletRequest;

  private final Map<String, Object> extensionDataMap;

  protected int rowSetSize;

  /** Whether the feedback is shown. */
  protected boolean feedbackShown;

  /** The feedback size. */
  protected int feedbackSize;

  /** Instantiates a new pipeline. */
  public Pipeline() {

    log = LogChannel.GENERAL;

    status = new AtomicInteger();

    executionStartedListeners = Collections.synchronizedList(new ArrayList<>());
    executionFinishedListeners = Collections.synchronizedList(new ArrayList<>());
    executionStoppedListeners = Collections.synchronizedList(new ArrayList<>());

    errors = new AtomicInteger(0);

    transformPerformanceSnapshotSeqNr = new AtomicInteger(0);
    lastWrittenTransformPerformanceSequenceNr = 0;

    activeSubPipelines = new ConcurrentHashMap<>();
    activeSubWorkflows = new HashMap<>();

    resultRows = new ArrayList<>();
    resultFiles = new ArrayList<>();

    extensionDataMap = new HashMap<>();

    rowSetSize = Const.ROWS_IN_ROWSET;

    dataSamplers = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * Initializes a pipeline from pipeline meta-data defined in memory.
   *
   * @param pipelineMeta the pipeline meta-data to use.
   */
  public Pipeline(PipelineMeta pipelineMeta) {
    this(pipelineMeta, new Variables(), null);
  }

  /**
   * Initializes a pipeline from pipeline meta-data defined in memory. Also take into account the
   * parent log channel interface (workflow or pipeline) for logging lineage purposes.
   *
   * @param pipelineMeta the pipeline meta-data to use.
   * @param variables The variables to inherit from
   * @param parent the parent workflow that is executing this pipeline
   */
  public Pipeline(PipelineMeta pipelineMeta, IVariables variables, ILoggingObject parent) {
    this();

    this.pipelineMeta = pipelineMeta;
    this.metadataProvider = pipelineMeta.getMetadataProvider();

    setParent(parent);

    initializeFrom(variables);

    this.log = new LogChannel(this, parent);
    this.logLevel = log.getLogLevel();
    this.containerObjectId = UUID.randomUUID().toString();
  }

  /**
   * Sets the parent logging object.
   *
   * @param parent the new parent
   */
  @Override
  public void setParent(ILoggingObject parent) {
    this.parent = parent;
  }

  /** Sets the default log commit size. */
  private void setDefaultLogCommitSize() {
    String propLogCommitSize = this.getVariable("hop.log.commit.size");
    if (propLogCommitSize != null) {
      // override the logCommit variable
      try {
        logCommitSize = Integer.parseInt(propLogCommitSize);
      } catch (Exception ignored) {
        logCommitSize = 10; // ignore parsing error and default to 10
      }
    }
  }

  /**
   * Gets the log channel interface for the pipeline.
   *
   * @return the log channel
   * @see IHasLogChannel#getLogChannel()
   */
  @Override
  public ILogChannel getLogChannel() {
    return log;
  }

  /**
   * Sets the log channel interface for the pipeline.
   *
   * @param log the new log channel interface
   */
  @Override
  public void setLogChannel(ILogChannel log) {
    this.log = log;
  }

  /**
   * Gets the name of the pipeline.
   *
   * @return the pipeline name
   */
  public String getName() {
    if (pipelineMeta == null) {
      return null;
    }

    return pipelineMeta.getName();
  }

  /**
   * Instantiates a new pipeline using any of the provided parameters including the variable
   * bindings, a name and a filename. This contstructor loads the specified pipeline from a file.
   *
   * @param parent the parent variable variables and named params
   * @param name the name of the pipeline
   * @param filename the filename containing the pipeline definition
   * @param metadataProvider The MetaStore to use when referencing metadata objects
   * @throws HopException if any error occurs during loading, parsing, or creation of the pipeline
   */
  public <Parent extends IVariables & INamedParameters> Pipeline(
      Parent parent, String name, String filename, IHopMetadataProvider metadataProvider)
      throws HopException {
    this();

    this.metadataProvider = metadataProvider;

    try {
      pipelineMeta = new PipelineMeta(filename, metadataProvider, this);

      this.log = new LogChannel(pipelineMeta);

      initializeFrom(parent);

      this.activateParameters(this);

      this.setDefaultLogCommitSize();
    } catch (HopException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "Pipeline.Exception.UnableToOpenPipeline", name), e);
    }
  }

  /**
   * Executes the pipeline. This method will prepare the pipeline for execution and then start all
   * the threads associated with the pipeline and its transforms.
   *
   * @throws HopException if the pipeline could not be prepared (initialized)
   */
  @Override
  public void execute() throws HopException {
    prepareExecution();
    startThreads();
  }

  /**
   * Prepares the pipeline for execution. This includes setting the arguments and parameters as well
   * as preparing and tracking the transforms and hops in the pipeline.
   *
   * @throws HopException in case the pipeline could not be prepared (initialized)
   */
  @Override
  public void prepareExecution() throws HopException {
    setPreparing(true);
    executionStartDate = new Date();
    setRunning(false);

    // We create the log channel when we're ready to rock and roll
    // Before that it makes little sense. We default to GENERAL there.
    // We force the creation of a new log channel ID every time we run this pipeline
    //
    this.log = new LogChannel(this, parent, isGatheringMetrics(), true);
    this.log.setLogLevel(logLevel);

    // We changed the log channel which changes a few internal variables:
    //
    setInternalLoggingVariables();

    if (this.containerObjectId == null) {
      this.containerObjectId = log.getContainerObjectId();
    }

    if (log.isDebug()) {
      log.logDebug(
          BaseMessages.getString(
              PKG,
              "Pipeline.Log.NumberOfTransformsToRun",
              String.valueOf(pipelineMeta.nrTransforms()),
              String.valueOf(pipelineMeta.nrPipelineHops())));
    }

    log.snap(Metrics.METRIC_PIPELINE_EXECUTION_START);
    log.snap(Metrics.METRIC_PIPELINE_INIT_START);

    log.logBasic(
        "Executing this pipeline using the Local Pipeline Engine with run configuration '"
            + pipelineRunConfiguration.getName()
            + "'");

    ExtensionPointHandler.callExtensionPoint(
        log, this, HopExtensionPoint.PipelinePrepareExecution.id, this);

    activateParameters(this);

    if (pipelineMeta.getName() == null) {
      if (pipelineMeta.getFilename() != null) {
        log.logBasic(
            BaseMessages.getString(
                PKG, "Pipeline.Log.ExecutionStartedForFilename", pipelineMeta.getFilename()));
      }
    } else {
      log.logBasic(
          BaseMessages.getString(
              PKG, "Pipeline.Log.ExecutionStartedForPipeline", pipelineMeta.getName()));
    }

    if (isSafeModeEnabled()) {
      if (log.isDetailed()) {
        log.logDetailed(
            BaseMessages.getString(PKG, "Pipeline.Log.SafeModeIsEnabled", pipelineMeta.getName()));
      }
    }

    // setInternalHopVariables(this); --> Let's not do this, when running
    // without file, for example remote, it spoils the fun

    // Keep track of all the row sets and allocated transforms
    //
    transforms = Collections.synchronizedList(new ArrayList<>());
    rowsets = new ArrayList<>();

    List<TransformMeta> hopTransforms = pipelineMeta.getPipelineHopTransforms(false);

    if (log.isDetailed()) {
      log.logDetailed(
          BaseMessages.getString(
              PKG, "Pipeline.Log.FoundDefferentTransforms", String.valueOf(hopTransforms.size())));
      log.logDetailed(BaseMessages.getString(PKG, "Pipeline.Log.AllocatingRowsets"));
    }
    // First allocate all the rowsets required!
    // Note that a mapping doesn't receive ANY input or output rowsets...
    //
    for (int i = 0; i < hopTransforms.size(); i++) {
      TransformMeta thisTransform = hopTransforms.get(i);
      if (thisTransform.isMapping()) {
        continue; // handled and allocated by the mapping transform itself.
      }

      if (log.isDetailed()) {
        log.logDetailed(
            BaseMessages.getString(
                PKG,
                "Pipeline.Log.AllocateingRowsetsForTransform",
                String.valueOf(i),
                thisTransform.getName()));
      }

      List<TransformMeta> nextTransforms = pipelineMeta.findNextTransforms(thisTransform);
      for (TransformMeta nextTransform : nextTransforms) {
        // What's the next transform?
        if (nextTransform.isMapping()) {
          continue; // handled and allocated by the mapping transform itself.
        }

        // How many times do we start the source transform?
        int thisCopies = thisTransform.getCopies(this);

        if (thisCopies < 0) {
          // This can only happen if a variable is used that didn't resolve to a positive integer
          // value
          //
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Pipeline.Log.TransformCopiesNotCorrectlyDefined", thisTransform.getName()));
        }

        // How many times do we start the target transform?
        int nextCopies = nextTransform.getCopies(this);

        // Are we re-partitioning?
        boolean repartitioning;
        if (thisTransform.isPartitioned()) {
          repartitioning =
              !thisTransform
                  .getTransformPartitioningMeta()
                  .equals(nextTransform.getTransformPartitioningMeta());
        } else {
          repartitioning = nextTransform.isPartitioned();
        }

        int nrCopies;
        if (log.isDetailed()) {
          log.logDetailed(
              BaseMessages.getString(
                  PKG,
                  "Pipeline.Log.copiesInfo",
                  String.valueOf(thisCopies),
                  String.valueOf(nextCopies)));
        }
        int dispatchType;
        if (thisCopies == 1 && nextCopies == 1) {
          dispatchType = TYPE_DISP_1_1;
          nrCopies = 1;
        } else if (thisCopies == 1 && nextCopies > 1) {
          dispatchType = TYPE_DISP_1_N;
          nrCopies = nextCopies;
        } else if (thisCopies > 1 && nextCopies == 1) {
          dispatchType = TYPE_DISP_N_1;
          nrCopies = thisCopies;
        } else if (thisCopies == nextCopies && !repartitioning) {
          dispatchType = TYPE_DISP_N_N;
          nrCopies = nextCopies;
        } else {
          // > 1!
          dispatchType = TYPE_DISP_N_M;
          nrCopies = nextCopies;
        } // Allocate a rowset for each destination transform

        // Allocate the rowsets
        //
        if (dispatchType != TYPE_DISP_N_M) {
          for (int c = 0; c < nrCopies; c++) {
            IRowSet rowSet;
            switch (pipelineMeta.getPipelineType()) {
              case Normal:
                // This is a temporary patch until the batching rowset has proven
                // to be working in all situations.
                // Currently there are stalling problems when dealing with small
                // amounts of rows.
                //
                Boolean batchingRowSet =
                    ValueMetaBase.convertStringToBoolean(
                        System.getProperty(Const.HOP_BATCHING_ROWSET));
                if (batchingRowSet != null && batchingRowSet.booleanValue()) {
                  rowSet = new BlockingBatchingRowSet(rowSetSize);
                } else {
                  rowSet = new BlockingRowSet(rowSetSize);
                }
                break;

              case SingleThreaded:
                rowSet = new QueueRowSet();
                break;

              default:
                throw new HopException(
                    "Unhandled pipeline type: " + pipelineMeta.getPipelineType());
            }

            switch (dispatchType) {
              case TYPE_DISP_1_1:
                rowSet.setThreadNameFromToCopy(
                    thisTransform.getName(), 0, nextTransform.getName(), 0);
                break;
              case TYPE_DISP_1_N:
                rowSet.setThreadNameFromToCopy(
                    thisTransform.getName(), 0, nextTransform.getName(), c);
                break;
              case TYPE_DISP_N_1:
                rowSet.setThreadNameFromToCopy(
                    thisTransform.getName(), c, nextTransform.getName(), 0);
                break;
              case TYPE_DISP_N_N:
                rowSet.setThreadNameFromToCopy(
                    thisTransform.getName(), c, nextTransform.getName(), c);
                break;
              default:
                break;
            }
            rowsets.add(rowSet);
            if (log.isDetailed()) {
              log.logDetailed(
                  BaseMessages.getString(
                      PKG, "Pipeline.PipelineAllocatedNewRowset", rowSet.toString()));
            }
          }
        } else {
          // For each N source transforms we have M target transforms
          //
          // From each input transform we go to all output transforms.
          // This allows maximum flexibility for re-partitioning,
          // distribution...
          for (int s = 0; s < thisCopies; s++) {
            for (int t = 0; t < nextCopies; t++) {
              BlockingRowSet rowSet = new BlockingRowSet(rowSetSize);
              rowSet.setThreadNameFromToCopy(
                  thisTransform.getName(), s, nextTransform.getName(), t);
              rowsets.add(rowSet);
              if (log.isDetailed()) {
                log.logDetailed(
                    BaseMessages.getString(
                        PKG, "Pipeline.PipelineAllocatedNewRowset", rowSet.toString()));
              }
            }
          }
        }
      }
      log.logDetailed(
          BaseMessages.getString(
                  PKG,
                  "Pipeline.Log.AllocatedRowsets",
                  String.valueOf(rowsets.size()),
                  String.valueOf(i),
                  thisTransform.getName())
              + " ");
    }

    if (log.isDetailed()) {
      log.logDetailed(
          BaseMessages.getString(PKG, "Pipeline.Log.AllocatingTransformsAndTransformData"));
    }

    // Allocate the transforms & the data...
    //
    for (TransformMeta transformMeta : hopTransforms) {
      String transformid = transformMeta.getTransformPluginId();

      if (log.isDetailed()) {
        log.logDetailed(
            BaseMessages.getString(
                PKG,
                "Pipeline.Log.PipelineIsToAllocateTransform",
                transformMeta.getName(),
                transformid));
      }

      // How many copies are launched of this transform?
      int nrCopies = transformMeta.getCopies(this);

      if (log.isDebug()) {
        log.logDebug(
            BaseMessages.getString(
                PKG, "Pipeline.Log.TransformHasNumberRowCopies", String.valueOf(nrCopies)));
      }

      // At least run once...
      for (int c = 0; c < nrCopies; c++) {
        // Make sure we haven't started it yet!
        if (!hasTransformStarted(transformMeta.getName(), c)) {
          TransformMetaDataCombi combi = new TransformMetaDataCombi();

          combi.transformName = transformMeta.getName();
          combi.copy = c;

          // The meta-data
          combi.transformMeta = transformMeta;
          combi.meta = transformMeta.getTransform();

          // Allocate the transform data
          ITransformData data = combi.meta.createTransformData();
          combi.data = data;

          // Allocate the transform
          ITransform transform =
              combi.meta.createTransform(transformMeta, data, c, pipelineMeta, this);

          // Copy the variables of the pipeline to the transform...
          // don't share. Each copy of the transform has its own variables.
          //
          transform.initializeFrom(this);

          // Pass the metadataProvider to the transforms runtime
          //
          transform.setMetadataProvider(metadataProvider);

          // If the transform is partitioned, set the partitioning ID and some other
          // things as well...
          if (transformMeta.isPartitioned()) {
            List<String> partitionIDs =
                transformMeta
                    .getTransformPartitioningMeta()
                    .getPartitionSchema()
                    .calculatePartitionIds(this);
            if (partitionIDs != null && !partitionIDs.isEmpty()) {
              transform.setPartitionId(partitionIDs.get(c)); // Pass the partition ID
              // to the transform
            }
          }

          // Save the transform too
          combi.transform = transform;

          // Pass logging level and metrics gathering down to the transform level.
          // /
          if (combi.transform instanceof ILoggingObject) {
            ILogChannel logChannel = combi.transform.getLogChannel();
            logChannel.setLogLevel(logLevel);
            logChannel.setGatheringMetrics(log.isGatheringMetrics());
          }

          // Add to the bunch...
          transforms.add(combi);

          if (log.isDetailed()) {
            log.logDetailed(
                BaseMessages.getString(
                    PKG,
                    "Pipeline.Log.PipelineHasAllocatedANewTransform",
                    transformMeta.getName(),
                    String.valueOf(c)));
          }
        }
      }
    }

    // Now we need to verify if certain rowsets are not meant to be for error
    // handling...
    // Loop over the transforms and for every transform verify the output rowsets
    // If a rowset is going to a target transform in the transforms error handling
    // metadata, set it to the errorRowSet.
    // The input rowsets are already in place, so the next transform just accepts the
    // rows.
    // Metadata wise we need to do the same trick in PipelineMeta
    //
    for (TransformMetaDataCombi combi : transforms) {
      if (combi.transformMeta.isDoingErrorHandling()) {
        combi.transform.identifyErrorOutput();
      }
    }

    // Now (optionally) write start log record!
    // Make sure we synchronize appropriately to avoid duplicate batch IDs.
    //
    Object syncObject = this;
    if (parentWorkflow != null) {
      syncObject = parentWorkflow; // parallel execution in a workflow
    }
    if (parentPipeline != null) {
      syncObject = parentPipeline; // multiple sub-pipelines
    }
    synchronized (syncObject) {
      calculateBatchIdAndDateRange();
      beginProcessing();
    }

    // Set the partition-to-rowset mapping
    //
    for (TransformMetaDataCombi sid : transforms) {
      TransformMeta transformMeta = sid.transformMeta;
      ITransform baseTransform = sid.transform;

      baseTransform.setPartitioned(transformMeta.isPartitioned());

      // Now let's take a look at the source and target relation
      //
      // If this source transform is not partitioned, and the target transform is: it
      // means we need to re-partition the incoming data.
      // If both transforms are partitioned on the same method and schema, we don't
      // need to re-partition
      // If both transforms are partitioned on a different method or schema, we need
      // to re-partition as well.
      // If both transforms are not partitioned, we don't need to re-partition
      //
      boolean isThisPartitioned = transformMeta.isPartitioned();
      PartitionSchema thisPartitionSchema = null;
      if (isThisPartitioned) {
        thisPartitionSchema = transformMeta.getTransformPartitioningMeta().getPartitionSchema();
      }

      boolean isNextPartitioned = false;
      TransformPartitioningMeta nextTransformPartitioningMeta = null;
      PartitionSchema nextPartitionSchema = null;

      List<TransformMeta> nextTransforms = pipelineMeta.findNextTransforms(transformMeta);
      for (TransformMeta nextTransform : nextTransforms) {
        if (nextTransform.isPartitioned()) {
          isNextPartitioned = true;
          nextTransformPartitioningMeta = nextTransform.getTransformPartitioningMeta();
          nextPartitionSchema = nextTransformPartitioningMeta.getPartitionSchema();
        }
      }

      baseTransform.setRepartitioning(TransformPartitioningMeta.PARTITIONING_METHOD_NONE);

      // If the next transform is partitioned differently, set re-partitioning, when
      // running locally.
      //
      if ((!isThisPartitioned && isNextPartitioned)
          || (isThisPartitioned
              && isNextPartitioned
              && !thisPartitionSchema.equals(nextPartitionSchema))) {
        baseTransform.setRepartitioning(nextTransformPartitioningMeta.getMethodType());
      }

      // For partitioning to a set of remove transforms (repartitioning from a master
      // to a set or remote output transforms)
      //
      TransformPartitioningMeta targetTransformPartitioningMeta =
          baseTransform.getTransformMeta().getTargetTransformPartitioningMeta();
      if (targetTransformPartitioningMeta != null) {
        baseTransform.setRepartitioning(targetTransformPartitioningMeta.getMethodType());
      }
    }

    setPreparing(false);
    setInitializing(true);

    // Do a topology sort... Over 150 transform (copies) things might be slowing down too much.
    //
    if (isSortingTransformsTopologically() && transforms.size() < 150) {
      doTopologySortOfTransforms();
    }

    if (log.isDetailed()) {
      log.logDetailed(
          BaseMessages.getString(
              PKG, "Pipeline.Log.InitialisingTransforms", String.valueOf(transforms.size())));
    }

    TransformInitThread[] initThreads = new TransformInitThread[transforms.size()];
    Thread[] threads = new Thread[transforms.size()];

    // Initialize all the threads...
    //
    for (int i = 0; i < transforms.size(); i++) {
      final TransformMetaDataCombi sid = transforms.get(i);

      // Do the init code in the background!
      // Init all transforms at once, but ALL transforms need to finish before we can
      // continue properly!
      //
      initThreads[i] = new TransformInitThread(sid, this, log);

      // Put it in a separate thread!
      //
      threads[i] = new Thread(initThreads[i]);
      threads[i].setName(
          "init of " + sid.transformName + "." + sid.copy + " (" + threads[i].getName() + ")");

      ExtensionPointHandler.callExtensionPoint(
          log, this, HopExtensionPoint.TransformBeforeInitialize.id, initThreads[i]);

      threads[i].start();
    }

    for (int i = 0; i < threads.length; i++) {
      try {
        threads[i].join();
        ExtensionPointHandler.callExtensionPoint(
            log, this, HopExtensionPoint.TransformAfterInitialize.id, initThreads[i]);
      } catch (Exception ex) {
        log.logError("Error with init thread: " + ex.getMessage(), ex.getMessage());
        log.logError(Const.getStackTracker(ex));
      }
    }

    setInitializing(false);
    boolean ok = true;

    // All transform are initialized now: see if there was one that didn't do it
    // correctly!
    //
    for (TransformInitThread thread : initThreads) {
      TransformMetaDataCombi combi = thread.getCombi();
      if (!thread.isOk()) {
        log.logError(
            BaseMessages.getString(
                PKG, "Pipeline.Log.TransformFailedToInit", combi.transformName + "." + combi.copy));
        combi.data.setStatus(ComponentExecutionStatus.STATUS_STOPPED);
        ok = false;
      } else {
        combi.data.setStatus(ComponentExecutionStatus.STATUS_IDLE);
        if (log.isDetailed()) {
          log.logDetailed(
              BaseMessages.getString(
                  PKG,
                  "Pipeline.Log.TransformInitialized",
                  combi.transformName + "." + combi.copy));
        }
      }
    }

    if (!ok) {
      // Halt the other threads as well, signal end-of-the line to the outside
      // world...
      // Also explicitly call dispose() to clean up resources opened during
      // init()
      //
      for (TransformInitThread initThread : initThreads) {
        TransformMetaDataCombi combi = initThread.getCombi();

        // Dispose will overwrite the status, but we set it back right after
        // this.
        combi.transform.dispose();

        if (initThread.isOk()) {
          combi.data.setStatus(ComponentExecutionStatus.STATUS_HALTED);
        } else {
          combi.data.setStatus(ComponentExecutionStatus.STATUS_STOPPED);
        }
      }

      // Just for safety, fire the pipeline finished listeners...
      try {
        fireExecutionFinishedListeners();
      } catch (HopException e) {
        // listeners produces errors
        log.logError(BaseMessages.getString(PKG, "Pipeline.FinishListeners.Exception"));
        // we will not pass this exception up to prepareExecuton() entry point.
      } finally {
        // Flag the pipeline as finished even if exception was thrown
        setFinished(true);
      }

      // Pass along the log during preview. Otherwise it becomes hard to see
      // what went wrong.
      //
      if (preview) {
        String logText = HopLogStore.getAppender().getBuffer(getLogChannelId(), true).toString();
        throw new HopException(
            BaseMessages.getString(PKG, "Pipeline.Log.FailToInitializeAtLeastOneTransform")
                + Const.CR
                + logText);
      } else {
        throw new HopException(
            BaseMessages.getString(PKG, "Pipeline.Log.FailToInitializeAtLeastOneTransform")
                + Const.CR);
      }
    }

    log.snap(Metrics.METRIC_PIPELINE_INIT_STOP);

    setReadyToStart(true);
  }

  /**
   * Starts the threads prepared by prepareThreads(). Before you start the threads, you can add
   * RowListeners to them.
   *
   * @throws HopException if there is a communication error with a remote output socket.
   */
  @Override
  public void startThreads() throws HopException {
    // Now prepare to start all the threads...
    //
    nrOfFinishedTransforms = 0;

    ExtensionPointHandler.callExtensionPoint(
        log, this, HopExtensionPoint.PipelineStartThreads.id, this);

    fireExecutionStartedListeners();

    for (int i = 0; i < transforms.size(); i++) {
      final TransformMetaDataCombi sid = transforms.get(i);
      sid.transform.markStart();
      sid.transform.initBeforeStart();

      // also attach a listener to detect when we're done...
      //
      ITransformFinishedListener finishedListener =
          (pipeline, transformMeta, transform) -> {
            synchronized (Pipeline.this) {
              nrOfFinishedTransforms++;

              if (nrOfFinishedTransforms >= transforms.size()) {
                // Set the finished flag
                //
                setFinished(true);

                // Grab the performance statistics one last time (if enabled)
                //
                addTransformPerformanceSnapShot();

                // We're really done now.
                //
                executionEndDate = new Date();

                try {
                  fireExecutionFinishedListeners();
                } catch (Exception e) {
                  transform.setErrors(transform.getErrors() + 1L);
                  log.logError(
                      getName()
                          + " : "
                          + BaseMessages.getString(
                              PKG, "Pipeline.Log.UnexpectedErrorAtPipelineEnd"),
                      e);
                }

                log.logBasic(
                    "Execution finished on a local pipeline engine with run configuration '"
                        + pipelineRunConfiguration.getName()
                        + "'");
              }

              // If a transform fails with an error, we want to kill/stop the others
              // too...
              //
              if (transform.getErrors() > 0) {

                log.logMinimal(BaseMessages.getString(PKG, "Pipeline.Log.PipelineDetectedErrors"));
                log.logMinimal(
                    BaseMessages.getString(
                        PKG, "Pipeline.Log.PipelineIsKillingTheOtherTransforms"));

                killAllNoWait();
              }
            }
          };

      // Make sure this is called first!
      //
      if (sid.transform instanceof BaseTransform baseTransform) {
        baseTransform.getTransformFinishedListeners().add(0, finishedListener);
      } else {
        sid.transform.addTransformFinishedListener(finishedListener);
      }
    }

    if (pipelineMeta.isCapturingTransformPerformanceSnapShots()) {
      transformPerformanceSnapshotSeqNr = new AtomicInteger(0);
      transformPerformanceSnapShots = new ConcurrentHashMap<>();

      // Calculate the maximum number of snapshots to be kept in memory
      //
      String limitString = resolve(pipelineMeta.getTransformPerformanceCapturingSizeLimit());
      if (Utils.isEmpty(limitString)) {
        limitString = EnvUtil.getSystemProperty(Const.HOP_TRANSFORM_PERFORMANCE_SNAPSHOT_LIMIT);
      }
      transformPerformanceSnapshotSizeLimit = Const.toInt(limitString, 0);

      // Set a timer to collect the performance data from the running threads...
      //
      transformPerformanceSnapShotTimer =
          new Timer("transformPerformanceSnapShot Timer: " + pipelineMeta.getName());
      TimerTask timerTask =
          new TimerTask() {
            @Override
            public void run() {
              if (!isFinished()) {
                addTransformPerformanceSnapShot();
              }
            }
          };
      transformPerformanceSnapShotTimer.schedule(
          timerTask, 100, pipelineMeta.getTransformPerformanceCapturingDelay());
    }

    // Now start a thread to monitor the running pipeline...
    //
    setFinished(false);
    setPaused(false);
    setStopped(false);

    pipelineWaitUntilFinishedBlockingQueue = new ArrayBlockingQueue<>(10);

    // Do all sorts of nifty things at the end of the pipeline execution
    ///
    IExecutionFinishedListener<IPipelineEngine<PipelineMeta>> executionListener =
        pipeline -> {
          try {
            ExtensionPointHandler.callExtensionPoint(
                log, this, HopExtensionPoint.PipelineFinish.id, pipeline);
          } catch (HopException e) {
            throw new RuntimeException("Error calling extension point at end of pipeline", e);
          }

          // First of all, stop the performance snapshot timer if there is is
          // one...
          //
          if (pipelineMeta.isCapturingTransformPerformanceSnapShots()
              && transformPerformanceSnapShotTimer != null) {
            ExecutorUtil.cleanup(transformPerformanceSnapShotTimer);
          }

          setFinished(true);
          setRunning(false); // no longer running

          log.snap(Metrics.METRIC_PIPELINE_EXECUTION_STOP);

          // release unused vfs connections
          HopVfs.freeUnusedResources();
        };
    // This should always be done first so that the other listeners achieve a clean state to start
    // from (setFinished and
    // so on)
    //
    executionFinishedListeners.add(0, executionListener);

    setRunning(true);

    switch (pipelineMeta.getPipelineType()) {
      case Normal:

        // Now start all the threads...
        //
        for (final TransformMetaDataCombi combi : transforms) {
          RunThread runThread = new RunThread(combi);
          Thread thread = new Thread(runThread);
          thread.setName(getName() + " - " + combi.transformName);
          ExtensionPointHandler.callExtensionPoint(
              log, this, HopExtensionPoint.TransformBeforeStart.id, combi);
          // Call an extension point at the end of the transform
          //
          combi.transform.addTransformFinishedListener(
              (pipeline, transformMeta, transform) -> {
                try {
                  ExtensionPointHandler.callExtensionPoint(
                      log, this, HopExtensionPoint.TransformFinished.id, combi);
                } catch (HopException e) {
                  throw new RuntimeException(
                      "Unexpected error in calling extension point upon transform finish", e);
                }
              });

          thread.start();
        }
        break;

      case SingleThreaded:
        // Don't do anything, this needs to be handled by the pipeline
        // executor!
        //
        break;
      default:
        break;
    }

    ExtensionPointHandler.callExtensionPoint(log, this, HopExtensionPoint.PipelineStart.id, this);

    // If there are no transforms we don't catch the number of active transforms dropping to zero
    // So we fire the execution finished listeners here.
    //
    if (transforms.isEmpty()) {
      fireExecutionFinishedListeners();
    }

    if (log.isDetailed()) {
      log.logDetailed(
          BaseMessages.getString(
              PKG,
              "Pipeline.Log.PipelineHasAllocated",
              String.valueOf(transforms.size()),
              String.valueOf(rowsets.size())));
    }
  }

  /**
   * @deprecated Make attempt to fire all registered finished listeners if possible.
   * @throws HopException if any errors occur during notification
   */
  @Override
  @Deprecated(since = "2.9", forRemoval = true)
  public void firePipelineExecutionFinishedListeners() throws HopException {
    fireExecutionFinishedListeners();
  }

  @Override
  public void fireExecutionFinishedListeners() throws HopException {
    synchronized (executionFinishedListeners) {
      if (executionFinishedListeners.isEmpty()) {
        return;
      }
      // prevent Exception from one listener to block others execution
      List<HopException> badGuys = new ArrayList<>(executionFinishedListeners.size());
      for (IExecutionFinishedListener<IPipelineEngine<PipelineMeta>> listener :
          executionFinishedListeners) {
        try {
          listener.finished(this);
        } catch (HopException e) {
          badGuys.add(e);
        }
      }
      if (!badGuys.isEmpty()) {
        // FIFO
        throw new HopException(badGuys.get(0));
      }
    }

    // Now the status and everything else is set correctly. We've completed the pipeline.
    //
    pipelineCompleted();

    // Also call an extension point in case plugins want to play along
    //
    ExtensionPointHandler.callExtensionPoint(
        log, this, HopExtensionPoint.PipelineCompleted.id, this);
  }

  public void pipelineCompleted() throws HopException {
    // Signal for the waitUntilFinished blocker.
    // This will cause the wait loop in the waitUntilFinished() method to stop.
    //
    if (pipelineWaitUntilFinishedBlockingQueue != null) {
      pipelineWaitUntilFinishedBlockingQueue.add(new Object());
    }
  }

  /**
   * @deprecated Fires the start-event listeners (if any are registered).
   * @throws HopException if any errors occur during notification
   */
  @Override
  @Deprecated(since = "2.9", forRemoval = true)
  public void firePipelineExecutionStartedListeners() throws HopException {
    fireExecutionStartedListeners();
  }

  @Override
  public void fireExecutionStartedListeners() throws HopException {
    synchronized (executionStartedListeners) {
      for (IExecutionStartedListener<IPipelineEngine<PipelineMeta>> listener :
          executionStartedListeners) {
        listener.started(this);
      }
    }
  }

  /** Adds a transform performance snapshot. */
  protected void addTransformPerformanceSnapShot() {

    if (transformPerformanceSnapShots == null) {
      return; // Race condition somewhere?
    }

    boolean pausedAndNotEmpty = isPaused() && !transformPerformanceSnapShots.isEmpty();
    boolean stoppedAndNotEmpty = isStopped() && !transformPerformanceSnapShots.isEmpty();

    if (pipelineMeta.isCapturingTransformPerformanceSnapShots()
        && !pausedAndNotEmpty
        && !stoppedAndNotEmpty) {
      // get the statistics from the transforms and keep them...
      //
      int seqNr = transformPerformanceSnapshotSeqNr.incrementAndGet();
      for (TransformMetaDataCombi iTransformITransformMetaITransformDataTransformMetaDataCombi :
          transforms) {
        TransformMeta transformMeta =
            iTransformITransformMetaITransformDataTransformMetaDataCombi.transformMeta;
        ITransform transform =
            iTransformITransformMetaITransformDataTransformMetaDataCombi.transform;

        PerformanceSnapShot snapShot =
            new PerformanceSnapShot(
                seqNr,
                new Date(),
                getName(),
                transformMeta.getName(),
                transform.getCopy(),
                transform.getLinesRead(),
                transform.getLinesWritten(),
                transform.getLinesInput(),
                transform.getLinesOutput(),
                transform.getLinesUpdated(),
                transform.getLinesRejected(),
                transform.getErrors());

        synchronized (transformPerformanceSnapShots) {
          List<PerformanceSnapShot> snapShotList =
              transformPerformanceSnapShots.get(transform.toString());
          PerformanceSnapShot previous;
          if (snapShotList == null) {
            snapShotList = new ArrayList<>();
            transformPerformanceSnapShots.put(transform.toString(), snapShotList);
            previous = null;
          } else {
            previous = snapShotList.get(snapShotList.size() - 1); // the last one...
          }
          // Make the difference...
          //
          snapShot.diff(previous, transform.rowsetInputSize(), transform.rowsetOutputSize());
          snapShotList.add(snapShot);

          if (transformPerformanceSnapshotSizeLimit > 0
              && snapShotList.size() > transformPerformanceSnapshotSizeLimit) {
            snapShotList.remove(0);
          }
        }
      }

      lastTransformPerformanceSnapshotSeqNrAdded = transformPerformanceSnapshotSeqNr.get();
    }
  }

  /**
   * This method performs any cleanup operations, typically called after the pipeline has finished.
   */
  @Override
  public void cleanup() {
    // Close all open server sockets.
    // We can only close these after all processing has been confirmed to be finished.
    //
    if (transforms == null) {
      return;
    }

    for (TransformMetaDataCombi combi : transforms) {
      combi.transform.cleanup();
    }
  }

  /** Waits until all RunThreads have finished. */
  @Override
  public void waitUntilFinished() {
    try {
      if (pipelineWaitUntilFinishedBlockingQueue == null) {
        return;
      }
      boolean wait = true;
      while (wait) {
        // Wait a little while
        //
        wait = pipelineWaitUntilFinishedBlockingQueue.poll(50, TimeUnit.MILLISECONDS) == null;
        if (wait) {
          // poll returns immediately - this was hammering the CPU with poll checks. Added
          // a sleep to let the CPU breathe
          Thread.sleep(1);

          // If a parent workflow or pipeline has a stopped state, stop this pipeline as well
          //
          if (parentWorkflow != null && parentWorkflow.isStopped() && !isStopped()) {
            stopAll();
          }
          if (parentPipeline != null && parentPipeline.isStopped() && !isStopped()) {
            stopAll();
          }
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Waiting for pipeline to be finished interrupted!", e);
    }
  }

  /**
   * Gets the number of errors that have occurred during execution of the pipeline.
   *
   * @return the number of errors
   */
  @Override
  public int getErrors() {
    int nrErrors = errors.get();

    if (transforms == null) {
      return nrErrors;
    }

    for (TransformMetaDataCombi sid : transforms) {
      if (sid.transform.getErrors() != 0L) {
        nrErrors += sid.transform.getErrors();
      }
    }

    return nrErrors;
  }

  /**
   * Checks if the pipeline is finished\.
   *
   * @return true if the pipeline is finished, false otherwise
   */
  @Override
  public boolean isFinished() {
    int exist = status.get() & FINISHED.mask;
    return exist != 0;
  }

  protected void setFinished(boolean finished) {
    status.updateAndGet(v -> finished ? v | FINISHED.mask : (BIT_STATUS_SUM ^ FINISHED.mask) & v);
  }

  public boolean isFinishedOrStopped() {
    return isFinished() || isStopped();
  }

  /**
   * Asks all transforms to stop but doesn't wait around for it to happen. This is a special method
   * for use with mappings.
   */
  private void killAllNoWait() {
    if (transforms == null) {
      return;
    }

    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;

      if (log.isDebug()) {
        log.logDebug(
            BaseMessages.getString(PKG, "Pipeline.Log.LookingAtTransform")
                + transform.getTransformName());
      }

      transform.stopAll();
    }
  }

  /**
   * Finds the IRowSet between two transforms (or copies of transforms).
   *
   * @param from the name of the "from" transform
   * @param fromcopy the copy number of the "from" transform
   * @param to the name of the "to" transform
   * @param tocopy the copy number of the "to" transform
   * @return the row set, or null if none found
   */
  @Override
  public IRowSet findRowSet(String from, int fromcopy, String to, int tocopy) {
    // Start with the pipeline.
    for (IRowSet rs : rowsets) {
      if (rs.getOriginTransformName().equalsIgnoreCase(from)
          && rs.getDestinationTransformName().equalsIgnoreCase(to)
          && rs.getOriginTransformCopy() == fromcopy
          && rs.getDestinationTransformCopy() == tocopy) {
        return rs;
      }
    }

    return null;
  }

  /**
   * Checks whether the specified transform (or transform copy) has started.
   *
   * @param sname the transform name
   * @param copy the copy number
   * @return true the specified transform (or transform copy) has started, false otherwise
   */
  public boolean hasTransformStarted(String sname, int copy) {
    for (TransformMetaDataCombi sid : transforms) {
      boolean started =
          (sid.transformName != null && sid.transformName.equalsIgnoreCase(sname))
              && sid.copy == copy;
      if (started) {
        return true;
      }
    }
    return false;
  }

  /**
   * Stops only input transforms so that all downstream transforms can finish processing rows that
   * have already been input
   */
  public void safeStop() {
    if (transforms == null) {
      return;
    }
    transforms.stream().filter(this::isInputTransform).forEach(combi -> stopTransform(combi, true));

    fireExecutionStoppedListeners();
  }

  private boolean isInputTransform(TransformMetaDataCombi combi) {
    checkNotNull(combi);
    return pipelineMeta.findPreviousTransforms(combi.transformMeta, true).isEmpty();
  }

  /** Stops all transforms from running, and alerts any registered listeners. */
  @Override
  public void stopAll() {
    if (transforms == null || isAlreadyStopped.get()) {
      return;
    }

    transforms.forEach(combi -> stopTransform(combi, false));

    // if it is stopped it is not paused
    setPaused(false);
    setStopped(true);
    isAlreadyStopped.set(true);

    fireExecutionStoppedListeners();
  }

  public void stopTransform(TransformMetaDataCombi combi, boolean safeStop) {
    ITransform rt = combi.transform;
    rt.setStopped(true);
    rt.setSafeStopped(safeStop);
    rt.resumeRunning();

    try {
      rt.stopRunning();
    } catch (Exception e) {
      log.logError("Something went wrong while trying to safe stop the pipeline: ", e);
    }
    combi.data.setStatus(ComponentExecutionStatus.STATUS_STOPPED);
    if (safeStop) {
      rt.setOutputDone();
    }
  }

  /**
   * @deprecated
   */
  @Override
  @Deprecated(since = "2.9", forRemoval = true)
  public void firePipelineExecutionStoppedListeners() {
    fireExecutionStoppedListeners();
  }

  @Override
  public void fireExecutionStoppedListeners() {
    synchronized (executionStoppedListeners) {
      for (IExecutionStoppedListener<IPipelineEngine<PipelineMeta>> listener :
          executionStoppedListeners) {
        listener.stopped(this);
      }
    }
  }

  /**
   * Gets the number of transforms in this pipeline.
   *
   * @return the number of transforms
   */
  public int nrTransforms() {
    if (transforms == null) {
      return 0;
    }
    return transforms.size();
  }

  /**
   * Checks whether the pipeline transforms are running lookup.
   *
   * @return a boolean array associated with the transform list, indicating whether that transform
   *     is running a lookup.
   */
  public boolean[] getPipelineTransformIsRunningLookup() {
    if (transforms == null) {
      return null;
    }

    boolean[] tResult = new boolean[transforms.size()];
    for (int i = 0; i < transforms.size(); i++) {
      TransformMetaDataCombi sid = transforms.get(i);
      tResult[i] =
          (sid.transform.isRunning()
              || sid.transform.getStatus() != ComponentExecutionStatus.STATUS_FINISHED);
    }
    return tResult;
  }

  /**
   * Checks the execution status of each transform in the pipelines.
   *
   * @return an array associated with the transform list, indicating the status of that transform.
   */
  public ComponentExecutionStatus[] getPipelineTransformExecutionStatusLookup() {
    if (transforms == null) {
      return null;
    }

    // we need this snapshot for the PipelineGridDelegate refresh method to handle the
    // difference between a timed refresh and continual transform status updates
    int totalTransforms = transforms.size();
    ComponentExecutionStatus[] tList = new ComponentExecutionStatus[totalTransforms];
    for (int i = 0; i < totalTransforms; i++) {
      TransformMetaDataCombi sid = transforms.get(i);
      tList[i] = sid.transform.getStatus();
    }
    return tList;
  }

  /**
   * Gets the run thread for the transform at the specified index.
   *
   * @param i the index of the desired transform
   * @return a ITransform object corresponding to the run thread for the specified transform
   */
  public ITransform getRunThread(int i) {
    if (transforms == null) {
      return null;
    }
    return transforms.get(i).transform;
  }

  /**
   * Gets the run thread for the transform with the specified name and copy number.
   *
   * @param name the transform name
   * @param copy the copy number
   * @return a ITransform object corresponding to the run thread for the specified transform
   */
  public ITransform getRunThread(String name, int copy) {
    if (transforms == null) {
      return null;
    }

    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;
      if (transform.getTransformName().equalsIgnoreCase(name) && transform.getCopy() == copy) {
        return transform;
      }
    }

    return null;
  }

  /**
   * Calculate the batch id and date range for the pipeline.
   *
   * @throws HopPipelineException if there are any errors during calculation
   */
  public void calculateBatchIdAndDateRange() throws HopPipelineException {

    // TODO: implement date ranges using the audit manager API
  }

  /**
   * Begin processing. Also handle logging operations related to the start of the pipeline
   *
   * @throws HopPipelineException the hop pipeline exception
   */
  public void beginProcessing() throws HopPipelineException {
    // TODO: inform the active audit manager that the pipeline started processing
  }

  /**
   * Gets the result of the pipeline. The Result object contains such measures as the number of
   * errors, number of lines read/written/input/output/updated/rejected, etc.
   *
   * @return the Result object containing resulting measures from execution of the pipeline
   */
  @Override
  public Result getResult() {
    if (transforms == null) {
      return null;
    }

    Result result = new Result();
    result.setNrErrors(errors.longValue());
    result.setResult(errors.longValue() == 0);

    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;

      result.setNrErrors(result.getNrErrors() + sid.transform.getErrors());
      result.getResultFiles().putAll(transform.getResultFiles());

      // For every transform metric, take the maximum amount
      //
      result.setNrLinesRead(Math.max(result.getNrLinesRead(), transform.getLinesRead()));
      result.setNrLinesWritten(Math.max(result.getNrLinesWritten(), transform.getLinesWritten()));
      result.setNrLinesInput(Math.max(result.getNrLinesInput(), transform.getLinesInput()));
      result.setNrLinesOutput(Math.max(result.getNrLinesOutput(), transform.getLinesOutput()));
      result.setNrLinesUpdated(Math.max(result.getNrLinesUpdated(), transform.getLinesUpdated()));
      result.setNrLinesRejected(
          Math.max(result.getNrLinesRejected(), transform.getLinesRejected()));
    }

    result.setRows(resultRows);
    if (!Utils.isEmpty(resultFiles)) {
      result.setResultFiles(new HashMap<>());
      for (ResultFile resultFile : resultFiles) {
        result.getResultFiles().put(resultFile.toString(), resultFile);
      }
    }
    result.setStopped(isStopped());
    result.setLogChannelId(log.getLogChannelId());

    return result;
  }

  /**
   * Find the run thread for the transform with the specified name.
   *
   * @param transformName the transform name
   * @return a ITransform object corresponding to the run thread for the specified transform
   */
  public ITransform findRunThread(String transformName) {
    if (transforms == null) {
      return null;
    }

    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;
      if (transform.getTransformName().equalsIgnoreCase(transformName)) {
        return transform;
      }
    }
    return null;
  }

  /**
   * Gets sortingTransformsTopologically
   *
   * @return value of sortingTransformsTopologically
   */
  public boolean isSortingTransformsTopologically() {
    return sortingTransformsTopologically;
  }

  /**
   * @param sortingTransformsTopologically The sortingTransformsTopologically to set
   */
  public void setSortingTransformsTopologically(boolean sortingTransformsTopologically) {
    this.sortingTransformsTopologically = sortingTransformsTopologically;
  }

  /**
   * Gets the meta-data for the pipeline.
   *
   * @return Returns the pipeline meta-data
   */
  @Override
  public PipelineMeta getPipelineMeta() {
    return pipelineMeta;
  }

  /**
   * Sets the meta-data for the pipeline.
   *
   * @param pipelineMeta The pipeline meta-data to set.
   */
  @Override
  public void setPipelineMeta(PipelineMeta pipelineMeta) {
    this.pipelineMeta = pipelineMeta;
  }

  /**
   * Gets the rowsets for the pipeline.
   *
   * @return a list of rowsets
   */
  public List<IRowSet> getRowsets() {
    return rowsets;
  }

  /**
   * Gets a list of transforms in the pipeline.
   *
   * @return a list of the transforms in the pipeline
   */
  public List<TransformMetaDataCombi> getTransforms() {
    return transforms;
  }

  protected void setTransforms(List<TransformMetaDataCombi> transforms) {
    this.transforms = transforms;
  }

  /**
   * Gets a string representation of the pipeline.
   *
   * @return the string representation of the pipeline
   * @see Object#toString()
   */
  @Override
  public String toString() {
    if (pipelineMeta == null || pipelineMeta.getName() == null) {
      return getClass().getSimpleName();
    }

    // See if there is a parent pipeline. If so, print the name of the parent here as well...
    //
    StringBuilder string = new StringBuilder(50);

    // If we're running as a mapping, we get a reference to the calling (parent) pipeline as well...
    //
    if (getParentPipeline() != null) {
      string.append('[').append(getParentPipeline().toString()).append(']').append('.');
    }

    string.append(pipelineMeta.getName());

    return string.toString();
  }

  /**
   * Find the ITransform (thread) by looking it up using the name.
   *
   * @param name The name of the transform to look for
   * @param copyNr The copy number of the transform to look for
   * @return the ITransform or null if nothing was found.
   */
  public ITransform getTransform(String name, int copyNr) {
    if (transforms == null) {
      return null;
    }

    // Now start all the threads...
    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;
      if (transform.getTransformName().equalsIgnoreCase(name) && sid.copy == copyNr) {
        return transform;
      }
    }

    return null;
  }

  /**
   * Find the available executing transform copies for the transform with the specified name
   *
   * @param name The transform name
   * @return the list of executing transform copies found or null if no transforms are available yet
   *     (incorrect usage)
   */
  public List<ITransform> getTransforms(String name) {
    if (transforms == null) {
      return null;
    }

    List<ITransform> list = new ArrayList<>();

    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;
      if (transform.getTransformName().equalsIgnoreCase(name)) {
        list.add(transform);
      }
    }
    return list;
  }

  /**
   * Turn on safe mode during running: the pipeline will run slower but with more checking enabled.
   *
   * @param safeModeEnabled true for safe mode
   */
  public void setSafeModeEnabled(boolean safeModeEnabled) {
    this.safeModeEnabled = safeModeEnabled;
  }

  /**
   * Checks whether safe mode is enabled.
   *
   * @return Returns true if the safe mode is enabled: the pipeline will run slower but with more
   *     checking enabled
   */
  @Override
  public boolean isSafeModeEnabled() {
    return safeModeEnabled;
  }

  /**
   * This adds a row producer to the pipeline that just got set up. It is preferable to run this
   * BEFORE execute() but after prepareExecution()
   *
   * @param transformName The transform to produce rows for
   * @param copynr The copynr of the transform to produce row for (normally 0 unless you have
   *     multiple copies running)
   * @return the row producer
   * @throws HopException in case the thread/transform to produce rows for could not be found.
   * @see Pipeline#execute()
   * @see Pipeline#prepareExecution()
   */
  public RowProducer addRowProducer(String transformName, int copynr) throws HopException {
    ITransform transform = getTransform(transformName, copynr);
    if (transform == null) {
      throw new HopException(
          "Unable to find thread with name " + transformName + " and copy number " + copynr);
    }

    // We are going to add an extra IRowSet to this iTransform.
    IRowSet rowSet;
    switch (pipelineMeta.getPipelineType()) {
      case Normal:
        rowSet = new BlockingRowSet(rowSetSize);
        break;
      case SingleThreaded:
        rowSet = new QueueRowSet();
        break;
      default:
        throw new HopException("Unhandled pipeline type: " + pipelineMeta.getPipelineType());
    }

    // Add this rowset to the list of active rowsets for the selected transform
    transform.addRowSetToInputRowSets(rowSet);

    return new RowProducer(transform, rowSet);
  }

  /**
   * Gets the parent workflow, or null if there is no parent.
   *
   * @return the parent workflow, or null if there is no parent
   */
  @Override
  public IWorkflowEngine<WorkflowMeta> getParentWorkflow() {
    return parentWorkflow;
  }

  /**
   * Sets the parent workflow for the pipeline.
   *
   * @param parentWorkflow The parent workflow to set
   */
  @Override
  public void setParentWorkflow(IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    this.parentWorkflow = parentWorkflow;
  }

  /**
   * Finds the ITransformData (currently) associated with the specified transform.
   *
   * @param name The name of the transform to look for
   * @param copyNr The copy number (0 based) of the transform
   * @return The ITransformData or null if non found.
   */
  public ITransformData getTransformData(String name, int copyNr) {
    if (transforms == null) {
      return null;
    }

    for (TransformMetaDataCombi sid : transforms) {
      ITransform transform = sid.transform;
      if (transform.getTransformName().equalsIgnoreCase(name) && sid.copy == copyNr) {
        return sid.data;
      }
    }
    return null;
  }

  /**
   * Checks whether the pipeline has any components that are halted.
   *
   * @return true if one or more components are halted, false otherwise
   */
  @Override
  public boolean hasHaltedComponents() {
    // not yet 100% sure of this, if there are no transforms... or none halted?
    if (transforms == null) {
      return false;
    }

    for (TransformMetaDataCombi sid : transforms) {
      if (sid.data.getStatus() == ComponentExecutionStatus.STATUS_HALTED) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the status of the pipeline (Halting, Finished, Paused, etc.)
   *
   * @return the status of the pipeline
   */
  public String getStatus() {
    String message;

    if (isRunning()) {
      if (isStopped()) {
        message = STRING_HALTING;
      } else {
        if (isPaused()) {
          message = STRING_PAUSED;
        } else {
          message = STRING_RUNNING;
        }
      }
    } else if (isFinished()) {
      message = STRING_FINISHED;
      if (getResult().getNrErrors() > 0) {
        message += " (with errors)";
      }
    } else if (isStopped()) {
      message = STRING_STOPPED;
    } else if (isPreparing()) {
      message = STRING_PREPARING;
    } else if (isInitializing()) {
      message = STRING_INITIALIZING;
    } else {
      message = STRING_WAITING;
    }

    return message;
  }

  /**
   * Checks whether the pipeline is initializing.
   *
   * @return true if the pipeline is initializing, false otherwise
   */
  public boolean isInitializing() {
    int exist = status.get() & INITIALIZING.mask;
    return exist != 0;
  }

  /**
   * Sets whether the pipeline is initializing.
   *
   * @param initializing true if the pipeline is initializing, false otherwise
   */
  public void setInitializing(boolean initializing) {
    status.updateAndGet(
        v -> initializing ? v | INITIALIZING.mask : (BIT_STATUS_SUM ^ INITIALIZING.mask) & v);
  }

  /**
   * Checks whether the pipeline is preparing for execution.
   *
   * @return true if the pipeline is preparing for execution, false otherwise
   */
  @Override
  public boolean isPreparing() {
    int exist = status.get() & PREPARING.mask;
    return exist != 0;
  }

  /**
   * Sets whether the pipeline is preparing for execution.
   *
   * @param preparing true if the pipeline is preparing for execution, false otherwise
   */
  public void setPreparing(boolean preparing) {
    status.updateAndGet(
        v -> preparing ? v | PREPARING.mask : (BIT_STATUS_SUM ^ PREPARING.mask) & v);
  }

  /**
   * Checks whether the pipeline is running.
   *
   * @return true if the pipeline is running, false otherwise
   */
  @Override
  public boolean isRunning() {
    int exist = status.get() & RUNNING.mask;
    return exist != 0;
  }

  /**
   * Sets whether the pipeline is running.
   *
   * @param running true if the pipeline is running, false otherwise
   */
  public void setRunning(boolean running) {
    status.updateAndGet(v -> running ? v | RUNNING.mask : (BIT_STATUS_SUM ^ RUNNING.mask) & v);
  }

  /**
   * Checks whether the pipeline is ready to start (i.e. execution preparation was successful)
   *
   * @return true if the pipeline was prepared for execution successfully, false otherwise
   * @see Pipeline#prepareExecution()
   */
  @Override
  public boolean isReadyToStart() {
    return readyToStart;
  }

  protected void setReadyToStart(boolean ready) {
    readyToStart = ready;
  }

  /**
   * Sets the internal Hop variables.
   *
   * @param var the new internal hop variables
   */
  @Override
  public void setInternalHopVariables(IVariables var) {
    boolean hasFilename = pipelineMeta != null && !Utils.isEmpty(pipelineMeta.getFilename());
    if (hasFilename) { // we have a filename that's defined.
      try {
        FileObject fileObject = HopVfs.getFileObject(pipelineMeta.getFilename());
        FileName fileName = fileObject.getName();

        // The filename of the pipeline
        setVariable(Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_NAME, fileName.getBaseName());

        // The directory of the pipeline
        FileName fileDir = fileName.getParent();
        setVariable(Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY, fileDir.getURI());
      } catch (HopFileException e) {
        setVariable(Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY, "");
        setVariable(Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_NAME, "");
      }
    } else {
      setVariable(Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY, "");
      setVariable(Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_NAME, "");
    }

    // The name of the pipeline
    setVariable(Const.INTERNAL_VARIABLE_PIPELINE_NAME, Const.NVL(pipelineMeta.getName(), ""));

    setInternalLoggingVariables();

    // Here we don't clear the definition of the workflow specific parameters, as they may come in
    // handy.
    // A pipeline can be called from a workflow and may inherit the workflow internal variables
    // but the other around is not possible.

    setInternalEntryCurrentDirectory(hasFilename);
  }

  private void setInternalLoggingVariables() {
    // The ID of the pipeline (log channel ID)
    setVariable(Const.INTERNAL_VARIABLE_PIPELINE_ID, log != null ? log.getLogChannelId() : "");

    if (parent != null) {
      setVariable(Const.INTERNAL_VARIABLE_PIPELINE_PARENT_ID, parent.getLogChannelId());
    } else {
      setVariable(Const.INTERNAL_VARIABLE_PIPELINE_PARENT_ID, null);
    }
  }

  protected void setInternalEntryCurrentDirectory(boolean hasFilename) {
    setVariable(
        Const.INTERNAL_VARIABLE_ENTRY_CURRENT_FOLDER,
        getVariable(
            hasFilename
                ? Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY
                : Const.INTERNAL_VARIABLE_ENTRY_CURRENT_FOLDER));
  }

  /**
   * Copies variables from a given variable variables to this pipeline.
   *
   * @param variables the variable variables
   * @see IVariables#copyFrom(IVariables)
   */
  @Override
  public void copyFrom(IVariables variables) {
    this.variables.copyFrom(variables);
  }

  /**
   * Substitutes any variable values into the given string, and returns the resolved string.
   *
   * @param aString the string to resolve against environment variables
   * @return the string after variables have been resolved/susbstituted
   * @see IVariables#resolve(String)
   */
  @Override
  public String resolve(String aString) {
    return variables.resolve(aString);
  }

  /**
   * Substitutes any variable values into each of the given strings, and returns an array containing
   * the resolved string(s).
   *
   * @param aString an array of strings to resolve against environment variables
   * @return the array of strings after variables have been resolved/susbstituted
   * @see IVariables#resolve(String[])
   */
  @Override
  public String[] resolve(String[] aString) {
    return variables.resolve(aString);
  }

  @Override
  public String resolve(String aString, IRowMeta rowMeta, Object[] rowData)
      throws HopValueException {
    return variables.resolve(aString, rowMeta, rowData);
  }

  /**
   * Gets the parent variable variables.
   *
   * @return the parent variable variables
   * @see IVariables#getParentVariables()
   */
  @Override
  public IVariables getParentVariables() {
    if (getParentPipeline() != null) {
      return getParentPipeline();
    }
    if (getParentWorkflow() != null) {
      return getParentWorkflow();
    }
    return variables.getParentVariables();
  }

  /**
   * Sets the parent variable variables.
   *
   * @param parent the new parent variable variables
   * @see IVariables#setParentVariables( IVariables)
   */
  @Override
  public void setParentVariables(IVariables parent) {
    variables.setParentVariables(parent);
  }

  /**
   * Gets the value of the specified variable, or returns a default value if no such variable
   * exists.
   *
   * @param variableName the variable name
   * @param defaultValue the default value
   * @return the value of the specified variable, or returns a default value if no such variable
   *     exists
   * @see IVariables#getVariable(String, String)
   */
  @Override
  public String getVariable(String variableName, String defaultValue) {
    return variables.getVariable(variableName, defaultValue);
  }

  /**
   * Gets the value of the specified variable, or returns a default value if no such variable
   * exists.
   *
   * @param variableName the variable name
   * @return the value of the specified variable, or returns a default value if no such variable
   *     exists
   * @see IVariables#getVariable(String)
   */
  @Override
  public String getVariable(String variableName) {
    return variables.getVariable(variableName);
  }

  /**
   * Returns a boolean representation of the specified variable after performing any necessary
   * substitution. Truth values include case-insensitive versions of "Y", "YES", "TRUE" or "1".
   *
   * @param variableName the variable name
   * @param defaultValue the default value
   * @return a boolean representation of the specified variable after performing any necessary
   *     substitution
   * @see IVariables#getVariableBoolean(String, boolean)
   */
  @Override
  public boolean getVariableBoolean(String variableName, boolean defaultValue) {
    if (!Utils.isEmpty(variableName)) {
      String value = resolve(variableName);
      if (!Utils.isEmpty(value)) {
        return ValueMetaBase.convertStringToBoolean(value);
      }
    }
    return defaultValue;
  }

  /**
   * Sets the values of the pipeline's variables to the values from the parent variables.
   *
   * @param parent the parent
   * @see IVariables#initializeFrom( IVariables)
   */
  @Override
  public void initializeFrom(IVariables parent) {
    variables.initializeFrom(parent);
  }

  /**
   * Gets a list of variable names for the pipeline.
   *
   * @return a list of variable names
   * @see IVariables#getVariableNames()
   */
  @Override
  public String[] getVariableNames() {
    return variables.getVariableNames();
  }

  /**
   * Sets the value of the specified variable to the specified value.
   *
   * @param variableName the variable name
   * @param variableValue the variable value
   * @see IVariables#setVariable(String, String)
   */
  @Override
  public void setVariable(String variableName, String variableValue) {
    variables.setVariable(variableName, variableValue);
  }

  /**
   * Shares a variable variables from another variable variables. This means that the object should
   * take over the variables used as argument.
   *
   * @param variables the variable variables
   * @see IVariables#shareWith(IVariables)
   */
  @Override
  public void shareWith(IVariables variables) {
    this.variables = variables;
  }

  /**
   * Injects variables using the given Map. The behavior should be that the properties object will
   * be stored and at the time the IVariables is initialized (or upon calling this method if the
   * variables is already initialized). After injecting the link of the properties object should be
   * removed.
   *
   * @param map the property map
   * @see IVariables#setVariables(Map)
   */
  @Override
  public void setVariables(Map<String, String> map) {
    variables.setVariables(map);
  }

  /** Pauses the pipeline (pause all transforms). */
  @Override
  public void pauseExecution() {
    setPaused(true);
    for (TransformMetaDataCombi combi : transforms) {
      combi.transform.pauseRunning();
    }
  }

  /** Resumes running the pipeline after a pause (resume all transforms). */
  @Override
  public void resumeExecution() {
    for (TransformMetaDataCombi combi : transforms) {
      combi.transform.resumeRunning();
    }
    setPaused(false);
  }

  /**
   * @deprecated Checks whether the pipeline is being previewed.
   * @return true if the pipeline is being previewed, false otherwise
   */
  @Override
  @Deprecated(since = "2.10")
  public boolean isPreview() {
    return preview;
  }

  /**
   * @deprecated Sets whether the pipeline is being previewed.
   * @param preview true if the pipeline is being previewed, false otherwise
   */
  @Override
  @Deprecated(since = "2.10")
  public void setPreview(boolean preview) {
    this.preview = preview;
  }

  /**
   * Gets a named list (map) of transform performance snapshots.
   *
   * @return a named list (map) of transform performance snapshots
   */
  public Map<String, List<PerformanceSnapShot>> getTransformPerformanceSnapShots() {
    return transformPerformanceSnapShots;
  }

  /**
   * Sets the named list (map) of transform performance snapshots.
   *
   * @param transformPerformanceSnapShots a named list (map) of transform performance snapshots to
   *     set
   */
  public void setTransformPerformanceSnapShots(
      Map<String, List<PerformanceSnapShot>> transformPerformanceSnapShots) {
    this.transformPerformanceSnapShots = transformPerformanceSnapShots;
  }

  @Override
  public void addExecutionStartedListener(
      IExecutionStartedListener<IPipelineEngine<PipelineMeta>> listener) {
    synchronized (executionStartedListeners) {
      executionStartedListeners.add(listener);
    }
  }

  @Override
  public void removeExecutionStartedListener(
      IExecutionStartedListener<IPipelineEngine<PipelineMeta>> listener) {
    synchronized (executionStartedListeners) {
      executionStartedListeners.remove(listener);
    }
  }

  @Override
  public void addExecutionFinishedListener(
      IExecutionFinishedListener<IPipelineEngine<PipelineMeta>> listener) {
    synchronized (executionFinishedListeners) {
      executionFinishedListeners.add(listener);
    }
  }

  @Override
  public void removeExecutionFinishedListener(
      IExecutionFinishedListener<IPipelineEngine<PipelineMeta>> listener) {
    synchronized (executionFinishedListeners) {
      executionFinishedListeners.remove(listener);
    }
  }

  @Override
  public void addExecutionStoppedListener(
      IExecutionStoppedListener<IPipelineEngine<PipelineMeta>> listener) {
    synchronized (executionStoppedListeners) {
      executionStoppedListeners.add(listener);
    }
  }

  @Override
  public void removeExecutionStoppedListener(
      IExecutionStoppedListener<IPipelineEngine<PipelineMeta>> listener) {
    synchronized (executionStoppedListeners) {
      executionStoppedListeners.remove(listener);
    }
  }

  /**
   * @deprecated Sets the list of stop-event listeners for the pipeline.
   * @param executionStoppedListeners the list of stop-event listeners to set
   */
  @Deprecated(since = "2.9", forRemoval = true)
  public void setExecutionStoppedListeners(
      List<IExecutionStoppedListener<IPipelineEngine<PipelineMeta>>> executionStoppedListeners) {
    this.executionStoppedListeners = Collections.synchronizedList(executionStoppedListeners);
  }

  /**
   * @deprecated Gets the list of stop-event listeners for the pipeline. This is not concurrent
   *     safe. Please note this is mutable implementation only for backward compatibility reasons.
   * @return the list of stop-event listeners
   */
  @Deprecated(since = "2.9", forRemoval = true)
  public List<IExecutionStoppedListener<IPipelineEngine<PipelineMeta>>>
      getExecutionStoppedListeners() {
    return executionStoppedListeners;
  }

  /**
   * Adds a stop-event listener to the pipeline.
   *
   * @param pipelineStoppedListener the stop-event listener to add
   */
  public void addPipelineStoppedListener(
      IExecutionStoppedListener<IPipelineEngine<PipelineMeta>> pipelineStoppedListener) {
    executionStoppedListeners.add(pipelineStoppedListener);
  }

  /**
   * Checks if the pipeline is paused.
   *
   * @return true if the pipeline is paused, false otherwise
   */
  @Override
  public boolean isPaused() {
    int exist = status.get() & PAUSED.mask;
    return exist != 0;
  }

  public void setPaused(boolean paused) {
    status.updateAndGet(v -> paused ? v | PAUSED.mask : (BIT_STATUS_SUM ^ PAUSED.mask) & v);
  }

  /**
   * Checks if the pipeline is stopped.
   *
   * @return true if the pipeline is stopped, false otherwise
   */
  @Override
  public boolean isStopped() {
    int exist = status.get() & STOPPED.mask;
    return exist != 0;
  }

  public void setStopped(boolean stopped) {
    status.updateAndGet(v -> stopped ? v | STOPPED.mask : (BIT_STATUS_SUM ^ STOPPED.mask) & v);
  }

  /**
   * Adds a parameter definition to this pipeline.
   *
   * @param key the name of the parameter
   * @param defValue the default value for the parameter
   * @param description the description of the parameter
   * @throws DuplicateParamException the duplicate param exception
   * @see INamedParameters#addParameterDefinition(String, String, String)
   */
  @Override
  public void addParameterDefinition(String key, String defValue, String description)
      throws DuplicateParamException {
    namedParams.addParameterDefinition(key, defValue, description);
  }

  /**
   * Gets the default value of the specified parameter.
   *
   * @param key the name of the parameter
   * @return the default value of the parameter
   * @throws UnknownParamException if the parameter does not exist
   * @see INamedParameters#getParameterDefault(String)
   */
  @Override
  public String getParameterDefault(String key) throws UnknownParamException {
    return namedParams.getParameterDefault(key);
  }

  /**
   * Gets the description of the specified parameter.
   *
   * @param key the name of the parameter
   * @return the parameter description
   * @throws UnknownParamException if the parameter does not exist
   * @see INamedParameters#getParameterDescription(String)
   */
  @Override
  public String getParameterDescription(String key) throws UnknownParamException {
    return namedParams.getParameterDescription(key);
  }

  /**
   * Gets the value of the specified parameter.
   *
   * @param key the name of the parameter
   * @return the parameter value
   * @throws UnknownParamException if the parameter does not exist
   * @see INamedParameters#getParameterValue(String)
   */
  @Override
  public String getParameterValue(String key) throws UnknownParamException {
    return namedParams.getParameterValue(key);
  }

  /**
   * Gets a list of the parameters for the pipeline.
   *
   * @return an array of strings containing the names of all parameters for the pipeline
   * @see INamedParameters#listParameters()
   */
  @Override
  public String[] listParameters() {
    return namedParams.listParameters();
  }

  /**
   * Sets the value for the specified parameter.
   *
   * @param key the name of the parameter
   * @param value the name of the value
   * @throws UnknownParamException if the parameter does not exist
   * @see INamedParameters#setParameterValue(String, String)
   */
  @Override
  public void setParameterValue(String key, String value) throws UnknownParamException {
    namedParams.setParameterValue(key, value);
  }

  /**
   * Remove all parameters.
   *
   * @see INamedParameters#removeAllParameters()
   */
  @Override
  public void removeAllParameters() {
    namedParams.removeAllParameters();
  }

  /**
   * Clear the values of all parameters.
   *
   * @see INamedParameters#clearParameterValues()
   */
  @Override
  public void clearParameterValues() {
    namedParams.clearParameterValues();
  }

  /**
   * Activates all parameters by setting their values. If no values already exist, the method will
   * attempt to set the parameter to the default value. If no default value exists, the method will
   * set the value of the parameter to the empty string ("").
   */
  @Override
  public void activateParameters(IVariables variables) {
    namedParams.activateParameters(variables);
  }

  @Override
  public void copyParametersFromDefinitions(INamedParameterDefinitions definitions) {
    namedParams.copyParametersFromDefinitions(definitions);
  }

  /**
   * Gets the parent pipeline, which is null if no parent pipeline exists.
   *
   * @return a reference to the parent pipeline's Pipeline object, or null if no parent pipeline
   *     exists
   */
  @Override
  public IPipelineEngine getParentPipeline() {
    return parentPipeline;
  }

  /**
   * Sets the parent pipeline.
   *
   * @param parentPipeline the parentPipeline to set
   */
  @Override
  public void setParentPipeline(IPipelineEngine parentPipeline) {
    this.parentPipeline = parentPipeline;
  }

  /**
   * Gets the object name.
   *
   * @return the object name
   * @see ILoggingObject#getObjectName()
   */
  @Override
  public String getObjectName() {
    return getName();
  }

  /**
   * Gets the object copy. For Pipeline, this always returns null
   *
   * @return null
   * @see ILoggingObject#getObjectCopy()
   */
  @Override
  public String getObjectCopy() {
    return null;
  }

  /**
   * Gets the filename of the pipeline, or null if no filename exists
   *
   * @return the filename
   * @see ILoggingObject#getFilename()
   */
  @Override
  public String getFilename() {
    if (pipelineMeta == null) {
      return null;
    }
    return pipelineMeta.getFilename();
  }

  /**
   * Gets the log channel ID.
   *
   * @return the log channel ID
   * @see ILoggingObject#getLogChannelId()
   */
  @Override
  public String getLogChannelId() {
    return log.getLogChannelId();
  }

  /**
   * Gets the object type. For Pipeline, this always returns LoggingObjectType.PIPELINE
   *
   * @return the object type
   * @see ILoggingObject#getObjectType()
   */
  @Override
  public LoggingObjectType getObjectType() {
    return LoggingObjectType.PIPELINE;
  }

  /**
   * Gets the parent logging object interface.
   *
   * @return the parent
   * @see ILoggingObject#getParent()
   */
  @Override
  public ILoggingObject getParent() {
    return parent;
  }

  /**
   * Gets the log level.
   *
   * @return the log level
   * @see ILoggingObject#getLogLevel()
   */
  @Override
  public LogLevel getLogLevel() {
    return logLevel;
  }

  /**
   * Sets the log level.
   *
   * @param logLevel the new log level
   */
  @Override
  public void setLogLevel(LogLevel logLevel) {
    this.logLevel = logLevel;
    log.setLogLevel(logLevel);
  }

  /**
   * Gets the logging hierarchy.
   *
   * @return the logging hierarchy
   */
  public List<LoggingHierarchy> getLoggingHierarchy() {
    List<LoggingHierarchy> hierarchy = new ArrayList<>();
    List<String> childIds = LoggingRegistry.getInstance().getLogChannelChildren(getLogChannelId());
    for (String childId : childIds) {
      ILoggingObject loggingObject = LoggingRegistry.getInstance().getLoggingObject(childId);
      if (loggingObject != null) {
        hierarchy.add(new LoggingHierarchy(getLogChannelId(), loggingObject));
      }
    }

    return hierarchy;
  }

  @Override
  public void addActiveSubPipeline(final String subPipelineName, IPipelineEngine subPipeline) {
    activeSubPipelines.put(subPipelineName, subPipeline);
  }

  @Override
  public IPipelineEngine getActiveSubPipeline(final String subPipelineName) {
    return activeSubPipelines.get(subPipelineName);
  }

  @Override
  public void addActiveSubWorkflow(
      final String subWorkflowName, IWorkflowEngine<WorkflowMeta> subWorkflow) {
    activeSubWorkflows.put(subWorkflowName, subWorkflow);
  }

  @Override
  public IWorkflowEngine<WorkflowMeta> getActiveSubWorkflow(final String subWorkflowName) {
    return activeSubWorkflows.get(subWorkflowName);
  }

  /**
   * Gets the active sub-workflows.
   *
   * @return a map (by name) of the active sub-workflows
   */
  public Map<String, IWorkflowEngine<WorkflowMeta>> getActiveSubWorkflows() {
    return activeSubWorkflows;
  }

  /**
   * Gets the container object ID.
   *
   * @return the HopServer object ID
   */
  @Override
  public String getContainerId() {
    return containerObjectId;
  }

  /**
   * Sets the container object ID.
   *
   * @param containerId the HopServer object ID to set
   */
  @Override
  public void setContainerId(String containerId) {
    this.containerObjectId = containerId;
  }

  /**
   * Gets the registration date. For Pipeline, this always returns null
   *
   * @return null
   */
  @Override
  public Date getRegistrationDate() {
    return null;
  }

  /**
   * Gets the name of the executing server.
   *
   * @return the executingServer
   */
  @Override
  public String getExecutingServer() {
    if (executingServer == null) {
      setExecutingServer(Const.getHostname());
    }
    return executingServer;
  }

  /**
   * Sets the name of the executing server.
   *
   * @param executingServer the executingServer to set
   */
  @Override
  public void setExecutingServer(String executingServer) {
    this.executingServer = executingServer;
  }

  /**
   * Gets the name of the executing user.
   *
   * @return the executingUser
   */
  @Override
  public String getExecutingUser() {
    return executingUser;
  }

  /**
   * Sets the name of the executing user.
   *
   * @param executingUser the executingUser to set
   */
  @Override
  public void setExecutingUser(String executingUser) {
    this.executingUser = executingUser;
  }

  @Override
  public boolean isGatheringMetrics() {
    return log != null && log.isGatheringMetrics();
  }

  @Override
  public void setGatheringMetrics(boolean gatheringMetrics) {
    if (log != null) {
      log.setGatheringMetrics(gatheringMetrics);
    }
  }

  @Override
  public boolean isForcingSeparateLogging() {
    return log != null && log.isForcingSeparateLogging();
  }

  @Override
  public void setForcingSeparateLogging(boolean forcingSeparateLogging) {
    if (log != null) {
      log.setForcingSeparateLogging(forcingSeparateLogging);
    }
  }

  public List<ResultFile> getResultFiles() {
    return resultFiles;
  }

  public void setResultFiles(List<ResultFile> resultFiles) {
    this.resultFiles = resultFiles;
  }

  public List<RowMetaAndData> getResultRows() {
    return resultRows;
  }

  public void setResultRows(List<RowMetaAndData> resultRows) {
    this.resultRows = resultRows;
  }

  @Override
  public Result getPreviousResult() {
    return previousResult;
  }

  @Override
  public void setPreviousResult(Result previousResult) {
    this.previousResult = previousResult;
  }

  /**
   * Clear the error in the pipeline, clear all the rows from all the row sets, to make sure the
   * pipeline can continue with other data. This is intended for use when running single threaded.
   */
  public void clearError() {
    setStopped(false);
    errors.set(0);
    setFinished(false);
    for (TransformMetaDataCombi combi : transforms) {
      ITransform transform = combi.transform;
      for (IRowSet rowSet : transform.getInputRowSets()) {
        rowSet.clear();
      }
      transform.setStopped(false);
    }
  }

  @Override
  public IHopMetadataProvider getMetadataProvider() {
    return metadataProvider;
  }

  @Override
  public void setMetadataProvider(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
    if (pipelineMeta != null) {
      pipelineMeta.setMetadataProvider(metadataProvider);
    }
  }

  /**
   * Sets encoding of HttpServletResponse according to System encoding.Check if system encoding is
   * null or an empty and set it to HttpServletResponse when not and writes error to log if null.
   * Throw IllegalArgumentException if input parameter is null.
   *
   * @param response the HttpServletResponse to set encoding, mayn't be null
   */
  public void setServletReponse(HttpServletResponse response) {
    if (response == null) {
      throw new IllegalArgumentException("HttpServletResponse cannot be null ");
    }
    String encoding = System.getProperty(Const.HOP_DEFAULT_SERVLET_ENCODING, null);
    // true if encoding is null or an empty (also for the next kin of strings: " ")
    if (!StringUtils.isBlank(encoding)) {
      try {
        response.setCharacterEncoding(encoding.trim());
        response.setContentType("text/html; charset=" + encoding);
      } catch (Exception ex) {
        LogChannel.GENERAL.logError("Unable to encode data with encoding : '" + encoding + "'", ex);
      }
    }
    this.servletResponse = response;
  }

  public HttpServletResponse getServletResponse() {
    return servletResponse;
  }

  public void setServletRequest(HttpServletRequest request) {
    this.servletRequest = request;
  }

  public HttpServletRequest getServletRequest() {
    return servletRequest;
  }

  public synchronized void doTopologySortOfTransforms() {
    // The bubble sort algorithm in contrast to the QuickSort or MergeSort
    // algorithms
    // does indeed cover all possibilities.
    // Sorting larger pipelines with hundreds of transforms might be too slow
    // though.
    // We should consider caching PipelineMeta.findPrevious() results in that case.
    //
    pipelineMeta.clearCaches();

    //
    // Cocktail sort (bi-directional bubble sort)
    //
    // Original sort was taking 3ms for 30 transforms
    // cocktail sort takes about 8ms for the same 30, but it works :)
    //
    int transformsMinSize = 0;
    int transformsSize = transforms.size();

    // Noticed a problem with an immediate shrinking iteration window
    // trapping rows that need to be sorted.
    // This threshold buys us some time to get the sorting close before
    // starting to decrease the window size.
    //
    // TODO: this could become much smarter by tracking row movement
    // and reacting to that each outer iteration verses
    // using a threshold.
    //
    // After this many iterations enable trimming inner iteration
    // window on no change being detected.
    //
    int windowShrinkThreshold = (int) Math.round(transformsSize * 0.75);

    // give ourselves some room to sort big lists. the window threshold should
    // stop us before reaching this anyway.
    //
    int totalIterations = transformsSize * 2;

    boolean isBefore = false;
    boolean forwardChange = false;
    boolean backwardChange = false;

    boolean lastForwardChange = true;
    boolean keepSortingForward = true;

    TransformMetaDataCombi one = null;
    TransformMetaDataCombi two = null;

    for (int x = 0; x < totalIterations; x++) {

      // Go forward through the list
      //
      if (keepSortingForward) {
        for (int y = transformsMinSize; y < transformsSize - 1; y++) {
          one = transforms.get(y);
          two = transforms.get(y + 1);

          if (one.transformMeta.equals(two.transformMeta)) {
            isBefore = one.copy > two.copy;
          } else {
            isBefore = pipelineMeta.findPrevious(one.transformMeta, two.transformMeta);
          }
          if (isBefore) {
            // two was found to be positioned BEFORE one so we need to
            // switch them...
            //
            transforms.set(y, two);
            transforms.set(y + 1, one);
            forwardChange = true;
          }
        }
      }

      // Go backward through the list
      //
      for (int z = transformsSize - 1; z > transformsMinSize; z--) {
        one = transforms.get(z);
        two = transforms.get(z - 1);

        if (one.transformMeta.equals(two.transformMeta)) {
          isBefore = one.copy > two.copy;
        } else {
          isBefore = pipelineMeta.findPrevious(one.transformMeta, two.transformMeta);
        }
        if (!isBefore) {
          // two was found NOT to be positioned BEFORE one so we need to
          // switch them...
          //
          transforms.set(z, two);
          transforms.set(z - 1, one);
          backwardChange = true;
        }
      }

      // Shrink transformsSize(max) if there was no forward change
      //
      if (x > windowShrinkThreshold && !forwardChange) {

        // should we keep going? check the window size
        //
        transformsSize--;
        if (transformsSize <= transformsMinSize) {
          break;
        }
      }

      // shrink transformsMinSize(min) if there was no backward change
      //
      if (x > windowShrinkThreshold && !backwardChange) {

        // should we keep going? check the window size
        //
        transformsMinSize++;
        if (transformsMinSize >= transformsSize) {
          break;
        }
      }

      // End of both forward and backward traversal.
      // Time to see if we should keep going.
      //
      if (!forwardChange && !backwardChange) {
        break;
      }

      //
      // if we are past the first iteration and there has been no change twice,
      // quit doing it!
      //
      if (keepSortingForward && x > 0 && !lastForwardChange && !forwardChange) {
        keepSortingForward = false;
      }
      lastForwardChange = forwardChange;
      forwardChange = false;
      backwardChange = false;
    } // finished sorting
  }

  /**
   * Gets executionStartDate
   *
   * @return value of executionStartDate
   */
  @Override
  public Date getExecutionStartDate() {
    return executionStartDate;
  }

  /**
   * @param executionStartDate The executionStartDate to set
   */
  public void setExecutionStartDate(Date executionStartDate) {
    this.executionStartDate = executionStartDate;
  }

  /**
   * Gets executionEndDate
   *
   * @return value of executionEndDate
   */
  @Override
  public Date getExecutionEndDate() {
    return executionEndDate;
  }

  /**
   * @param executionEndDate The executionEndDate to set
   */
  public void setExecutionEndDate(Date executionEndDate) {
    this.executionEndDate = executionEndDate;
  }

  @Override
  public Map<String, Object> getExtensionDataMap() {
    return extensionDataMap;
  }

  // TODO: i18n
  public static final IEngineMetric METRIC_INPUT =
      new EngineMetric(
          METRIC_NAME_INPUT, "Input", "The number of rows read from physical I/O", "010", true);
  public static final IEngineMetric METRIC_READ =
      new EngineMetric(
          METRIC_NAME_READ, "Read", "The number of rows read from other transforms", "020", true);
  public static final IEngineMetric METRIC_WRITTEN =
      new EngineMetric(
          METRIC_NAME_WRITTEN,
          "Written",
          "The number of rows written to other transforms",
          "030",
          true);
  public static final IEngineMetric METRIC_OUTPUT =
      new EngineMetric(
          METRIC_NAME_OUTPUT, "Output", "The number of rows written to physical I/O", "040", true);
  public static final IEngineMetric METRIC_UPDATED =
      new EngineMetric(METRIC_NAME_UPDATED, "Updated", "The number of rows updated", "050", true);
  public static final IEngineMetric METRIC_REJECTED =
      new EngineMetric(
          METRIC_NAME_REJECTED,
          "Rejected",
          "The number of rows rejected by a transform",
          "060",
          true);
  public static final IEngineMetric METRIC_ERROR =
      new EngineMetric(METRIC_NAME_ERROR, "Errors", "The number of errors", "070", true);
  public static final IEngineMetric METRIC_BUFFER_IN =
      new EngineMetric(
          METRIC_NAME_BUFFER_IN,
          "Buffers Input",
          "The number of rows in the transforms input buffers",
          "080",
          true);
  public static final IEngineMetric METRIC_BUFFER_OUT =
      new EngineMetric(
          METRIC_NAME_BUFFER_OUT,
          "Buffers Output",
          "The number of rows in the transforms output buffers",
          "090",
          true);

  public static final IEngineMetric METRIC_INIT =
      new EngineMetric(
          METRIC_NAME_INIT,
          "Inits",
          "The number of times the transform was initialised",
          "000",
          true);
  public static final IEngineMetric METRIC_FLUSH_BUFFER =
      new EngineMetric(
          METRIC_NAME_FLUSH_BUFFER,
          "Flushes",
          "The number of times a buffer flush occurred on a ",
          "100",
          true);

  @Override
  public EngineMetrics getEngineMetrics() {
    return getEngineMetrics(null, -1);
  }

  @Override
  public EngineMetrics getEngineMetrics(String componentName, int copyNr) {
    EngineMetrics metrics = new EngineMetrics();
    metrics.setStartDate(getExecutionStartDate());
    metrics.setEndDate(getExecutionEndDate());

    if (transforms != null) {
      synchronized (transforms) {
        for (TransformMetaDataCombi combi : transforms) {
          ITransform transform = combi.transform;

          boolean collect = true;
          if (copyNr >= 0) {
            collect = collect && copyNr == combi.copy;
          }
          if (componentName != null) {
            collect = collect && componentName.equalsIgnoreCase(combi.transformName);
          }

          if (collect) {

            metrics.addComponent(combi.transform);

            metrics.setComponentMetric(
                combi.transform, METRIC_INPUT, combi.transform.getLinesInput());
            metrics.setComponentMetric(
                combi.transform, METRIC_OUTPUT, combi.transform.getLinesOutput());
            metrics.setComponentMetric(
                combi.transform, METRIC_READ, combi.transform.getLinesRead());
            metrics.setComponentMetric(
                combi.transform, METRIC_WRITTEN, combi.transform.getLinesWritten());
            metrics.setComponentMetric(
                combi.transform, METRIC_UPDATED, combi.transform.getLinesUpdated());
            metrics.setComponentMetric(
                combi.transform, METRIC_REJECTED, combi.transform.getLinesRejected());
            metrics.setComponentMetric(combi.transform, METRIC_ERROR, combi.transform.getErrors());

            long inputBufferSize = 0;
            for (IRowSet rowSet : transform.getInputRowSets()) {
              inputBufferSize += rowSet.size();
            }
            metrics.setComponentMetric(combi.transform, METRIC_BUFFER_IN, inputBufferSize);
            long outputBufferSize = 0;
            for (IRowSet rowSet : transform.getOutputRowSets()) {
              outputBufferSize += rowSet.size();
            }
            metrics.setComponentMetric(combi.transform, METRIC_BUFFER_OUT, outputBufferSize);

            TransformStatus transformStatus = new TransformStatus(combi.transform);
            metrics.setComponentSpeed(combi.transform, transformStatus.getSpeed());
            metrics.setComponentStatus(
                combi.transform, combi.transform.getStatus().getDescription());
            metrics.setComponentRunning(combi.transform, combi.transform.isRunning());
          }
        }
      }
    }

    // Also pass on the performance snapshot data...
    //
    if (transformPerformanceSnapShots != null) {
      for (String componentString : transformPerformanceSnapShots.keySet()) {
        String snapshotName = componentString;
        int snapshotCopyNr = 0;
        int lastDot = componentString.lastIndexOf('.');
        if (lastDot > 0) {
          componentString.substring(0, lastDot);
          snapshotCopyNr = Const.toInt(componentString.substring(lastDot + 1), 0);
        }
        boolean collect = true;
        if (componentName != null) {
          collect = collect && componentName.equalsIgnoreCase(componentString);
        }
        if (copyNr >= 0) {
          collect = collect && snapshotCopyNr == copyNr;
        }

        if (collect) {
          IEngineComponent component = findComponent(snapshotName, snapshotCopyNr);
          if (component != null) {
            List<PerformanceSnapShot> snapShots =
                transformPerformanceSnapShots.get(componentString);
            metrics.getComponentPerformanceSnapshots().put(component, snapShots);
          }
        }
      }
    }

    return metrics;
  }

  @Override
  public String getComponentLogText(String componentName, int copyNr) {
    ITransform transform = getTransform(componentName, copyNr);
    if (transform == null) {
      return null;
    }
    StringBuffer logBuffer =
        HopLogStore.getAppender().getBuffer(transform.getLogChannel().getLogChannelId(), false);
    if (logBuffer == null) {
      return null;
    }
    return logBuffer.toString();
  }

  @Override
  public List<IEngineComponent> getComponents() {
    List<IEngineComponent> list = new ArrayList<>();
    if (transforms != null) {
      synchronized (transforms) {
        for (TransformMetaDataCombi transform : transforms) {
          list.add(transform.transform);
        }
      }
    }
    return list;
  }

  @Override
  public IEngineComponent findComponent(String name, int copyNr) {
    return getTransform(name, copyNr);
  }

  @Override
  public List<IEngineComponent> getComponentCopies(String name) {
    List<IEngineComponent> list = new ArrayList<>();
    if (transforms != null) {
      for (TransformMetaDataCombi transform : transforms) {
        if (transform.transformName.equalsIgnoreCase(name)) {
          list.add(transform.transform);
        }
      }
    }
    return list;
  }

  /**
   * Gets pluginId
   *
   * @return value of pluginId
   */
  @Override
  public String getPluginId() {
    return pluginId;
  }

  /**
   * @param pluginId The pluginId to set
   */
  @Override
  public void setPluginId(String pluginId) {
    this.pluginId = pluginId;
  }

  @Override
  public IPipelineEngineRunConfiguration createDefaultPipelineEngineRunConfiguration() {
    return new EmptyPipelineRunConfiguration();
  }

  @Override
  public void retrieveComponentOutput(
      IVariables variables,
      String componentName,
      int copyNr,
      int nrRows,
      IPipelineComponentRowsReceived rowsReceived)
      throws HopException {
    ITransform iTransform = getTransform(componentName, copyNr);
    if (iTransform == null) {
      throw new HopException(
          "Unable to find transform '"
              + componentName
              + "', copy "
              + copyNr
              + " to retrieve output rows from");
    }
    RowBuffer rowBuffer = new RowBuffer(pipelineMeta.getTransformFields(variables, componentName));
    iTransform.addRowListener(
        new RowAdapter() {
          @Override
          public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) throws HopTransformException {
            if (rowBuffer.getBuffer().size() < nrRows) {
              rowBuffer.getBuffer().add(row);
              if (rowBuffer.getBuffer().size() >= nrRows) {
                try {
                  rowsReceived.rowsReceived(Pipeline.this, rowBuffer);
                } catch (HopException e) {
                  throw new HopTransformException(
                      "Error recieving rows from '" + componentName + " copy " + copyNr, e);
                }
              }
            }
          }
        });
  }

  public void addStartedListener(IExecutionStartedListener<IPipelineEngine<PipelineMeta>> listener)
      throws HopException {
    executionStartedListeners.add(listener);
  }

  public void addFinishedListener(
      IExecutionFinishedListener<IPipelineEngine<PipelineMeta>> listener) throws HopException {
    executionFinishedListeners.add(listener);
  }

  /**
   * Gets rowSetSize
   *
   * @return value of rowSetSize
   */
  public int getRowSetSize() {
    return rowSetSize;
  }

  /**
   * @param rowSetSize The rowSetSize to set
   */
  public void setRowSetSize(int rowSetSize) {
    this.rowSetSize = rowSetSize;
  }

  /**
   * Gets feedbackShown
   *
   * @return value of feedbackShown
   */
  @Override
  public boolean isFeedbackShown() {
    return feedbackShown;
  }

  /**
   * @param feedbackShown The feedbackShown to set
   */
  public void setFeedbackShown(boolean feedbackShown) {
    this.feedbackShown = feedbackShown;
  }

  /**
   * Gets feedbackSize
   *
   * @return value of feedbackSize
   */
  @Override
  public int getFeedbackSize() {
    return feedbackSize;
  }

  /**
   * @param feedbackSize The feedbackSize to set
   */
  public void setFeedbackSize(int feedbackSize) {
    this.feedbackSize = feedbackSize;
  }

  /**
   * @deprecated Gets executionStartedListeners
   * @return value of executionStartedListeners
   */
  @Deprecated(since = "2.9", forRemoval = true)
  public List<IExecutionStartedListener<IPipelineEngine<PipelineMeta>>>
      getExecutionStartedListeners() {
    return executionStartedListeners;
  }

  /**
   * @deprecated
   * @param executionStartedListeners The executionStartedListeners to set
   */
  @Deprecated(since = "2.9", forRemoval = true)
  public void setExecutionStartedListeners(
      List<IExecutionStartedListener<IPipelineEngine<PipelineMeta>>> executionStartedListeners) {
    this.executionStartedListeners = executionStartedListeners;
  }

  /**
   * @deprecated Gets executionFinishedListeners
   * @return value of executionFinishedListeners
   */
  @Deprecated(since = "2.9", forRemoval = true)
  public List<IExecutionFinishedListener<IPipelineEngine<PipelineMeta>>>
      getExecutionFinishedListeners() {
    return executionFinishedListeners;
  }

  /**
   * @deprecated
   * @param executionFinishedListeners The executionFinishedListeners to set
   */
  @Deprecated(since = "2.9", forRemoval = true)
  public void setExecutionFinishedListeners(
      List<IExecutionFinishedListener<IPipelineEngine<PipelineMeta>>> executionFinishedListeners) {
    this.executionFinishedListeners = executionFinishedListeners;
  }

  /**
   * Gets pipelineRunConfiguration
   *
   * @return value of pipelineRunConfiguration
   */
  @Override
  public PipelineRunConfiguration getPipelineRunConfiguration() {
    return pipelineRunConfiguration;
  }

  /**
   * @param pipelineRunConfiguration The pipelineRunConfiguration to set
   */
  @Override
  public void setPipelineRunConfiguration(PipelineRunConfiguration pipelineRunConfiguration) {
    this.pipelineRunConfiguration = pipelineRunConfiguration;
  }

  /**
   * Gets activeSubPipelines
   *
   * @return value of activeSubPipelines
   */
  public Map<String, IPipelineEngine> getActiveSubPipelines() {
    return activeSubPipelines;
  }

  /**
   * @param activeSubPipelines The activeSubPipelines to set
   */
  public void setActiveSubPipelines(Map<String, IPipelineEngine> activeSubPipelines) {
    this.activeSubPipelines = activeSubPipelines;
  }

  /**
   * @param activeSubWorkflows The activeSubWorkflows to set
   */
  public void setActiveSubWorkflows(Map<String, IWorkflowEngine<WorkflowMeta>> activeSubWorkflows) {
    this.activeSubWorkflows = activeSubWorkflows;
  }

  /**
   * Gets variables
   *
   * @return value of variables
   */
  public IVariables getVariables() {
    return variables;
  }

  /**
   * @param variables The variables to set
   */
  public void setVariables(IVariables variables) {
    this.variables = variables;
  }

  /**
   * Gets dataSamplers
   *
   * @return value of dataSamplers
   */
  public List<IExecutionDataSampler<? extends IExecutionDataSamplerStore>> getDataSamplers() {
    return dataSamplers;
  }

  /**
   * Sets dataSamplers
   *
   * @param dataSamplers value of dataSamplers
   */
  public void setDataSamplers(
      List<IExecutionDataSampler<? extends IExecutionDataSamplerStore>> dataSamplers) {
    this.dataSamplers = dataSamplers;
  }

  @Override
  public <Store extends IExecutionDataSamplerStore, Sampler extends IExecutionDataSampler<Store>>
      void addExecutionDataSampler(Sampler sampler) {
    dataSamplers.add(sampler);
  }
}
