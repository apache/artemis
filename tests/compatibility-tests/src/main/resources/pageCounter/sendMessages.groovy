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

package pageCounter

import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*

// starts an artemis server
String serverType = arg[0];
String protocol = arg[1];
int messages = Integer.parseInt(arg[2]);

// Can't depend directly on artemis, otherwise it wouldn't compile in hornetq
if (protocol != null && protocol.equals("AMQP")) {
    GroovyRun.evaluate("clients/artemisClientAMQP.groovy", "serverArg", serverType, protocol);
} else {
    GroovyRun.evaluate("clients/artemisClient.groovy", "serverArg", serverType);
}

Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
Queue destination = session.createQueue("queue")

MessageProducer producer = session.createProducer(destination);
producer.setDeliveryMode(DeliveryMode.PERSISTENT);

for (int i = 0; i < messages; i++) {
    TextMessage message = session.createTextMessage("Message " + i);
    producer.send(message);
    if (i % 100 == 0) {
        session.commit();
    }
}

session.commit();

connection.close();