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

package org.apache.hop.ui.hopgui.file;

import java.io.File;
import java.util.Properties;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;

public abstract class HopFileTypeBase implements IHopFileType {

  @Override
  public abstract String getName();

  @Override
  public abstract Properties getCapabilities();

  @Override
  public abstract String[] getFilterExtensions();

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    return obj.getClass().equals(this.getClass()); // same class is enough
  }

  /**
   * Check if a path is a VFS path (contains a scheme like sftp://, s3://, etc.)
   *
   * @param path the path to check
   * @return true if this is a VFS path
   */
  private boolean isVfsPath(String path) {
    return path != null && path.contains("://");
  }

  @Override
  public boolean isHandledBy(String filename, boolean checkContent) throws HopException {
    try {
      if (checkContent) {
        throw new HopException(
            "Generic file content validation is not possible at this time for file '"
                + filename
                + "'");
      } else {
        // Use standard Java I/O for local paths (faster than VFS)
        String fileExtension;
        if (isVfsPath(filename)) {
          FileObject fileObject = HopVfs.getFileObject(filename);
          FileName fileName = fileObject.getName();
          fileExtension = fileName.getExtension().toLowerCase();
        } else {
          String name = new File(filename).getName();
          int lastDot = name.lastIndexOf('.');
          fileExtension = lastDot >= 0 ? name.substring(lastDot + 1).toLowerCase() : "";
        }

        // No extension
        if (Utils.isEmpty(fileExtension)) return false;

        // Verify the extension
        //
        for (String typeExtension : getFilterExtensions()) {
          if (typeExtension.toLowerCase().endsWith(fileExtension)) {
            return true;
          }
        }

        return false;
      }
    } catch (Exception e) {
      throw new HopException(
          "Unable to verify file handling of file '" + filename + "' by extension", e);
    }
  }

  @Override
  public boolean hasCapability(String capability) {
    if (getCapabilities() == null) {
      return false;
    }
    Object available = getCapabilities().get(capability);
    if (available == null) {
      return false;
    }
    return "true".equalsIgnoreCase(available.toString());
  }
}
