/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.extensions;

import javax.security.auth.Subject;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

public class SubjectDotCallAsExtension implements InvocationInterceptor {

   final Subject subject;

   public SubjectDotCallAsExtension(Subject subject) {
      this.subject = subject;
   }

   @Override
   public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
      Exception e = SecurityManagerShim.callAs(subject, (Callable<Exception>) () -> {
         try {
            invocation.proceed();
         } catch (Throwable e1) {
            return new Exception(e1);
         }
         return null;
      });
      if (e != null) {
         throw e;
      }
   }
}
