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

package org.apache.hop.pipeline.transforms.calculator;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Contains the meta-data for the Calculator transform: calculates predefined formula's */
@Transform(
    id = "Calculator",
    image = "calculator.svg",
    name = "i18n::BaseTransform.TypeLongDesc.Calculator",
    description = "i18n::BaseTransform.TypeTooltipDesc.Calculator",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Transform",
    keywords = "i18n::CalculatorMeta.keyword",
    documentationUrl = "/pipeline/transforms/calculator.html")
@Getter
@Setter
public class CalculatorMeta extends BaseTransformMeta<Calculator, CalculatorData> {

  private static final Class<?> PKG = CalculatorMeta.class;

  /** The calculations to be performed */
  @HopMetadataProperty(
      key = "calculation",
      injectionGroupKey = "Calculations",
      injectionGroupDescription = "CalculatorMeta.Injection.Calculations")
  private List<CalculatorMetaFunction> functions;

  /** Raise an error if file does not exist */
  @HopMetadataProperty(injectionKeyDescription = "CalculatorMeta.Injection.FailIfNoFile")
  private boolean failIfNoFile;

  public CalculatorMeta() {
    this.failIfNoFile = true;
    this.functions = new ArrayList<>();
  }

  @Override
  public CalculatorMeta clone() {
    CalculatorMeta meta = new CalculatorMeta();
    meta.setFailIfNoFile(isFailIfNoFile());

    for (CalculatorMetaFunction function : functions) {
      meta.getFunctions().add(new CalculatorMetaFunction(function));
    }

    return meta;
  }

  @Override
  public void getFields(
      IRowMeta row,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    for (CalculatorMetaFunction calculation : functions) {
      if (!calculation.isRemovedFromResult() && !Utils.isEmpty(calculation.getFieldName())) {
        IValueMeta v = getValueMeta(calculation, origin);
        row.addValueMeta(v);
      }
    }
  }

  private IValueMeta getValueMeta(CalculatorMetaFunction fn, String origin) {
    IValueMeta v;
    // What if the user didn't specify a data type?
    // In that case we look for the default data type
    //
    int defaultResultType = ValueMetaFactory.getIdForValueMeta(fn.getValueType());
    if (defaultResultType == IValueMeta.TYPE_NONE) {
      defaultResultType = fn.getCalcType().getDefaultResultType();
    }
    try {
      v = ValueMetaFactory.createValueMeta(fn.getFieldName(), defaultResultType);
    } catch (Exception ex) {
      return null;
    }
    v.setLength(fn.getValueLength());
    v.setPrecision(fn.getValuePrecision());
    v.setOrigin(origin);
    v.setComments(fn.getCalcType().getDescription());
    v.setConversionMask(fn.getConversionMask());
    v.setDecimalSymbol(fn.getDecimalSymbol());
    v.setGroupingSymbol(fn.getGroupingSymbol());
    v.setCurrencySymbol(fn.getCurrencySymbol());

    return v;
  }

  public IRowMeta getAllFields(IRowMeta inputRowMeta) {
    IRowMeta rowMeta = inputRowMeta.clone();

    for (CalculatorMetaFunction calculation : getFunctions()) {
      if (!Utils.isEmpty(calculation.getFieldName())) { // It's a new field!
        IValueMeta v = getValueMeta(calculation, null);
        rowMeta.addValueMeta(v);
      }
    }
    return rowMeta;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    CheckResult cr;

    // See if we have input streams leading to this transform!
    if (input.length > 0) {
      cr =
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "CalculatorMeta.CheckResult.ExpectedInputOk"),
              transformMeta);
      remarks.add(cr);

      if (prev == null || prev.size() == 0) {
        cr =
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(PKG, "CalculatorMeta.CheckResult.ExpectedInputError"),
                transformMeta);
        remarks.add(cr);
      } else {
        cr =
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(
                    PKG, "CalculatorMeta.CheckResult.FieldsReceived", "" + prev.size()),
                transformMeta);
        remarks.add(cr);
      }
    } else {
      cr =
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "CalculatorMeta.CheckResult.ExpectedInputError"),
              transformMeta);
      remarks.add(cr);
    }
  }
}
