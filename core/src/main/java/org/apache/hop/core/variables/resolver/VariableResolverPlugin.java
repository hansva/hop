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

package org.apache.hop.core.variables.resolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This annotation indicates that the given plugin is a variable resolver plugin. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VariableResolverPlugin {
  /**
   * @return The ID of the password encoder plugin. You can specify more than one ID in a comma
   *     separated format: id1,id2,id3 for deprecation purposes.
   */
  String id();

  String name();

  String description() default "";

  /**
   * @return True if a separate class loader is needed every time this class is instantiated
   */
  boolean isSeparateClassLoaderNeeded() default false;

  String documentationUrl() default "";

  String casesUrl() default "";

  String forumUrl() default "";

  String classLoaderGroup() default "";
}
