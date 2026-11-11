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
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Address;
import org.apache.activemq.artemis.core.postoffice.impl.AddressImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class AddressImplTest extends ActiveMQTestBase {

   @Test
   public void testNoDots() {
      SimpleString s1 = SimpleString.of("abcde");
      SimpleString s2 = SimpleString.of("abcde");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      assertTrue(a1.matches(a2));
   }

   @Test
   public void testDotsSameLength2() {
      SimpleString s1 = SimpleString.of("a.b");
      SimpleString s2 = SimpleString.of("a.b");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      assertTrue(a1.matches(a2));
   }

   @Test
   public void testA() {
      SimpleString s1 = SimpleString.of("a.b.c");
      SimpleString s2 = SimpleString.of("a.b.c.d.e.f.g.h.i.j.k.l.m.n.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      assertFalse(a1.matches(a2));
   }

   @Test
   public void testB() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("a.b.c.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testC() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s2 = SimpleString.of("a.b.c.x");
      SimpleString s3 = SimpleString.of("a.b.*.d");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testD() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e");
      SimpleString s2 = SimpleString.of("a.b.c.x.e");
      SimpleString s3 = SimpleString.of("a.b.*.d.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testE() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.b.*.d.*.f");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testF() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));
   }

   @Test
   public void testG() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));
   }

   @Test
   public void testH() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("#.b.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));
   }

   @Test
   public void testI() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.#.b.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));
   }

   @Test
   public void testJ() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.#.c.d.e.f");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testK() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.d.e.x");
      SimpleString s3 = SimpleString.of("a.#.c.d.e.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));
   }

   @Test
   public void testL() {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.d.e.x");
      SimpleString s3 = SimpleString.of("a.#.c.d.*.f");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testM() {
      SimpleString s1 = SimpleString.of("a.b.c");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("a.b.c.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testN() {
      SimpleString s1 = SimpleString.of("usd.stock");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("*.stock.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testO() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("a.b.c.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));
   }

   @Test
   public void testP() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("a.b.c#");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));
   }

   @Test
   public void testQ() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("#a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));
   }

   @Test
   public void testR() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("#*a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));
   }

   @Test
   public void testS() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("a.b.c*");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));
   }

   @Test
   public void testT() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("*a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));
   }

   @Test
   public void testU() {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("*a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));
   }

   /**
    * https://issues.apache.org/jira/browse/ARTEMIS-1890
    */
   @Test
   public void testV() {
      final SimpleString s1 = SimpleString.of("a.b.d");
      final SimpleString s3 = SimpleString.of("a.b.#.d");
      final Address a1 = new AddressImpl(s1);
      final Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
   }
}
