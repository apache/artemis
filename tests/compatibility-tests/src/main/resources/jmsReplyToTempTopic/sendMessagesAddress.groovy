package jmsReplyToTempTopic

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

ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616");
try {
    cf.setEnable1xPrefixes(true);
} catch (Throwable totallyIgnored) {
    // older versions will not have this method, dont even bother about seeing the stack trace or exception
}
Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
connection.start();

Queue myQueue = session.createQueue("myQueue");

GroovyRun.assertEquals("myQueue", myQueue.getQueueName());

TemporaryTopic replyTopic = session.createTemporaryTopic();
MessageConsumer consumer = session.createConsumer(replyTopic);


MessageProducer queueProducer = session.createProducer(myQueue)

queueProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
sendMessage(session.createTextMessage("hello"), replyTopic, myQueue, queueProducer);
sendMessage(session.createMapMessage(), replyTopic, myQueue, queueProducer);
sendMessage(session.createObjectMessage(), replyTopic, myQueue, queueProducer);
sendMessage(session.createStreamMessage(), replyTopic, myQueue, queueProducer);
sendMessage(session.createMessage(), replyTopic, myQueue, queueProducer);
session.commit();


for (int i = 0; i < 5; i++) {
    message = consumer.receive(10000);
    GroovyRun.assertNotNull(message);
}
GroovyRun.assertNull(consumer.receiveNoWait());
session.commit();

connection.close();
senderLatch.countDown();


void sendMessage(Message message, TemporaryTopic replyTopic, Queue myQueue, MessageProducer queueProducer) {
    message.setJMSReplyTo(replyTopic);
    queueProducer.send(message);
}