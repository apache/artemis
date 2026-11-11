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
package org.apache.activemq.artemis.core.security.jaas;

import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RolePrincipalTest {

   @Test
   public void testArguments() {
      RolePrincipal principal = new RolePrincipal("FOO");

      assertEquals("FOO", principal.getName());

      try {
         new RolePrincipal(null);
         fail("Should have thrown IllegalArgumentException");
      } catch (IllegalArgumentException ingore) {

      }
   }

   @Test
   public void testHash() {
      RolePrincipal p1 = new RolePrincipal("FOO");
      RolePrincipal p2 = new RolePrincipal("FOO");

      assertEquals(p1.hashCode(), p1.hashCode());
      assertEquals(p1.hashCode(), p2.hashCode());
   }

   @Test
   public void testEquals() {
      RolePrincipal p1 = new RolePrincipal("FOO");
      RolePrincipal p2 = new RolePrincipal("FOO");
      RolePrincipal p3 = new RolePrincipal("BAR");

      assertTrue(p1.equals(p1));
      assertTrue(p1.equals(p2));
      assertFalse(p1.equals(null));
      assertFalse(p1.equals("FOO"));
      assertFalse(p1.equals(p3));
   }
}
