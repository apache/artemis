package ReplyToTest

import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue
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

cf = new ActiveMQConnectionFactory("tcp://localhost:61616?confirmationWindowSize=1048576&blockOnDurableSend=false&ha=true&reconnectAttempts=-1&retryInterval=100");
Connection connection = cf.createConnection();
connection.start();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
Queue queue = session.createQueue("queue");
QueueBrowser browser = session.createBrowser(queue);

Enumeration<Message> messageEnumeration = browser.getEnumeration();

ArrayList<Message> messages = new ArrayList<>();

while (messageEnumeration.hasMoreElements()) {
    messages.add(messageEnumeration.nextElement());
}

check(messages);

MessageConsumer consumer = session.createConsumer(queue);
messages.clear();

while(true) {
    Message message = consumer.receiveNoWait();
    if (message == null) {
        break;
    }
    messages.add(message);
}

check(messages);

connection.close();

void check(List<Message> messages) {
    Iterator<Message> iterator = messages.iterator();
    Message bareMessage = iterator.next();
    checkMessage(bareMessage);

    BytesMessage bytesMessage = iterator.next();
    checkMessage(bytesMessage);


    MapMessage mapMessage = iterator.next();
    checkMessage(mapMessage);

    ObjectMessage objectMessage = iterator.next();
    checkMessage(objectMessage);

    StreamMessage streamMessage = iterator.next();
    checkMessage(streamMessage);

    TextMessage textMessage = iterator.next();
    checkMessage(textMessage);
}


void checkMessage(Message message) {
    ActiveMQQueue queue = message.getJMSReplyTo();
    GroovyRun.assertEquals("jms.queue.t1", queue.getAddress());
    GroovyRun.assertEquals("t1", queue.getName());
}
