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

package org.apache.hop.workflow.actions.repeat;

import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

@Action(
    id = "EndRepeat",
    name = "i18n::EndRepeat.Name",
    description = "i18n::EndRepeat.Description",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::EndRepeat.keywords",
    image = "endrepeat.svg",
    documentationUrl = "/workflow/actions/repeat-end.html")
public class EndRepeat extends ActionBase implements IAction, Cloneable {

  public EndRepeat(String name, String description) {
    super(name, description);
  }

  public EndRepeat() {
    this("", "");
  }

  /**
   * Simply set a flag in the parent workflow, this is also a success
   *
   * @param prevResult
   * @param nr
   * @return
   * @throws HopException
   */
  @Override
  public Result execute(Result prevResult, int nr) throws HopException {

    parentWorkflow.getExtensionDataMap().put(Repeat.REPEAT_END_LOOP, getName());

    // Force success.
    //
    prevResult.setResult(true);
    prevResult.setNrErrors(0);

    return prevResult;
  }

  @Override
  public EndRepeat clone() {
    return (EndRepeat) super.clone();
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }
}
