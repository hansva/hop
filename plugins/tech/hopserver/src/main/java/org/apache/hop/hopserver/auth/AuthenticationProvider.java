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

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.MsalException;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;
import java.util.Collections;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;

@GuiPlugin
public class AuthenticationProvider {

  public static final String ID_MAIN_MENU_AUTHENTICATION = "toolbar-90010-authentication";
  private String authToken;
  private static String authority;
  private static Set<String> scope;
  private static String clientId;
  private static UsernamePasswordReturn usernamePasswordReturn;
  private static IAuthenticationResult authenticationResult;
  public static final String AZURE_OAUTH_CLIENT_ID = "AZURE_OAUTH_CLIENT_ID";

  private static IAuthenticationResult acquireTokenUsernamePassword(
      PublicClientApplication pca,
      Set<String> scope,
      IAccount account,
      String username,
      String password)
      throws Exception {
    IAuthenticationResult result;
    try {
      SilentParameters silentParameters = SilentParameters.builder(scope).account(account).build();
      // Try to acquire token silently. This will fail on the first acquireTokenUsernamePassword()
      // call
      // because the token cache does not have any data for the user you are trying to acquire a
      // token for
      result = pca.acquireTokenSilently(silentParameters).join();
    } catch (Exception ex) {
      if (ex.getCause() instanceof MsalException) {
        UserNamePasswordParameters parameters =
            UserNamePasswordParameters.builder(scope, username, password.toCharArray()).build();
        // Try to acquire a token via username/password. If successful, you should see
        // the token and account information printed out to console
        result = pca.acquireToken(parameters).join();
      } else {
        // Handle other exceptions accordingly
        throw ex;
      }
    }
    return result;
  }

  /**
   * Helper function unique to this sample setting. In a real application these wouldn't be so
   * hardcoded, for example values such as username/password would come from the user, and different
   * users may require different scopes
   */
  private static void setUpDefaultValues() {
    authority = "https://login.microsoftonline.com/organizations/";
    scope = Collections.singleton("openid");
    clientId = System.getenv(AZURE_OAUTH_CLIENT_ID);
  }

  public String getAuthToken() {
    return authToken;
  }

  public void setAuthToken(String authToken) {
    this.authToken = authToken;
  }

  @GuiToolbarElement(
      root = HopGui.ID_MAIN_TOOLBAR,
      id = ID_MAIN_MENU_AUTHENTICATION,
      toolTip = "i18n::HopGui.Toolbar.Project.Edit.Tooltip",
      image = "ui/images/server.svg")
  public void editSelectedProject() {
    setUpDefaultValues();
    HopGui hopGui = HopGui.getInstance();

    try {
      AuthenticationDialog dialog =
          new AuthenticationDialog(hopGui.getActiveShell(), hopGui.getVariables());
      setUsernamePassword(dialog.open());

      if (usernamePasswordReturn.getButtonPressed().equals("cancel")) {
        return;
      }

      if (usernamePasswordReturn.getUsername().equals("")
          || usernamePasswordReturn.getPassword().equals("")) {
        MessageBox mb = new MessageBox(hopGui.getActiveShell(), SWT.OK | SWT.ICON_ERROR);
        mb.setText("Missing username/password");
        mb.setMessage("Username or password not filled in");
        mb.open();
        return;
      }

      PublicClientApplication pca =
          PublicClientApplication.builder(clientId).authority(authority).build();

      // Attempt to acquire token when user's account is not in the application's token cache
      authenticationResult =
          acquireTokenUsernamePassword(
              pca,
              scope,
              null,
              getUsernamePassword().getUsername(),
              getUsernamePassword().getPassword());
      MessageBox mb = new MessageBox(hopGui.getActiveShell(), SWT.OK | SWT.ICON_INFORMATION);
      mb.setText("Authenticated");
      mb.setMessage("Authenticated");
      mb.open();

      // Add token
      setAuthToken(authenticationResult.accessToken());

      // Add timer to Automatically refresh tokens
      // Periodic logging
      final Timer timer = new Timer();
      TimerTask timerTask =
          new TimerTask() {
            @Override
            public void run() {
              try {
                SilentParameters silentParameters =
                    SilentParameters.builder(scope).account(authenticationResult.account()).build();
                authenticationResult = pca.acquireTokenSilently(silentParameters).join();
              } catch (Exception e) {

              }
            }
          };
      timer.schedule(timerTask, 3600 * 1000L, 3600 * 1000L);

    } catch (Exception e) {
      LogChannel.UI.logError("Error getting account details", e);
      MessageBox mb = new MessageBox(hopGui.getActiveShell(), SWT.OK | SWT.ICON_ERROR);
      mb.setText("Error Authenticating");
      mb.setMessage("Could not Authenticate \n Reason: " + e.getMessage());
      mb.open();
    }
  }

  /**
   * Helper function to return an account from a given set of accounts based on the given username,
   * or return null if no accounts in the set match
   */
  private static IAccount getAccountByUsername(Set<IAccount> accounts, String username) {
    if (!accounts.isEmpty()) {
      for (IAccount account : accounts) {
        if (account.username().equals(username)) {
          return account;
        }
      }
    }
    return null;
  }

  public static String getAuthority() {
    return authority;
  }

  public static void setAuthority(String authority) {
    AuthenticationProvider.authority = authority;
  }

  public static Set<String> getScope() {
    return scope;
  }

  public static void setScope(Set<String> scope) {
    AuthenticationProvider.scope = scope;
  }

  public static String getClientId() {
    return clientId;
  }

  public static void setClientId(String clientId) {
    AuthenticationProvider.clientId = clientId;
  }

  public static UsernamePasswordReturn getUsernamePassword() {
    return usernamePasswordReturn;
  }

  public static void setUsernamePassword(UsernamePasswordReturn usernamePasswordReturn) {
    AuthenticationProvider.usernamePasswordReturn = usernamePasswordReturn;
  }

  public static IAuthenticationResult getAuthenticationResult() {
    return authenticationResult;
  }

  public static void setAuthenticationResult(IAuthenticationResult authenticationResult) {
    AuthenticationProvider.authenticationResult = authenticationResult;
  }

  public static UsernamePasswordReturn getUsernamePasswordReturn() {
    return usernamePasswordReturn;
  }

  public static void setUsernamePasswordReturn(UsernamePasswordReturn usernamePasswordReturn) {
    AuthenticationProvider.usernamePasswordReturn = usernamePasswordReturn;
  }
}
