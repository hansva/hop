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

package org.apache.hop.workflow.actions.filesexist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.apache.hop.core.Result;
import org.apache.hop.junit.rules.RestoreHopEngineEnvironment;
import org.apache.hop.utils.TestUtils;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.apache.hop.workflow.engines.local.LocalWorkflowEngine;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class WorkflowActionFilesExistTest {
  private IWorkflowEngine<WorkflowMeta> workflow;
  private ActionFilesExist action;

  private String existingFile1;
  private String existingFile2;

  @ClassRule public static RestoreHopEngineEnvironment env = new RestoreHopEngineEnvironment();

  @Before
  public void setUp() {
    workflow = new LocalWorkflowEngine(new WorkflowMeta());
    action = new ActionFilesExist();

    workflow.getWorkflowMeta().addAction(new ActionMeta(action));
    action.setParentWorkflow(workflow);
    WorkflowMeta mockWorkflowMeta = mock(WorkflowMeta.class);
    action.setParentWorkflowMeta(mockWorkflowMeta);

    workflow.setStopped(false);

    existingFile1 =
        TestUtils.createRamFile(getClass().getSimpleName() + "/existingFile1.ext", action);
    existingFile2 =
        TestUtils.createRamFile(getClass().getSimpleName() + "/existingFile2.ext", action);
  }

  @Test
  public void testSetNrErrorsFalseResult() {
    action.setArguments(new String[] {"nonExistingFile.ext"});

    Result res = action.execute(new Result(), 0);

    assertFalse("Entry should fail", res.getResult());
    assertEquals(
        "Files not found. Result is false. But... No of errors should be zero",
        0,
        res.getNrErrors());
  }

  @Test
  public void testExecuteWithException() {
    action.setArguments(new String[] {null});

    Result res = action.execute(new Result(), 0);

    assertFalse("Entry should fail", res.getResult());
    assertEquals(
        "File with wrong name was specified. One error should be reported", 1, res.getNrErrors());
  }

  @Test
  public void testExecuteSuccess() {
    action.setArguments(new String[] {existingFile1, existingFile2});

    Result res = action.execute(new Result(), 0);

    assertTrue("Entry failed", res.getResult());
  }

  @Test
  public void testExecuteFail() {
    action.setArguments(
        new String[] {
          existingFile1, existingFile2, "nonExistingFile1.ext", "nonExistingFile2.ext"
        });

    Result res = action.execute(new Result(), 0);

    assertFalse("Entry should fail", res.getResult());
  }
}
