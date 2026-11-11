package ReplyToTest

import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue
import org.apache.activemq.artemis.jms.client.ActiveMQTopic
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

ActiveMQQueue queue = (ActiveMQQueue) ActiveMQJMSClient.createQueue("q1");
GroovyRun.assertEquals("jms.queue.q1", queue.getAddress());
GroovyRun.assertEquals("q1", queue.getQueueName());
ActiveMQTopic topic = (ActiveMQTopic) ActiveMQJMSClient.createTopic("t1");
GroovyRun.assertEquals("jms.topic.t1", topic.getAddress());
GroovyRun.assertEquals("t1", topic.getTopicName());

cf = new ActiveMQConnectionFactory("tcp://localhost:61616?confirmationWindowSize=1048576&blockOnDurableSend=false&ha=true&reconnectAttempts=-1&retryInterval=100");
Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
queue = session.createQueue("queue");
replyToQueue = ActiveMQJMSClient.createQueue("t1");

producer = session.createProducer(queue);
producer.setDeliveryMode(DeliveryMode.PERSISTENT);

Message bareMessage = session.createMessage();
send(bareMessage);

BytesMessage bytesMessage = session.createBytesMessage();
bytesMessage.writeBytes("hello".getBytes());
send(bytesMessage);


MapMessage mapMessage = session.createMapMessage();
send(mapMessage);

ObjectMessage objectMessage = session.createObjectMessage("hello");
send(objectMessage);

send(session.createStreamMessage());

TextMessage textMessage = session.createTextMessage("May the force be with you");
send(textMessage);

session.commit();


void send(Message message) {
    message.setJMSReplyTo(replyToQueue);
    producer.send(message);
}
