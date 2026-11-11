package hqselector

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

try {
    legacyOption = legacy;
} catch (Throwable e) {
    legacyOption = false;
}


if (legacyOption) {
    queueName = "jms.queue.queue"
    topicName = "jms.topic.topic"
} else {
    queueName = "queue";
    topicName = "topic";
}

// Can't depend directly on hornetq, otherwise it wouldn't compile in artemis
GroovyRun.evaluate("clients/hornetqClient.groovy", "serverArg");

Connection connection = cf.createConnection();
Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
Queue queue = session.createQueue(queueName)
MessageProducer messageProducer = session.createProducer(queue);
Message message = session.createMessage();
messageProducer.setPriority(5);
messageProducer.send(message);
message = session.createMessage();
messageProducer.setPriority(1)
messageProducer.send(message);

connection.start();

MessageConsumer consumer = session.createConsumer(queue, "HQPriority>=5");

message = consumer.receive(5000);
GroovyRun.assertNotNull(message);
GroovyRun.assertEquals(5, message.getJMSPriority());

message = consumer.receiveNoWait();
GroovyRun.assertNull(message);

connection.close();
