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
package org.apache.activemq.artemis.core.server.plugin.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.logging.log4j.core.util.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ConnectionPeriodicExpiryPluginTest {

   ConnectionPeriodicExpiryPlugin underTest;

   @BeforeEach
   public void initUnderTest() {
      underTest = new ConnectionPeriodicExpiryPlugin();
   }

   @Test
   public void init() {
      Map<String, String> props = new HashMap<>();
      props.put("name", "joe");
      props.put("periodSeconds", "4");
      props.put("accuracyWindowSeconds", "2");
      props.put("acceptorMatchRegex", "rex");

      underTest.init(props);

      assertEquals("joe", underTest.getName());
      assertEquals(4, underTest.getPeriodSeconds());
      assertEquals(2, underTest.getAccuracyWindowSeconds());
      assertEquals("rex", underTest.getAcceptorMatchRegex());
   }

   @Test
   public void initError() {
      assertThrows(IllegalArgumentException.class, () -> {
         Map<String, String> props = new HashMap<>();
         props.put("accuracyWindowSeconds", "-2");

         underTest.init(props);
      });
   }


   @Test
   public void initErrorAcceptorMatchRegex() {
      assertThrows(IllegalArgumentException.class, () -> {
         Map<String, String> props = new HashMap<>();
         props.put("accuracyWindowSeconds", "2");

         underTest.init(props);
      });
   }

   @Test
   public void testRegisterThrowsOnConfigError() {
      assertThrows(IllegalArgumentException.class, () -> {
         underTest.registered(null);
      });
   }

   @Test
   public void name() {
      assertNull(underTest.getName());
      underTest.setName("p");
      assertEquals("p", underTest.getName());
   }

   @Test
   public void periodSeconds() {
      underTest.setPeriodSeconds(4);
      assertEquals(4, underTest.getPeriodSeconds());
   }

   @Test
   public void accuracyWindowSeconds() {
      underTest.setAccuracyWindowSeconds(2);
      assertEquals(2, underTest.getAccuracyWindowSeconds());
   }

   @Test
   public void acceptorMatchRegex() {
      underTest.setAcceptorMatchRegex("rex");
      assertEquals("rex", underTest.getAcceptorMatchRegex());
   }


   @Test
   public void testRescheduleOnErrorAcceptorNullName() {

      underTest.setAcceptorMatchRegex(".*");
      underTest.setAccuracyWindowSeconds(1);
      underTest.setPeriodSeconds(1);

      List<Exception> exceptions = new LinkedList<>();

      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      // hack a scheduler to call twice in the caller thread
      ScheduledExecutorService scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
      Mockito.when(scheduledExecutorService.scheduleWithFixedDelay(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenAnswer(invocationOnMock -> {
         Runnable runnable = invocationOnMock.getArgument(0);
         try {
            runnable.run();
            runnable.run();
         } catch (Exception oops) {
            exceptions.add(oops);
         }
         return null;
      });
      Mockito.when(server.getScheduledPool()).thenReturn(scheduledExecutorService);

      RemotingService remotingService = Mockito.mock(RemotingService.class);
      Mockito.when(server.getRemotingService()).thenReturn(remotingService);

      Map<String, Acceptor> acceptors = new HashMap<>();
      // getName returns null
      NettyAcceptor acceptor = Mockito.mock(NettyAcceptor.class);
      acceptors.put("a", acceptor);
      Mockito.when(remotingService.getAcceptors()).thenReturn(acceptors);

      underTest.registered(server);

      Assert.isEmpty(exceptions);
      Mockito.verify(scheduledExecutorService, Mockito.times(1)).scheduleWithFixedDelay(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any());
      Mockito.verify(remotingService, Mockito.times(2)).getAcceptors();
   }

   @Test
   public void testRescheduleOnErrorAcceptorConnectionNotFound() {

      underTest.setAcceptorMatchRegex(".*");
      underTest.setAccuracyWindowSeconds(1);
      underTest.setPeriodSeconds(1);

      List<Exception> exceptions = new LinkedList<>();

      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      // hack a scheduler to call twice in the caller thread
      ScheduledExecutorService scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
      Mockito.when(scheduledExecutorService.scheduleWithFixedDelay(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenAnswer(invocationOnMock -> {
         Runnable runnable = invocationOnMock.getArgument(0);
         try {
            runnable.run();
            runnable.run();
         } catch (Exception oops) {
            exceptions.add(oops);
         }
         return null;
      });
      Mockito.when(server.getScheduledPool()).thenReturn(scheduledExecutorService);

      RemotingService remotingService = Mockito.mock(RemotingService.class);
      Mockito.when(server.getRemotingService()).thenReturn(remotingService);

      final String id = "a";
      Map<String, Acceptor> acceptors = new HashMap<>();
      NettyAcceptor acceptor = Mockito.mock(NettyAcceptor.class);
      Mockito.when(acceptor.getName()).thenReturn(id);
      Map<Object, NettyServerConnection> connections = new HashMap<>();
      NettyServerConnection connection = Mockito.mock(NettyServerConnection.class);
      Mockito.when(connection.getID()).thenReturn(id);
      connections.put(id, connection);
      Mockito.when(acceptor.getConnections()).thenReturn(connections);
      acceptors.put(id, acceptor);
      Mockito.when(remotingService.getAcceptors()).thenReturn(acceptors);

      // connection already closed
      Mockito.when(remotingService.getConnection(Mockito.eq(id))).thenReturn(null);

      underTest.registered(server);

      Assert.isEmpty(exceptions);
      Mockito.verify(scheduledExecutorService, Mockito.times(1)).scheduleWithFixedDelay(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any());
      Mockito.verify(remotingService, Mockito.times(2)).getAcceptors();
      Mockito.verify(remotingService, Mockito.times(2)).getConnection(Mockito.eq(id));
   }

   @Test
   public void testFailExpired() {

      underTest.setAcceptorMatchRegex(".*");
      underTest.setAccuracyWindowSeconds(1);
      underTest.setPeriodSeconds(1);

      List<Exception> exceptions = new LinkedList<>();

      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      // hack a scheduler to run in the caller thread
      ScheduledExecutorService scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
      Mockito.when(scheduledExecutorService.scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenAnswer(invocationOnMock -> {
         Runnable runnable = invocationOnMock.getArgument(0);
         try {
            runnable.run();
         } catch (Exception oops) {
            exceptions.add(oops);
         }
         return null;
      });

      Mockito.when(scheduledExecutorService.schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any())).thenAnswer(invocationOnMock -> {
         Runnable runnable = invocationOnMock.getArgument(0);
         try {
            runnable.run();
         } catch (Exception oops) {
            exceptions.add(oops);
         }
         return null;
      });


      Mockito.when(server.getScheduledPool()).thenReturn(scheduledExecutorService);

      RemotingService remotingService = Mockito.mock(RemotingService.class);
      Mockito.when(server.getRemotingService()).thenReturn(remotingService);

      final String id = "a";
      Map<String, Acceptor> acceptors = new HashMap<>();
      NettyAcceptor acceptor = Mockito.mock(NettyAcceptor.class);
      Mockito.when(acceptor.getName()).thenReturn(id);
      Map<Object, NettyServerConnection> connections = new HashMap<>();
      NettyServerConnection connection = Mockito.mock(NettyServerConnection.class);
      Mockito.when(connection.getID()).thenReturn(id);
      connections.put(id, connection);
      Mockito.when(acceptor.getConnections()).thenReturn(connections);
      acceptors.put(id, acceptor);
      Mockito.when(remotingService.getAcceptors()).thenReturn(acceptors);

      RemotingConnection remotingConnection = Mockito.mock(RemotingConnection.class);
      Mockito.when(remotingConnection.getID()).thenReturn(id);
      Mockito.when(remotingConnection.getCreationTime()).thenReturn(System.currentTimeMillis() - 20000);

      Mockito.when(remotingService.getConnection(Mockito.eq(id))).thenReturn(remotingConnection);

      underTest.registered(server);

      Assert.isEmpty(exceptions);
      Mockito.verify(scheduledExecutorService, Mockito.times(1)).scheduleWithFixedDelay(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any());
      Mockito.verify(remotingService, Mockito.times(1)).getAcceptors();
      Mockito.verify(remotingService, Mockito.times(1)).getConnection(Mockito.eq(id));
      Mockito.verify(remotingService, Mockito.times(1)).removeConnection(Mockito.eq(id));
      Mockito.verify(remotingConnection, Mockito.times(1)).fail(Mockito.any());
   }
}