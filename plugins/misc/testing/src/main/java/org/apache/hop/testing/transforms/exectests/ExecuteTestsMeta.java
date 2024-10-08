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

package org.apache.hop.testing.transforms.exectests;

import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.testing.TestType;
import org.apache.hop.testing.UnitTestResult;

@Transform(
    id = "ExecuteTests",
    description = "Execute Unit Tests",
    name = "Execute Unit Tests",
    image = "executetests.svg",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Flow",
    keywords = "i18n::ExecuteTestsMeta.keyword",
    documentationUrl = "/pipeline/transforms/exectests.html")
public class ExecuteTestsMeta extends BaseTransformMeta<ExecuteTests, ExecuteTestsData> {

  public static final String TAG_TEST_NAME_INPUT_FIELD = "test_name_input_field";
  public static final String TAG_TYPE_TO_EXECUTE = "type_to_execute";
  public static final String TAG_PIPELINE_NAME_FIELD = "pipeline_name_field";
  public static final String TAG_UNIT_TEST_NAME_FIELD = "unit_test_name_field";
  public static final String TAG_DATASET_NAME_FIELD = "data_set_name_field";
  public static final String TAG_TRANSFORM_NAME_FIELD = "transform_name_field";
  public static final String TAG_ERROR_FIELD = "error_field";
  public static final String TAG_COMMENT_FIELD = "comment_field";

  @HopMetadataProperty(key = TAG_TEST_NAME_INPUT_FIELD)
  private String testNameInputField;

  @HopMetadataProperty(key = TAG_TYPE_TO_EXECUTE)
  private TestType typeToExecute;

  @HopMetadataProperty(key = TAG_PIPELINE_NAME_FIELD)
  private String pipelineNameField;

  @HopMetadataProperty(key = TAG_UNIT_TEST_NAME_FIELD)
  private String unitTestNameField;

  @HopMetadataProperty(key = TAG_DATASET_NAME_FIELD)
  private String dataSetNameField;

  @HopMetadataProperty(key = TAG_TRANSFORM_NAME_FIELD)
  private String transformNameField;

  @HopMetadataProperty(key = TAG_ERROR_FIELD)
  private String errorField;

  @HopMetadataProperty(key = TAG_COMMENT_FIELD)
  private String commentField;

  public ExecuteTestsMeta() {
    super();
  }

  @Override
  public void getFields(
      IRowMeta inputRowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    IRowMeta rowMeta = UnitTestResult.getRowMeta();
    int index = 0;
    rowMeta.getValueMeta(index++).setName(variables.resolve(pipelineNameField));
    rowMeta.getValueMeta(index++).setName(variables.resolve(unitTestNameField));
    rowMeta.getValueMeta(index++).setName(variables.resolve(dataSetNameField));
    rowMeta.getValueMeta(index++).setName(variables.resolve(transformNameField));
    rowMeta.getValueMeta(index++).setName(variables.resolve(errorField));
    rowMeta.getValueMeta(index++).setName(variables.resolve(commentField));

    inputRowMeta.clear();
    inputRowMeta.addRowMeta(rowMeta);
  }

  @Override
  public void setDefault() {
    testNameInputField = null;
    pipelineNameField = "pipeline";
    unitTestNameField = "unittest";
    dataSetNameField = "dataset";
    transformNameField = "transform";
    errorField = "error";
    commentField = "comment";
  }

  /**
   * Gets testNameInputField
   *
   * @return value of testNameInputField
   */
  public String getTestNameInputField() {
    return testNameInputField;
  }

  /**
   * @param testNameInputField The testNameInputField to set
   */
  public void setTestNameInputField(String testNameInputField) {
    this.testNameInputField = testNameInputField;
  }

  /**
   * Gets typeToExecute
   *
   * @return value of typeToExecute
   */
  public TestType getTypeToExecute() {
    return typeToExecute;
  }

  /**
   * @param typeToExecute The typeToExecute to set
   */
  public void setTypeToExecute(TestType typeToExecute) {
    this.typeToExecute = typeToExecute;
  }

  /**
   * Gets pipelineNameField
   *
   * @return value of pipelineNameField
   */
  public String getPipelineNameField() {
    return pipelineNameField;
  }

  /**
   * @param pipelineNameField The pipelineNameField to set
   */
  public void setPipelineNameField(String pipelineNameField) {
    this.pipelineNameField = pipelineNameField;
  }

  /**
   * Gets unitTestNameField
   *
   * @return value of unitTestNameField
   */
  public String getUnitTestNameField() {
    return unitTestNameField;
  }

  /**
   * @param unitTestNameField The unitTestNameField to set
   */
  public void setUnitTestNameField(String unitTestNameField) {
    this.unitTestNameField = unitTestNameField;
  }

  /**
   * Gets dataSetNameField
   *
   * @return value of dataSetNameField
   */
  public String getDataSetNameField() {
    return dataSetNameField;
  }

  /**
   * @param dataSetNameField The dataSetNameField to set
   */
  public void setDataSetNameField(String dataSetNameField) {
    this.dataSetNameField = dataSetNameField;
  }

  /**
   * Gets transform name field
   *
   * @return value of transform name field
   */
  public String getTransformNameField() {
    return transformNameField;
  }

  /**
   * @param transformNameField The transform name field to set
   */
  public void setTransformNameField(String transformNameField) {
    this.transformNameField = transformNameField;
  }

  /**
   * Gets errorField
   *
   * @return value of errorField
   */
  public String getErrorField() {
    return errorField;
  }

  /**
   * @param errorField The errorField to set
   */
  public void setErrorField(String errorField) {
    this.errorField = errorField;
  }

  /**
   * Gets commentField
   *
   * @return value of commentField
   */
  public String getCommentField() {
    return commentField;
  }

  /**
   * @param commentField The commentField to set
   */
  public void setCommentField(String commentField) {
    this.commentField = commentField;
  }
}
