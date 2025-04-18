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

package org.apache.hop.pipeline.transforms.jsonoutput;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class JsonOutputDialog extends BaseTransformDialog {
  private static final Class<?> PKG = JsonOutputMeta.class;

  private Label wlEncoding;
  private ComboVar wEncoding;

  private Label wlOutputValue;
  private TextVar wOutputValue;

  private TextVar wBlocName;

  private TextVar wNrRowsInBloc;

  private TableView wFields;

  private final JsonOutputMeta input;

  private boolean gotEncodings = false;

  private ColumnInfo[] colinf;

  private Label wlAddToResult;
  private Button wAddToResult;

  private Label wlFilename;
  private Button wbFilename;
  private TextVar wFilename;

  private Label wlExtension;
  private TextVar wExtension;

  private Label wlCreateParentFolder;
  private Button wCreateParentFolder;

  private Label wlDoNotOpenNewFileInit;
  private Button wDoNotOpenNewFileInit;

  private Label wlAddDate;
  private Button wAddDate;

  private Label wlAddTime;
  private Button wAddTime;

  private Button wbShowFiles;

  private Label wlAppend;
  private Button wAppend;

  private CCombo wOperation;

  private final List<String> inputFields = new ArrayList<>();

  public JsonOutputDialog(
      Shell parent, IVariables variables, JsonOutputMeta transformMeta, PipelineMeta pipelineMeta) {
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
    shell.setText(BaseMessages.getString(PKG, "JsonOutputDialog.DialogTitle"));

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    // TransformName line
    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText(BaseMessages.getString(PKG, "System.TransformName.Label"));
    wlTransformName.setToolTipText(BaseMessages.getString(PKG, "System.TransformName.Tooltip"));
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.top = new FormAttachment(0, margin);
    fdlTransformName.right = new FormAttachment(middle, -margin);
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

    // Buttons at the bottom
    //
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    setButtonPositions(new Button[] {wOk, wCancel}, margin, null);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);

    // ////////////////////////
    // START OF General TAB///
    // /
    CTabItem wGeneralTab = new CTabItem(wTabFolder, SWT.NONE);
    wGeneralTab.setFont(GuiResource.getInstance().getFontDefault());
    wGeneralTab.setText(BaseMessages.getString(PKG, "JsonOutputDialog.GeneralTab.TabTitle"));

    FormLayout generalLayout = new FormLayout();
    generalLayout.marginWidth = 3;
    generalLayout.marginHeight = 3;

    Composite wGeneralComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wGeneralComp);
    wGeneralComp.setLayout(generalLayout);

    // Operation
    Label wlOperation = new Label(wGeneralComp, SWT.RIGHT);
    wlOperation.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Operation.Label"));
    PropsUi.setLook(wlOperation);
    FormData fdlOperation = new FormData();
    fdlOperation.left = new FormAttachment(0, 0);
    fdlOperation.right = new FormAttachment(middle, -margin);
    fdlOperation.top = new FormAttachment(wNrRowsInBloc, margin);
    wlOperation.setLayoutData(fdlOperation);

    wOperation = new CCombo(wGeneralComp, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wOperation);
    wOperation.addModifyListener(lsMod);
    FormData fdOperation = new FormData();
    fdOperation.left = new FormAttachment(middle, 0);
    fdOperation.top = new FormAttachment(wNrRowsInBloc, margin);
    fdOperation.right = new FormAttachment(100, -margin);
    wOperation.setLayoutData(fdOperation);
    String[] operationTypeDescArray =
        JsonOutputMeta.operationDescType
            .keySet()
            .toArray(new String[JsonOutputMeta.operationDescType.size()]);
    wOperation.setItems(operationTypeDescArray);
    wOperation.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            updateOperation();
          }
        });

    // Connection grouping?
    // ////////////////////////
    // START OF Settings GROUP
    //

    Group wSettings = new Group(wGeneralComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wSettings);
    wSettings.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Group.Settings.Label"));

    FormLayout groupFileLayout = new FormLayout();
    groupFileLayout.marginWidth = 10;
    groupFileLayout.marginHeight = 10;
    wSettings.setLayout(groupFileLayout);

    Label wlBlocName = new Label(wSettings, SWT.RIGHT);
    wlBlocName.setText(BaseMessages.getString(PKG, "JsonOutputDialog.BlocName.Label"));
    PropsUi.setLook(wlBlocName);
    FormData fdlBlocName = new FormData();
    fdlBlocName.left = new FormAttachment(0, 0);
    fdlBlocName.top = new FormAttachment(wOperation, margin);
    fdlBlocName.right = new FormAttachment(middle, -margin);
    wlBlocName.setLayoutData(fdlBlocName);
    wBlocName = new TextVar(variables, wSettings, SWT.BORDER | SWT.READ_ONLY);
    wBlocName.setEditable(true);
    PropsUi.setLook(wBlocName);
    wBlocName.addModifyListener(lsMod);
    FormData fdBlocName = new FormData();
    fdBlocName.left = new FormAttachment(middle, 0);
    fdBlocName.top = new FormAttachment(wOperation, margin);
    fdBlocName.right = new FormAttachment(100, 0);
    wBlocName.setLayoutData(fdBlocName);

    Label wlNrRowsInBloc = new Label(wSettings, SWT.RIGHT);
    wlNrRowsInBloc.setText(BaseMessages.getString(PKG, "JsonOutputDialog.NrRowsInBloc.Label"));
    PropsUi.setLook(wlNrRowsInBloc);
    FormData fdlNrRowsInBloc = new FormData();
    fdlNrRowsInBloc.left = new FormAttachment(0, 0);
    fdlNrRowsInBloc.top = new FormAttachment(wBlocName, margin);
    fdlNrRowsInBloc.right = new FormAttachment(middle, -margin);
    wlNrRowsInBloc.setLayoutData(fdlNrRowsInBloc);
    wNrRowsInBloc = new TextVar(variables, wSettings, SWT.BORDER | SWT.READ_ONLY);
    wNrRowsInBloc.setToolTipText(
        BaseMessages.getString(PKG, "JsonOutputDialog.NrRowsInBloc.ToolTip"));
    wNrRowsInBloc.setEditable(true);
    PropsUi.setLook(wNrRowsInBloc);
    wNrRowsInBloc.addModifyListener(lsMod);
    FormData fdNrRowsInBloc = new FormData();
    fdNrRowsInBloc.left = new FormAttachment(middle, 0);
    fdNrRowsInBloc.top = new FormAttachment(wBlocName, margin);
    fdNrRowsInBloc.right = new FormAttachment(100, 0);
    wNrRowsInBloc.setLayoutData(fdNrRowsInBloc);

    wlOutputValue = new Label(wSettings, SWT.RIGHT);
    wlOutputValue.setText(BaseMessages.getString(PKG, "JsonOutputDialog.OutputValue.Label"));
    PropsUi.setLook(wlOutputValue);
    FormData fdlOutputValue = new FormData();
    fdlOutputValue.left = new FormAttachment(0, 0);
    fdlOutputValue.top = new FormAttachment(wNrRowsInBloc, margin);
    fdlOutputValue.right = new FormAttachment(middle, -margin);
    wlOutputValue.setLayoutData(fdlOutputValue);
    wOutputValue = new TextVar(variables, wSettings, SWT.BORDER | SWT.READ_ONLY);
    wOutputValue.setEditable(true);
    PropsUi.setLook(wOutputValue);
    wOutputValue.addModifyListener(lsMod);
    FormData fdOutputValue = new FormData();
    fdOutputValue.left = new FormAttachment(middle, 0);
    fdOutputValue.top = new FormAttachment(wNrRowsInBloc, margin);
    fdOutputValue.right = new FormAttachment(100, 0);
    wOutputValue.setLayoutData(fdOutputValue);

    FormData fdSettings = new FormData();
    fdSettings.left = new FormAttachment(0, margin);
    fdSettings.top = new FormAttachment(wOperation, 2 * margin);
    fdSettings.right = new FormAttachment(100, -margin);
    wSettings.setLayoutData(fdSettings);

    // ///////////////////////////////////////////////////////////
    // / END OF Settings GROUP
    // ///////////////////////////////////////////////////////////

    // Connection grouping?
    // ////////////////////////
    // START OF FileName GROUP
    //

    Group wFileName = new Group(wGeneralComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wFileName);
    wFileName.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Group.File.Label"));

    FormLayout groupfilenameayout = new FormLayout();
    groupfilenameayout.marginWidth = 10;
    groupfilenameayout.marginHeight = 10;
    wFileName.setLayout(groupfilenameayout);

    // Filename line
    wlFilename = new Label(wFileName, SWT.RIGHT);
    wlFilename.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Filename.Label"));
    PropsUi.setLook(wlFilename);
    FormData fdlFilename = new FormData();
    fdlFilename.left = new FormAttachment(0, 0);
    fdlFilename.top = new FormAttachment(wSettings, margin);
    fdlFilename.right = new FormAttachment(middle, -margin);
    wlFilename.setLayoutData(fdlFilename);

    wbFilename = new Button(wFileName, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbFilename);
    wbFilename.setText(BaseMessages.getString(PKG, "System.Button.Browse"));
    FormData fdbFilename = new FormData();
    fdbFilename.right = new FormAttachment(100, 0);
    fdbFilename.top = new FormAttachment(wSettings, 0);
    wbFilename.setLayoutData(fdbFilename);

    wbFilename.addListener(
        SWT.Selection,
        e ->
            BaseDialog.presentFileDialog(
                true,
                shell,
                wFilename,
                variables,
                new String[] {"*.js", "*.json", "*"},
                new String[] {
                  BaseMessages.getString(PKG, "System.FileType.JsonFiles"),
                  BaseMessages.getString(PKG, "System.FileType.AllFiles")
                },
                true));

    wFilename = new TextVar(variables, wFileName, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFilename);
    wFilename.addModifyListener(lsMod);
    FormData fdFilename = new FormData();
    fdFilename.left = new FormAttachment(middle, 0);
    fdFilename.top = new FormAttachment(wOutputValue, margin);
    fdFilename.right = new FormAttachment(wbFilename, -margin);
    wFilename.setLayoutData(fdFilename);

    // Append to end of file?
    wlAppend = new Label(wFileName, SWT.RIGHT);
    wlAppend.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Append.Label"));
    PropsUi.setLook(wlAppend);
    FormData fdlAppend = new FormData();
    fdlAppend.left = new FormAttachment(0, 0);
    fdlAppend.top = new FormAttachment(wFilename, margin);
    fdlAppend.right = new FormAttachment(middle, -margin);
    wlAppend.setLayoutData(fdlAppend);
    wAppend = new Button(wFileName, SWT.CHECK);
    wAppend.setToolTipText(BaseMessages.getString(PKG, "JsonOutputDialog.Append.Tooltip"));
    PropsUi.setLook(wAppend);
    FormData fdAppend = new FormData();
    fdAppend.left = new FormAttachment(middle, 0);
    fdAppend.top = new FormAttachment(wlAppend, 0, SWT.CENTER);
    fdAppend.right = new FormAttachment(100, 0);
    wAppend.setLayoutData(fdAppend);
    wAppend.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
          }
        });

    // Create Parent Folder
    wlCreateParentFolder = new Label(wFileName, SWT.RIGHT);
    wlCreateParentFolder.setText(
        BaseMessages.getString(PKG, "JsonOutputDialog.CreateParentFolder.Label"));
    PropsUi.setLook(wlCreateParentFolder);
    FormData fdlCreateParentFolder = new FormData();
    fdlCreateParentFolder.left = new FormAttachment(0, 0);
    fdlCreateParentFolder.top = new FormAttachment(wAppend, margin);
    fdlCreateParentFolder.right = new FormAttachment(middle, -margin);
    wlCreateParentFolder.setLayoutData(fdlCreateParentFolder);
    wCreateParentFolder = new Button(wFileName, SWT.CHECK);
    wCreateParentFolder.setToolTipText(
        BaseMessages.getString(PKG, "JsonOutputDialog.CreateParentFolder.Tooltip"));
    PropsUi.setLook(wCreateParentFolder);
    FormData fdCreateParentFolder = new FormData();
    fdCreateParentFolder.left = new FormAttachment(middle, 0);
    fdCreateParentFolder.top = new FormAttachment(wlCreateParentFolder, 0, SWT.CENTER);
    fdCreateParentFolder.right = new FormAttachment(100, 0);
    wCreateParentFolder.setLayoutData(fdCreateParentFolder);
    wCreateParentFolder.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
          }
        });

    // Open new File at Init
    wlDoNotOpenNewFileInit = new Label(wFileName, SWT.RIGHT);
    wlDoNotOpenNewFileInit.setText(
        BaseMessages.getString(PKG, "JsonOutputDialog.DoNotOpenNewFileInit.Label"));
    PropsUi.setLook(wlDoNotOpenNewFileInit);
    FormData fdlDoNotOpenNewFileInit = new FormData();
    fdlDoNotOpenNewFileInit.left = new FormAttachment(0, 0);
    fdlDoNotOpenNewFileInit.top = new FormAttachment(wCreateParentFolder, margin);
    fdlDoNotOpenNewFileInit.right = new FormAttachment(middle, -margin);
    wlDoNotOpenNewFileInit.setLayoutData(fdlDoNotOpenNewFileInit);
    wDoNotOpenNewFileInit = new Button(wFileName, SWT.CHECK);
    wDoNotOpenNewFileInit.setToolTipText(
        BaseMessages.getString(PKG, "JsonOutputDialog.DoNotOpenNewFileInit.Tooltip"));
    PropsUi.setLook(wDoNotOpenNewFileInit);
    FormData fdDoNotOpenNewFileInit = new FormData();
    fdDoNotOpenNewFileInit.left = new FormAttachment(middle, 0);
    fdDoNotOpenNewFileInit.top = new FormAttachment(wlDoNotOpenNewFileInit, 0, SWT.CENTER);
    fdDoNotOpenNewFileInit.right = new FormAttachment(100, 0);
    wDoNotOpenNewFileInit.setLayoutData(fdDoNotOpenNewFileInit);
    wDoNotOpenNewFileInit.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
          }
        });

    // Extension line
    wlExtension = new Label(wFileName, SWT.RIGHT);
    wlExtension.setText(BaseMessages.getString(PKG, "System.Label.Extension"));
    PropsUi.setLook(wlExtension);
    FormData fdlExtension = new FormData();
    fdlExtension.left = new FormAttachment(0, 0);
    fdlExtension.top = new FormAttachment(wDoNotOpenNewFileInit, margin);
    fdlExtension.right = new FormAttachment(middle, -margin);
    wlExtension.setLayoutData(fdlExtension);

    wExtension = new TextVar(variables, wFileName, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wExtension);
    wExtension.addModifyListener(lsMod);
    FormData fdExtension = new FormData();
    fdExtension.left = new FormAttachment(middle, 0);
    fdExtension.top = new FormAttachment(wDoNotOpenNewFileInit, margin);
    fdExtension.right = new FormAttachment(100, -margin);
    wExtension.setLayoutData(fdExtension);

    wlEncoding = new Label(wFileName, SWT.RIGHT);
    wlEncoding.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Encoding.Label"));
    PropsUi.setLook(wlEncoding);
    FormData fdlEncoding = new FormData();
    fdlEncoding.left = new FormAttachment(0, 0);
    fdlEncoding.top = new FormAttachment(wExtension, margin);
    fdlEncoding.right = new FormAttachment(middle, -margin);
    wlEncoding.setLayoutData(fdlEncoding);
    wEncoding = new ComboVar(variables, wFileName, SWT.BORDER | SWT.READ_ONLY);
    wEncoding.setEditable(true);
    PropsUi.setLook(wEncoding);
    wEncoding.addModifyListener(lsMod);
    FormData fdEncoding = new FormData();
    fdEncoding.left = new FormAttachment(middle, 0);
    fdEncoding.top = new FormAttachment(wExtension, margin);
    fdEncoding.right = new FormAttachment(100, 0);
    wEncoding.setLayoutData(fdEncoding);
    wEncoding.addFocusListener(
        new FocusListener() {
          @Override
          public void focusLost(FocusEvent e) {
            // Do nothing
          }

          @Override
          public void focusGained(FocusEvent e) {
            Cursor busy = new Cursor(shell.getDisplay(), SWT.CURSOR_WAIT);
            shell.setCursor(busy);
            setEncodings();
            shell.setCursor(null);
            busy.dispose();
          }
        });

    // Create multi-part file?
    wlAddDate = new Label(wFileName, SWT.RIGHT);
    wlAddDate.setText(BaseMessages.getString(PKG, "JsonOutputDialog.AddDate.Label"));
    PropsUi.setLook(wlAddDate);
    FormData fdlAddDate = new FormData();
    fdlAddDate.left = new FormAttachment(0, 0);
    fdlAddDate.top = new FormAttachment(wEncoding, margin);
    fdlAddDate.right = new FormAttachment(middle, -margin);
    wlAddDate.setLayoutData(fdlAddDate);
    wAddDate = new Button(wFileName, SWT.CHECK);
    PropsUi.setLook(wAddDate);
    FormData fdAddDate = new FormData();
    fdAddDate.left = new FormAttachment(middle, 0);
    fdAddDate.top = new FormAttachment(wlAddDate, 0, SWT.CENTER);
    fdAddDate.right = new FormAttachment(100, 0);
    wAddDate.setLayoutData(fdAddDate);
    wAddDate.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
          }
        });
    // Create multi-part file?
    wlAddTime = new Label(wFileName, SWT.RIGHT);
    wlAddTime.setText(BaseMessages.getString(PKG, "JsonOutputDialog.AddTime.Label"));
    PropsUi.setLook(wlAddTime);
    FormData fdlAddTime = new FormData();
    fdlAddTime.left = new FormAttachment(0, 0);
    fdlAddTime.top = new FormAttachment(wAddDate, margin);
    fdlAddTime.right = new FormAttachment(middle, -margin);
    wlAddTime.setLayoutData(fdlAddTime);
    wAddTime = new Button(wFileName, SWT.CHECK);
    PropsUi.setLook(wAddTime);
    FormData fdAddTime = new FormData();
    fdAddTime.left = new FormAttachment(middle, 0);
    fdAddTime.top = new FormAttachment(wlAddTime, 0, SWT.CENTER);
    fdAddTime.right = new FormAttachment(100, 0);
    wAddTime.setLayoutData(fdAddTime);
    wAddTime.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
          }
        });

    wbShowFiles = new Button(wFileName, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbShowFiles);
    wbShowFiles.setText(BaseMessages.getString(PKG, "JsonOutputDialog.ShowFiles.Button"));
    FormData fdbShowFiles = new FormData();
    fdbShowFiles.left = new FormAttachment(middle, 0);
    fdbShowFiles.top = new FormAttachment(wAddTime, margin * 2);
    wbShowFiles.setLayoutData(fdbShowFiles);
    wbShowFiles.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            JsonOutputMeta tfoi = new JsonOutputMeta();
            getInfo(tfoi);
            String[] files = tfoi.getFiles(variables);
            if (files != null && files.length > 0) {
              EnterSelectionDialog esd =
                  new EnterSelectionDialog(
                      shell,
                      files,
                      BaseMessages.getString(PKG, "JsonOutputDialog.SelectOutputFiles.DialogTitle"),
                      BaseMessages.getString(
                          PKG, "JsonOutputDialog.SelectOutputFiles.DialogMessage"));
              esd.setViewOnly();
              esd.open();
            } else {
              MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
              mb.setMessage(
                  BaseMessages.getString(PKG, "JsonOutputDialog.NoFilesFound.DialogMessage"));
              mb.setText(BaseMessages.getString(PKG, "System.DialogTitle.Error"));
              mb.open();
            }
          }
        });

    // Add File to the result files name
    wlAddToResult = new Label(wFileName, SWT.RIGHT);
    wlAddToResult.setText(BaseMessages.getString(PKG, "JsonOutputDialog.AddFileToResult.Label"));
    PropsUi.setLook(wlAddToResult);
    FormData fdlAddToResult = new FormData();
    fdlAddToResult.left = new FormAttachment(0, 0);
    fdlAddToResult.top = new FormAttachment(wbShowFiles, margin);
    fdlAddToResult.right = new FormAttachment(middle, -margin);
    wlAddToResult.setLayoutData(fdlAddToResult);
    wAddToResult = new Button(wFileName, SWT.CHECK);
    wAddToResult.setToolTipText(
        BaseMessages.getString(PKG, "JsonOutputDialog.AddFileToResult.Tooltip"));
    PropsUi.setLook(wAddToResult);
    FormData fdAddToResult = new FormData();
    fdAddToResult.left = new FormAttachment(middle, 0);
    fdAddToResult.top = new FormAttachment(wlAddToResult, 0, SWT.CENTER);
    fdAddToResult.right = new FormAttachment(100, 0);
    wAddToResult.setLayoutData(fdAddToResult);
    SelectionAdapter lsSelR =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            input.setChanged();
          }
        };
    wAddToResult.addSelectionListener(lsSelR);

    FormData fdFileName = new FormData();
    fdFileName.left = new FormAttachment(0, margin);
    fdFileName.top = new FormAttachment(wSettings, 2 * margin);
    fdFileName.right = new FormAttachment(100, -margin);
    wFileName.setLayoutData(fdFileName);

    // ///////////////////////////////////////////////////////////
    // / END OF FileName GROUP
    // ///////////////////////////////////////////////////////////

    FormData fdGeneralComp = new FormData();
    fdGeneralComp.left = new FormAttachment(0, 0);
    fdGeneralComp.top = new FormAttachment(wTransformName, margin);
    fdGeneralComp.right = new FormAttachment(100, 0);
    fdGeneralComp.bottom = new FormAttachment(100, 0);
    wGeneralComp.setLayoutData(fdGeneralComp);

    wGeneralComp.layout();
    wGeneralTab.setControl(wGeneralComp);

    // ///////////////////////////////////////////////////////////
    // / END OF General TAB
    // ///////////////////////////////////////////////////////////

    // Fields tab...
    //
    CTabItem wFieldsTab = new CTabItem(wTabFolder, SWT.NONE);
    wFieldsTab.setFont(GuiResource.getInstance().getFontDefault());
    wFieldsTab.setText(BaseMessages.getString(PKG, "JsonOutputDialog.FieldsTab.TabTitle"));

    FormLayout fieldsLayout = new FormLayout();
    fieldsLayout.marginWidth = PropsUi.getFormMargin();
    fieldsLayout.marginHeight = PropsUi.getFormMargin();

    Composite wFieldsComp = new Composite(wTabFolder, SWT.NONE);
    wFieldsComp.setLayout(fieldsLayout);
    PropsUi.setLook(wFieldsComp);

    wGet = new Button(wFieldsComp, SWT.PUSH);
    wGet.setText(BaseMessages.getString(PKG, "JsonOutputDialog.Get.Button"));
    wGet.setToolTipText(BaseMessages.getString(PKG, "JsonOutputDialog.Get.Tooltip"));

    setButtonPositions(new Button[] {wGet}, margin, null);

    final int FieldsRows = input.getOutputFields().size();

    colinf =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "JsonOutputDialog.Fieldname.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {""},
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "JsonOutputDialog.ElementName.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };
    colinf[1].setUsingVariables(true);
    wFields =
        new TableView(
            variables,
            wFieldsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            colinf,
            FieldsRows,
            lsMod,
            props);

    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(0, 0);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(wGet, -margin);
    wFields.setLayoutData(fdFields);

    //
    // Search the fields in the background

    final Runnable runnable =
        () -> {
          TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
          if (transformMeta != null) {
            try {
              IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);

              // Remember these fields...
              for (int i = 0; i < row.size(); i++) {
                inputFields.add(row.getValueMeta(i).getName());
              }
              setComboBoxes();
            } catch (HopException e) {
              logError(BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"));
            }
          }
        };
    new Thread(runnable).start();

    FormData fdFieldsComp = new FormData();
    fdFieldsComp.left = new FormAttachment(0, 0);
    fdFieldsComp.top = new FormAttachment(0, 0);
    fdFieldsComp.right = new FormAttachment(100, 0);
    fdFieldsComp.bottom = new FormAttachment(100, 0);
    wFieldsComp.setLayoutData(fdFieldsComp);

    wFieldsComp.layout();
    wFieldsTab.setControl(wFieldsComp);

    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wTransformName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    wGet.addListener(SWT.Selection, e -> get());

    lsResize =
        event -> {
          Point size = shell.getSize();
          wFields.setSize(size.x - 10, size.y - 50);
          wFields.table.setSize(size.x - 10, size.y - 50);
          wFields.redraw();
        };
    shell.addListener(SWT.Resize, lsResize);

    wTabFolder.setSelection(0);

    getData();
    updateOperation();
    input.setChanged(changed);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  protected void setComboBoxes() {
    // Something was changed in the row.
    String[] fieldNames = ConstUi.sortFieldNames(inputFields);
    colinf[0].setComboValues(fieldNames);
  }

  private void setEncodings() {
    // Encoding of the text file:
    if (!gotEncodings) {
      gotEncodings = true;

      wEncoding.removeAll();
      List<Charset> values = new ArrayList<>(Charset.availableCharsets().values());
      for (Charset charSet : values) {
        wEncoding.add(charSet.displayName());
      }

      // Now select the default!
      String defEncoding = Const.getEnvironmentVariable("file.encoding", "UTF-8");
      int idx = Const.indexOfString(defEncoding, wEncoding.getItems());
      if (idx >= 0) {
        wEncoding.select(idx);
      } else {
        wEncoding.select(Const.indexOfString("UTF-8", wEncoding.getItems()));
      }
    }
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData() {
    wBlocName.setText(Const.NVL(input.getJsonBloc(), ""));
    wNrRowsInBloc.setText(Const.NVL(input.getNrRowsInBloc(), ""));
    wEncoding.setText(Const.NVL(input.getEncoding(), ""));
    wOutputValue.setText(Const.NVL(input.getOutputValue(), ""));

    wOperation.setText(JsonOutputMeta.operationTypeDesc.get(input.getOperationType()));
    wFilename.setText(Const.NVL(input.getFileName(), ""));
    wCreateParentFolder.setSelection(input.isCreateParentFolder());
    wExtension.setText(Const.NVL(input.getExtension(), ""));

    wAddDate.setSelection(input.isDateInFilename());
    wAddTime.setSelection(input.isTimeInFilename());
    wAppend.setSelection(input.isFileAppended());

    wEncoding.setText(Const.NVL(input.getEncoding(), ""));
    wAddToResult.setSelection(input.isAddToResult());
    wDoNotOpenNewFileInit.setSelection(input.isDoNotOpenNewFileInit());

    if (isDebug()) {
      logDebug(BaseMessages.getString(PKG, "JsonOutputDialog.Log.GettingFieldsInfo"));
    }

    for (int i = 0; i < input.getOutputFields().size(); i++) {
      JsonOutputField field = input.getOutputFields().get(i);

      TableItem item = wFields.table.getItem(i);
      item.setText(1, Const.NVL(field.getFieldName(), ""));
      item.setText(2, Const.NVL(field.getElementName(), ""));
    }

    wFields.optWidth(true);
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void cancel() {
    transformName = null;

    input.setChanged(backupChanged);

    dispose();
  }

  private void getInfo(JsonOutputMeta jsometa) {
    jsometa.setJsonBloc(wBlocName.getText());
    jsometa.setNrRowsInBloc(wNrRowsInBloc.getText());
    jsometa.setEncoding(wEncoding.getText());
    jsometa.setOutputValue(wOutputValue.getText());
    jsometa.setOperationType(JsonOutputMeta.operationDescType.get(wOperation.getText()));
    jsometa.setCreateParentFolder(wCreateParentFolder.getSelection());
    jsometa.setFileName(wFilename.getText());
    jsometa.setExtension(wExtension.getText());
    jsometa.setFileAppended(wAppend.getSelection());

    jsometa.setDateInFilename(wAddDate.getSelection());
    jsometa.setTimeInFilename(wAddTime.getSelection());

    jsometa.setEncoding(wEncoding.getText());
    jsometa.setAddToResult(wAddToResult.getSelection());
    jsometa.setDoNotOpenNewFileInit(wDoNotOpenNewFileInit.getSelection());

    int nrFields = wFields.nrNonEmpty();
    input.getOutputFields().clear();

    for (int i = 0; i < nrFields; i++) {
      JsonOutputField field = new JsonOutputField();

      TableItem item = wFields.getNonEmpty(i);
      field.setFieldName(item.getText(1));
      field.setElementName(item.getText(2));
      jsometa.getOutputFields().add(field);
    }
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }

    transformName = wTransformName.getText(); // return value

    getInfo(input);

    dispose();
  }

  private void get() {
    try {
      IRowMeta r = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (r != null) {
        BaseTransformDialog.getFieldsFromPrevious(
            r,
            wFields,
            1,
            new int[] {1, 2},
            new int[] {3},
            5,
            6,
            (tableItem, v) -> {
              if (v.isNumber() && v.getLength() > 0) {
                int le = v.getLength();
                int pr = v.getPrecision();

                if (v.getPrecision() <= 0) {
                  pr = 0;
                }

                String mask = " ";
                for (int m = 0; m < le - pr; m++) {
                  mask += "0";
                }
                if (pr > 0) {
                  mask += ".";
                }
                for (int m = 0; m < pr; m++) {
                  mask += "0";
                }
                tableItem.setText(4, mask);
              }
              return true;
            });
      }
    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Title"),
          BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"),
          ke);
    }
  }

  private void updateOperation() {
    String opType = wOperation.getText();
    boolean activeFile = !opType.equals(JsonOutputMeta.OPERATION_TYPE_OUTPUT_VALUE);

    wlFilename.setEnabled(activeFile);
    wFilename.setEnabled(activeFile);
    wbFilename.setEnabled(activeFile);
    wlExtension.setEnabled(activeFile);
    wExtension.setEnabled(activeFile);
    wlEncoding.setEnabled(activeFile);
    wEncoding.setEnabled(activeFile);
    wlAppend.setEnabled(activeFile);
    wAppend.setEnabled(activeFile);
    wlCreateParentFolder.setEnabled(activeFile);
    wCreateParentFolder.setEnabled(activeFile);
    wlDoNotOpenNewFileInit.setEnabled(activeFile);
    wDoNotOpenNewFileInit.setEnabled(activeFile);
    wlAddDate.setEnabled(activeFile);
    wAddDate.setEnabled(activeFile);
    wlAddTime.setEnabled(activeFile);
    wAddTime.setEnabled(activeFile);
    wlAddToResult.setEnabled(activeFile);
    wAddToResult.setEnabled(activeFile);
    wbShowFiles.setEnabled(activeFile);

    boolean activeOutputValue =
        !Objects.equals(wOperation.getText(), JsonOutputMeta.OPERATION_TYPE_WRITE_TO_FILE);

    wlOutputValue.setEnabled(activeOutputValue);
    wOutputValue.setEnabled(activeOutputValue);
  }
}
