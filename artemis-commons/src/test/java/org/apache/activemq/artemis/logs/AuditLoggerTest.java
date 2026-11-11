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

import javax.security.auth.Subject;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditLoggerTest {

   @Test
   void getCaller() {

      Subject subject = new Subject();
      subject.getPrincipals().add(new TestUserPrincipal("A"));
      assertEquals("A@addr", AuditLogger.getCaller(subject, "addr"));

      subject.getPrincipals().add(new TestRolePrincipal("B"));
      assertEquals("A(B)@addr", AuditLogger.getCaller(subject, "addr"));

      subject.getPrincipals().add(new TestRolePrincipal("D"));
      assertEquals("A(B,D)@addr", AuditLogger.getCaller(subject, "addr"));

      // verify consistent order
      subject.getPrincipals().add(new TestRolePrincipal("C"));
      assertEquals("A(B,C,D)@addr", AuditLogger.getCaller(subject, "addr"));
   }

   private class TestRolePrincipal implements Principal {
      final String name;
      TestRolePrincipal(String s) {
         name = s;
      }

      @Override
      public String getName() {
         return name;
      }
   }

   private class TestUserPrincipal implements Principal {
      final String name;
      TestUserPrincipal(String s) {
         name = s;
      }

      @Override
      public String getName() {
         return name;
      }
   }
}

