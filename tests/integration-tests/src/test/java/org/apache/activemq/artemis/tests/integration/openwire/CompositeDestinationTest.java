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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

public class CompositeDestinationTest extends BasicOpenWireTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      AddressInfo addressInfo = new AddressInfo(SimpleString.of("p.IN"), RoutingType.MULTICAST);
      this.server.addAddressInfo(addressInfo);
      addressInfo = new AddressInfo(SimpleString.of("q.IN"), RoutingType.MULTICAST);
      this.server.addAddressInfo(addressInfo);
   }

   @Test
   public void testDurableSub() throws Exception {
      Connection conn = factory.createConnection();
      try {
         conn.setClientID("my-client");
         conn.start();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic("p.IN,q.IN");

         TopicSubscriber sub1 = session.createDurableSubscriber(topic, "durable1", null, false);

         MessageProducer producer = session.createProducer(topic);

         final int num = 10;

         for (int i = 0; i < num; i++) {
            producer.send(session.createTextMessage("msg" + i));
         }

         int count = 0;
         TextMessage msg = (TextMessage) sub1.receive(2000);
         while (msg != null) {
            count++;
            msg = (TextMessage) sub1.receive(2000);
         }
         assertEquals(2 * num, count, "Consumer should receive all messages from every topic");
      } finally {
         conn.close();
      }
   }
}
