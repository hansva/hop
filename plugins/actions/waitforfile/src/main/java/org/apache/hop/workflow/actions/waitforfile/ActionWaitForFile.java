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

package org.apache.hop.workflow.actions.waitforfile;

import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Result;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.resource.ResourceEntry;
import org.apache.hop.resource.ResourceEntry.ResourceType;
import org.apache.hop.resource.ResourceReference;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.action.validator.ActionValidatorUtils;
import org.apache.hop.workflow.action.validator.AndValidator;

/** This defines a 'wait for file' action. Its use is to wait for a file to appear. */
@Action(
    id = "WAIT_FOR_FILE",
    name = "i18n::ActionWaitForFile.Name",
    description = "i18n::ActionWaitForFile.Description",
    image = "WaitForFile.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.FileManagement",
    keywords = "i18n::ActionWaitForFile.keyword",
    documentationUrl = "/workflow/actions/waitforfile.html")
public class ActionWaitForFile extends ActionBase implements Cloneable, IAction {
  private static final Class<?> PKG = ActionWaitForFile.class;
  public static final String CONST_TO_STOP_GROWING = "] to stop growing";

  @HopMetadataProperty(key = "filename")
  private String filename;

  /** Maximum timeout in seconds */
  @HopMetadataProperty(key = "maximum_timeout")
  private String maximumTimeout;

  /** cycle time in seconds */
  @HopMetadataProperty(key = "check_cycle_time")
  private String checkCycleTime;

  @HopMetadataProperty(key = "success_on_timeout")
  private boolean successOnTimeout;

  @HopMetadataProperty(key = "file_size_check")
  private boolean fileSizeCheck;

  @HopMetadataProperty(key = "add_filename_result")
  private boolean addFilenameToResult;

  // infinite timeout
  private static final String DEFAULT_MAXIMUM_TIMEOUT = "0";

  // 1 minute
  private static final String DEFAULT_CHECK_CYCLE_TIME = "60";

  public ActionWaitForFile(String n) {
    super(n, "");
    filename = null;
    maximumTimeout = DEFAULT_MAXIMUM_TIMEOUT;
    checkCycleTime = DEFAULT_CHECK_CYCLE_TIME;
    successOnTimeout = false;
    fileSizeCheck = false;
    addFilenameToResult = false;
  }

  public ActionWaitForFile() {
    this("");
  }

