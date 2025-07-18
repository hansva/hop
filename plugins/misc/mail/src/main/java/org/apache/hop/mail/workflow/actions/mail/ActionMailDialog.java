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

package org.apache.hop.mail.workflow.actions.mail;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Random;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.mail.metadata.MailServerConnection;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.LabelText;
import org.apache.hop.ui.core.widget.LabelTextVar;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.ui.workflow.dialog.WorkflowDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
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
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/** Dialog that allows you to edit a ActionMail object. */
public class ActionMailDialog extends ActionDialog {
  private static final Class<?> PKG = ActionMail.class;

  private static final String[] IMAGES_FILE_TYPES =
      new String[] {
        BaseMessages.getString(PKG, "ActionMail.Filetype.Png"),
        BaseMessages.getString(PKG, "ActionMail.Filetype.Jpeg"),
        BaseMessages.getString(PKG, "ActionMail.Filetype.Gif"),
        BaseMessages.getString(PKG, "ActionMail.Filetype.All")
      };
  public static final String CONST_NORMAL = "normal";

  private LabelText wName;

  private LabelTextVar wDestination;

  private LabelTextVar wDestinationCc;

  private LabelTextVar wDestinationBCc;

  private LabelTextVar wServer;

  private LabelTextVar wPort;

  private Button wUseAuth;

  private Label wlUseSecAuth;

  private Button wUseSecAuth;

  private Label wlUseXOAUTH2;
  private Button wUseXOAUTH2;

  private LabelTextVar wAuthUser;

  private LabelTextVar wAuthPass;

  private LabelTextVar wReply;
  private LabelTextVar wReplyName;

  private LabelTextVar wSubject;

  private Button wAddDate;

  private Button wIncludeFiles;

  private Label wlTypes;

  private List wTypes;

  private Label wlZipFiles;

  private Button wZipFiles;

  private LabelTextVar wZipFilename;

  private LabelTextVar wPerson;

  private LabelTextVar wPhone;

  private TextVar wComment;

  private Button wOnlyComment;
  private Button wUseHTML;
  private Button wUsePriority;

  private Label wlEncoding;
  private CCombo wEncoding;

  private Label wlSecureConnectionType;
  private CCombo wSecureConnectionType;

  private Label wlPriority;
  private CCombo wPriority;

  private Label wlImportance;
  private CCombo wImportance;

  private Label wlSensitivity;
  private CCombo wSensitivity;

  private ActionMail action;

  private boolean backupDate;
  private boolean backupChanged;

  private boolean gotEncodings = false;

  private LabelTextVar wReplyToAddress;

  private Label wlImageFilename;
  private Label wlContentID;
  private Label wlFields;
  private Button wbImageFilename;
  private Button wbaImageFilename;
  private Button wbdImageFilename;
  private Button wbeImageFilename;
  private TextVar wImageFilename;
  private TextVar wContentID;
  private TableView wFields;

  private Button wCheckServerIdentity;

  private Label wlCheckServerIdentity;

  private LabelTextVar wTrustedHosts;

  private MetaSelectionLine wSelectionLine;

  public ActionMailDialog(
      Shell parent, ActionMail action, WorkflowMeta workflowMeta, IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
  }

  @Override
  public IAction open() {

    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.MIN | SWT.MAX | SWT.RESIZE);
    PropsUi.setLook(shell);
    WorkflowDialog.setShellImage(shell, action);

    ModifyListener lsMod = e -> action.setChanged();
    backupChanged = action.hasChanged();
    backupDate = action.isIncludeDate();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();

    shell.setLayout(formLayout);
    shell.setText(BaseMessages.getString(PKG, "ActionMail.Header"));

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    // Buttons go at the very bottom
    //
    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    // Name line
    wName =
        new LabelText(
            shell,
            BaseMessages.getString(PKG, "ActionMail.NameOfEntry.Label"),
            BaseMessages.getString(PKG, "ActionMail.NameOfEntry.Tooltip"));
    wName.addModifyListener(lsMod);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(0, 0);
    fdName.left = new FormAttachment(0, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    CTabFolder wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);

    // ////////////////////////
    // START OF GENERAL TAB ///
    // ////////////////////////

    CTabItem wGeneralTab = new CTabItem(wTabFolder, SWT.NONE);
    wGeneralTab.setFont(GuiResource.getInstance().getFontDefault());
    wGeneralTab.setText(BaseMessages.getString(PKG, "ActionMail.Tab.General.Label"));

