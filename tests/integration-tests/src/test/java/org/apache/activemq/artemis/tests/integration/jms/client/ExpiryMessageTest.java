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
package org.apache.activemq.artemis.tests.integration.jms.client;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.JMSTestBase;

public class ExpiryMessageTest extends JMSTestBase {



   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      return super.createDefaultConfig(netty).setMessageExpiryScanPeriod(50);
   }

   @Test
   public void testSendTopicNoSubscription() throws Exception {
      Topic topic = createTopic("test-topic");
      AddressControl control = ManagementControlHelper.createAddressControl(SimpleString.of(topic.getTopicName()), mbeanServer);

      Connection conn2 = cf.createConnection();

      conn2.setClientID("client1");

      Session sess2 = conn2.createSession(true, Session.SESSION_TRANSACTED);

      sess2.createDurableSubscriber(topic, "client-sub1");
      sess2.createDurableSubscriber(topic, "client-sub2");

      conn2.close();

      conn = cf.createConnection();
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer prod = sess.createProducer(topic);
      prod.setTimeToLive(100);

      for (int i = 0; i < 100; i++) {
         TextMessage txt = sess.createTextMessage("txt");
         prod.send(txt);
      }

      sess.commit();

      conn.close();

      Wait.assertEquals(0, control::getMessageCount);

   }



}
