/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.jms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.Test;

public class RedeployTempTest extends ActiveMQTestBase {

   @Test
   public void testRedeployAddressQueueOpenWire() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("RedeployTempTest-reload-temp.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("RedeployTempTest-reload-temp-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      ConnectionFactory connectionFactory = new org.apache.activemq.ActiveMQConnectionFactory();
      Connection connection = connectionFactory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination destination = session.createQueue("queue");
      MessageProducer messageProducer = session.createProducer(destination);

      Destination replyTo = session.createTemporaryQueue();
      Message message = session.createTextMessage("hello");
      message.setJMSReplyTo(replyTo);
      messageProducer.send(message);

      try {
         latch.await(10, TimeUnit.SECONDS);

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         try (Connection connectionConsumer = connectionFactory.createConnection()) {
            connectionConsumer.start();
            try (Session sessionConsumer = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
               Destination destinationConsumer = session.createQueue("queue");
               MessageConsumer messageConsumer = sessionConsumer.createConsumer(destinationConsumer);

               Message receivedMessage = messageConsumer.receive(1000);
               assertEquals("hello", ((TextMessage) receivedMessage).getText());

               Destination replyToDest = receivedMessage.getJMSReplyTo();
               Message message1 = sessionConsumer.createTextMessage("hi there");

               session.createProducer(replyToDest).send(message1);
            }
         }

         MessageConsumer messageConsumerProducer = session.createConsumer(replyTo);
         Message message2 = messageConsumerProducer.receive(1000);
         assertNotNull(message2);
         assertEquals("hi there", ((TextMessage) message2).getText());

      } finally {
         connection.close();
         embeddedActiveMQ.stop();
      }
   }

   private AddressSettings getAddressSettings(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch(address);
   }

   private Set<Role> getSecurityRoles(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch(address);
   }

   private AddressInfo getAddressInfo(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getPostOffice().getAddressInfo(SimpleString.of(address));
   }

   private org.apache.activemq.artemis.core.server.Queue getQueue(EmbeddedActiveMQ embeddedActiveMQ,
                                                                  String queueName) throws Exception {
      QueueBinding queueBinding = (QueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of(queueName));
      return queueBinding == null ? null : queueBinding.getQueue();
   }

   private List<String> listQueuesNamesForAddress(EmbeddedActiveMQ embeddedActiveMQ, String address) throws Exception {
      return embeddedActiveMQ.getActiveMQServer().getPostOffice().listQueuesForAddress(SimpleString.of(address)).stream().map(org.apache.activemq.artemis.core.server.Queue::getName).map(SimpleString::toString).collect(Collectors.toList());
   }

}
