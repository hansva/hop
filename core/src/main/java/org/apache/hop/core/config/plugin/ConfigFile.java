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

package org.apache.hop.core.config.plugin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.config.ConfigFileSerializer;
import org.apache.hop.core.config.ConfigNoFileSerializer;
import org.apache.hop.core.config.IConfigFile;
import org.apache.hop.core.config.IHopConfigSerializer;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.DescribedVariable;

public abstract class ConfigFile implements IConfigFile {

  public static final String HOP_VARIABLES_KEY = "variables";
  public static final String HOP_CONFIG_KEY = "config";

  @JsonProperty("config")
  protected Map<String, Object> configMap;

  @JsonIgnore protected IHopConfigSerializer serializer;

  // Cached parsed variables for performance - avoids repeated JSON parsing
  @JsonIgnore private List<DescribedVariable> cachedVariables;
  @JsonIgnore private Map<String, DescribedVariable> variablesByName;

  public ConfigFile() {
    configMap = new HashMap<>();
    serializer = new ConfigNoFileSerializer();
  }

  public void readFromFile() throws HopException {
    try {
      if (new File(getConfigFilename()).exists()) {
        // Let's write to the file
        //
        this.serializer = new ConfigFileSerializer();
      } else {
        boolean createWhenMissing =
            "Y".equalsIgnoreCase(System.getProperty(Const.HOP_AUTO_CREATE_CONFIG, "N"));
        if (createWhenMissing) {
          System.out.println("Creating new default Hop configuration file: " + getConfigFilename());
          this.serializer = new ConfigFileSerializer();
        } else {
          // Doesn't serialize anything really, reads an empty map with an empty file
          //
          System.out.println(
              "Hop configuration file not found, not serializing: " + getConfigFilename());
          this.serializer = new ConfigNoFileSerializer();
        }
      }
      configMap = serializer.readFromFile(getConfigFilename());
    } catch (Exception e) {
      throw new HopException("Unable to read config file '" + getConfigFilename() + "'", e);
    }
  }

  public void saveToFile() throws HopException {
    try {
      serializer.writeToFile(getConfigFilename(), configMap);
    } catch (Exception e) {
      throw new HopException("Error saving configuration file '" + getConfigFilename() + "'", e);
    }
  }

  public ConfigFile(String filename, List<DescribedVariable> describedVariables) {
    this();
    setConfigFilename(filename);
    configMap.put(HOP_VARIABLES_KEY, describedVariables);
  }

  @JsonIgnore
  @Override
  public List<DescribedVariable> getDescribedVariables() {
    // Return cached variables if already parsed
    if (cachedVariables != null) {
      return cachedVariables;
    }

    List<DescribedVariable> variables = new ArrayList<>();
    variablesByName = new HashMap<>();

    Map<String, Object> configObj = (Map<String, Object>) configMap.get(HOP_CONFIG_KEY);
    if (configObj != null) {
      configMap = configObj;
    }

    Object variablesObject = configMap.get(HOP_VARIABLES_KEY);
    if (variablesObject != null) {
      try {
        // Reuse single Gson and ObjectMapper instance for all variables
        Gson gson = new Gson();
        com.fasterxml.jackson.databind.ObjectMapper mapper = HopJson.newMapper();
        for (Object dvObject : (List) variablesObject) {
          String dvJson = gson.toJson(dvObject);
          DescribedVariable describedVariable = mapper.readValue(dvJson, DescribedVariable.class);
          variables.add(describedVariable);
          // Build index for O(1) lookup
          if (describedVariable.getName() != null) {
            variablesByName.put(describedVariable.getName(), describedVariable);
          }
        }
      } catch (Exception e) {
        LogChannel.GENERAL.logError(
            "Error parsing described variables from configuration file '"
                + getConfigFilename()
                + "'",
            e);
        variables = new ArrayList<>();
        variablesByName = new HashMap<>();
      }
    }

    configMap.put(HOP_VARIABLES_KEY, variables);
    cachedVariables = variables;

    return variables;
  }

  @Override
  public DescribedVariable findDescribedVariable(String name) {
    // Ensure variables are loaded and indexed
    getDescribedVariables();
    // O(1) lookup using the index
    return variablesByName != null ? variablesByName.get(name) : null;
  }

  @Override
  public void setDescribedVariable(DescribedVariable variable) {
    // Ensure variables are loaded and indexed
    getDescribedVariables();

    // Use the index for O(1) lookup
    DescribedVariable existing =
        variablesByName != null ? variablesByName.get(variable.getName()) : null;

    if (existing != null) {
      // Variable found? Update the value and description
      existing.setValue(variable.getValue());
      existing.setDescription(variable.getDescription());
    } else {
      // Variable not found? Add it and update index
      cachedVariables.add(variable);
      if (variablesByName != null && variable.getName() != null) {
        variablesByName.put(variable.getName(), variable);
      }
    }
  }

  @Override
  public String findDescribedVariableValue(String name) {
    DescribedVariable describedVariable = findDescribedVariable(name);
    if (describedVariable == null) {
      return null;
    }
    return describedVariable.getValue();
  }

  @Override
  public void setDescribedVariables(List<DescribedVariable> describedVariables) {
    configMap.put(HOP_VARIABLES_KEY, describedVariables);
    // Update cache and index
    cachedVariables = describedVariables;
    variablesByName = new HashMap<>();
    if (describedVariables != null) {
      for (DescribedVariable v : describedVariables) {
        if (v.getName() != null) {
          variablesByName.put(v.getName(), v);
        }
      }
    }
  }

  /**
   * Gets filename
   *
   * @return value of filename
   */
  @Override
  public abstract String getConfigFilename();

  /**
   * @param filename The filename to set
   */
  @Override
  public abstract void setConfigFilename(String filename);

  /**
   * Gets configMap
   *
   * @return value of configMap
   */
  public Map<String, Object> getConfigMap() {
    return configMap;
  }

  /**
   * @param configMap The configMap to set
   */
  public void setConfigMap(Map<String, Object> configMap) {
    this.configMap = configMap;
  }

  /**
   * Gets serializer
   *
   * @return value of serializer
   */
  public IHopConfigSerializer getSerializer() {
    return serializer;
  }

  /**
   * @param serializer The serializer to set
   */
  public void setSerializer(IHopConfigSerializer serializer) {
    this.serializer = serializer;
  }
}
