package queuepause

import org.apache.activemq.artemis.api.core.management.AddressControl
import org.apache.activemq.artemis.api.core.management.QueueControl
import org.apache.activemq.artemis.api.core.management.ResourceNames
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*

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


String address = arg[0]

ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616?confirmationWindowSize=1048576&blockOnDurableSend=false&ha=true&reconnectAttempts=-1&retryInterval=100")

Connection connection = cf.createConnection();
connection.setClientID("myClientID");
connection.start();
Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
Topic topic = session.createTopic("topic");
Queue queue = session.createQueue("queue");
MessageConsumer consumer;

if (address.equals("topic")) {
   TopicSubscriber subscriber1 = session.createDurableSubscriber(topic, "my-subscription1");
   consumer = subscriber1;
} else {
   consumer = session.createConsumer(queue);
}

QueueControl addressControl = (QueueControl) server.getJMSServerManager().getActiveMQServer().getManagementService().getResource(ResourceNames.QUEUE + address);
GroovyRun.assertNotNull(addressControl)

GroovyRun.assertTrue(addressControl.isPaused())
GroovyRun.assertNull(consumer.receiveNoWait());

int numMessages = 10;

addressControl.resume();
for (int i = 0; i < numMessages; i++) {
   TextMessage m = (TextMessage) consumer.receive(5000);
   GroovyRun.assertNotNull(m);
}
session.commit();
GroovyRun.assertNull(consumer.receiveNoWait());
connection.close();


GroovyRun.assertFalse(addressControl.isPaused())

