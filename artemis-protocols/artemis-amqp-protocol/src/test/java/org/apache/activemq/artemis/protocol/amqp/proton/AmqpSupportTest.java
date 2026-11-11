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

package org.apache.activemq.artemis.protocol.amqp.proton;

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.RECEIVER_PRIORITY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.getReceiverPriority;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.engine.Link;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Test for utility APIs in the AMQP support class
 */
public class AmqpSupportTest {

   private static final Symbol A = Symbol.valueOf("A");
   private static final Symbol B = Symbol.valueOf("B");
   private static final Symbol C = Symbol.valueOf("C");
   private static final Symbol D = Symbol.valueOf("D");

   private static final Symbol[] ALL = new Symbol[] {D, B, C, A};

   @Test
   public void testContains() {
      assertFalse(AmqpSupport.contains(null, Symbol.valueOf("test")));
      assertFalse(AmqpSupport.contains(new Symbol[] {Symbol.valueOf("test")}, null));
      assertFalse(AmqpSupport.contains(new Symbol[] {Symbol.valueOf("a")}, Symbol.valueOf("test")));

      assertTrue(AmqpSupport.contains(new Symbol[] {Symbol.valueOf("test")}, Symbol.valueOf("test")));
      assertTrue(AmqpSupport.contains(ALL, B));
      assertTrue(AmqpSupport.contains(ALL, D));
      assertTrue(AmqpSupport.contains(ALL, C));
      assertTrue(AmqpSupport.contains(ALL, A));
   }

   @Test
   public void testVerifyOfferedCapabilitiesOfLink() {
      final Link link = Mockito.mock(Link.class);

      Mockito.when(link.getDesiredCapabilities()).thenReturn(new Symbol[] {A});
      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(new Symbol[] {B, C});

      assertFalse(AmqpSupport.verifyOfferedCapabilities(link));

      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(null);

      assertFalse(AmqpSupport.verifyOfferedCapabilities(link));

      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(new Symbol[] {B, C});
      Mockito.when(link.getDesiredCapabilities()).thenReturn(new Symbol[] {B, C});

      assertTrue(AmqpSupport.verifyOfferedCapabilities(link));

      Mockito.when(link.getDesiredCapabilities()).thenReturn(null);
      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(null);

      assertTrue(AmqpSupport.verifyOfferedCapabilities(link));
   }

   @Test
   public void testVerifyOfferedCapabilities() {
      final Link link = Mockito.mock(Link.class);

      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(new Symbol[] {B, C});

      assertFalse(AmqpSupport.verifyOfferedCapabilities(link, ALL));

      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(ALL);

      assertTrue(AmqpSupport.verifyOfferedCapabilities(link, ALL));

      Mockito.when(link.getRemoteOfferedCapabilities()).thenReturn(null);

      assertTrue(AmqpSupport.verifyOfferedCapabilities(link, (Symbol[]) null));
   }

   @Test
   public void testVerifyDesiredCapability() {
      final Link link = Mockito.mock(Link.class);

      Mockito.when(link.getRemoteDesiredCapabilities()).thenReturn(new Symbol[] {B, C});

      assertFalse(AmqpSupport.verifyDesiredCapability(link, A));
      assertTrue(AmqpSupport.verifyDesiredCapability(link, C));
      assertTrue(AmqpSupport.verifyDesiredCapability(link, B));

      assertThrows(NullPointerException.class, () -> AmqpSupport.verifyDesiredCapability(link, null));

      Mockito.when(link.getRemoteDesiredCapabilities()).thenReturn((Symbol[]) null);

      assertFalse(AmqpSupport.verifyDesiredCapability(link, A));
   }

   @Test
   public void testVerifySourceCapability() {
      final Source source = Mockito.mock(Source.class);

      Mockito.when(source.getCapabilities()).thenReturn(new Symbol[] {B, C});

      assertFalse(AmqpSupport.verifySourceCapability(source, A));

      Mockito.when(source.getCapabilities()).thenReturn(ALL);

      assertTrue(AmqpSupport.verifySourceCapability(source, A));
      assertTrue(AmqpSupport.verifySourceCapability(source, B));
      assertTrue(AmqpSupport.verifySourceCapability(source, C));

      assertThrows(NullPointerException.class, () -> AmqpSupport.verifySourceCapability(source, null));

      Mockito.when(source.getCapabilities()).thenReturn(null);

      assertFalse(AmqpSupport.verifySourceCapability(source, A));
   }

   @Test
   public void testVerifyTargetCapability() {
      final Target target = Mockito.mock(Target.class);

      Mockito.when(target.getCapabilities()).thenReturn(new Symbol[] {B, C});

      assertFalse(AmqpSupport.verifyTargetCapability(target, A));

      Mockito.when(target.getCapabilities()).thenReturn(ALL);

      assertTrue(AmqpSupport.verifyTargetCapability(target, A));
      assertTrue(AmqpSupport.verifyTargetCapability(target, B));
      assertTrue(AmqpSupport.verifyTargetCapability(target, C));

      assertThrows(NullPointerException.class, () -> AmqpSupport.verifyTargetCapability(target, null));

      Mockito.when(target.getCapabilities()).thenReturn(null);

      assertFalse(AmqpSupport.verifyTargetCapability(target, A));
   }

   @Test
   public void testGetReceiverPriority() {
      final Map<Symbol, Object> priorityPresent = Map.of(RECEIVER_PRIORITY, 10);
      final Map<Symbol, Object> priorityNotPresent = Map.of(Symbol.valueOf("test"), 10);
      final Map<Symbol, Object> priorityPresentButNotNumeric = Map.of(RECEIVER_PRIORITY, "test");

      assertEquals(10, getReceiverPriority(priorityPresent));
      assertEquals(10, getReceiverPriority(priorityPresent, 20));

      assertNull(getReceiverPriority(priorityNotPresent));
      assertEquals(10, getReceiverPriority(priorityNotPresent, 10));
      assertEquals(10, getReceiverPriority(null, 10));

      assertThrows(ClassCastException.class, () -> getReceiverPriority(priorityPresentButNotNumeric));
   }
}
