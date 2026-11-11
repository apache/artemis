package serial
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

// Create a client connection factory

import org.apache.activemq.artemis.tests.compatibility.GroovyRun;
import javax.jms.*;
import org.apache.activemq.artemis.jms.client.*

file = arg[0]
method = arg[1]
version = arg[2]

if (method.equals("write")) {
    cf = new ActiveMQConnectionFactory("tcp://localhost:61616?confirmationWindowSize=1048576&blockOnDurableSend=false");
    queue = new ActiveMQQueue("queue");
    topic = new ActiveMQTopic("topic")

    if (version.equals("ARTEMIS-155")) {
        destination = new ActiveMQDestination("address", "name", false, true, null)
    } else {
        destination = new ActiveMQDestination("address", "name", ActiveMQDestination.TYPE.DESTINATION, null)
    }

    ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(file));
    objectOutputStream.writeObject(cf);
    objectOutputStream.writeObject(queue)
    objectOutputStream.writeObject(topic)
    objectOutputStream.writeObject(destination)
    objectOutputStream.close();
} else {
    ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file))

    cf = inputStream.readObject();
    queue = inputStream.readObject()
    topic = inputStream.readObject()
    destination = inputStream.readObject()
    inputStream.close();
}

GroovyRun.assertTrue(!cf.getServerLocator().isBlockOnDurableSend());
GroovyRun.assertEquals(1048576, cf.getServerLocator().getConfirmationWindowSize())
GroovyRun.assertEquals(destination.getName(), "name")
GroovyRun.assertEquals(destination.getAddress(), "address")

Connection connection = cf.createConnection();
Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
MessageConsumer consumer = session.createConsumer(queue);
MessageProducer topicProducer = session.createProducer(topic)
connection.close();


