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

package org.apache.hop.pipeline.transforms.systemdata;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelinePreviewFactory;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterNumberDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.PreviewRowsDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class SystemDataDialog extends BaseTransformDialog {
  private static final Class<?> PKG = SystemDataMeta.class;

  private Text wTransformName;

  private TableView wFields;

  private final SystemDataMeta input;

  public SystemDataDialog(
      Shell parent, IVariables variables, SystemDataMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    Shell parent = getParent();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    setShellImage(shell, input);

    ModifyListener lsMod = e -> input.setChanged();
    changed = input.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();

    shell.setLayout(formLayout);
    shell.setText(BaseMessages.getString(PKG, "SystemDataDialog.DialogTitle"));

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    // See if the transform receives input.
    //
    boolean isReceivingInput = !pipelineMeta.findPreviousTransforms(transformMeta).isEmpty();

    // Some buttons
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "SystemDataDialog.Button.PreviewRows"));
    wPreview.setEnabled(!isReceivingInput);
    wPreview.addListener(SWT.Selection, e -> preview());
    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    setButtonPositions(new Button[] {wOk, wPreview, wCancel}, margin, null);

    // TransformName line
    Label wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText(BaseMessages.getString(PKG, "System.TransformName.Label"));
    wlTransformName.setToolTipText(BaseMessages.getString(PKG, "System.TransformName.Tooltip"));
    PropsUi.setLook(wlTransformName);
    FormData fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(middle, -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);
    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    wTransformName.addModifyListener(lsMod);
    FormData fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(middle, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    fdTransformName.right = new FormAttachment(100, 0);
    wTransformName.setLayoutData(fdTransformName);

    Label wlFields = new Label(shell, SWT.NONE);
    wlFields.setText(BaseMessages.getString(PKG, "SystemDataDialog.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, 0);
    fdlFields.top = new FormAttachment(wTransformName, margin);
    wlFields.setLayoutData(fdlFields);

    final int FieldsCols = 2;
    final int FieldsRows = input.getFieldName().length;

    final String[] functionDesc = new String[SystemDataTypes.values().length - 1];
    for (int i = 1; i < SystemDataTypes.values().length; i++) {
      functionDesc[i - 1] = SystemDataTypes.values()[i].getDescription();
    }

    ColumnInfo[] colinf = new ColumnInfo[FieldsCols];
    colinf[0] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "SystemDataDialog.NameColumn.Column"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    colinf[1] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "SystemDataDialog.TypeColumn.Column"),
            ColumnInfo.COLUMN_TYPE_TEXT,
            false);
    colinf[1].setSelectionAdapter(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            EnterSelectionDialog esd =
                new EnterSelectionDialog(
                    shell,
                    functionDesc,
                    BaseMessages.getString(PKG, "SystemDataDialog.SelectInfoType.DialogTitle"),
                    BaseMessages.getString(PKG, "SystemDataDialog.SelectInfoType.DialogMessage"));
            String string = esd.open();
            if (string != null) {
              TableView tv = (TableView) e.widget;
              tv.setText(string, e.x, e.y);
            }
            input.setChanged();
          }
        });

    wFields =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            colinf,
            FieldsRows,
            lsMod,
            props);

    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(wOk, -2 * margin);
    wFields.setLayoutData(fdFields);

    getData();
    input.setChanged(changed);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData() {
    wTransformName.setText(transformName);

    for (int i = 0; i < input.getFieldName().length; i++) {
      TableItem item = wFields.table.getItem(i);
      String name = input.getFieldName()[i];
      String type = input.getFieldType()[i].getDescription();

      if (name != null) {
        item.setText(1, name);
      }
      if (type != null) {
        item.setText(2, type);
      }
    }

    wFields.setRowNums();
    wFields.optWidth(true);

    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }

    try {
      getInfo(input);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SystemDataDialog.ErrorParsingData.DialogTitle"),
          BaseMessages.getString(PKG, "SystemDataDialog.ErrorParsingData.DialogMessage"),
          e);
    }
    dispose();
  }

  private void getInfo(SystemDataMeta in) {

    transformName = wTransformName.getText(); // return value
    int count = wFields.nrNonEmpty();
    in.allocate(count);

    for (int i = 0; i < count; i++) {
      TableItem item = wFields.getNonEmpty(i);
      in.getFieldName()[i] = item.getText(1);
      in.getFieldType()[i] = SystemDataTypes.getTypeFromString(item.getText(2));
    }
  }

  // Preview the data
  private void preview() {
    try {
      SystemDataMeta oneMeta = new SystemDataMeta();
      getInfo(oneMeta);

      PipelineMeta previewMeta =
          PipelinePreviewFactory.generatePreviewPipeline(
              pipelineMeta.getMetadataProvider(), oneMeta, wTransformName.getText());

      EnterNumberDialog numberDialog =
          new EnterNumberDialog(
              shell,
              props.getDefaultPreviewSize(),
              BaseMessages.getString(PKG, "SystemDataDialog.NumberRows.DialogTitle"),
              BaseMessages.getString(PKG, "SystemDataDialog.NumberRows.DialogMessage"));

      int previewSize = numberDialog.open();
      if (previewSize > 0) {
        PipelinePreviewProgressDialog progressDialog =
            new PipelinePreviewProgressDialog(
                shell,
                variables,
                previewMeta,
                new String[] {wTransformName.getText()},
                new int[] {previewSize});
        progressDialog.open();

        if (!progressDialog.isCancelled()) {
          Pipeline pipeline = progressDialog.getPipeline();
          String loggingText = progressDialog.getLoggingText();

          if (pipeline.getResult() != null && pipeline.getResult().getNrErrors() > 0) {
            EnterTextDialog etd =
                new EnterTextDialog(
                    shell,
                    BaseMessages.getString(PKG, "System.Dialog.PreviewError.Title"),
                    BaseMessages.getString(PKG, "System.Dialog.PreviewError.Message"),
                    loggingText,
                    true);
            etd.setReadOnly();
            etd.open();
          }

          PreviewRowsDialog prd =
              new PreviewRowsDialog(
                  shell,
                  variables,
                  SWT.NONE,
                  wTransformName.getText(),
                  progressDialog.getPreviewRowsMeta(wTransformName.getText()),
                  progressDialog.getPreviewRows(wTransformName.getText()),
                  loggingText);
          prd.open();
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SystemDataDialog.ErrorPreviewingData.DialogTitle"),
          BaseMessages.getString(PKG, "SystemDataDialog.ErrorPreviewingData.DialogMessage"),
          e);
    }
  }
}
