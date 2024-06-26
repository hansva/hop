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
package org.apache.hop.pipeline.transforms.loadsave.validator;

import java.util.Random;
import java.util.UUID;
import org.apache.hop.core.Condition;
import org.apache.hop.core.exception.HopException;

public class ConditionLoadSaveValidator implements IFieldLoadSaveValidator<Condition> {
  final Random rand = new Random();

  @Override
  public Condition getTestObject() {
    Condition rtn = new Condition();
    rtn.setFunction(Condition.Function.lookupType(rand.nextInt(Condition.functions.length)));
    rtn.setLeftValueName(UUID.randomUUID().toString());
    rtn.setNegated(rand.nextBoolean());
    rtn.setOperator(Condition.Operator.lookupType(rand.nextInt(Condition.operators.length)));
    rtn.setRightValueName(UUID.randomUUID().toString());
    return rtn;
  }

  @Override
  public boolean validateTestObject(Condition testObject, Object actual) {
    if (!(actual instanceof Condition)) {
      return false;
    }
    Condition another = (Condition) actual;
    try {
      return (testObject.getXml().equals(another.getXml()));
    } catch (HopException ex) {
      throw new RuntimeException(ex);
    }
  }
}
