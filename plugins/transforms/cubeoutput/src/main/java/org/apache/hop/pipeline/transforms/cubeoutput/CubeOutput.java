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

package org.apache.hop.pipeline.transforms.cubeoutput;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Outputs a stream/series of rows to a file, effectively building a sort of (compressed) microcube.
 */
public class CubeOutput extends BaseTransform<CubeOutputMeta, CubeOutputData> {

  private static final Class<?> PKG = CubeOutputMeta.class;

  public CubeOutput(
      TransformMeta transformMeta,
      CubeOutputMeta meta,
      CubeOutputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] r;
    boolean result = true;

    r = getRow(); // This also waits for a row to be finished.

    if (first) { // Always run this code once, even if stream is empty (r==null)
      if (getInputRowMeta() != null) {
        data.outputMeta = getInputRowMeta().clone();
      } else {
        // If the stream is empty, then row metadata probably hasn't been received. In this case,
        // use
        // the design-time algorithm to calculate the output metadata.
        data.outputMeta = getPipelineMeta().getPrevTransformFields(this, getTransformMeta());
      }

      // If input stream is empty, but file was already opened in init(), then
      // write metadata so as to create a valid, empty cube file.
      if (r == null && data.oneFileOpened) {
        result = writeHeaderToFile();
        if (!result) {
          setErrors(1);
          stopAll();
          return false;
        }
      }
    }

    if (r == null) {
      setOutputDone();
      return false;
    }
    if (first) {
      if (meta.isDoNotOpenNewFileInit()) {
        try {
          prepareFile();
          data.oneFileOpened = true;
        } catch (HopFileException ioe) {
          logError(
              BaseMessages.getString(PKG, "CubeOutput.Log.ErrorOpeningCubeOutputFile")
                  + ioe.toString());
          setErrors(1);
          return false;
        }
      }

      result = writeHeaderToFile();
      if (!result) {
        setErrors(1);
        stopAll();
        return false;
      }

      first = false;
    }
    result = writeRowToFile(r);
    if (!result) {
      setErrors(1);
      stopAll();
      return false;
    }

    putRow(data.outputMeta, r); // in case we want it to go further...

    if (checkFeedback(getLinesOutput()) && isBasic()) {
      logBasic(BaseMessages.getString(PKG, "CubeOutput.Log.LineNumber") + getLinesOutput());
    }

    return result;
  }

  private synchronized boolean writeHeaderToFile() {
    try {
      data.outputMeta.writeMeta(data.dos);
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "CubeOutput.Log.ErrorWritingLine") + e.toString());
      return false;
    }

    return true;
  }

  private synchronized boolean writeRowToFile(Object[] r) {
    try {
      // Write data to the cube file...
      data.outputMeta.writeData(data.dos, r);
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "CubeOutput.Log.ErrorWritingLine") + e.toString());
      return false;
    }

    incrementLinesOutput();

    return true;
  }

  @Override
  public boolean init() {
    if (super.init()) {
      if (!meta.isDoNotOpenNewFileInit()) {
        try {
          prepareFile();
          data.oneFileOpened = true;
          return true;
        } catch (HopFileException ioe) {
          logError(
              BaseMessages.getString(PKG, "CubeOutput.Log.ErrorOpeningCubeOutputFile")
                  + ioe.toString());
        }
      } else {
        return true;
      }
    }
    return false;
  }

  private void prepareFile() throws HopFileException {
    try {
      String filename = resolve(meta.getFilename());

      FileObject fileObject = HopVfs.getFileObject(filename, variables);

      // See if we need to create the parent folder(s)...
      //
      if (meta.isFilenameCreatingParentFolders()) {
        createParentFolder(fileObject.getParent());
      }

      if (meta.isAddToResultFilenames()) {
        // Add this to the result file names...
        ResultFile resultFile =
            new ResultFile(
                ResultFile.FILE_TYPE_GENERAL,
                fileObject,
                getPipelineMeta().getName(),
                getTransformName());
        resultFile.setComment("This file was created with a cube file output transform");
        addResultFile(resultFile);
      }

      data.fos = HopVfs.getOutputStream(filename, false, variables);
      data.zip = new GZIPOutputStream(data.fos);
      data.dos = new DataOutputStream(data.zip);
    } catch (Exception e) {
      throw new HopFileException(e);
    }
  }

  private void createParentFolder(FileObject parentFolder) throws HopTransformException {
    if (parentFolder == null) return;

    try {
      // See if we need to create the parent folder(s)...
      if (!parentFolder.exists()) {

        createParentFolder(parentFolder.getParent());

        // Try to create the parent folder...
        parentFolder.createFolder();
        if (isDebug()) {
          logDebug(
              BaseMessages.getString(
                  PKG, "CubeOutput.Log.ParentFolderCreated", parentFolder.getName()));
        }
      }
    } catch (Exception e) {
      throw new HopTransformException(
          BaseMessages.getString(
              PKG, "CubeOutput.Error.ErrorCreatingParentFolder", parentFolder.getName()));
    } finally {
      if (parentFolder != null) {
        try {
          parentFolder.close();
        } catch (Exception ex) {
          // Ignore
        }
      }
    }
  }

  @Override
  public void dispose() {
    if (data.oneFileOpened) {
      try {
        if (data.dos != null) {
          data.dos.close();
          data.dos = null;
        }
        if (data.zip != null) {
          data.zip.close();
          data.zip = null;
        }
        if (data.fos != null) {
          data.fos.close();
          data.fos = null;
        }
      } catch (IOException e) {
        logError(
            BaseMessages.getString(PKG, "CubeOutput.Log.ErrorClosingFile") + meta.getFilename());
        setErrors(1);
        stopAll();
      }
    }

    super.dispose();
  }
}
