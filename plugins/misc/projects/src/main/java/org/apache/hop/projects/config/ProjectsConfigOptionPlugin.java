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

package org.apache.hop.projects.config;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.config.plugin.ConfigPlugin;
import org.apache.hop.core.config.plugin.IConfigOptions;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.projects.util.ProjectsUtil;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import picocli.CommandLine;

@ConfigPlugin(
    id = "ProjectsConfigOptionPlugin",
    description = "Configuration options for the global projects plugin")
@GuiPlugin(
    description = "i18n::ProjectConfig.Tab.Name" // label in options dialog
    )
public class ProjectsConfigOptionPlugin
    implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  protected static Class<?> PKG = ProjectsConfigOptionPlugin.class;

  private static final String WIDGET_ID_ENABLE_PROJECTS = "10000-enable-projects-plugin";
  private static final String WIDGET_ID_PROJECT_MANDATORY = "10010-project-mandatory";
  private static final String WIDGET_ID_ENVIRONMENT_MANDATORY = "10020-environment-mandatory";
  private static final String WIDGET_ID_DEFAULT_PROJECT = "10030-default-project";
  private static final String WIDGET_ID_DEFAULT_ENVIRONMENT = "10040-default-environment";
  private static final String WIDGET_ID_STANDARD_PARENT_PROJECT = "10050-standard-parent-project";
  private static final String WIDGET_ID_STANDARD_PROJECTS_FOLDER = "10060-standard-projects-folder";
  private static final String WIDGET_ID_RESTRICT_ENVIRONMENTS_TO_ACTIVE_PROJECT =
      "10070-restrict-environments-to-active-project";
  private static final String WIDGET_ID_DEFAULT_PROJECT_CONFIG_FILENAME =
      "10070-default-project-config-filename";
  private static final String WIDGET_ID_SAVE_ENVIRONMENTS_IN_PROJECT_CONFIG =
      "10080-save-environments-in-project-config";

  @GuiWidgetElement(
      id = WIDGET_ID_ENABLE_PROJECTS,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::ProjectConfig.EnableProjectPlugin.Message")
  @CommandLine.Option(
      names = {"-pn", "--projects-enabled"},
      description = "Enable or disable the projects plugin")
  private Boolean projectsEnabled;

  @GuiWidgetElement(
      id = WIDGET_ID_PROJECT_MANDATORY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::ProjectConfig.ProjectMandatory.Message")
  @CommandLine.Option(
      names = {"-py", "--project-mandatory"},
      description = "Make it mandatory to reference a project")
  private Boolean projectMandatory;

  @GuiWidgetElement(
      id = WIDGET_ID_ENVIRONMENT_MANDATORY,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      label = "i18n::ProjectConfig.EnvironmentMandatory.Message")
  @CommandLine.Option(
      names = {"-ey", "--environment-mandatory"},
      description = "Make it mandatory to reference an environment")
  private Boolean environmentMandatory;

  @GuiWidgetElement(
      id = WIDGET_ID_DEFAULT_PROJECT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      comboValuesMethod = "getProjectsList",
      variables = true,
      label = "i18n::ProjectConfig.DefaultProject.Message")
  @CommandLine.Option(
      names = {"-dp", "--default-project"},
      description = "The default project to use when none is specified")
  private String defaultProject;

  @GuiWidgetElement(
      id = WIDGET_ID_DEFAULT_ENVIRONMENT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ProjectConfig.DefaultEnvironment.Message")
  @CommandLine.Option(
      names = {"-de", "--default-environment"},
      description = "The name of the default environment to use when none is specified")
  private String defaultEnvironment;

  @GuiWidgetElement(
      id = WIDGET_ID_STANDARD_PARENT_PROJECT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.COMBO,
      comboValuesMethod = "getProjectsList",
      variables = true,
      label = "i18n::ProjectConfig.ParentProject.Message")
  @CommandLine.Option(
      names = {"-sp", "--standard-parent-project"},
      description =
          "The name of the standard project to use as a parent when creating new projects")
  private String standardParentProject;

  @GuiWidgetElement(
      id = WIDGET_ID_STANDARD_PROJECTS_FOLDER,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ProjectConfig.StdProjectFolder.Message")
  @CommandLine.Option(
      names = {"-sj", "--standard-projects-folder"},
      description = "The standard projects folder for new projects")
  private String standardProjectsFolder;

  @GuiWidgetElement(
      id = WIDGET_ID_DEFAULT_PROJECT_CONFIG_FILENAME,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::ProjectConfig.StdProjectFilename.Message")
  @CommandLine.Option(
      names = {"-dc", "--default-projects-folder"},
      description = "The project configuration filename for new projects")
  private String defaultProjectConfigFile;

  @GuiWidgetElement(
      id = WIDGET_ID_RESTRICT_ENVIRONMENTS_TO_ACTIVE_PROJECT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      variables = false,
      label = "i18n::ProjectConfig.RestrictEnvsToActiveProject.Message")
  @CommandLine.Option(
      names = {"-eap", "--environments-for-active-project"},
      description = "Restrict environment list to active project")
  private Boolean environmentsForActiveProject;

  @GuiWidgetElement(
      id = WIDGET_ID_SAVE_ENVIRONMENTS_IN_PROJECT_CONFIG,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID,
      type = GuiElementType.CHECKBOX,
      variables = false,
      label = "i18n::ProjectConfig.SaveEnvsInProjectConfig.Message")
  @CommandLine.Option(
      names = {"-epc", "--environments-in-project-config"},
      description = "i18n::ProjectConfig.SaveEnvsInProjectConfig.Description")
  private Boolean saveEnvironmentsInProjectConfig;

  /**
   * Gets instance
   *
   * @return value of instance
   */
  public static ProjectsConfigOptionPlugin getInstance() {
    ProjectsConfigOptionPlugin instance = new ProjectsConfigOptionPlugin();

    ProjectsConfig config = ProjectsConfigSingleton.getConfig();
    config.syncLifecycleEnvironmentsWithProjectConfigs();
    instance.projectsEnabled = config.isEnabled();
    instance.defaultProject = config.getDefaultProject();
    instance.defaultEnvironment = config.getDefaultEnvironment();
    instance.projectMandatory = config.isProjectMandatory();
    instance.environmentMandatory = config.isEnvironmentMandatory();
    instance.standardParentProject = config.getStandardParentProject();
    instance.standardProjectsFolder = config.getStandardProjectsFolder();
    instance.defaultProjectConfigFile = config.getDefaultProjectConfigFile();
    instance.environmentsForActiveProject = config.isEnvironmentsForActiveProject();
    instance.saveEnvironmentsInProjectConfig = config.isSaveEnvironmentsInProjectConfig();
    return instance;
  }

  @Override
  public boolean handleOption(
      ILogChannel log, IHasHopMetadataProvider hasHopMetadataProvider, IVariables variables)
      throws HopException {
    ProjectsConfig config = ProjectsConfigSingleton.getConfig();
    try {
      boolean changed = false;
      if (projectsEnabled != null) {
        config.setEnabled(projectsEnabled);
        if (projectsEnabled) {
          log.logBasic("Enabled the projects system");
        } else {
          log.logBasic("Disabled the projects system");
        }
        changed = true;
      }
      if (projectMandatory != null) {
        config.setProjectMandatory(projectMandatory);
        if (projectMandatory) {
          log.logBasic("Using a project is set to be mandatory");
        } else {
          log.logBasic("Using a project is set to be optional");
        }
        changed = true;
      }
      if (environmentMandatory != null) {
        config.setEnvironmentMandatory(environmentMandatory);
        if (environmentMandatory) {
          log.logBasic("Using an environment is set to be mandatory");
        } else {
          log.logBasic("Using an environment is set to be optional");
        }
        changed = true;
      }
      if (defaultProject != null) {
        config.setDefaultProject(defaultProject);
        log.logBasic("The default project is set to '" + defaultProject + "'");
        changed = true;
      }
      if (defaultEnvironment != null) {
        config.setDefaultEnvironment(defaultEnvironment);
        log.logBasic("The default environment is set to '" + defaultEnvironment + "'");
        changed = true;
      }
      if (standardParentProject != null) {
        config.setStandardParentProject(standardParentProject);
        log.logBasic(
            "The standard project to inherit from when creating a project is set to '"
                + standardParentProject
                + "'");
        changed = true;
      }
      if (standardProjectsFolder != null) {
        config.setStandardProjectsFolder(standardProjectsFolder);
        log.logBasic(
            "The standard projects folder to browse to in the GUI is set to '"
                + standardProjectsFolder
                + "'");
        changed = true;
      }
      if (defaultProjectConfigFile != null) {
        config.setDefaultProjectConfigFile(defaultProjectConfigFile);
        log.logBasic(
            "The default project configuration filename is set to '"
                + defaultProjectConfigFile
                + "'");
        changed = true;
      }
      if (environmentsForActiveProject != null) {
        config.setEnvironmentsForActiveProject(environmentsForActiveProject);
        if (environmentsForActiveProject) {
          log.logBasic("Only listing environments for the active project");
        } else {
          log.logBasic("Listing all environments, regardless of the active project");
        }
        changed = true;
      }
      if (saveEnvironmentsInProjectConfig != null) {
        config.setSaveEnvironmentsInProjectConfig(saveEnvironmentsInProjectConfig);
        log.logBasic(
            "Saving environments in project-config.json is set to '"
                + saveEnvironmentsInProjectConfig
                + "'");
        changed = true;
      }
      // Save to file if anything changed
      //
      if (changed) {
        ProjectsConfigSingleton.saveConfig();
      }
      return changed;
    } catch (Exception e) {
      throw new HopException("Error handling projects plugin configuration options", e);
    }
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {
    // Do nothing
  }

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {
    // Do nothing
  }

  @Override
  public void widgetModified(
      GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
    persistContents(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
      Control control = compositeWidgets.getWidgetsMap().get(widgetId);
      switch (widgetId) {
        case WIDGET_ID_ENABLE_PROJECTS:
          projectsEnabled = ((Button) control).getSelection();
          ProjectsConfigSingleton.getConfig().setEnabled(projectsEnabled);
          break;
        case WIDGET_ID_PROJECT_MANDATORY:
          projectMandatory = ((Button) control).getSelection();
          ProjectsConfigSingleton.getConfig().setProjectMandatory(projectMandatory);
          break;
        case WIDGET_ID_ENVIRONMENT_MANDATORY:
          environmentMandatory = ((Button) control).getSelection();
          ProjectsConfigSingleton.getConfig().setEnvironmentMandatory(environmentMandatory);
          break;
        case WIDGET_ID_DEFAULT_PROJECT:
          String defProject = ((ComboVar) control).getText();
          if (!StringUtils.isEmpty(defProject)) {
            boolean defParentPrjExists = ProjectsUtil.projectExists(defProject);
            if (!defParentPrjExists) {
              MessageBox box =
                  new MessageBox(HopGui.getInstance().getShell(), SWT.OK | SWT.ICON_ERROR);
              box.setText(
                  BaseMessages.getString(PKG, "ProjectConfig.ProjectNotExists.Error.Header"));
              box.setMessage(
                  BaseMessages.getString(
                      PKG,
                      "ProjectConfig.ProjectNotExists.DefaultProject.Error.Message",
                      defProject));
              box.open();
            } else {
              defaultProject = defProject;
              ProjectsConfigSingleton.getConfig().setDefaultProject(defaultProject);
            }
          }
          break;
        case WIDGET_ID_DEFAULT_ENVIRONMENT:
          defaultEnvironment = ((TextVar) control).getText();
          ProjectsConfigSingleton.getConfig().setDefaultEnvironment(defaultEnvironment);
          break;
        case WIDGET_ID_STANDARD_PARENT_PROJECT:
          String stdParentProject = ((ComboVar) control).getText();
          if (!StringUtils.isEmpty(stdParentProject)) {
            boolean stdParentPrjExists = ProjectsUtil.projectExists(stdParentProject);
            if (!stdParentPrjExists) {
              MessageBox box =
                  new MessageBox(HopGui.getInstance().getShell(), SWT.OK | SWT.ICON_ERROR);
              box.setText(
                  BaseMessages.getString(PKG, "ProjectConfig.ProjectNotExists.Error.Header"));
              box.setMessage(
                  BaseMessages.getString(
                      PKG,
                      "ProjectConfig.ProjectNotExists.StandardProject.Error.Message",
                      stdParentProject));
              box.open();
            } else {
              standardParentProject = stdParentProject;
              ProjectsConfigSingleton.getConfig().setStandardParentProject(standardParentProject);
            }
          }
          break;
        case WIDGET_ID_STANDARD_PROJECTS_FOLDER:
          standardProjectsFolder = ((TextVar) control).getText();
          ProjectsConfigSingleton.getConfig().setStandardProjectsFolder(standardProjectsFolder);
          break;
        case WIDGET_ID_DEFAULT_PROJECT_CONFIG_FILENAME:
          defaultProjectConfigFile = ((TextVar) control).getText();
          ProjectsConfigSingleton.getConfig().setDefaultProjectConfigFile(defaultProjectConfigFile);
          break;
        case WIDGET_ID_RESTRICT_ENVIRONMENTS_TO_ACTIVE_PROJECT:
          environmentsForActiveProject = ((Button) control).getSelection();
          ProjectsConfigSingleton.getConfig()
              .setEnvironmentsForActiveProject(environmentsForActiveProject);
          break;
        case WIDGET_ID_SAVE_ENVIRONMENTS_IN_PROJECT_CONFIG:
          saveEnvironmentsInProjectConfig = ((Button) control).getSelection();
          ProjectsConfigSingleton.getConfig()
              .setSaveEnvironmentsInProjectConfig(saveEnvironmentsInProjectConfig);
          break;
      }
    }
    // Save the project...
    //
    try {
      ProjectsConfigSingleton.saveConfig();
    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(),
          BaseMessages.getString(PKG, "ProjectConfig.SavingOption.ErrorDialog.Header"),
          BaseMessages.getString(PKG, "ProjectConfig.SavingOption.ErrorDialog.Message"),
          e);
    }
  }

  /**
   * Gets projectsEnabled
   *
   * @return value of projectsEnabled
   */
  public Boolean getProjectsEnabled() {
    return projectsEnabled;
  }

  /**
   * @param projectsEnabled The projectsEnabled to set
   */
  public void setProjectsEnabled(Boolean projectsEnabled) {
    this.projectsEnabled = projectsEnabled;
  }

  /**
   * Gets projectMandatory
   *
   * @return value of projectMandatory
   */
  public Boolean getProjectMandatory() {
    return projectMandatory;
  }

  /**
   * @param projectMandatory The projectMandatory to set
   */
  public void setProjectMandatory(Boolean projectMandatory) {
    this.projectMandatory = projectMandatory;
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
   * Gets environmentMandatory
   *
   * @return value of environmentMandatory
   */
  public Boolean getEnvironmentMandatory() {
    return environmentMandatory;
  }

  /**
   * @param environmentMandatory The environmentMandatory to set
   */
  public void setEnvironmentMandatory(Boolean environmentMandatory) {
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
  public Boolean getEnvironmentsForActiveProject() {
    return environmentsForActiveProject;
  }

  /**
   * @param environmentsForActiveProject The environmentsForActiveProject flag to set
   */
  public void setEnvironmentsForActiveProject(Boolean environmentsForActiveProject) {
    this.environmentsForActiveProject = environmentsForActiveProject;
  }

  /**
   * Gets saveEnvironmentsInProjectConfig
   *
   * @return value of saveEnvironmentsInProjectConfig
   */
  public Boolean getSaveEnvironmentsInProjectConfig() {
    return saveEnvironmentsInProjectConfig;
  }

  /**
   * @param saveEnvironmentsInProjectConfig The saveEnvironmentsInProjectConfig flag to set
   */
  public void setSaveEnvironmentsInProjectConfig(Boolean saveEnvironmentsInProjectConfig) {
    this.saveEnvironmentsInProjectConfig = saveEnvironmentsInProjectConfig;
  }

  /**
   * Used to generate the list that is shown in the mySqlDriverClass GuiWidget
   *
   * @param log Logging object
   * @param metadataProvider If shared metadata is needed to get the values
   * @return The list of driver type names shown in the GUI
   */
  public List<String> getProjectsList(ILogChannel log, IHopMetadataProvider metadataProvider) {
    ProjectsConfig prjsConfig = ProjectsConfigSingleton.getConfig();
    List<String> prjs = prjsConfig.listProjectConfigNames();

    List<String> prjsList = new ArrayList<>();

    // Add empty entry for no selection
    prjsList.add("");

    for (String prj : prjs) {
      prjsList.add(prj);
    }

    return prjsList;
  }
}
