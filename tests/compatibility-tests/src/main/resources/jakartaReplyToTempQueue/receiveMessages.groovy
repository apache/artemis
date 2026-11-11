package jmsReplyToTempQueue

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import jakarta.jms.*

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
Queue myQueue = session.createQueue("myQueue");
MessageConsumer queueConsumer = session.createConsumer(myQueue);
consumerCreated.countDown();
connection.start()

Message message = queueConsumer.receive(5000);
GroovyRun.assertNotNull(message)
session.commit();
queueConsumer.close();

MessageProducer producer = session.createProducer(message.getJMSReplyTo());
message = session.createMessage();
producer.send(message);
session.commit();

connection.close();

latch.countDown();



