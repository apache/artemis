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
package org.apache.activemq.artemis.logs;

import java.lang.reflect.Constructor;
import java.security.PrivilegedAction;

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleFactory {


   public static <T> T newBundle(final Class<T> type) {
      return newBundle(type, type.getName());
   }

   public static <T> T newBundle(final Class<T> type, String category) {
      if (!SecurityManagerShim.isSecurityManagerEnabled()) {
         return doNewBundle(type, category);
      } else {
         return SecurityManagerShim.doPrivileged((PrivilegedAction<T>) () -> doNewBundle(type, category));
      }
   }

   private static <T> T doNewBundle(final Class<T> type, String category) {
      final String implClassName = type.getName() + "_impl";

      final Class<? extends T> implClass;
      try {
         implClass = Class.forName(implClassName, true, type.getClassLoader()).asSubclass(type);
      } catch (Exception e) {
         throw new IllegalArgumentException("Unable to find class for log/message impl: " + implClassName, e);
      }

      final Constructor<? extends T> constructor;
      try {
         constructor = implClass.getConstructor(Logger.class);
      } catch (Exception e) {
         throw new IllegalArgumentException("Unable to find constructor for log/message impl: " + implClassName, e);
      }

      try {
         Logger logger = LoggerFactory.getLogger(category);

         return type.cast(constructor.newInstance(logger));
      } catch (Exception e) {
         throw new IllegalArgumentException("Unable to create instance for log/message impl: " + implClassName, e);
      }
   }
}
