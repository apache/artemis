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
package org.apache.activemq.artemis.tests.integration.jms.jms2client;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

public class NonExistentQueueTest extends JMSTestBase {

   @Test
   public void sendToNonExistentDestination() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateQueues(false));
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false));
      Destination destination = ActiveMQJMSClient.createTopic("DoesNotExist");
      TransportConfiguration transportConfiguration = new TransportConfiguration(InVMConnectorFactory.class.getName());
      ConnectionFactory localConnectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
      // Using JMS 1 API
      Connection connection = localConnectionFactory.createConnection();
      Session session = connection.createSession();
      try {
         MessageProducer messageProducer = session.createProducer(null);
         messageProducer.send(destination, session.createMessage());
         fail("Succeeded in sending message to a non-existent destination using JMS 1 API!");
      } catch (JMSException e) { // Expected }

      }

      // Using JMS 2 API
      JMSContext context = localConnectionFactory.createContext();
      JMSProducer jmsProducer = context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT);

      try {
         jmsProducer.send(destination, context.createMessage());
         fail("Succeeded in sending message to a non-existent destination using JMS 2 API!");
      } catch (JMSRuntimeException e) { // Expected }
      }

   }
}
