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
package org.apache.activemq.artemis.tests.integration.clientcrash;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Code to be run in an external VM, via main()
 */
public class CrashClient {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static int OK = 9;
   public static int NOT_OK = 1;


   public static void main(final String[] args) throws Exception {
      try {
         ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName()));
         locator.setClientFailureCheckPeriod(ClientCrashTest.PING_PERIOD);
         locator.setConnectionTTL(ClientCrashTest.CONNECTION_TTL);
         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session = sf.createSession(false, true, true);
         ClientProducer producer = session.createProducer(ClientCrashTest.QUEUE);

         // it has to be durable otherwise it may race dying before the client is killed
         ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 1);
         message.getBodyBuffer().writeString(ClientCrashTest.MESSAGE_TEXT_FROM_CLIENT);

         producer.send(message);

         // exit without closing the session properly
         System.exit(OK);
      } catch (Throwable t) {
         CrashClient.logger.error(t.getMessage(), t);
         System.exit(NOT_OK);
      }
   }
}
