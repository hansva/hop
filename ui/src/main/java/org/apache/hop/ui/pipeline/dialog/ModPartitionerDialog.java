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

package org.apache.hop.ui.pipeline.dialog;

import java.util.Arrays;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PartitionerPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.ModPartitioner;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformPartitioningMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.HelpUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class ModPartitionerDialog extends BaseTransformDialog {
  private static final Class<?> PKG = PipelineDialog.class;

  private TransformPartitioningMeta partitioningMeta;
  private ModPartitioner partitioner;
  private String fieldName;

  private CCombo wFieldname;

  public ModPartitionerDialog(
      Shell parent,
      IVariables variables,
      TransformMeta transformMeta,
      TransformPartitioningMeta partitioningMeta,
      PipelineMeta pipelineMeta) {
    super(
        parent,
        variables,
        (BaseTransformMeta) transformMeta.getTransform(),
        pipelineMeta,
        partitioningMeta.getPartitioner().getDescription());
    this.transformMeta = transformMeta;
    this.partitioningMeta = partitioningMeta;
    partitioner = (ModPartitioner) partitioningMeta.getPartitioner();
    fieldName = partitioner.getFieldName();
  }

  @Override
  public String open() {
    Shell parent = getParent();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell);

    ModifyListener lsMod = e -> partitioningMeta.hasChanged(true);
    changed = partitioningMeta.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();

    shell.setLayout(formLayout);
    shell.setText(partitioner.getDescription());

    int margin = PropsUi.getMargin();

    int middle = props.getMiddlePct();

    Label wlFieldname = new Label(shell, SWT.RIGHT);
    wlFieldname.setText("Fieldname");
    PropsUi.setLook(wlFieldname);
    FormData fdlFieldname = new FormData();
    fdlFieldname.left = new FormAttachment(0, 0);
    fdlFieldname.right = new FormAttachment(middle, -margin);
    fdlFieldname.top = new FormAttachment(0, margin);
    wlFieldname.setLayoutData(fdlFieldname);
    wFieldname = new CCombo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wFieldname.setText(fieldName == null ? "" : fieldName);
    PropsUi.setLook(wFieldname);
    wFieldname.addModifyListener(lsMod);
    FormData fdFieldname = new FormData();
    fdFieldname.left = new FormAttachment(middle, 0);
    fdFieldname.top = new FormAttachment(0, margin);
    fdFieldname.right = new FormAttachment(100, 0);
    wFieldname.setLayoutData(fdFieldname);
    try {
      IRowMeta inputFields = pipelineMeta.getPrevTransformFields(variables, transformMeta);
      if (inputFields != null) {
        String[] fieldNames = inputFields.getFieldNames();
        Arrays.sort(fieldNames);
        wFieldname.setItems(fieldNames);
      }
    } catch (Exception e) {
      new ErrorDialog(shell, "Error", "Error obtaining list of input fields:", e);
    }

    // Some buttons
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    fdOk = new FormData();

    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    // Add listeners
    wCancel.addListener(SWT.Selection, e -> cancel());
    wOk.addListener(SWT.Selection, e -> ok());

    getData();

    partitioningMeta.hasChanged(changed);

    wOk.setEnabled(!StringUtil.isEmpty(wFieldname.getText()));
    ModifyListener modifyListener =
        modifyEvent -> wOk.setEnabled(!StringUtil.isEmpty(wFieldname.getText()));
    wFieldname.addModifyListener(modifyListener);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData() {
    wFieldname.setText(fieldName == null ? "" : fieldName);
  }

  private void cancel() {
    transformName = null;
    partitioningMeta.hasChanged(changed);
    dispose();
  }

  private void ok() {
    fieldName = wFieldname.getText();
    partitioner.setFieldName(fieldName);
    dispose();
  }

  private void setShellImage(Shell shell) {
    IPlugin plugin =
        PluginRegistry.getInstance().getPlugin(PartitionerPluginType.class, partitioner.getId());
    if (!Utils.isEmpty(plugin.getDocumentationUrl())) {
      HelpUtils.createHelpButton(shell, plugin);
    }

    shell.setImage(GuiResource.getInstance().getImageHopUi());
  }
}
