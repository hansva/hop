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

package org.apache.hop.projects.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.projects.environment.LifecycleEnvironment;
import org.apache.hop.projects.lifecycle.ProjectLifecycle;
import org.apache.hop.projects.project.Project;
import org.apache.hop.projects.project.ProjectConfig;
import org.apache.hop.ui.hopgui.HopGui;

@JsonIgnoreProperties(value = {"openingLastProjectAtStartup"})
public class ProjectsConfig {

  public static final String HOP_CONFIG_PROJECTS_CONFIG_KEY = "projectsConfig";
  public static final String DEFAULT_PROJECT_CONFIG_FILENAME = "project-config.json";

  private boolean enabled;

  private boolean projectMandatory;
  private boolean environmentMandatory;
  private boolean environmentsForActiveProject;
  private boolean saveEnvironmentsInProjectConfig;
  private String defaultProject;
  private String defaultEnvironment;
  private String standardParentProject;
  private String standardProjectsFolder;
  private String defaultProjectConfigFile;

  private List<ProjectConfig> projectConfigurations;
  private List<LifecycleEnvironment> lifecycleEnvironments;
  private List<ProjectLifecycle> projectLifecycles;

  public ProjectsConfig() {
    enabled = true;
    defaultProjectConfigFile = DEFAULT_PROJECT_CONFIG_FILENAME;
    projectConfigurations = new ArrayList<>();
    lifecycleEnvironments = new ArrayList<>();
    projectLifecycles = new ArrayList<>();
  }

  public ProjectsConfig(ProjectsConfig config) {
    this();
    enabled = config.enabled;
    projectConfigurations = new ArrayList<>(config.projectConfigurations);
    lifecycleEnvironments = new ArrayList<>(config.lifecycleEnvironments);
    projectLifecycles = new ArrayList<>(config.projectLifecycles);
    projectMandatory = config.projectMandatory;
    environmentMandatory = config.environmentMandatory;
    defaultProject = config.defaultProject;
    defaultEnvironment = config.defaultEnvironment;
    standardParentProject = config.standardParentProject;
    standardProjectsFolder = config.standardProjectsFolder;
    defaultProjectConfigFile = config.defaultProjectConfigFile;
    environmentsForActiveProject = config.environmentsForActiveProject;
    saveEnvironmentsInProjectConfig = config.saveEnvironmentsInProjectConfig;
  }

  public ProjectConfig findProjectConfig(String projectName) {
    if (StringUtils.isEmpty(projectName)) {
      return null;
    }
    for (ProjectConfig projectConfig : projectConfigurations) {
      if (projectConfig.getProjectName().equalsIgnoreCase(projectName)) {
        return projectConfig;
      }
    }
    return null;
  }

  /**
   * Find the environments for a given project
   *
   * @param projectName The name of the environment to look up
   * @return The environments for the project
   */
  public List<LifecycleEnvironment> findEnvironmentsOfProject(String projectName) {
    List<LifecycleEnvironment> list = new ArrayList<>();
    lifecycleEnvironments.stream()
        .forEach(
            e -> {
              if (e.getProjectName().equals(projectName)) {
                list.add(e);
              }
            });
    return list;
  }

  public void addProjectConfig(ProjectConfig projectConfig) {
    ProjectConfig existing = findProjectConfig(projectConfig.getProjectName());
    if (existing == null) {
      projectConfigurations.add(projectConfig);
    } else {
      existing.setProjectName(projectConfig.getProjectName());
      existing.setProjectHome(projectConfig.getProjectHome());
      existing.setConfigFilename(projectConfig.getConfigFilename());
    }
  }

  public int indexOfProjectConfig(String projectName) {
    return projectConfigurations.indexOf(
        new ProjectConfig(projectName, null, null)); // Only considers the name
  }

  public ProjectConfig removeProjectConfig(String projectName) {
    int index = indexOfProjectConfig(projectName);
    if (index >= 0) {
      return projectConfigurations.remove(index);
    } else {
      return null;
    }
  }

  public List<String> listProjectConfigNames() {
    List<String> names = new ArrayList<>();
    projectConfigurations.stream().forEach(config -> names.add(config.getProjectName()));
    Collections.sort(names);
    return names;
  }

  public LifecycleEnvironment findEnvironment(String environmentName) {
    if (StringUtils.isEmpty(environmentName)) {
      return null;
    }
    for (LifecycleEnvironment environment : lifecycleEnvironments) {
      if (environment.getName().equals(environmentName)) {
        return environment;
      }
    }
    return null;
  }

