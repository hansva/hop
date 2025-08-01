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

package org.apache.hop.pipeline.transforms.rest;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.util.HttpClientManager;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.rest.RestConnection;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.uri.UriComponent;
import org.json.simple.JSONObject;

public class Rest extends BaseTransform<RestMeta, RestData> {
  private static final Class<?> PKG = RestMeta.class;
  public static final String CONST_REST_EXCEPTION_ERROR_FINDING_FIELD =
      "Rest.Exception.ErrorFindingField";
  private String baseUrl = "";
  private RestConnection connection;

  public Rest(
      TransformMeta transformMeta,
      RestMeta meta,
      RestData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  /* for unit test*/
  MultivaluedHashMap createMultivalueMap(String paramName, String paramValue) {
    MultivaluedHashMap queryParams = new MultivaluedHashMap();
    queryParams.add(paramName, UriComponent.encode(paramValue, UriComponent.Type.QUERY_PARAM));
    return queryParams;
  }

  /**
   * Perform the rest call Ignore Sonar SSL warning, SSL can be disabled by a user action
   *
   * @param rowData
   * @return
   * @throws HopException
   */
  @SuppressWarnings("java:S5527")
  protected Object[] callRest(Object[] rowData) throws HopException {

    // get dynamic url ?
    if (meta.isUrlInField()) {
      if (!Utils.isEmpty(data.connectionName)) {
        data.realUrl = baseUrl + data.inputRowMeta.getString(rowData, data.indexOfUrlField);
      } else {
        data.realUrl = data.inputRowMeta.getString(rowData, data.indexOfUrlField);
      }
    }

    // get dynamic method?
    if (meta.isDynamicMethod()) {
      data.method = data.inputRowMeta.getString(rowData, data.indexOfMethod);
      if (Utils.isEmpty(data.method)) {
        throw new HopException(BaseMessages.getString(PKG, "Rest.Error.MethodMissing"));
      }
    }
    WebTarget webResource = null;
    Client client = null;
    Object[] newRow = null;
    if (rowData != null) {
      newRow = rowData.clone();
    }
    try {
      if (isDetailed()) {
        logDetailed(BaseMessages.getString(PKG, "Rest.Log.ConnectingToURL", data.realUrl));
      }
      ClientBuilder clientBuilder = ClientBuilder.newBuilder();
      clientBuilder
          .withConfig(data.config)
          .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);

      if (meta.isIgnoreSsl() || !Utils.isEmpty(data.trustStoreFile)) {
        clientBuilder.hostnameVerifier((s1, s2) -> true);
        clientBuilder.sslContext(data.sslContext);
      }

      client = clientBuilder.build();
      if (data.basicAuthentication != null) {
        client.register(data.basicAuthentication);
      }
      // create a WebResource object, which encapsulates a web resource for the client
      webResource = client.target(data.realUrl);

      // used for calculating the responseTime
      long startTime = System.currentTimeMillis();

      if (data.useMatrixParams) {
        // Add matrix parameters
        UriBuilder builder = webResource.getUriBuilder();
        for (int i = 0; i < data.nrMatrixParams; i++) {
          String value = data.inputRowMeta.getString(rowData, data.indexOfMatrixParamFields[i]);
          if (isDebug()) {
            logDebug(
                BaseMessages.getString(
                    PKG, "Rest.Log.matrixParameterValue", data.matrixParamNames[i], value));
          }
          builder =
              builder.matrixParam(
                  data.matrixParamNames[i],
                  UriComponent.encode(value, UriComponent.Type.QUERY_PARAM));
        }
        webResource = client.target(builder.build());
      }

      if (data.useParams) {
        // Add query parameters
        for (int i = 0; i < data.nrParams; i++) {
          String value = data.inputRowMeta.getString(rowData, data.indexOfParamFields[i]);
          if (isDebug()) {
            logDebug(
                BaseMessages.getString(
                    PKG, "Rest.Log.queryParameterValue", data.paramNames[i], value));
          }
          webResource = webResource.queryParam(data.paramNames[i], value);
        }
      }
      if (isDebug()) {
        logDebug(BaseMessages.getString(PKG, "Rest.Log.ConnectingToURL", webResource.getUri()));
      }
      Invocation.Builder invocationBuilder = webResource.request();

      // set the Authentication/Authorization header from the connection first, if available.
      // this transform's headers will override this value if available.
      if (connection != null) {
        if (!StringUtils.isEmpty(resolve(connection.getAuthorizationHeaderName())))
          if (!Utils.isEmpty(resolve(connection.getAuthorizationHeaderName()))) {
            if (!StringUtils.isEmpty(resolve(connection.getAuthorizationPrefix()))) {
              invocationBuilder.header(
                  resolve(connection.getAuthorizationHeaderName()),
                  resolve(connection.getAuthorizationPrefix())
                      + " "
                      + resolve(connection.getAuthorizationHeaderValue()));
            } else {
              invocationBuilder.header(
                  resolve(connection.getAuthorizationHeaderName()),
                  resolve(connection.getAuthorizationHeaderValue()));
            }
          }
      }

      String contentType = null; // media type override, if not null
      if (data.useHeaders) {
        // Add headers
        for (int i = 0; i < data.nrheader; i++) {
          String value = data.inputRowMeta.getString(rowData, data.indexOfHeaderFields[i]);

          // unsure if an already set header will be returned to builder
          invocationBuilder.header(data.headerNames[i], value);
          if ("Content-Type".equals(data.headerNames[i])) {
            contentType = value;
          }
          if (isDebug()) {
            logDebug(
                BaseMessages.getString(PKG, "Rest.Log.HeaderValue", data.headerNames[i], value));
          }
        }
      }

      Response response = null;
      String entityString = null;
      if (data.useBody) {
        // Set Http request entity
        entityString = Const.NVL(data.inputRowMeta.getString(rowData, data.indexOfBodyField), null);
        if (isDebug()) {
          logDebug(BaseMessages.getString(PKG, "Rest.Log.BodyValue", entityString));
        }
      }
      try {
        if (data.method.equals(RestMeta.HTTP_METHOD_GET)) {
          response = invocationBuilder.get(Response.class);
        } else if (data.method.equals(RestMeta.HTTP_METHOD_POST)) {
          if (null != contentType) {
            response = invocationBuilder.post(Entity.entity(entityString, contentType));
          } else {
            response = invocationBuilder.post(Entity.entity(entityString, data.mediaType));
          }
        } else if (data.method.equals(RestMeta.HTTP_METHOD_PUT)) {
          if (null != contentType) {
            response = invocationBuilder.put(Entity.entity(entityString, contentType));
          } else {
            response = invocationBuilder.put(Entity.entity(entityString, data.mediaType));
          }
        } else if (data.method.equals(RestMeta.HTTP_METHOD_DELETE)) {
          response = invocationBuilder.delete();
        } else if (data.method.equals(RestMeta.HTTP_METHOD_HEAD)) {
          response = invocationBuilder.head();
        } else if (data.method.equals(RestMeta.HTTP_METHOD_OPTIONS)) {
          response = invocationBuilder.options();
        } else if (data.method.equals(RestMeta.HTTP_METHOD_PATCH)) {
          if (null != contentType) {
            response =
                invocationBuilder.method(
                    RestMeta.HTTP_METHOD_PATCH, Entity.entity(entityString, contentType));
          } else {
            response =
                invocationBuilder.method(
                    RestMeta.HTTP_METHOD_PATCH, Entity.entity(entityString, data.mediaType));
          }
        } else {
          throw new HopException(
              BaseMessages.getString(PKG, "Rest.Error.UnknownMethod", data.method));
        }
      } catch (Exception e) {
        throw new HopException("Request could not be processed", e);
      }
      // Get response time
      long responseTime = System.currentTimeMillis() - startTime;
      if (isDetailed()) {
        logDetailed(
            BaseMessages.getString(
                PKG, "Rest.Log.ResponseTime", String.valueOf(responseTime), data.realUrl));
      }

      // Get status
      int status = response.getStatus();
      // Display status code
      if (isDebug()) {
        logDebug(BaseMessages.getString(PKG, "Rest.Log.ResponseCode", "" + status));
      }

      // Get Response
      String body;
      String headerString = null;
      try {
        body = response.readEntity(String.class);
      } catch (Exception ex) {
        body = "";
      }
      // get Header
      MultivaluedMap<String, Object> headers = searchForHeaders(response);
      JSONObject json = new JSONObject();
      for (java.util.Map.Entry<String, List<Object>> entry : headers.entrySet()) {
        String name = entry.getKey();
        List<Object> value = entry.getValue();
        if (value.size() > 1) {
          json.put(name, value);
        } else {
          json.put(name, value.get(0));
        }
      }
      headerString = json.toJSONString();
      // for output
      int returnFieldsOffset = data.inputRowMeta.size();
      // add response to output
      if (!Utils.isEmpty(data.resultFieldName)) {
        newRow = RowDataUtil.addValueData(newRow, returnFieldsOffset, body);
        returnFieldsOffset++;
      }

      // add status to output
      if (!Utils.isEmpty(data.resultCodeFieldName)) {
        newRow = RowDataUtil.addValueData(newRow, returnFieldsOffset, Long.valueOf(status));
        returnFieldsOffset++;
      }

      // add response time to output
      if (!Utils.isEmpty(data.resultResponseFieldName)) {
        newRow = RowDataUtil.addValueData(newRow, returnFieldsOffset, responseTime);
        returnFieldsOffset++;
      }
      // add response header to output
      if (!Utils.isEmpty(data.resultHeaderFieldName)) {
        newRow = RowDataUtil.addValueData(newRow, returnFieldsOffset, headerString);
      }
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "Rest.Error.CanNotReadURL", data.realUrl), e);
    } finally {
      if (webResource != null) {
        webResource = null;
      }
      if (client != null) {
        client.close();
      }
    }
    return newRow;
  }

  private void setConfig() throws HopException {
    if (data.config == null) {
      // Use ApacheHttpClient for supporting proxy authentication.
      data.config = new ClientConfig();
      data.config.connectorProvider(new ApacheConnectorProvider());
      data.config.property(
          ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);

      data.config.property(ClientProperties.READ_TIMEOUT, data.realReadTimeout);
      data.config.property(ClientProperties.CONNECT_TIMEOUT, data.realConnectionTimeout);

      // PROXY CONFIGURATION
      if (!Utils.isEmpty(data.realProxyHost)) {
        data.config.property(
            ClientProperties.PROXY_URI, "http://" + data.realProxyHost + ":" + data.realProxyPort);
      }
      // HTTP BASIC AUTHENTICATION
      if (!Utils.isEmpty(data.realHttpLogin) || !Utils.isEmpty(data.realHttpPassword)) {
        data.basicAuthentication =
            HttpAuthenticationFeature.basicBuilder()
                .credentials(data.realHttpLogin, data.realHttpPassword)
                .build();
      }
      // SSL TRUST STORE CONFIGURATION
      if (!Utils.isEmpty(data.trustStoreFile) && !meta.isIgnoreSsl()) {
        setTrustStoreFile();
      }
      if (meta.isIgnoreSsl()) {
        setTrustAll();
      }
    }
  }

  private void setTrustAll() throws HopException {
    try {
      SSLContext ctx = HttpClientManager.getTrustAllSslContext();

      data.sslContext = ctx;
    } catch (NoSuchAlgorithmException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.NoSuchAlgorithm"), e);
    } catch (KeyManagementException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.KeyManagementException"), e);
    }
  }

  private void setTrustStoreFile() throws HopException {
    try (FileInputStream trustFileStream = new FileInputStream(data.trustStoreFile)) {

      SSLContext ctx =
          HttpClientManager.getSslContextWithTrustStoreFile(
              trustFileStream, data.trustStorePassword);

      data.sslContext = ctx;
    } catch (NoSuchAlgorithmException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.NoSuchAlgorithm"), e);
    } catch (KeyStoreException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.KeyStoreException"), e);
    } catch (CertificateException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.CertificateException"), e);
    } catch (FileNotFoundException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "Rest.Error.FileNotFound", data.trustStoreFile), e);
    } catch (IOException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.IOException"), e);
    } catch (KeyManagementException e) {
      throw new HopException(BaseMessages.getString(PKG, "Rest.Error.KeyManagementException"), e);
    }
  }

  protected MultivaluedMap<String, Object> searchForHeaders(Response response) {
    return response.getHeaders();
  }

  @Override
  public boolean processRow() throws HopException {

    Object[] r = getRow(); // Get row from input rowset & set row busy!

    if (r == null) {
      // no more input to be expected...
      setOutputDone();
      return false;
    }
    if (first) {
      first = false;
      data.inputRowMeta = getInputRowMeta();
      data.outputRowMeta = data.inputRowMeta.clone();
      meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);

      // Let's set URL
      if (meta.isUrlInField()) {
        if (Utils.isEmpty(meta.getUrlField())) {
          logError(BaseMessages.getString(PKG, "Rest.Log.NoField"));
          throw new HopException(BaseMessages.getString(PKG, "Rest.Log.NoField"));
        }
        // cache the position of the field
        if (data.indexOfUrlField < 0) {
          String realUrlfieldName = resolve(meta.getUrlField());
          data.indexOfUrlField = data.inputRowMeta.indexOfValue(realUrlfieldName);
          if (data.indexOfUrlField < 0) {
            // The field is unreachable !
            throw new HopException(
                BaseMessages.getString(
                    PKG, CONST_REST_EXCEPTION_ERROR_FINDING_FIELD, realUrlfieldName));
          }
        }
      } else {
        // Static URL
        if (!Utils.isEmpty(data.connectionName)) {
          data.realUrl = baseUrl + resolve(meta.getUrl());
        } else {
          data.realUrl = resolve(meta.getUrl());
        }
      }
      // Check Method
      if (meta.isDynamicMethod()) {
        String field = resolve(meta.getMethodFieldName());
        if (Utils.isEmpty(field)) {
          throw new HopException(BaseMessages.getString(PKG, "Rest.Exception.MethodFieldMissing"));
        }
        data.indexOfMethod = data.inputRowMeta.indexOfValue(field);
        if (data.indexOfMethod < 0) {
          // The field is unreachable !
          throw new HopException(
              BaseMessages.getString(PKG, CONST_REST_EXCEPTION_ERROR_FINDING_FIELD, field));
        }
      }
      // set Headers
      if (!Utils.isEmpty(meta.getHeaderFields())) {
        data.nrheader = meta.getHeaderFields().size();
        data.indexOfHeaderFields = new int[meta.getHeaderFields().size()];
        data.headerNames = new String[meta.getHeaderFields().size()];
        for (int i = 0; i < meta.getHeaderFields().size(); i++) {
          // split into body / header
          data.headerNames[i] = resolve(meta.getHeaderFields().get(i).getName());
          String field = resolve(meta.getHeaderFields().get(i).getHeaderField());
          if (Utils.isEmpty(field)) {
            throw new HopException(BaseMessages.getString(PKG, "Rest.Exception.HeaderFieldEmpty"));
          }
          data.indexOfHeaderFields[i] = data.inputRowMeta.indexOfValue(field);
          if (data.indexOfHeaderFields[i] < 0) {
            throw new HopException(
                BaseMessages.getString(PKG, CONST_REST_EXCEPTION_ERROR_FINDING_FIELD, field));
          }
        }
        data.useHeaders = true;
      }
      if (RestMeta.isActiveParameters(meta.getMethod())) {
        // Parameters
        int nrparams = meta.getParameterFields() == null ? 0 : meta.getParameterFields().size();
        if (nrparams > 0) {
          data.nrParams = nrparams;
          data.paramNames = new String[nrparams];
          data.indexOfParamFields = new int[nrparams];
          for (int i = 0; i < nrparams; i++) {
            data.paramNames[i] = resolve(meta.getParameterFields().get(i).getName());
            String field = resolve(meta.getParameterFields().get(i).getHeaderField());
            if (Utils.isEmpty(field)) {
              throw new HopException(BaseMessages.getString(PKG, "Rest.Exception.ParamFieldEmpty"));
            }
            data.indexOfParamFields[i] = data.inputRowMeta.indexOfValue(field);
            if (data.indexOfParamFields[i] < 0) {
              throw new HopException(
                  BaseMessages.getString(PKG, CONST_REST_EXCEPTION_ERROR_FINDING_FIELD, field));
            }
          }
          data.useParams = true;
        }
        int nrmatrixparams =
            meta.getMatrixParameterFields() == null ? 0 : meta.getMatrixParameterFields().size();
        if (nrmatrixparams > 0) {
          data.nrMatrixParams = nrmatrixparams;
          data.matrixParamNames = new String[nrmatrixparams];
          data.indexOfMatrixParamFields = new int[nrmatrixparams];
          for (int i = 0; i < nrmatrixparams; i++) {
            data.matrixParamNames[i] = resolve(meta.getMatrixParameterFields().get(i).getName());
            String field = resolve(meta.getMatrixParameterFields().get(i).getHeaderField());
            if (Utils.isEmpty(field)) {
              throw new HopException(
                  BaseMessages.getString(PKG, "Rest.Exception.MatrixParamFieldEmpty"));
            }
            data.indexOfMatrixParamFields[i] = data.inputRowMeta.indexOfValue(field);
            if (data.indexOfMatrixParamFields[i] < 0) {
              throw new HopException(
                  BaseMessages.getString(PKG, CONST_REST_EXCEPTION_ERROR_FINDING_FIELD, field));
            }
          }
          data.useMatrixParams = true;
        }
      }

      // Do we need to set body
      if (RestMeta.isActiveBody(meta.getMethod())) {
        String field = resolve(meta.getBodyField());
        if (!Utils.isEmpty(field)) {
          data.indexOfBodyField = data.inputRowMeta.indexOfValue(field);
          if (data.indexOfBodyField < 0) {
            throw new HopException(
                BaseMessages.getString(PKG, CONST_REST_EXCEPTION_ERROR_FINDING_FIELD, field));
          }
          data.useBody = true;
        }
      }
    } // end if first
    try {
      Object[] outputRowData = callRest(r);
      putRow(data.outputRowMeta, outputRowData); // copy row to output rowset(s)
      if (checkFeedback(getLinesRead()) && isDetailed()) {
        logDetailed(BaseMessages.getString(PKG, "Rest.LineNumber") + getLinesRead());
      }
    } catch (HopException e) {
      boolean sendToErrorRow = false;
      String errorMessage = null;
      if (getTransformMeta().isDoingErrorHandling()) {
        sendToErrorRow = true;
        errorMessage = e.toString();
      } else {
        logError(BaseMessages.getString(PKG, "Rest.ErrorInTransformRunning") + e.getMessage());
        setErrors(1);
        logError(Const.getStackTracker(e));
        stopAll();
        setOutputDone(); // signal end to receiver(s)
        return false;
      }
      if (sendToErrorRow) {
        // Simply add this row to the error row
        putError(getInputRowMeta(), r, 1, errorMessage, null, "Rest001");
      }
    }
    return true;
  }

  @Override
  public boolean init() {

    if (super.init()) {

      // use the information from the selection line if we have one.
      data.connectionName = resolve(meta.getConnectionName());
      if (!Utils.isEmpty(data.connectionName)) {
        try {
          this.connection =
              metadataProvider.getSerializer(RestConnection.class).load(data.connectionName);
          baseUrl = resolve(connection.getBaseUrl());

        } catch (Exception e) {
          throw new RuntimeException(
              "REST connection " + meta.getConnectionName() + " could not be found");
        }
      }

      data.resultFieldName = resolve(meta.getResultField().getFieldName());
      data.resultCodeFieldName = resolve(meta.getResultField().getCode());
      data.resultResponseFieldName = resolve(meta.getResultField().getResponseTime());
      data.resultHeaderFieldName = resolve(meta.getResultField().getResponseHeader());

      data.realConnectionTimeout = Const.toInt(resolve(meta.getConnectionTimeout()), -1);
      data.realReadTimeout = Const.toInt(resolve(meta.getReadTimeout()), -1);

      // get authentication settings once
      data.realProxyHost = resolve(meta.getProxyHost());
      data.realProxyPort = Const.toInt(resolve(meta.getProxyPort()), 8080);
      data.realHttpLogin = resolve(meta.getHttpLogin());
      data.realHttpPassword =
          Encr.decryptPasswordOptionallyEncrypted(resolve(meta.getHttpPassword()));

      if (!meta.isDynamicMethod()) {
        data.method = resolve(meta.getMethod());
        if (Utils.isEmpty(data.method)) {
          logError(BaseMessages.getString(PKG, "Rest.Error.MethodMissing"));
          return false;
        }
      }

      data.trustStoreFile = resolve(meta.getTrustStoreFile());
      data.trustStorePassword = resolve(meta.getTrustStorePassword());

      String applicationType = Const.NVL(meta.getApplicationType(), "");
      if (applicationType.equals(RestMeta.APPLICATION_TYPE_XML)) {
        data.mediaType = MediaType.APPLICATION_XML_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_JSON)) {
        data.mediaType = MediaType.APPLICATION_JSON_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_OCTET_STREAM)) {
        data.mediaType = MediaType.APPLICATION_OCTET_STREAM_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_XHTML)) {
        data.mediaType = MediaType.APPLICATION_XHTML_XML_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_FORM_URLENCODED)) {
        data.mediaType = MediaType.APPLICATION_FORM_URLENCODED_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_ATOM_XML)) {
        data.mediaType = MediaType.APPLICATION_ATOM_XML_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_SVG_XML)) {
        data.mediaType = MediaType.APPLICATION_SVG_XML_TYPE;
      } else if (applicationType.equals(RestMeta.APPLICATION_TYPE_TEXT_XML)) {
        data.mediaType = MediaType.TEXT_XML_TYPE;
      } else {
        data.mediaType = MediaType.TEXT_PLAIN_TYPE;
      }
      try {
        setConfig();
      } catch (Exception e) {
        logError(BaseMessages.getString(PKG, "Rest.Error.Config"), e);
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public void dispose() {

    data.config = null;
    data.headerNames = null;
    data.indexOfHeaderFields = null;
    data.paramNames = null;
    super.dispose();
  }
}
