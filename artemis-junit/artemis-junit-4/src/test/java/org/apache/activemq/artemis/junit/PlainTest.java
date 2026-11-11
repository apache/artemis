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
package org.apache.activemq.artemis.junit;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class PlainTest {

   @Rule
   public EmbeddedJMSResource server = new EmbeddedJMSResource(true);

   @Test
   public void testPlain() throws Exception {
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory();
      Connection connection = cf.createConnection();
      Session session = connection.createSession();
      MessageProducer producer = session.createProducer(session.createQueue("queue"));
      producer.send(session.createTextMessage("hello"));
      connection.start();
      MessageConsumer consumer = session.createConsumer(session.createQueue("queue"));
      Assert.assertNotNull(consumer.receive(5000));
      connection.close();

   }

}
