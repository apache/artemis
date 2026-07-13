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
package org.apache.activemq.artemis.spi.core.protocol;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.remoting.AuthenticationListener;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractRemotingConnectionTest {

   /** Minimal concrete subclass — only what the constructor requires. */
   private static class TestRemotingConnection extends AbstractRemotingConnection {
      TestRemotingConnection(Connection transport) {
         super(transport, Runnable::run);
      }

      @Override
      public void bufferReceived(Object connectionID, org.apache.activemq.artemis.api.core.ActiveMQBuffer buffer) {
      }

      @Override
      public void fail(org.apache.activemq.artemis.api.core.ActiveMQException me) {
      }

      @Override
      public void fail(org.apache.activemq.artemis.api.core.ActiveMQException me, String scaleDownTargetNodeID) {
      }

      @Override
      public void destroy() {
      }

      @Override
      public void disconnect(boolean criticalError) {
      }

      @Override
      public void disconnect(String scaleDownNodeID, boolean criticalError) {
      }

      @Override
      public String getProtocolName() {
         return "TEST";
      }
   }

   private TestRemotingConnection connection;

   @BeforeEach
   public void setUp() {
      connection = new TestRemotingConnection(Mockito.mock(Connection.class));
   }

   @Test
   public void testIsAuthenticatedFalseByDefault() {
      assertFalse(connection.isAuthenticated());
   }

   @Test
   public void testSetAuthenticatedMarksConnectionAsAuthenticated() {
      connection.setAuthenticated();
      assertTrue(connection.isAuthenticated());
   }

   @Test
   public void testSetAuthenticatedIsIdempotent() {
      connection.setAuthenticated();
      connection.setAuthenticated(); // second call must be a no-op
      assertTrue(connection.isAuthenticated());
   }

   @Test
   public void testListenerIsCalledOnSetAuthenticated() {
      AtomicInteger callCount = new AtomicInteger(0);
      connection.addAuthenticationListener(callCount::incrementAndGet);

      connection.setAuthenticated();

      assertEquals(1, callCount.get());
   }

   @Test
   public void testListenerIsNotCalledWhenAlreadyAuthenticated() {
      connection.setAuthenticated(); // first call marks authenticated

      AtomicInteger callCount = new AtomicInteger(0);
      connection.addAuthenticationListener(callCount::incrementAndGet);

      connection.setAuthenticated(); // second call must be a no-op
      assertEquals(0, callCount.get());
   }

   @Test
   public void testMultipleListenersAreAllCalled() {
      AtomicInteger countA = new AtomicInteger(0);
      AtomicInteger countB = new AtomicInteger(0);
      connection.addAuthenticationListener(countA::incrementAndGet);
      connection.addAuthenticationListener(countB::incrementAndGet);

      connection.setAuthenticated();

      assertEquals(1, countA.get());
      assertEquals(1, countB.get());
   }

   @Test
   public void testRemovedListenerIsNotCalled() {
      AtomicInteger callCount = new AtomicInteger(0);
      AuthenticationListener listener = callCount::incrementAndGet;
      connection.addAuthenticationListener(listener);

      boolean removed = connection.removeAuthenticationListener(listener);
      assertTrue(removed);

      connection.setAuthenticated();
      assertEquals(0, callCount.get());
   }

   @Test
   public void testRemoveReturnsFalseForUnknownListener() {
      AuthenticationListener listener = () -> { };
      assertFalse(connection.removeAuthenticationListener(listener));
   }

   @Test
   public void testListenerThatThrowsDoesNotPreventOtherListeners() {
      AuthenticationListener throwing = () -> {
         throw new RuntimeException("simulated error");
      };
      AtomicInteger callCount = new AtomicInteger(0);
      connection.addAuthenticationListener(throwing);
      connection.addAuthenticationListener(callCount::incrementAndGet);

      connection.setAuthenticated(); // must not propagate the exception

      assertTrue(connection.isAuthenticated());
      assertEquals(1, callCount.get());
   }

   @Test
   public void testAddListenerRejectsNull() {
      assertThrows(NullPointerException.class, () -> connection.addAuthenticationListener(null));
   }

   @Test
   public void testRemoveListenerRejectsNull() {
      assertThrows(NullPointerException.class, () -> connection.removeAuthenticationListener(null));
   }

   @Test
   public void testListenerCanRemoveItselfDuringFiring() {
      AtomicInteger callCount = new AtomicInteger(0);
      AuthenticationListener[] holder = new AuthenticationListener[1];
      holder[0] = () -> {
         callCount.incrementAndGet();
         connection.removeAuthenticationListener(holder[0]);
      };
      connection.addAuthenticationListener(holder[0]);

      connection.setAuthenticated();

      assertEquals(1, callCount.get());
      // Listener removed itself — a second setAuthenticated on a fresh connection
      // would not call it again; verify removal succeeded
      assertFalse(connection.removeAuthenticationListener(holder[0]));
   }
}
