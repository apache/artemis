/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.logs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface LogMessage {

   int id();

   String value();

   Level level();

   enum Level {
      ERROR,
      WARN,
      INFO,

      /**
       * @deprecated Typically debug/trace logging will use class-level loggers rather than generated loggers with
       * codes. This mostly exists for existing historic uses and any unexpected level down-grades.
       */
      @Deprecated
      DEBUG,

      /**
       * @deprecated Typically debug/trace logging will use class-level loggers rather than generated loggers with
       * codes. This mostly exists for existing historic uses and any unexpected level down-grades.
       */
      @Deprecated
      TRACE;
   }

   /**
    * Override the default LogBundle-wide logger name for this message.
    */
   String loggerName() default "";
}
