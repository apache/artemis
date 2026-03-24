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
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.jboss.logging.Logger;

/**
 * Code to be run in an external VM, via main()
 */
public class CrashClient2 {

   private static final Logger log = Logger.getLogger(CrashClient2.class);
   public static final int OK = 9;

   public static void main(final String[] args) throws Exception {
      try {
         ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName()));
         locator.setClientFailureCheckPeriod(ClientCrashTest.PING_PERIOD);
         locator.setConnectionTTL(ClientCrashTest.CONNECTION_TTL);
         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session = sf.createSession(true, true, 1000000);
         ClientProducer producer = session.createProducer(ClientCrashTest.QUEUE2);

         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString(ClientCrashTest.MESSAGE_TEXT_FROM_CLIENT);

         producer.send(message);

         //Now consume the message, but don't let ack get to server

         //Consume the message
         ClientConsumer cons = session.createConsumer(ClientCrashTest.QUEUE2);

         session.start();

         ClientMessage msg = cons.receive(10000);

         if (msg == null) {
            log.error("Didn't receive msg");

            System.exit(1);
         }

         // exit without closing the session properly
         System.exit(OK);
      } catch (Throwable t) {
         log.error(t.getMessage(), t);
         System.exit(1);
      }
   }

}