  @Override
  public Object clone() {
    ActionWaitForFile je = (ActionWaitForFile) super.clone();
    return je;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  @Override
  public String getFilename() {
    return filename;
  }

  @Override
  public String getRealFilename() {
    return resolve(getFilename());
  }

  @Override
  public Result execute(Result previousResult, int nr) {
    Result result = previousResult;
    result.setResult(false);

    // Start time (in seconds)
    long timeStart = System.currentTimeMillis() / 1000;

    if (filename != null) {
      FileObject fileObject = null;
      String realFilename = getRealFilename();

      try {
        fileObject = HopVfs.getFileObject(realFilename);

        long iMaximumTimeout =
            Const.toInt(getRealMaximumTimeout(), Const.toInt(DEFAULT_MAXIMUM_TIMEOUT, 0));
        long iCycleTime =
            Const.toInt(getRealCheckCycleTime(), Const.toInt(DEFAULT_CHECK_CYCLE_TIME, 0));

        //
        // Sanity check on some values, and complain on insanity
        //
        if (iMaximumTimeout < 0) {
          iMaximumTimeout = Const.toInt(DEFAULT_MAXIMUM_TIMEOUT, 0);
          if (isBasic()) {
            logBasic("Maximum timeout invalid, reset to " + iMaximumTimeout);
          }
        }

        if (iCycleTime < 1) {
          // If lower than 1 set to the default
          iCycleTime = Const.toInt(DEFAULT_CHECK_CYCLE_TIME, 1);
          if (isBasic()) {
            logBasic("Check cycle time invalid, reset to " + iCycleTime);
          }
        }

        if (iMaximumTimeout == 0) {
          if (isBasic()) {
            logBasic("Waiting indefinitely for file [" + realFilename + "]");
          }
        } else {
          if (isBasic()) {
            logBasic("Waiting " + iMaximumTimeout + " seconds for file [" + realFilename + "]");
          }
        }

        boolean continueLoop = true;
        while (continueLoop && !parentWorkflow.isStopped()) {
          fileObject = HopVfs.getFileObject(realFilename);

          if (fileObject.exists()) {
            // file exists, we're happy to exit
            if (isBasic()) {
              logBasic("Detected file [" + realFilename + "] within timeout");
            }
            result.setResult(true);
            continueLoop = false;

            // add filename to result filenames
            if (addFilenameToResult && fileObject.getType() == FileType.FILE) {
              ResultFile resultFile =
                  new ResultFile(
                      ResultFile.FILE_TYPE_GENERAL,
                      fileObject,
                      parentWorkflow.getWorkflowName(),
                      toString());
              resultFile.setComment(BaseMessages.getString(PKG, "ActionWaitForFile.FilenameAdded"));
              result.getResultFiles().put(resultFile.getFile().toString(), resultFile);
            }
          } else {
            long now = System.currentTimeMillis() / 1000;

            if ((iMaximumTimeout > 0) && (now > (timeStart + iMaximumTimeout))) {
              continueLoop = false;

              // file doesn't exist after timeout, either true or false
              if (isSuccessOnTimeout()) {
                if (isBasic()) {
                  logBasic("Didn't detect file [" + realFilename + "] before timeout, success");
                }
                result.setResult(true);
              } else {
                if (isBasic()) {
                  logBasic("Didn't detect file [" + realFilename + "] before timeout, failure");
                }
                result.setResult(false);
              }
            }

            // sleep algorithm
            long sleepTime = 0;

            if (iMaximumTimeout == 0) {
              sleepTime = iCycleTime;
            } else {
              if ((now + iCycleTime) < (timeStart + iMaximumTimeout)) {
                sleepTime = iCycleTime;
              } else {
                sleepTime = iCycleTime - ((now + iCycleTime) - (timeStart + iMaximumTimeout));
              }
            }

            try {
              if (sleepTime > 0) {
                if (isDetailed()) {
                  logDetailed(
                      "Sleeping "
                          + sleepTime
                          + " seconds before next check for file ["
                          + realFilename
                          + "]");
                }
                Thread.sleep(sleepTime * 1000);
              }
            } catch (InterruptedException e) {
              // something strange happened
              result.setResult(false);
              continueLoop = false;
            }
          }
        }

        if (!parentWorkflow.isStopped() && fileObject.exists() && isFileSizeCheck()) {
          long oldSize = -1;
          long newSize = fileObject.getContent().getSize();

          if (isDetailed()) {
            logDetailed("File [" + realFilename + "] is " + newSize + " bytes long");
          }
          if (isBasic()) {
            logBasic(
                "Waiting until file ["
                    + realFilename
                    + "] stops growing for "
                    + iCycleTime
                    + " seconds");
          }
          while (oldSize != newSize && !parentWorkflow.isStopped()) {
            try {
              if (isDetailed()) {
                logDetailed(
                    "Sleeping "
                        + iCycleTime
                        + " seconds, waiting for file ["
                        + realFilename
                        + CONST_TO_STOP_GROWING);
              }
              Thread.sleep(iCycleTime * 1000);
            } catch (InterruptedException e) {
              // something strange happened
              result.setResult(false);
              continueLoop = false;
            }
            oldSize = newSize;
            newSize = fileObject.getContent().getSize();
            if (isDetailed()) {
              logDetailed("File [" + realFilename + "] is " + newSize + " bytes long");
            }
          }
          if (isBasic()) {
            logBasic("Stopped waiting for file [" + realFilename + CONST_TO_STOP_GROWING);
          }
        }

        if (parentWorkflow.isStopped()) {
          result.setResult(false);
        }
      } catch (Exception e) {
        logBasic("Exception while waiting for file [" + realFilename + CONST_TO_STOP_GROWING, e);
      } finally {
        if (fileObject != null) {
          try {
            fileObject.close();
          } catch (Exception e) {
            // Ignore errors
          }
        }
      }
    } else {
      logError("No filename is defined.");
    }

    return result;
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  public boolean isSuccessOnTimeout() {
    return successOnTimeout;
  }

  public void setSuccessOnTimeout(boolean successOnTimeout) {
    this.successOnTimeout = successOnTimeout;
  }

  public String getCheckCycleTime() {
    return checkCycleTime;
  }

  public String getRealCheckCycleTime() {
    return resolve(getCheckCycleTime());
  }

  public void setCheckCycleTime(String checkCycleTime) {
    this.checkCycleTime = checkCycleTime;
  }

  public String getMaximumTimeout() {
    return maximumTimeout;
  }

  public String getRealMaximumTimeout() {
    return resolve(getMaximumTimeout());
  }

  public void setMaximumTimeout(String maximumTimeout) {
    this.maximumTimeout = maximumTimeout;
  }

  public boolean isFileSizeCheck() {
    return fileSizeCheck;
  }

  public void setFileSizeCheck(boolean fileSizeCheck) {
    this.fileSizeCheck = fileSizeCheck;
  }

  public boolean isAddFilenameToResult() {
    return addFilenameToResult;
  }

  public void setAddFilenameToResult(boolean addFilenameToResult) {
    this.addFilenameToResult = addFilenameToResult;
  }

  @Override
  public List<ResourceReference> getResourceDependencies(
      IVariables variables, WorkflowMeta workflowMeta) {
    List<ResourceReference> references = super.getResourceDependencies(variables, workflowMeta);
    if (!Utils.isEmpty(filename)) {
      ResourceReference reference = new ResourceReference(this);
      reference.getEntries().add(new ResourceEntry(getRealFilename(), ResourceType.FILE));
      references.add(reference);
    }
    return references;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      WorkflowMeta workflowMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "filename",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.notBlankValidator()));
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "maximumTimeout",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.integerValidator()));
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "checkCycleTime",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.integerValidator()));
  }
}
