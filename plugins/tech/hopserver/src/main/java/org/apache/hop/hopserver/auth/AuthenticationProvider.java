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
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.ui.hopgui.HopGui;

@GuiPlugin
public class AuthenticationProvider {

  public static final String ID_MAIN_MENU_AUTHENTICATION = "toolbar-90010-authentication";
  private String authToken;
  private static String authority;
  private static Set<String> scope;
  private static String clientId;
  private static UsernamePassword usernamePassword;

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
      System.out.println("==acquireTokenSilently call succeeded");
    } catch (Exception ex) {
      if (ex.getCause() instanceof MsalException) {
        System.out.println("==acquireTokenSilently call failed: " + ex.getCause());
        UserNamePasswordParameters parameters =
            UserNamePasswordParameters.builder(scope, username, password.toCharArray()).build();
        // Try to acquire a token via username/password. If successful, you should see
        // the token and account information printed out to console
        result = pca.acquireToken(parameters).join();
        System.out.println("==username/password flow succeeded");
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
  private static void setUpSampleData() {
    // Load properties file and set properties used throughout the sample
    authority = "https://login.microsoftonline.com/organizations/";
    scope = Collections.singleton("user.read");
    clientId = "b8de2f43-662f-477a-b0a1-3eb35c6e3481";
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
    System.out.println("Button pressed");
    setUpSampleData();

    HopGui hopGui = HopGui.getInstance();

    try {
      AuthenticationDialog dialog =
          new AuthenticationDialog(hopGui.getActiveShell(), hopGui.getVariables());
      setUsernamePassword(dialog.open());

    } catch (Exception ex) {
      ex.printStackTrace();
    }

    try {
      PublicClientApplication pca =
          PublicClientApplication.builder(clientId).authority(authority).build();

      // Get list of accounts from the application's token cache, and search them for the configured
      // username
      // getAccounts() will be empty on this first call, as accounts are added to the cache when
      // acquiring a token
      Set<IAccount> accountsInCache = pca.getAccounts().join();
      IAccount account = getAccountByUsername(accountsInCache, getUsernamePassword().getUsername());

      // Attempt to acquire token when user's account is not in the application's token cache
      IAuthenticationResult result =
          acquireTokenUsernamePassword(
              pca,
              scope,
              account,
              getUsernamePassword().getUsername(),
              getUsernamePassword().getPassword());
      System.out.println("Account username: " + result.account().username());
      System.out.println("Access token:     " + result.accessToken());
      System.out.println("Id token:         " + result.idToken());
      System.out.println();

      accountsInCache = pca.getAccounts().join();
      account = getAccountByUsername(accountsInCache, getUsernamePassword().getUsername());

      // Attempt to acquire token again, now that the user's account and a token are in the
      // application's token cache
      result =
          acquireTokenUsernamePassword(
              pca,
              scope,
              account,
              getUsernamePassword().getUsername(),
              getUsernamePassword().getPassword());
      System.out.println("Account username: " + result.account().username());
      System.out.println("Access token:     " + result.accessToken());
      System.out.println("Id token:         " + result.idToken());

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Helper function to return an account from a given set of accounts based on the given username,
   * or return null if no accounts in the set match
   */
  private static IAccount getAccountByUsername(Set<IAccount> accounts, String username) {
    if (accounts.isEmpty()) {
      System.out.println("==No accounts in cache");
    } else {
      System.out.println("==Accounts in cache: " + accounts.size());
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

  public static UsernamePassword getUsernamePassword() {
    return usernamePassword;
  }

  public static void setUsernamePassword(UsernamePassword usernamePassword) {
    AuthenticationProvider.usernamePassword = usernamePassword;
  }
}
