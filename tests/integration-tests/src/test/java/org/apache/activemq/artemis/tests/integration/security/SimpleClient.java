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
package org.apache.activemq.artemis.tests.integration.security;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.utils.RandomUtil;

/**
 * Code to be run in an external VM, via main()
 */
final class SimpleClient {

   public static void main(final String[] args) throws Exception {
      try {
         if (args.length != 1) {
            throw new Exception("require 1 argument: connector factory class name");
         }

         String connectorFactoryClassName = args[0];

         String queueName = RandomUtil.randomUUIDString();
         String messageText = RandomUtil.randomUUIDString();

         ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(connectorFactoryClassName)).setReconnectAttempts(1).setInitialConnectAttempts(1);
         try {
            ClientSessionFactory sf = locator.createSessionFactory();
            ClientSession session = sf.createSession(false, true, true);

            session.createQueue(QueueConfiguration.of(queueName).setDurable(false));
            ClientProducer producer = session.createProducer(queueName);
            ClientConsumer consumer = session.createConsumer(queueName);

            ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
            message.getBodyBuffer().writeString(messageText);
            producer.send(message);

            session.start();

            ClientMessage receivedMsg = consumer.receive(5000);
            if (receivedMsg == null) {
               throw new Exception("did not receive the message");
            }

            String text = receivedMsg.getBodyBuffer().readString();
            if (text == null || !text.equals(messageText)) {
               throw new Exception("received " + text + ", was expecting " + messageText);
            }

            // clean all resources to exit cleanly
            consumer.close();
            session.deleteQueue(queueName);
            session.close();
            sf.close();
            System.out.println("OK");
         } finally {
            locator.close();
         }
      } catch (Throwable t) {
         t.printStackTrace(System.out);

         String allStack = t.getMessage() + "|";
         StackTraceElement[] stackTrace = t.getStackTrace();
         for (StackTraceElement stackTraceElement : stackTrace) {
            allStack += stackTraceElement.toString() + "|";
         }
         // System.out.println(t.getClass().getName());
         // System.out.println(t.getMessage());
         System.out.println(allStack);
         System.exit(1);
      }
   }
}
