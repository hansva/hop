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

package org.apache.hop.pipeline.transforms.tableexists;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class TableExistsDialog extends BaseTransformDialog {
  private static final Class<?> PKG = TableExistsMeta.class;

  private MetaSelectionLine<DatabaseMeta> wConnection;

  private CCombo wTableName;

  private Text wResult;

  private TextVar wSchemaname;

  private final TableExistsMeta input;

  public TableExistsDialog(
      Shell parent,
      IVariables variables,
      TableExistsMeta transformMeta,
      PipelineMeta pipelineMeta) {
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
    shell.setText(BaseMessages.getString(PKG, "TableExistsDialog.Shell.Title"));

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    // TransformName line
    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText(BaseMessages.getString(PKG, "TableExistsDialog.TransformName.Label"));
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(middle, -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);
    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    wTransformName.addModifyListener(lsMod);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(middle, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    fdTransformName.right = new FormAttachment(100, 0);
    wTransformName.setLayoutData(fdTransformName);

    // Connection line
    wConnection = addConnectionLine(shell, wTransformName, input.getConnection(), lsMod);
    wConnection.addModifyListener(lsMod);

    // Schema name line
    // Schema name
    Label wlSchemaname = new Label(shell, SWT.RIGHT);
    wlSchemaname.setText(BaseMessages.getString(PKG, "TableExistsDialog.Schemaname.Label"));
    PropsUi.setLook(wlSchemaname);
    FormData fdlSchemaname = new FormData();
    fdlSchemaname.left = new FormAttachment(0, 0);
    fdlSchemaname.right = new FormAttachment(middle, -margin);
    fdlSchemaname.top = new FormAttachment(wConnection, 2 * margin);
    wlSchemaname.setLayoutData(fdlSchemaname);

    Button wbSchema = new Button(shell, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbSchema);
    wbSchema.setText(BaseMessages.getString(PKG, "System.Button.Browse"));
    FormData fdbSchema = new FormData();
    fdbSchema.top = new FormAttachment(wConnection, 2 * margin);
    fdbSchema.right = new FormAttachment(100, 0);
    wbSchema.setLayoutData(fdbSchema);
    wbSchema.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            getSchemaNames();
          }
        });

    wSchemaname = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchemaname);
    wSchemaname.setToolTipText(BaseMessages.getString(PKG, "TableExistsDialog.Schemaname.Tooltip"));
    wSchemaname.addModifyListener(lsMod);
    FormData fdSchemaname = new FormData();
    fdSchemaname.left = new FormAttachment(middle, 0);
    fdSchemaname.top = new FormAttachment(wConnection, 2 * margin);
    fdSchemaname.right = new FormAttachment(wbSchema, -margin);
    wSchemaname.setLayoutData(fdSchemaname);

    Label wlTableName = new Label(shell, SWT.RIGHT);
    wlTableName.setText(BaseMessages.getString(PKG, "TableExistsDialog.TableName.Label"));
    PropsUi.setLook(wlTableName);
    FormData fdlTableName = new FormData();
    fdlTableName.left = new FormAttachment(0, 0);
    fdlTableName.right = new FormAttachment(middle, -margin);
    fdlTableName.top = new FormAttachment(wbSchema, margin);
    wlTableName.setLayoutData(fdlTableName);

    wTableName = new CCombo(shell, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wTableName);
    wTableName.addModifyListener(lsMod);
    FormData fdTableName = new FormData();
    fdTableName.left = new FormAttachment(middle, 0);
    fdTableName.top = new FormAttachment(wbSchema, margin);
    fdTableName.right = new FormAttachment(100, -margin);
    wTableName.setLayoutData(fdTableName);
    wTableName.addFocusListener(
        new FocusListener() {
          @Override
          public void focusLost(FocusEvent e) {
            // disable focuslost even
          }

          @Override
          public void focusGained(FocusEvent e) {
            Cursor busy = new Cursor(shell.getDisplay(), SWT.CURSOR_WAIT);
            shell.setCursor(busy);
            get();
            shell.setCursor(null);
            busy.dispose();
          }
        });

    // Result fieldname ...
    Label wlResult = new Label(shell, SWT.RIGHT);
    wlResult.setText(BaseMessages.getString(PKG, "TableExistsDialog.ResultField.Label"));
    PropsUi.setLook(wlResult);
    FormData fdlResult = new FormData();
    fdlResult.left = new FormAttachment(0, 0);
    fdlResult.right = new FormAttachment(middle, -margin);
    fdlResult.top = new FormAttachment(wTableName, margin * 2);
    wlResult.setLayoutData(fdlResult);
    wResult = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wResult.setToolTipText(BaseMessages.getString(PKG, "TableExistsDialog.ResultField.Tooltip"));
    PropsUi.setLook(wResult);
    wResult.addModifyListener(lsMod);
    FormData fdResult = new FormData();
    fdResult.left = new FormAttachment(middle, 0);
    fdResult.top = new FormAttachment(wTableName, margin * 2);
    fdResult.right = new FormAttachment(100, 0);
    wResult.setLayoutData(fdResult);

    // THE BUTTONS
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));

    setButtonPositions(new Button[] {wOk, wCancel}, margin, wResult);

    // Add listeners
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    getData();
    input.setChanged(changed);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData() {
    if (log.isDebug()) {
      logDebug(BaseMessages.getString(PKG, "TableExistsDialog.Log.GettingKeyInfo"));
    }

    if (input.getConnection() != null) {
      wConnection.setText(input.getConnection());
    }
    if (input.getTableNameField() != null) {
      wTableName.setText(input.getTableNameField());
    }
    if (input.getSchemaName() != null) {
      wSchemaname.setText(input.getSchemaName());
    }
    if (input.getResultFieldName() != null) {
      wResult.setText(input.getResultFieldName());
    }

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

    if (Utils.isEmpty(wConnection.getText())) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "TableExistsDialog.InvalidConnection.DialogMessage"));
      mb.setText(BaseMessages.getString(PKG, "TableExistsDialog.InvalidConnection.DialogTitle"));
      mb.open();
    }

    input.setConnection(wConnection.getText());
    input.setSchemaName(wSchemaname.getText());
    input.setTableNameField(wTableName.getText());
    input.setResultFieldName(wResult.getText());

    transformName = wTransformName.getText(); // return value

    dispose();
  }

  private void get() {
    try {

      wTableName.removeAll();
      IRowMeta r = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (r != null) {
        r.getFieldNames();

        for (int i = 0; i < r.getFieldNames().length; i++) {
          wTableName.add(r.getFieldNames()[i]);
        }
      }

    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "TableExistsDialog.FailedToGetFields.DialogTitle"),
          BaseMessages.getString(PKG, "TableExistsDialog.FailedToGetFields.DialogMessage"),
          ke);
    }
  }

  private void getSchemaNames() {
    if (wSchemaname.isDisposed()) {
      return;
    }
    DatabaseMeta databaseMeta = pipelineMeta.findDatabase(wConnection.getText(), variables);
    if (databaseMeta != null) {
      try (Database database = new Database(loggingObject, variables, databaseMeta)) {
        database.connect();
        String[] schemas = database.getSchemas();

        if (null != schemas && schemas.length > 0) {
          schemas = Const.sortStrings(schemas);
          EnterSelectionDialog dialog =
              new EnterSelectionDialog(
                  shell,
                  schemas,
                  BaseMessages.getString(
                      PKG, "System.Dialog.AvailableSchemas.Title", wConnection.getText()),
                  BaseMessages.getString(PKG, "System.Dialog.AvailableSchemas.Message"));
          String d = dialog.open();
          if (d != null) {
            wSchemaname.setText(Const.NVL(d, ""));
          }

        } else {
          MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
          mb.setMessage(
              BaseMessages.getString(PKG, "System.Dialog.AvailableSchemas.Empty.Message"));
          mb.setText(BaseMessages.getString(PKG, "System.Dialog.AvailableSchemas.Empty.Title"));
          mb.open();
        }
      } catch (Exception e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "System.Dialog.Error.Title"),
            BaseMessages.getString(PKG, "System.Dialog.AvailableSchemas.ConnectionError"),
            e);
      }
    }
  }
}
