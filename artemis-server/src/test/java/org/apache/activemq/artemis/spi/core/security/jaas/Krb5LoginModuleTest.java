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
package org.apache.activemq.artemis.spi.core.security.jaas;

import javax.security.auth.Subject;
import java.security.Principal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Krb5LoginModuleTest {

   @Test
   public void loginFail() throws Exception {
      Krb5LoginModule underTest = new Krb5LoginModule();

      final Subject subject = new Subject();
      underTest.initialize(subject, callbacks -> {
      }, null, null);

      assertFalse(underTest.login());
   }

   @Test
   public void loginSuccess() throws Exception {
      Krb5LoginModule underTest = new Krb5LoginModule();

      final Subject subject = new Subject();
      underTest.initialize(subject, callbacks -> ((PrincipalsCallback) callbacks[0]).setPeerPrincipals(new Principal[] {new UserPrincipal("A")}), null, null);

      assertTrue(underTest.login());
   }

}