  public void addEnvironment(LifecycleEnvironment environment) throws HopException {
    int index = lifecycleEnvironments.indexOf(environment);
    if (index < 0) {
      lifecycleEnvironments.add(environment);
    } else {
      lifecycleEnvironments.set(index, environment);
    }

    ProjectConfig projectConfig = findProjectConfig(environment.getProjectName());
    if (projectConfig == null) {
      throw new HopException("Project '" + environment.getProjectName() + "' not found");
    }
    HopGui hopGui = HopGui.getInstance();
    IVariables variables = hopGui.getVariables();
    projectConfig.addEnvironment(new LifecycleEnvironment(environment), variables);
  }

  public LifecycleEnvironment removeEnvironment(String environmentName) throws HopException {
    LifecycleEnvironment environment = findEnvironment(environmentName);
    if (environment != null) {
      lifecycleEnvironments.remove(environment);

      ProjectConfig projectConfig = findProjectConfig(environment.getProjectName());
      if (projectConfig == null) {
        throw new HopException("Project '" + environment.getProjectName() + "' not found");
      }
      HopGui hopGui = HopGui.getInstance();
      IVariables variables = hopGui.getVariables();
      projectConfig.removeEnvironment(environment, variables);
    }
    return environment;
  }

  public List<String> listEnvironmentNames() {
    List<String> names = new ArrayList<>();
    lifecycleEnvironments.stream().forEach(env -> names.add(env.getName()));
    Collections.sort(names);
    return names;
  }

  public List<String> listEnvironmentNamesForProject(String projectName) {
    List<String> names = new ArrayList<>();
    lifecycleEnvironments.stream()
        .forEach(
            env -> {
              if (env.getProjectName().equals(projectName)) {
                names.add(env.getName());
              }
            });

    Collections.sort(names);
    return names;
  }

  public int indexOfEnvironment(String environmentName) {
    return lifecycleEnvironments.indexOf(
        new LifecycleEnvironment(
            environmentName, null, null, Collections.emptyList())); // Only considers the name
  }

  public ProjectLifecycle findLifecycle(String lifecycleName) {
    if (StringUtils.isEmpty(lifecycleName)) {
      return null;
    }
    for (ProjectLifecycle lifecycle : projectLifecycles) {
      if (lifecycle.equals(lifecycleName)) {
        return lifecycle;
      }
    }
    return null;
  }

  public void addLifecycle(ProjectLifecycle lifecycle) {
    int index = projectLifecycles.indexOf(lifecycle);
    if (index < 0) {
      projectLifecycles.add(lifecycle);
    } else {
      projectLifecycles.set(index, lifecycle);
    }
  }

  public ProjectLifecycle removeLifecycle(String lifecycleName) {
    ProjectLifecycle lifecycle = findLifecycle(lifecycleName);
    if (lifecycle != null) {
      lifecycleEnvironments.remove(lifecycle);
    }
    return lifecycle;
  }

  public List<String> listLifecycleNames() {
    List<String> names = new ArrayList<>();
    projectLifecycles.stream().forEach(lifecycle -> names.add(lifecycle.getName()));
    Collections.sort(names);
    return names;
  }

  public int indexOfLifecycle(String lifecycleName) {
    return projectLifecycles.indexOf(
        new ProjectLifecycle(
            lifecycleName,
            Collections.emptyList(),
            Collections.emptyList())); // Only considers the name
  }

  public void syncLifecycleEnvironmentsWithProjectConfigs() {
    if (!saveEnvironmentsInProjectConfig) {
      return;
    }

    HopGui hopGui = HopGui.getInstance();
    IVariables variables = hopGui.getVariables();

    for (ProjectConfig projectConfig : projectConfigurations) {
      syncProjectConfigWithEnvironments(projectConfig, variables);
    }
  }

  private void syncProjectConfigWithEnvironments(
      ProjectConfig projectConfig, IVariables variables) {
    try {
      Project project = projectConfig.loadProject(variables);
      if (project == null) {
        return;
      }

      syncEnvironmentsFromProject(project);
      syncEnvironmentsToProject(projectConfig, project);

    } catch (HopException e) {
      // Ignore the exception
    }
  }

  private void syncEnvironmentsFromProject(Project project) {
    for (LifecycleEnvironment environment : project.getLifecycleEnvironments()) {
      int index = lifecycleEnvironments.indexOf(environment);
      if (index < 0) {
        lifecycleEnvironments.add(new LifecycleEnvironment(environment));
      } else {
        lifecycleEnvironments.set(index, new LifecycleEnvironment(environment));
      }
    }
  }

  private void syncEnvironmentsToProject(ProjectConfig projectConfig, Project project) {
    for (LifecycleEnvironment environment : lifecycleEnvironments) {
      if (projectConfig.getProjectName().equalsIgnoreCase(environment.getProjectName())) {
        project.addEnvironment(new LifecycleEnvironment(environment));
      }
    }
  }

