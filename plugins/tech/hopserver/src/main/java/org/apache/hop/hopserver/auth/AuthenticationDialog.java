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
 *
 */

package org.apache.hop.hopserver.auth;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.PasswordTextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class AuthenticationDialog extends Dialog {
  private static final Class<?> PKG = AuthenticationDialog.class; // For Translator

  UsernamePasswordReturn usernamePasswordReturn = new UsernamePasswordReturn();

  private Shell shell;
  private final PropsUi props;

  private Text wUsername;
  private PasswordTextVar wPassword;

  private IVariables variables;

  public AuthenticationDialog(Shell parent, IVariables variables) {
    super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);

    this.variables = variables;
    props = PropsUi.getInstance();
  }

  public UsernamePasswordReturn open() {

    Shell parent = getParent();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    shell.setImage(GuiResource.getInstance().getImageHopUi());
    PropsUi.setLook(shell);

    int margin = PropsUi.getMargin() + 2;
    int middle = props.getMiddlePct();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();

    shell.setLayout(formLayout);
    shell.setText(BaseMessages.getString(PKG, "AuthenticationDialog.Shell.Name"));

    // Buttons go at the bottom of the dialog
    //
    Button wOK = new Button(shell, SWT.PUSH);
    wOK.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOK.addListener(SWT.Selection, event -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, event -> cancel());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOK, wCancel}, margin * 3, null);

    // Username text field
    Label wlUsername = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlUsername);
    wlUsername.setText(BaseMessages.getString(PKG, "AuthenticationDialog.Label.Username"));
    FormData fdlUsername = new FormData();
    fdlUsername.left = new FormAttachment(0, 0);
    fdlUsername.right = new FormAttachment(middle, 0);
    fdlUsername.top = new FormAttachment(0, margin);
    wlUsername.setLayoutData(fdlUsername);
    wUsername = new Text(shell, SWT.SINGLE | SWT.BORDER | SWT.LEFT);
    PropsUi.setLook(wUsername);
    FormData fdUsername = new FormData();
    fdUsername.left = new FormAttachment(middle, margin);
    fdUsername.right = new FormAttachment(100, 0);
    fdUsername.top = new FormAttachment(wlUsername, 0, SWT.CENTER);
    wUsername.setLayoutData(fdUsername);
    Control lastControl = wUsername;

    // Password text field
    Label wlPassword = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlPassword);
    wlPassword.setText(BaseMessages.getString(PKG, "AuthenticationDialog.Label.Password"));
    FormData fdlPassword = new FormData();
    fdlPassword.left = new FormAttachment(0, 0);
    fdlPassword.right = new FormAttachment(middle, 0);
    fdlPassword.top = new FormAttachment(lastControl, margin);
    wlPassword.setLayoutData(fdlPassword);
    wPassword = new PasswordTextVar(variables, shell, SWT.SINGLE | SWT.BORDER | SWT.LEFT);
    PropsUi.setLook(wPassword);
    FormData fdPassword = new FormData();
    fdPassword.left = new FormAttachment(middle, margin);
    fdPassword.right = new FormAttachment(100, 0);
    fdPassword.top = new FormAttachment(wlPassword, 0, SWT.CENTER);
    wPassword.setLayoutData(fdPassword);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return usernamePasswordReturn;
  }

  private void ok() {
    usernamePasswordReturn.setUsername(wUsername.getText());
    usernamePasswordReturn.setPassword(wPassword.getText());
    usernamePasswordReturn.setButtonPressed("ok");

    dispose();
  }

  private void cancel() {
    usernamePasswordReturn.setButtonPressed("cancel");
    dispose();
  }

  public void dispose() {
    props.setScreen(new WindowProperty(shell));
    shell.dispose();
  }
}
