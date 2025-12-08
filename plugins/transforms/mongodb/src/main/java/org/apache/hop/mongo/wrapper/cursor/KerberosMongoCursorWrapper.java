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

package org.apache.hop.mongo.wrapper.cursor;

import com.mongodb.client.FindIterable;
import org.apache.hop.mongo.AuthContext;
import org.apache.hop.mongo.MongoDbException;
import org.apache.hop.mongo.wrapper.KerberosInvocationHandler;
import org.bson.Document;

public class KerberosMongoCursorWrapper extends DefaultCursorWrapper {
  private final AuthContext authContext;
  private final FindIterable<Document> findIterable;

  public KerberosMongoCursorWrapper(FindIterable<Document> findIterable, AuthContext authContext) {
    super(findIterable);
    this.findIterable = findIterable;
    this.authContext = authContext;
  }

  @Override
  public MongoCursorWrapper limit(int i) throws MongoDbException {
    return KerberosInvocationHandler.wrap(
        MongoCursorWrapper.class,
        authContext,
        new KerberosMongoCursorWrapper(findIterable.limit(i), authContext));
  }
}
