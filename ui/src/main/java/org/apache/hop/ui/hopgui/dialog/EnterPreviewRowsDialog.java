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

package org.apache.hop.ui.hopgui.dialog;

import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.dialog.PreviewRowsDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * Shows a dialog that allows you to select the transforms you want to preview by entering a number
 * of rows.
 */
public class EnterPreviewRowsDialog extends Dialog {
  private static final Class<?> PKG = EnterPreviewRowsDialog.class;

  private String transformName;

  private List wTransformList;

  private Shell shell;
  private java.util.List<String> transformNames;
  private java.util.List<IRowMeta> rowMetas;
  private java.util.List<java.util.List<Object[]>> rowDatas;
  private PropsUi props;

  public EnterPreviewRowsDialog(
      Shell parent,
      int style,
      java.util.List<String> transformNames,
      java.util.List<IRowMeta> rowMetas,
      java.util.List<java.util.List<Object[]>> rowBuffers) {
    super(parent, style);
    this.transformNames = transformNames;
    this.rowDatas = rowBuffers;
    this.rowMetas = rowMetas;
    props = PropsUi.getInstance();
  }

  public Object open() {
    Shell parent = getParent();
    Display display = parent.getDisplay();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();

    shell.setLayout(formLayout);
    shell.setText(
        BaseMessages.getString(
            PKG, "EnterPreviewRowsDialog.Dialog.PreviewTransform.Title")); // Select the
    // preview transform:
    shell.setImage(GuiResource.getInstance().getImageHopUi());

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    // Filename line
    Label wlTransformList = new Label(shell, SWT.NONE);
    wlTransformList.setText(
        BaseMessages.getString(
            PKG, "EnterPreviewRowsDialog.Dialog.PreviewTransform.Message")); // Transform
    // name :
    PropsUi.setLook(wlTransformList);
    FormData fdlTransformList = new FormData();
    fdlTransformList.left = new FormAttachment(0, 0);
    fdlTransformList.top = new FormAttachment(0, margin);
    wlTransformList.setLayoutData(fdlTransformList);
    wTransformList =
        new List(
            shell,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
    for (int i = 0; i < transformNames.size(); i++) {
      wTransformList.add(transformNames.get(i));
    }
    wTransformList.select(0);
    PropsUi.setLook(wTransformList);
    FormData fdTransformList = new FormData();
    fdTransformList.left = new FormAttachment(middle, 0);
    fdTransformList.top = new FormAttachment(0, margin);
    fdTransformList.bottom = new FormAttachment(100, -60);
    fdTransformList.right = new FormAttachment(100, 0);
    wTransformList.setLayoutData(fdTransformList);
    wTransformList.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetDefaultSelected(SelectionEvent arg0) {
            show();
          }
        });

    Button wShow = new Button(shell, SWT.PUSH);
    wShow.setText(BaseMessages.getString(PKG, "System.Button.Show"));

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wShow, wClose}, margin, null);
    // Add listeners
    Listener lsShow = e -> show();
    Listener lsClose = e -> close();

    wShow.addListener(SWT.Selection, lsShow);
    wClose.addListener(SWT.Selection, lsClose);

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener(
        new ShellAdapter() {
          @Override
          public void shellClosed(ShellEvent e) {
            close();
          }
        });

    getData();

    BaseTransformDialog.setSize(shell);

    // Immediately show the only preview entry
    if (transformNames.size() == 1) {
      wTransformList.select(0);
      show();
    }

    shell.open();
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
    return transformName;
  }

  public void dispose() {
    props.setScreen(new WindowProperty(shell));
    shell.dispose();
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData() {
    // Disable getData
  }

  private void close() {
    dispose();
  }

  private void show() {
    if (rowDatas.isEmpty()) {
      return;
    }

    int nr = wTransformList.getSelectionIndex();

    java.util.List<Object[]> buffer = rowDatas.get(nr);
    IRowMeta rowMeta = rowMetas.get(nr);
    String name = transformNames.get(nr);

    if (rowMeta != null && buffer != null && !buffer.isEmpty()) {
      PreviewRowsDialog prd =
          new PreviewRowsDialog(
              shell, Variables.getADefaultVariableSpace(), SWT.NONE, name, rowMeta, buffer);
      prd.open();
    } else {
      MessageBox mb = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      mb.setText(
          BaseMessages.getString(PKG, "EnterPreviewRowsDialog.Dialog.NoPreviewRowsFound.Title"));
      mb.setMessage(
          BaseMessages.getString(PKG, "EnterPreviewRowsDialog.Dialog.NoPreviewRowsFound.Message"));
      mb.open();
    }
  }
}
