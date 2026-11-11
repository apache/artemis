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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.netty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class NettyAcceptorFactoryTest extends ActiveMQTestBase {

   @Test
   public void testCreateAcceptor() throws Exception {
      NettyAcceptorFactory factory = new NettyAcceptorFactory();

      Map<String, Object> params = new HashMap<>();
      BufferHandler handler = (connectionID, buffer) -> {
      };

      ServerConnectionLifeCycleListener listener = new ServerConnectionLifeCycleListener() {

         @Override
         public void connectionException(final Object connectionID, final ActiveMQException me) {
         }

         @Override
         public void connectionDestroyed(final Object connectionID, boolean failed) {
         }

         @Override
         public void connectionCreated(ActiveMQComponent component,
                                       final Connection connection,
                                       final ProtocolManager protocol) {
         }

         @Override
         public void connectionReadyForWrites(Object connectionID, boolean ready) {
         }

      };

      Acceptor acceptor = factory.createAcceptor("netty", null, params, handler, listener, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(ActiveMQDefaultConfiguration.getDefaultScheduledThreadPoolMaxSize(), ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), new HashMap<>(), null, null);

      assertInstanceOf(NettyAcceptor.class, acceptor);
   }
}
