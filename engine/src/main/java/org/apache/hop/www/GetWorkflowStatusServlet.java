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

package org.apache.hop.www;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.hop.core.Const;
import org.apache.hop.core.annotations.HopServerServlet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.json.HopJson;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.server.HttpUtil;
import org.apache.hop.workflow.ActionResult;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.ActionStatus;
import org.apache.hop.workflow.action.Status;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.owasp.encoder.Encode;

@HopServerServlet(id = "workflowStatus", name = "Get the status of a workflow")
public class GetWorkflowStatusServlet extends BaseHttpServlet implements IHopServerPlugin {
  private static final Class<?> PKG = GetWorkflowStatusServlet.class;

  private static final long serialVersionUID = 3634806745372015720L;
  public static final String CONTEXT_PATH = "/hop/workflowStatus";
  private static final String CONST_LINK = "<a target=\"_blank\" href=\"";
  private static final String CONST_NAME = "?name=";
  private static final String CONST_DIV_CLOSE = "</div>";
  private static final String CONST_TD_CLOSE = "</td>";

  private static final byte[] XML_HEADER =
      XmlHandler.getXmlHeader(Const.XML_ENCODING).getBytes(StandardCharsets.UTF_8);

  public GetWorkflowStatusServlet() {}

  public GetWorkflowStatusServlet(WorkflowMap workflowMap) {
    super(workflowMap);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (isJettyMode() && !request.getContextPath().startsWith(CONTEXT_PATH)) {
      return;
    }

    if (log.isDebug()) {
      logDebug(BaseMessages.getString(PKG, "GetWorkflowStatusServlet.Log.WorkflowStatusRequested"));
    }

    String workflowName = request.getParameter("name");
    String id = request.getParameter("id");
    String root =
        request.getRequestURI() == null
            ? StatusServletUtils.HOP_ROOT
            : request.getRequestURI().substring(0, request.getRequestURI().indexOf(CONTEXT_PATH));
    String prefix =
        isJettyMode() ? StatusServletUtils.STATIC_PATH : root + StatusServletUtils.RESOURCES_PATH;
    boolean useXml = "Y".equalsIgnoreCase(request.getParameter("xml"));
    boolean useJson = "Y".equalsIgnoreCase(request.getParameter("json"));
    int startLineNr = Const.toInt(request.getParameter("from"), 0);

    response.setStatus(HttpServletResponse.SC_OK);

    if (useXml) {
      response.setContentType("text/xml");
      response.setCharacterEncoding(Const.XML_ENCODING);
    }
    if (useJson) {
      response.setContentType("application/json");
      response.setCharacterEncoding(Const.XML_ENCODING);
    } else {
      response.setContentType("text/html;charset=UTF-8");
    }

    // ID is optional...
    //
    IWorkflowEngine<WorkflowMeta> workflow;
    HopServerObjectEntry entry;
    if (Utils.isEmpty(id)) {
      // get the first workflow that matches...
      //
      entry = getWorkflowMap().getFirstHopServerObjectEntry(workflowName);
      if (entry == null) {
        workflow = null;
      } else {
        id = entry.getId();
        workflow = getWorkflowMap().getWorkflow(entry);
      }
    } else {
      // Actually, just providing the ID should be enough to identify the workflow
      //
      if (Utils.isEmpty(workflowName)) {
        // Take the ID into account!
        //
        workflow = getWorkflowMap().findWorkflow(id);
      } else {
        entry = new HopServerObjectEntry(workflowName, id);
        workflow = getWorkflowMap().getWorkflow(entry);
        if (workflow != null) {
          workflowName = workflow.getWorkflowName();
        }
      }
    }

    if (workflow != null) {
      if (useXml || useJson) {
        try {
          int lastLineNr = HopLogStore.getLastBufferLineNr();
          String logText = getLogText(workflow, startLineNr, lastLineNr);

          HopServerWorkflowStatus workflowStatus =
              new HopServerWorkflowStatus(workflowName, id, workflow.getStatusDescription());
          workflowStatus.setFirstLoggingLineNr(startLineNr);
          workflowStatus.setLastLoggingLineNr(lastLineNr);
          workflowStatus.setLogDate(workflow.getExecutionStartDate());

          // Add status of executed actions
          for (ActionResult actionResult : workflow.getActionResults()) {
            ActionStatus actionState = new ActionStatus();
            actionState.setName(actionResult.getActionName());
            if (actionResult.getResult().getResult()) {
              actionState.setStatus(Status.FINISHED);
            } else {
              actionState.setStatus(Status.STOPPED);
            }
            actionState.setResult(actionResult.getResult());
            workflowStatus.getActionStatusList().add(actionState);
          }

          // Add status of active actions
          for (ActionMeta actionMeta : workflow.getActiveActions()) {
            ActionStatus actionState = new ActionStatus();
            actionState.setName(actionMeta.getName());
            actionState.setStatus(Status.RUNNING);
            workflowStatus.getActionStatusList().add(actionState);
          }

          // The log can be quite large at times, we are going to putIfAbsent a base64 encoding
          // around a compressed
          // stream

          // of bytes to handle this one.
          String loggingString = HttpUtil.encodeBase64ZippedString(logText);
          workflowStatus.setLoggingString(loggingString);

          // Also set the result object...
          //
          workflowStatus.setResult(workflow.getResult()); // might be null

          OutputStream out = response.getOutputStream();

          if (useXml) {
            // XML
            //
            String xml = workflowStatus.getXml();
            byte[] data = xml.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(XML_HEADER.length + data.length);
            out.write(XML_HEADER);
            out.write(data);
          } else {
            // JSON
            //
            ObjectMapper mapper = HopJson.newMapper();
            String jsonString =
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflowStatus);
            byte[] data = jsonString.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(data.length);
            out.write(data);
          }
          out.flush();

          response.flushBuffer();
        } catch (HopException e) {
          throw new ServletException("Unable to get the workflow status in XML or JSON format", e);
        }
      } else {

        PrintWriter out = response.getWriter();

        int lastLineNr = HopLogStore.getLastBufferLineNr();
        int tableBorder = 0;

        response.setContentType("text/html");

        out.println("<HTML>");
        out.println("<HEAD>");
        out.println(
            "<TITLE>"
                + BaseMessages.getString(PKG, "GetWorkflowStatusServlet.HopWorkflowStatus")
                + "</TITLE>");
        if (EnvUtil.getSystemProperty(Const.HOP_SERVER_REFRESH_STATUS, "N").equalsIgnoreCase("Y")) {
          out.println(
              "<META http-equiv=\"Refresh\" content=\"10;url="
                  + convertContextPath(GetWorkflowStatusServlet.CONTEXT_PATH)
                  + CONST_NAME
                  + URLEncoder.encode(Const.NVL(workflowName, ""), UTF_8)
                  + "&id="
                  + URLEncoder.encode(id, UTF_8)
                  + "\">");
        }
        out.println("<META http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
        if (isJettyMode()) {
          out.println(
              "<link rel=\"stylesheet\" type=\"text/css\" href=\"/static/css/hop-server.css\" />");
          out.println(
              "<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/favicon.svg\">");
        }
        out.println("</HEAD>");
        out.println("<BODY style=\"overflow: auto;\">");
        out.println("<div class=\"row\" id=\"pucHeader\">");
        out.println(
            "<div class=\"workspaceHeading\" style=\"padding: 0px 0px 0px 10px;\">"
                + Encode.forHtml(
                    BaseMessages.getString(
                        PKG, "GetWorkflowStatusServlet.WorkflowStatus", workflowName))
                + CONST_DIV_CLOSE);
        out.println(CONST_DIV_CLOSE);

        try {
          out.println("<div class=\"row\" style=\"padding: 0px 0px 0px 30px\">");
          out.println("<div class=\"row\" style=\"padding-top: 30px;\">");
          out.print("<a href=\"" + convertContextPath(GetStatusServlet.CONTEXT_PATH) + "\">");
          out.print(
              "<img src=\""
                  + prefix
                  + "/images/back.svg\" style=\"margin-right: 5px; width: 16px; height: 16px; vertical-align: middle;\">");
          out.print(
              BaseMessages.getString(PKG, "HopServerStatusServlet.BackToHopServerStatus") + "</a>");
          out.println(CONST_DIV_CLOSE);
          out.println("<div class=\"row\" style=\"padding: 30px 0px 75px 0px; display: table;\">");
          out.println("<div style=\"display: table-row;\">");
          out.println(
              "<div style=\"padding: 0px 30px 0px 0px; width: 60px; display: table-cell; vertical-align: top;\">");
          out.println(
              "<img src=\""
                  + prefix
                  + "/images/workflow.svg\" style=\"width: 60px; height: 60px;\"></img>");
          out.println(CONST_DIV_CLOSE);
          out.println("<div style=\"vertical-align: top; display: table-cell;\">");
          out.println(
              "<table style=\"border-collapse: collapse;\" border=\"" + tableBorder + "\">");
          out.print(
              "<tr class=\"cellTableRow\" style=\"border: solid; border-width: 1px 0; border-top: none; border-color: #E3E3E3; font-size: 12; text-align: left;\"> <th style=\"font-weight: normal; "
                  + "padding: 8px 10px 10px 10px\" class=\"cellTableHeader\">"
                  + BaseMessages.getString(PKG, "PipelineStatusServlet.ServerObjectId")
                  + "</th> <th style=\"font-weight: normal; padding: 8px 10px 10px 10px\" class=\"cellTableHeader\">"
                  + BaseMessages.getString(PKG, "PipelineStatusServlet.PipelineStatus")
                  + "</th> <th style=\"font-weight: normal; padding: 8px 10px 10px 10px\" class=\"cellTableHeader\">"
                  + BaseMessages.getString(PKG, "PipelineStatusServlet.StartDate")
                  + "</th> </tr>");
          out.print(
              "<tr class=\"cellTableRow\" style=\"border: solid; border-width: 1px 0; border-bottom: none; font-size: 12; text-align:left\">");
          out.print(
              "<td style=\"padding: 8px 10px 10px 10px\" class=\"cellTableCell cellTableFirstColumn\">"
                  + Const.NVL(Encode.forHtml(id), "")
                  + CONST_TD_CLOSE);
          out.print(
              "<td style=\"padding: 8px 10px 10px 10px\" class=\"cellTableCell\" id=\"statusColor\" style=\"font-weight: bold;\">"
                  + workflow.getStatusDescription()
                  + CONST_TD_CLOSE);
          String dateStr = XmlHandler.date2string(workflow.getExecutionStartDate());
          out.print(
              "<td style=\"padding: 8px 10px 10px 10px\" class=\"cellTableCell cellTableLastColumn\">"
                  + (dateStr != null ? dateStr.substring(0, dateStr.indexOf(' ')) : "-")
                  + CONST_TD_CLOSE);
          out.print("</tr>");
          out.print("</table>");
          out.print(CONST_DIV_CLOSE);

          // Download as XML section...
          //
          out.println(
              "<div style=\"padding: 0px 0px 0px 20px; width: 90px; display: table-cell; vertical-align: top;\">");

          // XML Download icon
          //
          out.print(
              "<div style=\"display: block; margin-left: auto; margin-right: auto; padding: 5px 0px;\">");
          out.print(
              CONST_LINK
                  + convertContextPath(GetWorkflowStatusServlet.CONTEXT_PATH)
                  + CONST_NAME
                  + URLEncoder.encode(workflowName, UTF_8)
                  + "&id="
                  + URLEncoder.encode(id, UTF_8)
                  + "&xml=y\">"
                  + "<img src=\""
                  + prefix
                  + "/images/download.svg\" style=\"display: block; margin: auto; width: 22px; height: 22px;\"></a>");
          out.print(CONST_DIV_CLOSE); // End of icon

          // Show as XML text
          //
          out.println("<div style=\"text-align: center; padding-top: 12px; font-size: 12px;\">");
          out.print(
              CONST_LINK
                  + convertContextPath(GetWorkflowStatusServlet.CONTEXT_PATH)
                  + CONST_NAME
                  + URLEncoder.encode(workflowName, UTF_8)
                  + "&id="
                  + URLEncoder.encode(id, UTF_8)
                  + "&xml=y\">"
                  + BaseMessages.getString(PKG, "PipelineStatusServlet.ShowAsXml")
                  + "</a>");
          out.print(CONST_DIV_CLOSE); // End of XML text

          out.print(CONST_DIV_CLOSE); // End of XML block

          // Download as JSON block...
          //
          out.println(
              "<div style=\"padding: 0px 0px 0px 20px; width: 90px; display: table-cell; vertical-align: top;\">");

          // JSON Download icon
          //
          out.print(
              "<div style=\"display: block; margin-left: auto; margin-right: auto; padding: 5px 0px;\">");
          out.print(
              CONST_LINK
                  + convertContextPath(GetWorkflowStatusServlet.CONTEXT_PATH)
                  + CONST_NAME
                  + URLEncoder.encode(workflowName, UTF_8)
                  + "&id="
                  + URLEncoder.encode(id, UTF_8)
                  + "&json=y\">"
                  + "<img src=\""
                  + prefix
                  + "/images/download.svg\" style=\"display: block; margin: auto; width: 22px; height: 22px;\"></a>");
          out.print(CONST_DIV_CLOSE); // End of JSON icon

          // View as JSON text
          //
          out.println("<div style=\"text-align: center; padding-top: 12px; font-size: 12px;\">");
          out.print(
              CONST_LINK
                  + convertContextPath(GetWorkflowStatusServlet.CONTEXT_PATH)
                  + CONST_NAME
                  + URLEncoder.encode(workflowName, UTF_8)
                  + "&id="
                  + URLEncoder.encode(id, UTF_8)
                  + "&json=y\">"
                  + BaseMessages.getString(PKG, "PipelineStatusServlet.ShowAsJson")
                  + "</a>");
          out.print(CONST_DIV_CLOSE); // End of JSON text
          out.print(CONST_DIV_CLOSE); // End of JSON block

          out.print(CONST_DIV_CLOSE);
          out.print(CONST_DIV_CLOSE);

          if (supportGraphicEnvironment) {
            out.print("<div class=\"row\" style=\"padding: 0px 0px 75px 0px;\">");
            out.print("<div class=\"workspaceHeading\">Canvas preview</div>");
            // Show workflow image?
            //
            Point max = workflow.getWorkflowMeta().getMaximum();
            max.x += (int) (max.x * GetWorkflowImageServlet.ZOOM_FACTOR) + 100;
            max.y += (int) (max.y + GetWorkflowImageServlet.ZOOM_FACTOR) + 50;
            out.print(
                "<iframe height=\""
                    + (max.y + 100)
                    + "px\" width=\""
                    + (max.x + 100)
                    + "px\" src=\""
                    + convertContextPath(GetWorkflowImageServlet.CONTEXT_PATH)
                    + CONST_NAME
                    + URLEncoder.encode(workflowName, UTF_8)
                    + "&id="
                    + URLEncoder.encode(id, UTF_8)
                    + "\"></iframe>");
            out.print(CONST_DIV_CLOSE);
          }

          // Put the logging below that.

          out.print("<div class=\"row\" style=\"padding: 0px 0px 30px 0px;\">");
          out.print("<div class=\"workspaceHeading\">Workflow log</div>");
          out.println(
              "<textarea id=\"workflowlog\" cols=\"120\" rows=\"20\" wrap=\"off\" "
                  + "name=\"Workflow log\" readonly=\"readonly\" style=\"height: auto; width: 100%;\">"
                  + Encode.forHtml(getLogText(workflow, startLineNr, lastLineNr))
                  + "</textarea>");
          out.print(CONST_DIV_CLOSE);

          out.println("<script type=\"text/javascript\">");
          out.println("element = document.getElementById( 'statusColor' );");
          out.println("if( element.innerHTML == 'Running' || element.innerHTML == 'Finished' ){");
          out.println("element.style.color = '#009900';");
          out.println("} else if( element.innerHTML == 'Stopped' ) {");
          out.println("element.style.color = '#7C0B2B';");
          out.println("} else {");
          out.println("element.style.color = '#F1C40F';");
          out.println("}");
          out.println("</script>");
          out.println("<script type=\"text/javascript\"> ");
          out.println("  joblog.scrollTop=joblog.scrollHeight; ");
          out.println("</script> ");
        } catch (Exception ex) {
          out.println("<pre>");
          out.println(Encode.forHtml(Const.getStackTracker(ex)));
          out.println("</pre>");
        }

        out.println(CONST_DIV_CLOSE);
        out.println("</BODY>");
        out.println("</HTML>");
      }
    } else {
      PrintWriter out = response.getWriter();
      if (useXml) {
        out.println(
            new WebResult(
                WebResult.STRING_ERROR,
                BaseMessages.getString(
                    PKG, "StartWorkflowServlet.Log.SpecifiedWorkflowNotFound", workflowName, id)));
      } else {
        out.println(
            "<H1>Workflow "
                + Encode.forHtml("'" + workflowName + "'")
                + " could not be found.</H1>");
        out.println(
            "<a href=\""
                + convertContextPath(GetStatusServlet.CONTEXT_PATH)
                + "\">"
                + BaseMessages.getString(PKG, "WorkflowStatusServlet.BackToStatusPage")
                + "</a><p>");
      }
    }
  }

  public String toString() {
    return "Workflow Status IHandler";
  }

  @Override
  public String getService() {
    return CONTEXT_PATH + " (" + this + ")";
  }

  @Override
  public String getContextPath() {
    return CONTEXT_PATH;
  }

  private String getLogText(IWorkflowEngine<WorkflowMeta> workflow, int startLineNr, int lastLineNr)
      throws HopException {
    try {
      return HopLogStore.getAppender()
          .getBuffer(workflow.getLogChannel().getLogChannelId(), false, startLineNr, lastLineNr)
          .toString();
    } catch (OutOfMemoryError error) {
      throw new HopException("Log string is too long");
    }
  }
}
