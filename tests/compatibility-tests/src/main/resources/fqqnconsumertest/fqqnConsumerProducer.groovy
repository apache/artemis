package fqqnconsumertest

import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

// starts an artemis server
String serverType = arg[0];
String clientType = arg[1];
String operation = arg[2];


String queueName = "address::queue";


String textBody = "a rapadura e doce mas nao e mole nao";

if (clientType.startsWith("ARTEMIS")) {
    // Can't depend directly on artemis, otherwise it wouldn't compile in hornetq
    GroovyRun.evaluate("clients/artemisClient.groovy", "serverArg", serverType);
} else {
    // Can't depend directly on hornetq, otherwise it wouldn't compile in artemis
    GroovyRun.evaluate("clients/hornetqClient.groovy", "serverArg");
}


Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
Queue queue = session.createQueue(queueName);

if (operation.equals("sendMessage")) {

    MessageProducer producer = session.createProducer(queue);
    producer.setDeliveryMode(DeliveryMode.PERSISTENT);
    for (int i = 0; i < 10; i++) {
        producer.send(session.createTextMessage(textBody + i));
    }

    session.commit();

    connection.close();
} else if (operation.equals("receiveMessage")) {
    MessageConsumer consumer = session.createConsumer(queue);
    connection.start();

    for (int i = 0; i < 10; i++) {
        TextMessage message = consumer.receive(1000);
        GroovyRun.assertNotNull(message);
        GroovyRun.assertEquals(textBody + i, message.getText());
    }

    GroovyRun.assertNull(consumer.receiveNoWait());
    connection.close();
} else {
    throw new RuntimeException("Invalid operation " + operation);
}