  /**
   * Gets enabled
   *
   * @return value of enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * @param enabled The enabled to set
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets projectConfigurations
   *
   * @return value of projectConfigurations
   */
  public List<ProjectConfig> getProjectConfigurations() {
    return projectConfigurations;
  }

  /**
   * @param projectConfigurations The projectConfigurations to set
   */
  public void setProjectConfigurations(List<ProjectConfig> projectConfigurations) {
    this.projectConfigurations = projectConfigurations;
  }

  /**
   * Gets lifecycleEnvironments
   *
   * @return value of lifecycleEnvironments
   */
  public List<LifecycleEnvironment> getLifecycleEnvironments() {
    return lifecycleEnvironments;
  }

  /**
   * @param lifecycleEnvironments The lifecycleEnvironments to set
   */
  public void setLifecycleEnvironments(List<LifecycleEnvironment> lifecycleEnvironments) {
    this.lifecycleEnvironments = lifecycleEnvironments;
  }

  /**
   * Gets defaultProject
   *
   * @return value of defaultProject
   */
  public String getDefaultProject() {
    return defaultProject;
  }

  /**
   * @param defaultProject The defaultProject to set
   */
  public void setDefaultProject(String defaultProject) {
    this.defaultProject = defaultProject;
  }

  /**
   * Gets projectMandatory
   *
   * @return value of projectMandatory
   */
  public boolean isProjectMandatory() {
    return projectMandatory;
  }

  /**
   * @param projectMandatory The projectMandatory to set
   */
  public void setProjectMandatory(boolean projectMandatory) {
    this.projectMandatory = projectMandatory;
  }

  /**
   * Gets environmentMandatory
   *
   * @return value of environmentMandatory
   */
  public boolean isEnvironmentMandatory() {
    return environmentMandatory;
  }

  /**
   * @param environmentMandatory The environmentMandatory to set
   */
  public void setEnvironmentMandatory(boolean environmentMandatory) {
    this.environmentMandatory = environmentMandatory;
  }

  /**
   * Gets defaultEnvironment
   *
   * @return value of defaultEnvironment
   */
  public String getDefaultEnvironment() {
    return defaultEnvironment;
  }

  /**
   * @param defaultEnvironment The defaultEnvironment to set
   */
  public void setDefaultEnvironment(String defaultEnvironment) {
    this.defaultEnvironment = defaultEnvironment;
  }

  /**
   * Gets standardParentProject
   *
   * @return value of standardParentProject
   */
  public String getStandardParentProject() {
    return standardParentProject;
  }

  /**
   * @param standardParentProject The standardParentProject to set
   */
  public void setStandardParentProject(String standardParentProject) {
    this.standardParentProject = standardParentProject;
  }

  /**
   * Gets projectLifecycles
   *
   * @return value of projectLifecycles
   */
  public List<ProjectLifecycle> getProjectLifecycles() {
    return projectLifecycles;
  }

  /**
   * @param projectLifecycles The projectLifecycles to set
   */
  public void setProjectLifecycles(List<ProjectLifecycle> projectLifecycles) {
    this.projectLifecycles = projectLifecycles;
  }

  /**
   * Gets standardProjectsFolder
   *
   * @return value of standardProjectsFolder
   */
  public String getStandardProjectsFolder() {
    return standardProjectsFolder;
  }

  /**
   * @param standardProjectsFolder The standardProjectsFolder to set
   */
  public void setStandardProjectsFolder(String standardProjectsFolder) {
    this.standardProjectsFolder = standardProjectsFolder;
  }

  /**
   * Gets defaultProjectConfigFile
   *
   * @return value of defaultProjectConfigFile
   */
  public String getDefaultProjectConfigFile() {
    return defaultProjectConfigFile;
  }

  /**
   * @param defaultProjectConfigFile The defaultProjectConfigFile to set
   */
  public void setDefaultProjectConfigFile(String defaultProjectConfigFile) {
    this.defaultProjectConfigFile = defaultProjectConfigFile;
  }

  /**
   * Gets environmentsForActiveProject
   *
   * @return value of environmentsForActiveProject
   */
  public boolean isEnvironmentsForActiveProject() {
    return environmentsForActiveProject;
  }

  /**
   * @param environmentsForActiveProject The environmentMandatory to set
   */
  public void setEnvironmentsForActiveProject(boolean environmentsForActiveProject) {
    this.environmentsForActiveProject = environmentsForActiveProject;
  }

  public boolean isSaveEnvironmentsInProjectConfig() {
    return saveEnvironmentsInProjectConfig;
  }

  public void setSaveEnvironmentsInProjectConfig(boolean saveEnvironmentsInProjectConfig) {
    this.saveEnvironmentsInProjectConfig = saveEnvironmentsInProjectConfig;
  }
}