    Composite wGeneralComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wGeneralComp);

    FormLayout generalLayout = new FormLayout();
    generalLayout.marginWidth = 3;
    generalLayout.marginHeight = 3;
    wGeneralComp.setLayout(generalLayout);

    // ////////////////////////
    // START OF Destination Settings GROUP
    // ////////////////////////

    Group wDestinationGroup = new Group(wGeneralComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wDestinationGroup);
    wDestinationGroup.setText(
        BaseMessages.getString(PKG, "ActionMail.Group.DestinationAddress.Label"));

    FormLayout destinationgroupLayout = new FormLayout();
    destinationgroupLayout.marginWidth = 10;
    destinationgroupLayout.marginHeight = 10;
    wDestinationGroup.setLayout(destinationgroupLayout);

    // Destination line
    wDestination =
        new LabelTextVar(
            variables,
            wDestinationGroup,
            BaseMessages.getString(PKG, "ActionMail.DestinationAddress.Label"),
            BaseMessages.getString(PKG, "ActionMail.DestinationAddress.Tooltip"));
    wDestination.addModifyListener(lsMod);
    FormData fdDestination = new FormData();
    fdDestination.left = new FormAttachment(0, 0);
    fdDestination.top = new FormAttachment(wName, margin);
    fdDestination.right = new FormAttachment(100, 0);
    wDestination.setLayoutData(fdDestination);

    // Destination Cc
    wDestinationCc =
        new LabelTextVar(
            variables,
            wDestinationGroup,
            BaseMessages.getString(PKG, "ActionMail.DestinationAddressCc.Label"),
            BaseMessages.getString(PKG, "ActionMail.DestinationAddressCc.Tooltip"));
    wDestinationCc.addModifyListener(lsMod);
    FormData fdDestinationCc = new FormData();
    fdDestinationCc.left = new FormAttachment(0, 0);
    fdDestinationCc.top = new FormAttachment(wDestination, margin);
    fdDestinationCc.right = new FormAttachment(100, 0);
    wDestinationCc.setLayoutData(fdDestinationCc);

    // Destination BCc
    wDestinationBCc =
        new LabelTextVar(
            variables,
            wDestinationGroup,
            BaseMessages.getString(PKG, "ActionMail.DestinationAddressBCc.Label"),
            BaseMessages.getString(PKG, "ActionMail.DestinationAddressBCc.Tooltip"));
    wDestinationBCc.addModifyListener(lsMod);
    FormData fdDestinationBCc = new FormData();
    fdDestinationBCc.left = new FormAttachment(0, 0);
    fdDestinationBCc.top = new FormAttachment(wDestinationCc, margin);
    fdDestinationBCc.right = new FormAttachment(100, 0);
    wDestinationBCc.setLayoutData(fdDestinationBCc);

    FormData fdDestinationGroup = new FormData();
    fdDestinationGroup.left = new FormAttachment(0, margin);
    fdDestinationGroup.top = new FormAttachment(wName, margin);
    fdDestinationGroup.right = new FormAttachment(100, -margin);
    wDestinationGroup.setLayoutData(fdDestinationGroup);

    // ///////////////////////////////////////////////////////////
    // / END OF DESTINATION ADDRESS GROUP
    // ///////////////////////////////////////////////////////////

    // ////////////////////////
    // START OF Reply Settings GROUP
    // ////////////////////////

    Group wReplyGroup = new Group(wGeneralComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wReplyGroup);
    wReplyGroup.setText(BaseMessages.getString(PKG, "ActionMail.Group.Reply.Label"));

    FormLayout replygroupLayout = new FormLayout();
    replygroupLayout.marginWidth = 10;
    replygroupLayout.marginHeight = 10;
    wReplyGroup.setLayout(replygroupLayout);

    // Reply name
    wReplyName =
        new LabelTextVar(
            variables,
            wReplyGroup,
            BaseMessages.getString(PKG, "ActionMail.ReplyName.Label"),
            BaseMessages.getString(PKG, "ActionMail.ReplyName.Tooltip"));
    wReplyName.addModifyListener(lsMod);
    FormData fdReplyName = new FormData();
    fdReplyName.left = new FormAttachment(0, 0);
    fdReplyName.top = new FormAttachment(wDestinationGroup, 2 * margin);
    fdReplyName.right = new FormAttachment(100, 0);
    wReplyName.setLayoutData(fdReplyName);

    // Reply line
    wReply =
        new LabelTextVar(
            variables,
            wReplyGroup,
            BaseMessages.getString(PKG, "ActionMail.ReplyAddress.Label"),
            BaseMessages.getString(PKG, "ActionMail.ReplyAddress.Tooltip"));
    wReply.addModifyListener(lsMod);
    FormData fdReply = new FormData();
    fdReply.left = new FormAttachment(0, 0);
    fdReply.top = new FormAttachment(wReplyName, margin);
    fdReply.right = new FormAttachment(100, 0);
    wReply.setLayoutData(fdReply);

    FormData fdReplyGroup = new FormData();
    fdReplyGroup.left = new FormAttachment(0, margin);
    fdReplyGroup.top = new FormAttachment(wDestinationGroup, margin);
    fdReplyGroup.right = new FormAttachment(100, -margin);
    wReplyGroup.setLayoutData(fdReplyGroup);

    // ///////////////////////////////////////////////////////////
    // / END OF Replay GROUP
    // ///////////////////////////////////////////////////////////

    // Reply to
    wReplyToAddress =
        new LabelTextVar(
            variables,
            wGeneralComp,
            BaseMessages.getString(PKG, "ActionMail.ReplyToAddress.Label"),
            BaseMessages.getString(PKG, "ActionMail.ReplyToAddress.Tooltip"));
    wReplyToAddress.addModifyListener(lsMod);
    FormData fdReplyToAddress = new FormData();
    fdReplyToAddress.left = new FormAttachment(0, 0);
    fdReplyToAddress.top = new FormAttachment(wReplyGroup, 2 * margin);
    fdReplyToAddress.right = new FormAttachment(100, 0);
    wReplyToAddress.setLayoutData(fdReplyToAddress);

    // Contact line
    wPerson =
        new LabelTextVar(
            variables,
            wGeneralComp,
            BaseMessages.getString(PKG, "ActionMail.ContactPerson.Label"),
            BaseMessages.getString(PKG, "ActionMail.ContactPerson.Tooltip"));
    wPerson.addModifyListener(lsMod);
    FormData fdPerson = new FormData();
    fdPerson.left = new FormAttachment(0, 0);
    fdPerson.top = new FormAttachment(wReplyToAddress, 2 * margin);
    fdPerson.right = new FormAttachment(100, 0);
    wPerson.setLayoutData(fdPerson);

    // Phone line
    wPhone =
        new LabelTextVar(
            variables,
            wGeneralComp,
            BaseMessages.getString(PKG, "ActionMail.ContactPhone.Label"),
            BaseMessages.getString(PKG, "ActionMail.ContactPhone.Tooltip"));
    wPhone.addModifyListener(lsMod);
    FormData fdPhone = new FormData();
    fdPhone.left = new FormAttachment(0, 0);
    fdPhone.top = new FormAttachment(wPerson, margin);
    fdPhone.right = new FormAttachment(100, 0);
    wPhone.setLayoutData(fdPhone);

    FormData fdGeneralComp = new FormData();
    fdGeneralComp.left = new FormAttachment(0, 0);
    fdGeneralComp.top = new FormAttachment(0, 0);
    fdGeneralComp.right = new FormAttachment(100, 0);
    fdGeneralComp.bottom = new FormAttachment(500, -margin);
    wGeneralComp.setLayoutData(fdGeneralComp);

    wGeneralComp.layout();
    wGeneralTab.setControl(wGeneralComp);
    PropsUi.setLook(wGeneralComp);

    // ///////////////////////////////////////////////////////////
    // / END OF GENERAL TAB
    // ///////////////////////////////////////////////////////////

    // ////////////////////////////////////
    // START OF SERVER TAB ///
    // ///////////////////////////////////

    CTabItem wContentTab = new CTabItem(wTabFolder, SWT.NONE);
    wContentTab.setFont(GuiResource.getInstance().getFontDefault());
    wContentTab.setText(BaseMessages.getString(PKG, "ActionMailDialog.Server.Label"));

    FormLayout contentLayout = new FormLayout();
    contentLayout.marginWidth = 3;
    contentLayout.marginHeight = 3;

    Composite wContentComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wContentComp);
    wContentComp.setLayout(contentLayout);

    // ////////////////////////
    // START OF CONNECTION LINE GROUP
    // /////////////////////////

    Group wConnectionGroup = new Group(wContentComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wConnectionGroup);
    wConnectionGroup.setText(BaseMessages.getString(PKG, "ActionMail.Connection.Group.Label"));
    FormLayout connectionGroupLayout = new FormLayout();
    connectionGroupLayout.marginWidth = 10;
    connectionGroupLayout.marginHeight = 10;
    wConnectionGroup.setLayout(connectionGroupLayout);

    wSelectionLine =
        new MetaSelectionLine(
            variables,
            metadataProvider,
            MailServerConnection.class,
            wConnectionGroup,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "ActionMail.Connection.Label"),
            BaseMessages.getString(PKG, "ActionMail.Connection.ToolTip"));
    PropsUi.setLook(wSelectionLine);
    FormData fdSelectionLine = new FormData();
    fdSelectionLine.left = new FormAttachment(0, 0);
    fdSelectionLine.top = new FormAttachment(wGeneralComp, 0);
    fdSelectionLine.right = new FormAttachment(100, -margin);
    wSelectionLine.setLayoutData(fdSelectionLine);
    wSelectionLine.addListener(SWT.Selection, e -> action.setChanged(true));
    try {
      wSelectionLine.fillItems();
    } catch (Exception e) {
      new ErrorDialog(shell, "Error", "Error getting list of Mail Server connections", e);
    }

    FormData fdConnectionGroup = new FormData();
    fdConnectionGroup.left = new FormAttachment(0, 0);
    fdConnectionGroup.top = new FormAttachment(wName, margin);
    fdConnectionGroup.right = new FormAttachment(100, 0);
    wConnectionGroup.setLayoutData(fdConnectionGroup);

    // ////////////////////////
    // START OF SERVER GROUP
    // /////////////////////////

    Group wServerGroup = new Group(wContentComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wServerGroup);
    wServerGroup.setText(BaseMessages.getString(PKG, "ActionMail.Group.SMTPServer.Label"));

    FormLayout servergroupLayout = new FormLayout();
    servergroupLayout.marginWidth = 10;
    servergroupLayout.marginHeight = 10;
    wServerGroup.setLayout(servergroupLayout);

    // Server line
    wServer =
        new LabelTextVar(
            variables,
            wServerGroup,
            BaseMessages.getString(PKG, "ActionMail.SMTPServer.Label"),
            BaseMessages.getString(PKG, "ActionMail.SMTPServer.Tooltip"));
    wServer.addModifyListener(lsMod);
    FormData fdServer = new FormData();
    fdServer.left = new FormAttachment(0, 0);
    fdServer.top = new FormAttachment(wSelectionLine, margin);
    fdServer.right = new FormAttachment(100, 0);
    wServer.setLayoutData(fdServer);

    // Port line
    wPort =
        new LabelTextVar(
            variables,
            wServerGroup,
            BaseMessages.getString(PKG, "ActionMail.Port.Label"),
            BaseMessages.getString(PKG, "ActionMail.Port.Tooltip"));
    wPort.addModifyListener(lsMod);
    FormData fdPort = new FormData();
    fdPort.left = new FormAttachment(0, 0);
    fdPort.top = new FormAttachment(wServer, margin);
    fdPort.right = new FormAttachment(100, 0);
    wPort.setLayoutData(fdPort);

    FormData fdServerGroup = new FormData();
    fdServerGroup.left = new FormAttachment(0, margin);
    fdServerGroup.top = new FormAttachment(wConnectionGroup, margin);
    fdServerGroup.right = new FormAttachment(100, -margin);
    wServerGroup.setLayoutData(fdServerGroup);

    // //////////////////////////////////////
    // / END OF SERVER ADDRESS GROUP
    // ///////////////////////////////////////

    // ////////////////////////////////////
    // START OF AUTHENTIFICATION GROUP
    // ////////////////////////////////////

    Group wAuthentificationGroup = new Group(wContentComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wAuthentificationGroup);
    wAuthentificationGroup.setText(
        BaseMessages.getString(PKG, "ActionMail.Group.Authentification.Label"));

    FormLayout authentificationgroupLayout = new FormLayout();
    authentificationgroupLayout.marginWidth = 10;
    authentificationgroupLayout.marginHeight = 10;
    wAuthentificationGroup.setLayout(authentificationgroupLayout);

    // Authentication?
    Label wlUseAuth = new Label(wAuthentificationGroup, SWT.RIGHT);
    wlUseAuth.setText(BaseMessages.getString(PKG, "ActionMail.UseAuthentication.Label"));
    PropsUi.setLook(wlUseAuth);
    FormData fdlUseAuth = new FormData();
    fdlUseAuth.left = new FormAttachment(0, 0);
    fdlUseAuth.top = new FormAttachment(wServerGroup, 2 * margin);
    fdlUseAuth.right = new FormAttachment(middle, -margin);
    wlUseAuth.setLayoutData(fdlUseAuth);
    wUseAuth = new Button(wAuthentificationGroup, SWT.CHECK);
    PropsUi.setLook(wUseAuth);
    FormData fdUseAuth = new FormData();
    fdUseAuth.left = new FormAttachment(middle, 0);
    fdUseAuth.top = new FormAttachment(wlUseAuth, 0, SWT.CENTER);
    fdUseAuth.right = new FormAttachment(100, 0);
    wUseAuth.setLayoutData(fdUseAuth);
    wUseAuth.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            onSetUseAuth();
            action.setChanged();
          }
        });

    // USE connection with XOAUTH2
    wlUseXOAUTH2 = new Label(wAuthentificationGroup, SWT.RIGHT);
    wlUseXOAUTH2.setText(BaseMessages.getString(PKG, "ActionMail.UseXOAUTH2Mails.Label"));
    PropsUi.setLook(wlUseXOAUTH2);
    FormData fdlUseXOAUTH2 = new FormData();
    fdlUseXOAUTH2.left = new FormAttachment(0, 0);
    fdlUseXOAUTH2.top = new FormAttachment(wUseAuth, margin);
    fdlUseXOAUTH2.right = new FormAttachment(middle, -margin);
    wlUseXOAUTH2.setLayoutData(fdlUseXOAUTH2);
    wUseXOAUTH2 = new Button(wAuthentificationGroup, SWT.CHECK);
    PropsUi.setLook(wUseXOAUTH2);
    FormData fdUseXOAUTH2 = new FormData();
    wUseXOAUTH2.setToolTipText(BaseMessages.getString(PKG, "ActionMail.UseXOAUTH2Mails.Tooltip"));
    fdUseXOAUTH2.left = new FormAttachment(middle, 0);
    fdUseXOAUTH2.top = new FormAttachment(wlUseXOAUTH2, 0, SWT.CENTER);
    fdUseXOAUTH2.right = new FormAttachment(100, 0);
    wUseXOAUTH2.setLayoutData(fdUseXOAUTH2);

    // AuthUser line
    wAuthUser =
        new LabelTextVar(
            variables,
            wAuthentificationGroup,
            BaseMessages.getString(PKG, "ActionMail.AuthenticationUser.Label"),
            BaseMessages.getString(PKG, "ActionMail.AuthenticationUser.Tooltip"));
    wAuthUser.addModifyListener(lsMod);
    FormData fdAuthUser = new FormData();
    fdAuthUser.left = new FormAttachment(0, 0);
    fdAuthUser.top = new FormAttachment(wlUseXOAUTH2, 2 * margin);
    fdAuthUser.right = new FormAttachment(100, 0);
    wAuthUser.setLayoutData(fdAuthUser);

    // AuthPass line
    wAuthPass =
        new LabelTextVar(
            variables,
            wAuthentificationGroup,
            BaseMessages.getString(PKG, "ActionMail.AuthenticationPassword.Label"),
            BaseMessages.getString(PKG, "ActionMail.AuthenticationPassword.Tooltip"),
            true);
    wAuthPass.addModifyListener(lsMod);
    FormData fdAuthPass = new FormData();
    fdAuthPass.left = new FormAttachment(0, 0);
    fdAuthPass.top = new FormAttachment(wAuthUser, margin);
    fdAuthPass.right = new FormAttachment(100, 0);
    wAuthPass.setLayoutData(fdAuthPass);

    // Use secure authentication?
    wlUseSecAuth = new Label(wAuthentificationGroup, SWT.RIGHT);
    wlUseSecAuth.setText(BaseMessages.getString(PKG, "ActionMail.UseSecAuthentication.Label"));
    PropsUi.setLook(wlUseSecAuth);
    FormData fdlUseSecAuth = new FormData();
    fdlUseSecAuth.left = new FormAttachment(0, 0);
    fdlUseSecAuth.top = new FormAttachment(wAuthPass, 2 * margin);
    fdlUseSecAuth.right = new FormAttachment(middle, -margin);
    wlUseSecAuth.setLayoutData(fdlUseSecAuth);
    wUseSecAuth = new Button(wAuthentificationGroup, SWT.CHECK);
    PropsUi.setLook(wUseSecAuth);
    FormData fdUseSecAuth = new FormData();
    fdUseSecAuth.left = new FormAttachment(middle, 0);
    fdUseSecAuth.top = new FormAttachment(wlUseSecAuth, 0, SWT.CENTER);
    fdUseSecAuth.right = new FormAttachment(100, 0);
    wUseSecAuth.setLayoutData(fdUseSecAuth);
    wUseSecAuth.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            onSetSecureConnectiontype();
            action.setChanged();
          }
        });

    // SecureConnectionType
    wlSecureConnectionType = new Label(wAuthentificationGroup, SWT.RIGHT);
    wlSecureConnectionType.setText(
        BaseMessages.getString(PKG, "ActionMail.SecureConnectionType.Label"));
    PropsUi.setLook(wlSecureConnectionType);
    FormData fdlSecureConnectionType = new FormData();
    fdlSecureConnectionType.left = new FormAttachment(0, 0);
    fdlSecureConnectionType.top = new FormAttachment(wlUseSecAuth, 2 * margin);
    fdlSecureConnectionType.right = new FormAttachment(middle, -margin);
    wlSecureConnectionType.setLayoutData(fdlSecureConnectionType);
    wSecureConnectionType = new CCombo(wAuthentificationGroup, SWT.BORDER | SWT.READ_ONLY);
    wSecureConnectionType.setEditable(true);
    PropsUi.setLook(wSecureConnectionType);
    wSecureConnectionType.addModifyListener(lsMod);
    FormData fdSecureConnectionType = new FormData();
    fdSecureConnectionType.left = new FormAttachment(middle, 0);
    fdSecureConnectionType.top = new FormAttachment(wlUseSecAuth, 2 * margin);
    fdSecureConnectionType.right = new FormAttachment(100, 0);
    wSecureConnectionType.setLayoutData(fdSecureConnectionType);
    wSecureConnectionType.add("SSL");
    wSecureConnectionType.add("TLS");
    // Add support for TLS 1.2
    wSecureConnectionType.add("TLS 1.2");
    wSecureConnectionType.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            onSetSecureConnectiontype();
            action.setChanged();
          }
        });

    // Use check server identity
    wlCheckServerIdentity = new Label(wAuthentificationGroup, SWT.RIGHT);
    wlCheckServerIdentity.setText(
        BaseMessages.getString(PKG, "ActionMail.CheckServerIdentity.Label"));
    PropsUi.setLook(wlCheckServerIdentity);
    FormData fdlCheckServerIdentity = new FormData();
    fdlCheckServerIdentity.left = new FormAttachment(0, 0);
    fdlCheckServerIdentity.top = new FormAttachment(wSecureConnectionType, 2 * margin);
    fdlCheckServerIdentity.right = new FormAttachment(middle, -margin);
    wlCheckServerIdentity.setLayoutData(fdlCheckServerIdentity);
    wCheckServerIdentity = new Button(wAuthentificationGroup, SWT.CHECK);
    PropsUi.setLook(wCheckServerIdentity);
    FormData fdCheckServerIdentity = new FormData();
    fdCheckServerIdentity.left = new FormAttachment(middle, margin);
    fdCheckServerIdentity.top = new FormAttachment(wlCheckServerIdentity, 0, SWT.CENTER);
    fdCheckServerIdentity.right = new FormAttachment(100, 0);
    wCheckServerIdentity.setLayoutData(fdCheckServerIdentity);
    wCheckServerIdentity.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            action.setChanged();
          }
        });

    // Trusted Hosts line
    wTrustedHosts =
        new LabelTextVar(
            variables,
            wAuthentificationGroup,
            BaseMessages.getString(PKG, "ActionMail.TrustedHosts.Label"),
            BaseMessages.getString(PKG, "ActionMail.TrustedHosts.Tooltip"));
    wTrustedHosts.addModifyListener(lsMod);
    FormData fdTrustedHosts = new FormData();
    fdTrustedHosts.left = new FormAttachment(0, 0);
    fdTrustedHosts.top = new FormAttachment(wlCheckServerIdentity, 2 * margin);
    fdTrustedHosts.right = new FormAttachment(100, 0);
    wTrustedHosts.setLayoutData(fdTrustedHosts);

    FormData fdAuthentificationGroup = new FormData();
    fdAuthentificationGroup.left = new FormAttachment(0, margin);
    fdAuthentificationGroup.top = new FormAttachment(wServerGroup, margin);
    fdAuthentificationGroup.right = new FormAttachment(100, -margin);
    fdAuthentificationGroup.bottom = new FormAttachment(100, -margin);
    wAuthentificationGroup.setLayoutData(fdAuthentificationGroup);

    // //////////////////////////////////////
    // / END OF AUTHENTIFICATION GROUP
    // ///////////////////////////////////////

    FormData fdContentComp = new FormData();
    fdContentComp.left = new FormAttachment(0, 0);
    fdContentComp.top = new FormAttachment(0, 0);
    fdContentComp.right = new FormAttachment(100, 0);
    fdContentComp.bottom = new FormAttachment(100, 0);
    wContentComp.setLayoutData(wContentComp);

    wContentComp.layout();
    wContentTab.setControl(wContentComp);

    // ///////////////////////////////////////////////////////////
    // / END OF SERVER TAB
    // ///////////////////////////////////////////////////////////

    // ////////////////////////////////////
    // START OF MESSAGE TAB ///
    // ///////////////////////////////////

    CTabItem wMessageTab = new CTabItem(wTabFolder, SWT.NONE);
    wMessageTab.setFont(GuiResource.getInstance().getFontDefault());
    wMessageTab.setText(BaseMessages.getString(PKG, "ActionMail.Tab.Message.Label"));

    FormLayout messageLayout = new FormLayout();
    messageLayout.marginWidth = 3;
    messageLayout.marginHeight = 3;

    Composite wMessageComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wMessageComp);
    wMessageComp.setLayout(contentLayout);

    // ////////////////////////////////////
    // START OF MESSAGE SETTINGS GROUP
    // ////////////////////////////////////

    Group wMessageSettingsGroup = new Group(wMessageComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wMessageSettingsGroup);
    wMessageSettingsGroup.setText(
        BaseMessages.getString(PKG, "ActionMail.Group.MessageSettings.Label"));

    FormLayout messagesettingsgroupLayout = new FormLayout();
    messagesettingsgroupLayout.marginWidth = 10;
    messagesettingsgroupLayout.marginHeight = 10;
    wMessageSettingsGroup.setLayout(messagesettingsgroupLayout);

    // Add date to logfile name?
    Label wlAddDate = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlAddDate.setText(BaseMessages.getString(PKG, "ActionMail.IncludeDate.Label"));
    PropsUi.setLook(wlAddDate);
    FormData fdlAddDate = new FormData();
    fdlAddDate.left = new FormAttachment(0, 0);
    fdlAddDate.top = new FormAttachment(0, margin);
    fdlAddDate.right = new FormAttachment(middle, -margin);
    wlAddDate.setLayoutData(fdlAddDate);
    wAddDate = new Button(wMessageSettingsGroup, SWT.CHECK);
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
            action.setChanged();
          }
        });

    // Only send the comment in the mail body
    Label wlOnlyComment = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlOnlyComment.setText(BaseMessages.getString(PKG, "ActionMail.OnlyCommentInBody.Label"));
    PropsUi.setLook(wlOnlyComment);
    FormData fdlOnlyComment = new FormData();
    fdlOnlyComment.left = new FormAttachment(0, 0);
    fdlOnlyComment.top = new FormAttachment(wlAddDate, 2 * margin);
    fdlOnlyComment.right = new FormAttachment(middle, -margin);
    wlOnlyComment.setLayoutData(fdlOnlyComment);
    wOnlyComment = new Button(wMessageSettingsGroup, SWT.CHECK);
    PropsUi.setLook(wOnlyComment);
    FormData fdOnlyComment = new FormData();
    fdOnlyComment.left = new FormAttachment(middle, 0);
    fdOnlyComment.top = new FormAttachment(wlOnlyComment, 0, SWT.CENTER);
    fdOnlyComment.right = new FormAttachment(100, 0);
    wOnlyComment.setLayoutData(fdOnlyComment);
    wOnlyComment.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            action.setChanged();
          }
        });

    // HTML format ?
    Label wlUseHTML = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlUseHTML.setText(BaseMessages.getString(PKG, "ActionMail.UseHTMLInBody.Label"));
    PropsUi.setLook(wlUseHTML);
    FormData fdlUseHTML = new FormData();
    fdlUseHTML.left = new FormAttachment(0, 0);
    fdlUseHTML.top = new FormAttachment(wlOnlyComment, 2 * margin);
    fdlUseHTML.right = new FormAttachment(middle, -margin);
    wlUseHTML.setLayoutData(fdlUseHTML);
    wUseHTML = new Button(wMessageSettingsGroup, SWT.CHECK);
    PropsUi.setLook(wUseHTML);
    FormData fdUseHTML = new FormData();
    fdUseHTML.left = new FormAttachment(middle, 0);
    fdUseHTML.top = new FormAttachment(wlUseHTML, 0, SWT.CENTER);
    fdUseHTML.right = new FormAttachment(100, 0);
    wUseHTML.setLayoutData(fdUseHTML);
    wUseHTML.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            setEnabledEncoding();
            action.setChanged();
          }
        });

    // Encoding
    wlEncoding = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlEncoding.setText(BaseMessages.getString(PKG, "ActionMail.Encoding.Label"));
    PropsUi.setLook(wlEncoding);
    FormData fdlEncoding = new FormData();
    fdlEncoding.left = new FormAttachment(0, 0);
    fdlEncoding.top = new FormAttachment(wlUseHTML, 2 * margin);
    fdlEncoding.right = new FormAttachment(middle, -margin);
    wlEncoding.setLayoutData(fdlEncoding);
    wEncoding = new CCombo(wMessageSettingsGroup, SWT.BORDER | SWT.READ_ONLY);
    wEncoding.setEditable(true);
    PropsUi.setLook(wEncoding);
    wEncoding.addModifyListener(lsMod);
    FormData fdEncoding = new FormData();
    fdEncoding.left = new FormAttachment(middle, 0);
    fdEncoding.top = new FormAttachment(wlEncoding, 0, SWT.CENTER);
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

    // Use Priority ?
    Label wlUsePriority = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlUsePriority.setText(BaseMessages.getString(PKG, "ActionMail.UsePriority.Label"));
    PropsUi.setLook(wlUsePriority);
    FormData fdlPriority = new FormData();
    fdlPriority.left = new FormAttachment(0, 0);
    fdlPriority.top = new FormAttachment(wEncoding, margin);
    fdlPriority.right = new FormAttachment(middle, -margin);
    wlUsePriority.setLayoutData(fdlPriority);
    wUsePriority = new Button(wMessageSettingsGroup, SWT.CHECK);
    wUsePriority.setToolTipText(BaseMessages.getString(PKG, "ActionMail.UsePriority.Tooltip"));
    PropsUi.setLook(wUsePriority);
    FormData fdUsePriority = new FormData();
    fdUsePriority.left = new FormAttachment(middle, 0);
    fdUsePriority.top = new FormAttachment(wlUsePriority, 0, SWT.CENTER);
    fdUsePriority.right = new FormAttachment(100, 0);
    wUsePriority.setLayoutData(fdUsePriority);
    wUsePriority.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            activeUsePriority();
            action.setChanged();
          }
        });

    // Priority
    wlPriority = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlPriority.setText(BaseMessages.getString(PKG, "ActionMail.Priority.Label"));
    PropsUi.setLook(wlPriority);
    fdlPriority = new FormData();
    fdlPriority.left = new FormAttachment(0, 0);
    fdlPriority.right = new FormAttachment(middle, -margin);
    fdlPriority.top = new FormAttachment(wlUsePriority, 2 * margin);
    wlPriority.setLayoutData(fdlPriority);
    wPriority = new CCombo(wMessageSettingsGroup, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    wPriority.add(BaseMessages.getString(PKG, "ActionMail.Priority.Low.Label"));
    wPriority.add(BaseMessages.getString(PKG, "ActionMail.Priority.Normal.Label"));
    wPriority.add(BaseMessages.getString(PKG, "ActionMail.Priority.High.Label"));
    wPriority.select(1); // +1: starts at -1
    PropsUi.setLook(wPriority);
    FormData fdPriority = new FormData();
    fdPriority.left = new FormAttachment(middle, 0);
    fdPriority.top = new FormAttachment(wlUsePriority, 2 * margin);
    fdPriority.right = new FormAttachment(100, 0);
    wPriority.setLayoutData(fdPriority);

    // Importance
    wlImportance = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlImportance.setText(BaseMessages.getString(PKG, "ActionMail.Importance.Label"));
    PropsUi.setLook(wlImportance);
    FormData fdlImportance = new FormData();
    fdlImportance.left = new FormAttachment(0, 0);
    fdlImportance.right = new FormAttachment(middle, -margin);
    fdlImportance.top = new FormAttachment(wPriority, margin);
    wlImportance.setLayoutData(fdlImportance);
    wImportance = new CCombo(wMessageSettingsGroup, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    wImportance.add(BaseMessages.getString(PKG, "ActionMail.Priority.Low.Label"));
    wImportance.add(BaseMessages.getString(PKG, "ActionMail.Priority.Normal.Label"));
    wImportance.add(BaseMessages.getString(PKG, "ActionMail.Priority.High.Label"));

    wImportance.select(1); // +1: starts at -1

    PropsUi.setLook(wImportance);
    FormData fdImportance = new FormData();
    fdImportance.left = new FormAttachment(middle, 0);
    fdImportance.top = new FormAttachment(wPriority, margin);
    fdImportance.right = new FormAttachment(100, 0);
    wImportance.setLayoutData(fdImportance);

    // Sensitivity
    wlSensitivity = new Label(wMessageSettingsGroup, SWT.RIGHT);
    wlSensitivity.setText(BaseMessages.getString(PKG, "ActionMail.Sensitivity.Label"));
    PropsUi.setLook(wlSensitivity);
    FormData fdlSensitivity = new FormData();
    fdlSensitivity.left = new FormAttachment(0, 0);
    fdlSensitivity.right = new FormAttachment(middle, -margin);
    fdlSensitivity.top = new FormAttachment(wImportance, margin);
    wlSensitivity.setLayoutData(fdlSensitivity);
    wSensitivity = new CCombo(wMessageSettingsGroup, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    wSensitivity.add(BaseMessages.getString(PKG, "ActionMail.Sensitivity.normal.Label"));
    wSensitivity.add(BaseMessages.getString(PKG, "ActionMail.Sensitivity.personal.Label"));
    wSensitivity.add(BaseMessages.getString(PKG, "ActionMail.Sensitivity.private.Label"));
    wSensitivity.add(BaseMessages.getString(PKG, "ActionMail.Sensitivity.confidential.Label"));
    wSensitivity.select(0);

    PropsUi.setLook(wSensitivity);
    FormData fdSensitivity = new FormData();
    fdSensitivity.left = new FormAttachment(middle, 0);
    fdSensitivity.top = new FormAttachment(wImportance, margin);
    fdSensitivity.right = new FormAttachment(100, 0);
    wSensitivity.setLayoutData(fdSensitivity);

    FormData fdMessageSettingsGroup = new FormData();
    fdMessageSettingsGroup.left = new FormAttachment(0, margin);
    fdMessageSettingsGroup.top = new FormAttachment(wName, margin);
    fdMessageSettingsGroup.right = new FormAttachment(100, -margin);
    wMessageSettingsGroup.setLayoutData(fdMessageSettingsGroup);

    // //////////////////////////////////////
    // / END OF MESSAGE SETTINGS GROUP
    // ///////////////////////////////////////

    // ////////////////////////////////////
    // START OF MESSAGE GROUP
    // ////////////////////////////////////

    Group wMessageGroup = new Group(wMessageComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wMessageGroup);
    wMessageGroup.setText(BaseMessages.getString(PKG, "ActionMail.Group.Message.Label"));

    FormLayout messagegroupLayout = new FormLayout();
    messagegroupLayout.marginWidth = 10;
    messagegroupLayout.marginHeight = 10;
    wMessageGroup.setLayout(messagegroupLayout);

    // Subject line
    wSubject =
        new LabelTextVar(
            variables,
            wMessageGroup,
            BaseMessages.getString(PKG, "ActionMail.Subject.Label"),
            BaseMessages.getString(PKG, "ActionMail.Subject.Tooltip"));
    wSubject.addModifyListener(lsMod);
    FormData fdSubject = new FormData();
    fdSubject.left = new FormAttachment(0, 0);
    fdSubject.top = new FormAttachment(wMessageSettingsGroup, margin);
    fdSubject.right = new FormAttachment(100, 0);
    wSubject.setLayoutData(fdSubject);

    // Comment line
    Label wlComment = new Label(wMessageGroup, SWT.RIGHT);
    wlComment.setText(BaseMessages.getString(PKG, "ActionMail.Comment.Label"));
    PropsUi.setLook(wlComment);
    FormData fdlComment = new FormData();
    fdlComment.left = new FormAttachment(0, 0);
    fdlComment.top = new FormAttachment(wSubject, 2 * margin);
    fdlComment.right = new FormAttachment(middle, -margin);
    wlComment.setLayoutData(fdlComment);

    wComment =
        new TextVar(
            variables,
            wMessageGroup,
            SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wComment);
    wComment.addModifyListener(lsMod);
    FormData fdComment = new FormData();
    fdComment.left = new FormAttachment(middle, 0);
    fdComment.top = new FormAttachment(wSubject, 2 * margin);
    fdComment.right = new FormAttachment(100, 0);
    fdComment.bottom = new FormAttachment(100, -margin);
    wComment.setLayoutData(fdComment);

    FormData fdMessageGroup = new FormData();
    fdMessageGroup.left = new FormAttachment(0, 0);
    fdMessageGroup.top = new FormAttachment(wMessageSettingsGroup, margin);
    fdMessageGroup.bottom = new FormAttachment(100, -margin);
    fdMessageGroup.right = new FormAttachment(100, -margin);
    wMessageGroup.setLayoutData(fdMessageGroup);

    // //////////////////////////////////////
    // / END OF MESSAGE GROUP
    // ///////////////////////////////////////

    FormData fdMessageComp = new FormData();
    fdMessageComp.left = new FormAttachment(0, 0);
    fdMessageComp.top = new FormAttachment(0, 0);
    fdMessageComp.right = new FormAttachment(100, 0);
    fdMessageComp.bottom = new FormAttachment(100, 0);
    wMessageComp.setLayoutData(wMessageComp);

    wMessageComp.layout();
    wMessageTab.setControl(wMessageComp);

    // ///////////////////////////////////////////////////////////
    // / END OF MESSAGE TAB
    // ///////////////////////////////////////////////////////////

    // ////////////////////////////////////
    // START OF ATTACHED FILES TAB ///
    // ///////////////////////////////////

    CTabItem wAttachedTab = new CTabItem(wTabFolder, SWT.NONE);
    wAttachedTab.setFont(GuiResource.getInstance().getFontDefault());
    wAttachedTab.setText(BaseMessages.getString(PKG, "ActionMail.Tab.AttachedFiles.Label"));

    FormLayout attachedLayout = new FormLayout();
    attachedLayout.marginWidth = 3;
    attachedLayout.marginHeight = 3;

    Composite wAttachedComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wAttachedComp);
    wAttachedComp.setLayout(attachedLayout);

    // ////////////////////////////////////
    // START OF Result File GROUP
    // ////////////////////////////////////

    Group wResultFilesGroup = new Group(wAttachedComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wResultFilesGroup);
    wResultFilesGroup.setText(
        BaseMessages.getString(PKG, "ActionMail.Group.AddPreviousFiles.Label"));

    FormLayout resultfilesgroupLayout = new FormLayout();
    resultfilesgroupLayout.marginWidth = 10;
    resultfilesgroupLayout.marginHeight = 10;
    wResultFilesGroup.setLayout(resultfilesgroupLayout);

    // Include Files?
    Label wlIncludeFiles = new Label(wResultFilesGroup, SWT.RIGHT);
    wlIncludeFiles.setText(BaseMessages.getString(PKG, "ActionMail.AttachFiles.Label"));
    PropsUi.setLook(wlIncludeFiles);
    FormData fdlIncludeFiles = new FormData();
    fdlIncludeFiles.left = new FormAttachment(0, 0);
    fdlIncludeFiles.top = new FormAttachment(0, margin);
    fdlIncludeFiles.right = new FormAttachment(middle, -margin);
    wlIncludeFiles.setLayoutData(fdlIncludeFiles);
    wIncludeFiles = new Button(wResultFilesGroup, SWT.CHECK);
    PropsUi.setLook(wIncludeFiles);
    FormData fdIncludeFiles = new FormData();
    fdIncludeFiles.left = new FormAttachment(middle, 0);
    fdIncludeFiles.top = new FormAttachment(wlIncludeFiles, 0, SWT.CENTER);
    fdIncludeFiles.right = new FormAttachment(100, 0);
    wIncludeFiles.setLayoutData(fdIncludeFiles);
    wIncludeFiles.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            action.setChanged();
            setFlags();
          }
        });

    // Include Files?
    wlTypes = new Label(wResultFilesGroup, SWT.RIGHT);
    wlTypes.setText(BaseMessages.getString(PKG, "ActionMail.SelectFileTypes.Label"));
    PropsUi.setLook(wlTypes);
    FormData fdlTypes = new FormData();
    fdlTypes.left = new FormAttachment(0, 0);
    fdlTypes.top = new FormAttachment(wlIncludeFiles, 2 * margin);
    fdlTypes.right = new FormAttachment(middle, -margin);
    wlTypes.setLayoutData(fdlTypes);
    wTypes = new List(wResultFilesGroup, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(wTypes);
    FormData fdTypes = new FormData();
    fdTypes.left = new FormAttachment(middle, 0);
    fdTypes.top = new FormAttachment(wlIncludeFiles, 2 * margin);
    fdTypes.bottom =
        new FormAttachment(wIncludeFiles, margin + (int) (150 * props.getZoomFactor()));
    fdTypes.right = new FormAttachment(100, 0);
    wTypes.setLayoutData(fdTypes);
    for (int i = 0; i < ResultFile.getAllTypeDesc().length; i++) {
      wTypes.add(ResultFile.getAllTypeDesc()[i]);
    }

    // Zip Files?
    wlZipFiles = new Label(wResultFilesGroup, SWT.RIGHT);
    wlZipFiles.setText(BaseMessages.getString(PKG, "ActionMail.ZipFiles.Label"));
    PropsUi.setLook(wlZipFiles);
    FormData fdlZipFiles = new FormData();
    fdlZipFiles.left = new FormAttachment(0, 0);
    fdlZipFiles.top = new FormAttachment(wTypes, margin);
    fdlZipFiles.right = new FormAttachment(middle, -margin);
    wlZipFiles.setLayoutData(fdlZipFiles);
    wZipFiles = new Button(wResultFilesGroup, SWT.CHECK);
    PropsUi.setLook(wZipFiles);
    FormData fdZipFiles = new FormData();
    fdZipFiles.left = new FormAttachment(middle, 0);
    fdZipFiles.top = new FormAttachment(wlZipFiles, 0, SWT.CENTER);
    fdZipFiles.right = new FormAttachment(100, 0);
    wZipFiles.setLayoutData(fdZipFiles);
    wZipFiles.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            action.setChanged();
            setFlags();
          }
        });

    // ZipFilename line
    wZipFilename =
        new LabelTextVar(
            variables,
            wResultFilesGroup,
            BaseMessages.getString(PKG, "ActionMail.ZipFilename.Label"),
            BaseMessages.getString(PKG, "ActionMail.ZipFilename.Tooltip"));
    wZipFilename.addModifyListener(lsMod);
    FormData fdZipFilename = new FormData();
    fdZipFilename.left = new FormAttachment(0, 0);
    fdZipFilename.top = new FormAttachment(wlZipFiles, 2 * margin);
    fdZipFilename.right = new FormAttachment(100, 0);
    wZipFilename.setLayoutData(fdZipFilename);

    FormData fdResultFilesGroup = new FormData();
    fdResultFilesGroup.left = new FormAttachment(0, margin);
    fdResultFilesGroup.top = new FormAttachment(0, margin);
    fdResultFilesGroup.right = new FormAttachment(100, -margin);
    wResultFilesGroup.setLayoutData(fdResultFilesGroup);

    // //////////////////////////////////////
    // / END OF RESULT FILES GROUP
    // ///////////////////////////////////////

    // ////////////////////////////////////
    // START OF Embedded Images GROUP
    // ////////////////////////////////////

    Group wEmbeddedImagesGroup = new Group(wAttachedComp, SWT.SHADOW_NONE);
    PropsUi.setLook(wEmbeddedImagesGroup);
    wEmbeddedImagesGroup.setText(
        BaseMessages.getString(PKG, "ActionMail.Group.EmbeddedImages.Label"));

    FormLayout attachedimagesgroupLayout = new FormLayout();
    attachedimagesgroupLayout.marginWidth = 10;
    attachedimagesgroupLayout.marginHeight = 10;
    wEmbeddedImagesGroup.setLayout(attachedimagesgroupLayout);

    // ImageFilename line
    wlImageFilename = new Label(wEmbeddedImagesGroup, SWT.RIGHT);
    wlImageFilename.setText(BaseMessages.getString(PKG, "ActionMail.ImageFilename.Label"));
    PropsUi.setLook(wlImageFilename);
    FormData fdlImageFilename = new FormData();
    fdlImageFilename.left = new FormAttachment(0, 0);
    fdlImageFilename.top = new FormAttachment(wResultFilesGroup, margin);
    fdlImageFilename.right = new FormAttachment(middle, -margin);
    wlImageFilename.setLayoutData(fdlImageFilename);

    wbImageFilename = new Button(wEmbeddedImagesGroup, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbImageFilename);
    wbImageFilename.setText(BaseMessages.getString(PKG, "ActionMail.BrowseFiles.Label"));
    FormData fdbImageFilename = new FormData();
    fdbImageFilename.right = new FormAttachment(100, 0);
    fdbImageFilename.top = new FormAttachment(wResultFilesGroup, margin);
    fdbImageFilename.right = new FormAttachment(100, -margin);
    wbImageFilename.setLayoutData(fdbImageFilename);

    wbaImageFilename = new Button(wEmbeddedImagesGroup, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbaImageFilename);
    wbaImageFilename.setText(BaseMessages.getString(PKG, "ActionMail.ImageFilenameAdd.Button"));
    FormData fdbaImageFilename = new FormData();
    fdbaImageFilename.right = new FormAttachment(wbImageFilename, -margin);
    fdbaImageFilename.top = new FormAttachment(wResultFilesGroup, margin);
    wbaImageFilename.setLayoutData(fdbaImageFilename);

    wImageFilename =
        new TextVar(variables, wEmbeddedImagesGroup, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wImageFilename);
    wImageFilename.addModifyListener(lsMod);
    FormData fdImageFilename = new FormData();
    fdImageFilename.left = new FormAttachment(middle, 0);
    fdImageFilename.top = new FormAttachment(wResultFilesGroup, margin);
    fdImageFilename.right = new FormAttachment(wbaImageFilename, -margin);
    wImageFilename.setLayoutData(fdImageFilename);

    // Whenever something changes, set the tooltip to the expanded version:
    wImageFilename.addModifyListener(
        e -> wImageFilename.setToolTipText(variables.resolve(wImageFilename.getText())));

    wbImageFilename.addListener(
        SWT.Selection,
        e -> {
          String filename =
              BaseDialog.presentFileDialog(
                  shell,
                  wImageFilename,
                  variables,
                  new String[] {"*png;*PNG", "*jpeg;*jpg;*JPEG;*JPG", "*gif;*GIF", "*"},
                  IMAGES_FILE_TYPES,
                  true);
          if (filename != null) {
            Random random = new Random();
            wContentID.setText(Long.toString(Math.abs(random.nextLong()), 32));
          }
        });

    // ContentID
    wlContentID = new Label(wEmbeddedImagesGroup, SWT.RIGHT);
    wlContentID.setText(BaseMessages.getString(PKG, "ActionMail.ContentID.Label"));
    PropsUi.setLook(wlContentID);
    FormData fdlContentID = new FormData();
    fdlContentID.left = new FormAttachment(0, 0);
    fdlContentID.top = new FormAttachment(wImageFilename, margin);
    fdlContentID.right = new FormAttachment(middle, -margin);
    wlContentID.setLayoutData(fdlContentID);
    wContentID =
        new TextVar(
            variables,
            wEmbeddedImagesGroup,
            SWT.SINGLE | SWT.LEFT | SWT.BORDER,
            BaseMessages.getString(PKG, "ActionMail.ContentID.Tooltip"));
    PropsUi.setLook(wContentID);
    wContentID.addModifyListener(lsMod);
    FormData fdContentID = new FormData();
    fdContentID.left = new FormAttachment(middle, 0);
    fdContentID.top = new FormAttachment(wImageFilename, margin);
    fdContentID.right = new FormAttachment(wbaImageFilename, -margin);
    wContentID.setLayoutData(fdContentID);

    wlFields = new Label(wEmbeddedImagesGroup, SWT.NONE);
    wlFields.setText(BaseMessages.getString(PKG, "ActionMail.Fields.Label"));
    PropsUi.setLook(wlFields);
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment(0, 0);
    fdlFields.right = new FormAttachment(middle, -margin);
    fdlFields.top = new FormAttachment(wContentID, margin);
    wlFields.setLayoutData(fdlFields);

    // Buttons to the right of the screen...
    wbdImageFilename = new Button(wEmbeddedImagesGroup, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbdImageFilename);
    wbdImageFilename.setText(BaseMessages.getString(PKG, "ActionMail.ImageFilenameDelete.Button"));
    wbdImageFilename.setToolTipText(
        BaseMessages.getString(PKG, "ActionMail.ImageFilenameDelete.Tooltip"));
    FormData fdbdImageFilename = new FormData();
    fdbdImageFilename.right = new FormAttachment(100, 0);
    fdbdImageFilename.top = new FormAttachment(wlFields, margin);
    wbdImageFilename.setLayoutData(fdbdImageFilename);

    wbeImageFilename = new Button(wEmbeddedImagesGroup, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbeImageFilename);
    wbeImageFilename.setText(BaseMessages.getString(PKG, "ActionMail.ImageFilenameEdit.Button"));
    wbeImageFilename.setToolTipText(
        BaseMessages.getString(PKG, "ActionMail.ImageFilenameEdit.Tooltip"));
    FormData fdbeImageFilename = new FormData();
    fdbeImageFilename.right = new FormAttachment(100, 0);
    fdbeImageFilename.left = new FormAttachment(wbdImageFilename, 0, SWT.LEFT);
    fdbeImageFilename.top = new FormAttachment(wbdImageFilename, margin);
    wbeImageFilename.setLayoutData(fdbeImageFilename);

    int rows =
        action.embeddedimages == null
            ? 1
            : (action.embeddedimages.size() == 0 ? 0 : action.embeddedimages.size());
    final int FieldsRows = rows;

    ColumnInfo[] colinf =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "ActionMail.Fields.Image.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "ActionMail.Fields.ContentID.Label"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
        };

    colinf[0].setUsingVariables(true);
    colinf[0].setToolTip(BaseMessages.getString(PKG, "ActionMail.Fields.Image.Tooltip"));
    colinf[1].setUsingVariables(true);
    colinf[1].setToolTip(BaseMessages.getString(PKG, "ActionMail.Fields.ContentID.Tooltip"));

    wFields =
        new TableView(
            variables,
            wEmbeddedImagesGroup,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            colinf,
            FieldsRows,
            lsMod,
            props);

    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.right = new FormAttachment(wbeImageFilename, -margin);
    fdFields.bottom = new FormAttachment(100, -margin);
    wFields.setLayoutData(fdFields);

    // Add the file to the list of files...
    SelectionAdapter selA =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            wFields.add(new String[] {wImageFilename.getText(), wContentID.getText()});
            wImageFilename.setText("");
            wContentID.setText("");
            wFields.removeEmptyRows();
            wFields.setRowNums();
            wFields.optWidth(true);
          }
        };
    wbaImageFilename.addSelectionListener(selA);
    wImageFilename.addSelectionListener(selA);

    // Delete files from the list of files...
    wbdImageFilename.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            int[] idx = wFields.getSelectionIndices();
            wFields.remove(idx);
            wFields.removeEmptyRows();
            wFields.setRowNums();
          }
        });

    // Edit the selected file & remove from the list...
    wbeImageFilename.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            int idx = wFields.getSelectionIndex();
            if (idx >= 0) {
              String[] string = wFields.getItem(idx);
              wImageFilename.setText(string[0]);
              wContentID.setText(string[1]);
              wFields.remove(idx);
            }
            wFields.removeEmptyRows();
            wFields.setRowNums();
          }
        });

    FormData fdEmbeddedImagesGroup = new FormData();
    fdEmbeddedImagesGroup.left = new FormAttachment(0, margin);
    fdEmbeddedImagesGroup.top = new FormAttachment(wResultFilesGroup, margin);
    fdEmbeddedImagesGroup.bottom = new FormAttachment(100, -margin);
    fdEmbeddedImagesGroup.right = new FormAttachment(100, -margin);
    wEmbeddedImagesGroup.setLayoutData(fdEmbeddedImagesGroup);

    // //////////////////////////////////////
    // / END OF Embedded Images GROUP
    // ///////////////////////////////////////

    FormData fdAttachedComp = new FormData();
    fdAttachedComp.left = new FormAttachment(0, 0);
    fdAttachedComp.top = new FormAttachment(0, 0);
    fdAttachedComp.right = new FormAttachment(100, 0);
    fdAttachedComp.bottom = new FormAttachment(100, 0);
    wAttachedComp.setLayoutData(wAttachedComp);

    wAttachedComp.layout();
    wAttachedTab.setControl(wAttachedComp);

    // ///////////////////////////////////////////////////////////
    // / END OF FILES TAB
    // ///////////////////////////////////////////////////////////

    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wName, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);

    getData();

    setEnabledEncoding();
    activeUsePriority();
    setFlags();
    onSetUseAuth();
    onSetSecureConnectiontype();

    wTabFolder.setSelection(0);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return action;
  }

  private void activeUsePriority() {
    wlPriority.setEnabled(wUsePriority.getSelection());
    wPriority.setEnabled(wUsePriority.getSelection());
    wlImportance.setEnabled(wUsePriority.getSelection());
    wImportance.setEnabled(wUsePriority.getSelection());
    wlSensitivity.setEnabled(wUsePriority.getSelection());
    wSensitivity.setEnabled(wUsePriority.getSelection());
  }

  private void setEnabledEncoding() {
    wEncoding.setEnabled(wUseHTML.getSelection());
    wlEncoding.setEnabled(wUseHTML.getSelection());
    wlImageFilename.setEnabled(wUseHTML.getSelection());
    wlImageFilename.setEnabled(wUseHTML.getSelection());
    wbImageFilename.setEnabled(wUseHTML.getSelection());
    wbaImageFilename.setEnabled(wUseHTML.getSelection());
    wImageFilename.setEnabled(wUseHTML.getSelection());
    wlContentID.setEnabled(wUseHTML.getSelection());
    wContentID.setEnabled(wUseHTML.getSelection());
    wbdImageFilename.setEnabled(wUseHTML.getSelection());
    wbeImageFilename.setEnabled(wUseHTML.getSelection());
    wlFields.setEnabled(wUseHTML.getSelection());
    wFields.setEnabled(wUseHTML.getSelection());
  }

  protected void onSetSecureConnectiontype() {
    wSecureConnectionType.setEnabled(wUseSecAuth.getSelection());
    wlSecureConnectionType.setEnabled(wUseSecAuth.getSelection());
    wTrustedHosts.setEnabled(wUseSecAuth.getSelection());
    wCheckServerIdentity.setEnabled(wUseSecAuth.getSelection());
  }

  protected void setFlags() {
    wlTypes.setEnabled(wIncludeFiles.getSelection());
    wTypes.setEnabled(wIncludeFiles.getSelection());
    wlZipFiles.setEnabled(wIncludeFiles.getSelection());
    wZipFiles.setEnabled(wIncludeFiles.getSelection());
    wZipFilename.setEnabled(wIncludeFiles.getSelection() && wZipFiles.getSelection());
  }

  protected void onSetUseAuth() {
    wAuthUser.setEnabled(wUseAuth.getSelection());
    wAuthPass.setEnabled(wUseAuth.getSelection());
    wlUseXOAUTH2.setEnabled(wUseAuth.getSelection());
    wUseXOAUTH2.setEnabled(wUseAuth.getSelection());
    if (!wUseAuth.getSelection()) {
      wUseXOAUTH2.setEnabled(false);
    }
  }

  public void getData() {
    wName.setText(Const.nullToEmpty(action.getName()));
    wDestination.setText(Const.nullToEmpty(action.getDestination()));
    wDestinationCc.setText(Const.nullToEmpty(action.getDestinationCc()));
    wDestinationBCc.setText(Const.nullToEmpty(action.getDestinationBCc()));
    wSelectionLine.setText(Const.nullToEmpty(action.getConnectionName()));
    wServer.setText(Const.nullToEmpty(action.getServer()));
    wPort.setText(Const.nullToEmpty(action.getPort()));
    wReply.setText(Const.nullToEmpty(action.getReplyAddress()));
    wReplyName.setText(Const.nullToEmpty(action.getReplyName()));
    wSubject.setText(Const.nullToEmpty(action.getSubject()));
    wPerson.setText(Const.nullToEmpty(action.getContactPerson()));
    wPhone.setText(Const.nullToEmpty(action.getContactPhone()));
    wComment.setText(Const.nullToEmpty(action.getComment()));
    wUseXOAUTH2.setSelection(action.isUsexoauth2());
    wAddDate.setSelection(action.isIncludeDate());
    wIncludeFiles.setSelection(action.isIncludingFiles());

    if (action.getFileTypes() != null && !action.getFileTypes().isEmpty()) {
      java.util.List<String> types = action.getFileTypes();
      String[] typenames = new String[types.size()];
      for (int i = 0; i < types.size(); i++) {
        int resultInt = ResultFile.getType(types.get(i));
        typenames[i] = ResultFile.getTypeDesc(resultInt);
      }
      wTypes.setSelection(typenames);
    }

    wZipFiles.setSelection(action.isZipFiles());
    wZipFilename.setText(Const.nullToEmpty(action.getZipFilename()));

    wUseAuth.setSelection(action.isUsingAuthentication());
    wUseSecAuth.setSelection(action.isUsingSecureAuthentication());
    wCheckServerIdentity.setSelection(action.isCheckServerIdentity());
    wTrustedHosts.setText(Const.nullToEmpty(action.getTrustedHosts()));
    wAuthUser.setText(Const.nullToEmpty(action.getAuthenticationUser()));
    wAuthPass.setText(Const.nullToEmpty(action.getAuthenticationPassword()));

    wOnlyComment.setSelection(action.isOnlySendComment());

    wUseHTML.setSelection(action.isUseHTML());

    if (action.getEncoding() != null) {
      wEncoding.setText("" + action.getEncoding());
    } else {

      wEncoding.setText("UTF-8");
    }

    // Secure connection type
    if (action.getSecureConnectionType() != null) {
      wSecureConnectionType.setText(action.getSecureConnectionType());
    } else {
      wSecureConnectionType.setText("SSL");
    }
    wUsePriority.setSelection(action.isUsePriority());

    // Priority

    if (action.getPriority() != null) {
      if (action.getPriority().equals("low")) {
        wPriority.select(0); // Low
      } else if (action.getPriority().equals(CONST_NORMAL)) {
        wPriority.select(1); // Normal

      } else {
        wPriority.select(2); // Default High
      }
    } else {
      wPriority.select(3); // Default High
    }

    // Importance
    if (action.getImportance() != null) {
      if (action.getImportance().equals("low")) {
        wImportance.select(0); // Low
      } else if (action.getImportance().equals(CONST_NORMAL)) {
        wImportance.select(1); // Normal

      } else {
        wImportance.select(2); // Default High
      }
    } else {
      wImportance.select(3); // Default High
    }

    if (action.getReplyToAddresses() != null) {
      wReplyToAddress.setText(action.getReplyToAddresses());
    }

    // Sensitivity
    if (action.getSensitivity() != null) {
      if (action.getSensitivity().equals("personal")) {
        wSensitivity.select(1);
      } else if (action.getSensitivity().equals("private")) {
        wSensitivity.select(2);
      } else if (action.getSensitivity().equals("company-confidential")) {
        wSensitivity.select(3);
      } else {
        wSensitivity.select(0);
      }
    } else {
      wSensitivity.select(0); // Default normal
    }

    if (action.embeddedimages != null) {
      for (int i = 0; i < action.embeddedimages.size(); i++) {
        TableItem ti = wFields.table.getItem(i);
        if (action.embeddedimages.get(i) != null) {
          ti.setText(1, action.embeddedimages.get(i).getEmbeddedimage());
        }
        if (action.getEmbeddedimages().get(i).getContentId() != null) {
          ti.setText(2, action.getEmbeddedimages().get(i).getContentId());
        }
      }
      wFields.setRowNums();
      wFields.optWidth(true);
    }

    wName.selectAll();
    wName.setFocus();
  }

  private void cancel() {
    action.setChanged(backupChanged);
    action.setIncludeDate(backupDate);

    action = null;
    dispose();
  }

  private void ok() {
    if (Utils.isEmpty(wName.getText())) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setText(BaseMessages.getString(PKG, "System.TransformActionNameMissing.Title"));
      mb.setMessage(BaseMessages.getString(PKG, "System.ActionNameMissing.Msg"));
      mb.open();
      return;
    }
    action.setName(wName.getText());
    action.setDestination(wDestination.getText());
    action.setDestinationCc(wDestinationCc.getText());
    action.setDestinationBCc(wDestinationBCc.getText());
    action.setConnectionName(wSelectionLine.getText());
    action.setServer(wServer.getText());
    action.setPort(wPort.getText());
    action.setReplyAddress(wReply.getText());
    action.setReplyName(wReplyName.getText());
    action.setSubject(wSubject.getText());
    action.setContactPerson(wPerson.getText());
    action.setContactPhone(wPhone.getText());
    action.setComment(wComment.getText());
    action.setUsexoauth2(wUseXOAUTH2.getSelection());
    action.setIncludeDate(wAddDate.getSelection());
    action.setIncludingFiles(wIncludeFiles.getSelection());

    java.util.List<String> ft = new ArrayList<>();
    String[] fileTypes = wTypes.getSelection();
    for (int i = 0; i < fileTypes.length; i++) {
      String[] allFileTypes = ResultFile.getAllTypeDesc();
      for (int j = 0; j < allFileTypes.length; j++) {
        if (allFileTypes[j].equals(fileTypes[i])) {
          String typeCode = ResultFile.getTypeCode(ResultFile.getType(allFileTypes[j]));
          ft.add(typeCode);
        }
      }
    }
    action.setFileTypes(ft);

    action.setZipFilename(wZipFilename.getText());
    action.setZipFiles(wZipFiles.getSelection());
    action.setAuthenticationUser(wAuthUser.getText());
    action.setAuthenticationPassword(wAuthPass.getText());
    action.setUsingAuthentication(wUseAuth.getSelection());
    action.setUsingSecureAuthentication(wUseSecAuth.getSelection());
    action.setOnlySendComment(wOnlyComment.getSelection());
    action.setUseHTML(wUseHTML.getSelection());
    action.setUsePriority(wUsePriority.getSelection());

    action.setTrustedHosts(wTrustedHosts.getText());
    action.setCheckServerIdentity(wCheckServerIdentity.getSelection());

    action.setEncoding(wEncoding.getText());
    action.setPriority(wPriority.getText());

    // Priority
    if (wPriority.getSelectionIndex() == 0) {
      action.setPriority("low");
    } else if (wPriority.getSelectionIndex() == 1) {
      action.setPriority(CONST_NORMAL);
    } else {
      action.setPriority("high");
    }

    // Importance
    if (wImportance.getSelectionIndex() == 0) {
      action.setImportance("low");
    } else if (wImportance.getSelectionIndex() == 1) {
      action.setImportance(CONST_NORMAL);
    } else {
      action.setImportance("high");
    }

    // Sensitivity
    if (wSensitivity.getSelectionIndex() == 1) {
      action.setSensitivity("personal");
    } else if (wSensitivity.getSelectionIndex() == 2) {
      action.setSensitivity("private");
    } else if (wSensitivity.getSelectionIndex() == 3) {
      action.setSensitivity("company-confidential");
    } else {
      action.setSensitivity(CONST_NORMAL); // default is normal
    }

    // Secure Connection type
    action.setSecureConnectionType(wSecureConnectionType.getText());

    action.setReplyToAddresses(wReplyToAddress.getText());

    int nrItems = wFields.nrNonEmpty();
    int nr = 0;
    for (int i = 0; i < nrItems; i++) {
      String arg = wFields.getNonEmpty(i).getText(1);
      if (arg != null && arg.length() != 0) {
        nr++;
      }
    }
    java.util.List<MailEmbeddedImageField> images = new ArrayList<>();
    nr = 0;
    for (int i = 0; i < nrItems; i++) {
      String arg = wFields.getNonEmpty(i).getText(1);
      String wild = wFields.getNonEmpty(i).getText(2);
      if (arg != null && arg.length() != 0) {
        MailEmbeddedImageField image = new MailEmbeddedImageField();
        image.setEmbeddedimage(arg);
        image.setContentId(wild);
        images.add(image);
      }
    }
    action.setEmbeddedimages(images);

    dispose();
  }

  private void setEncodings() {
    // Encoding of the text file:
    if (!gotEncodings) {
      gotEncodings = true;

      wEncoding.removeAll();
      java.util.List<Charset> values = new ArrayList<>(Charset.availableCharsets().values());
      for (Charset charSet : values) {
        wEncoding.add(charSet.displayName());
      }

      // Now select the default!
      String defEncoding = Const.getEnvironmentVariable("file.encoding", "UTF-8");
      int idx = Const.indexOfString(defEncoding, wEncoding.getItems());
      if (idx >= 0) {
        wEncoding.select(idx);
      }
    }
  }
}
