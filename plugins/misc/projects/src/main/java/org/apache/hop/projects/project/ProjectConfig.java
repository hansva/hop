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

package org.apache.hop.projects.project;

import java.util.Objects;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.projects.environment.LifecycleEnvironment;

public class ProjectConfig {

  protected String projectName;
  protected String projectHome;
  protected String configFilename;

  public ProjectConfig() {
    super();
  }

  public ProjectConfig(String projectName, String projectHome, String configFilename) {
    super();
    this.projectName = projectName;
    this.projectHome = projectHome;
    this.configFilename = configFilename;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProjectConfig that = (ProjectConfig) o;
    return Objects.equals(projectName, that.projectName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectName);
  }

  /**
   * The full path to the project config filename
   *
   * @param variables
   * @return The path to the project config filename
   * @throws HopException In case the home folder doesn't exist or an invalid filename/path is being
   *     used.
   */
  public String getActualProjectConfigFilename(IVariables variables) throws HopException {
    try {
      String actualHomeFolder = variables.resolve(getProjectHome());
      FileObject actualHome = HopVfs.getFileObject(actualHomeFolder);
      if (!actualHome.exists()) {
        throw new HopException("Project home folder '" + actualHomeFolder + "' does not exist");
      }
      String actualConfigFilename = variables.resolve(getConfigFilename());
      String fullFilename = FilenameUtils.concat(actualHome.toString(), actualConfigFilename);
      if (fullFilename == null) {
        throw new HopException(
            "Unable to determine full path to the configuration file '"
                + actualConfigFilename
                + "' in home folder '"
                + actualHomeFolder);
      }
      return fullFilename;
    } catch (Exception e) {
      throw new HopException("Error calculating actual project config filename", e);
    }
  }

  public Project loadProject(IVariables variables) throws HopException {
    String configFilename = getActualProjectConfigFilename(variables);
    if (configFilename == null) {
      String projHome = variables.resolve(getProjectHome());
      String confFile = variables.resolve(getConfigFilename());
      throw new HopException(
          "Invalid project folder provided: home folder: '"
              + projHome
              + "', config file: '"
              + confFile
              + "'");
    }
    Project project = new Project(configFilename);
    try {
      if (HopVfs.getFileObject(configFilename).exists()) {
        project.readFromFile();
      }
    } catch (Exception e) {
      throw new HopException(
          "Error checking config filename '" + configFilename + "' existence while loading project",
          e);
    }
    return project;
  }

  /**
   * Gets projectName
   *
   * @return value of projectName
   */
  public String getProjectName() {
    return projectName;
  }

  /**
   * @param projectName The projectName to set
   */
  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  /**
   * Gets projectHome
   *
   * @return value of projectHome
   */
  public String getProjectHome() {
    return projectHome;
  }

  /**
   * @param projectHome The projectHome to set
   */
  public void setProjectHome(String projectHome) {
    this.projectHome = projectHome;
  }

  /**
   * Gets configFilename
   *
   * @return value of configFilename
   */
  public String getConfigFilename() {
    return configFilename;
  }

  /**
   * @param configFilename The configFilename to set
   */
  public void setConfigFilename(String configFilename) {
    this.configFilename = configFilename;
  }

  public void addEnvironment(LifecycleEnvironment environment, IVariables variables)
      throws HopException {
    Project project = loadProject(variables);
    if (project.addEnvironment(environment)) {
      project.saveToFile();
    }
  }

  public void removeEnvironment(LifecycleEnvironment environment, IVariables variables)
      throws HopException {
    Project project = loadProject(variables);
    if (project.removeEnvironment(environment)) {
      project.saveToFile();
    }
  }
}
