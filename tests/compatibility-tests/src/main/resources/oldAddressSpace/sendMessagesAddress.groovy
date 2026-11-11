package oldAddressSpace

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

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

ConnectionFactory cf = new ActiveMQConnectionFactory();
Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

String clientType = arg[0];

Queue queue;
Topic topic;

if (clientType.startsWith("ARTEMIS-1") || clientType.startsWith("HORNETQ")) {
    queue = session.createQueue("myQueue");
    topic = session.createTopic("myTopic");
} else {
    queue = session.createQueue("jms.queue.myQueue");
    topic = session.createTopic("jms.topic.myTopic");
}

MessageProducer queueProducer = session.createProducer(queue)
MessageProducer topicProducer = session.createProducer(topic);

queueProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
for (int i = 0; i < 500; i++) {
    BytesMessage bytesMessage = session.createBytesMessage();
    bytesMessage.writeBytes(new byte[512]);
    queueProducer.send(bytesMessage);
    // we send a big batch as that should be enough to cause blocking on the address
    // if the wrong address is being used
    if (i % 100 == 0) {
        session.commit();
    }
}
session.commit();

queueProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
for (int i = 0; i < 500; i++) {
    BytesMessage bytesMessage = session.createBytesMessage();
    bytesMessage.writeBytes(new byte[512]);
    topicProducer.send(bytesMessage);
    // we send a big batch as that should be enough to cause blocking on the address
    // if the wrong address is being used
    if (i % 100 == 0) {
        session.commit();
    }
}
session.commit();

connection.close();
senderLatch.countDown();



