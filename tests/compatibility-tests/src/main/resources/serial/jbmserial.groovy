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

import org.apache.activemq.artemis.tests.compatibility.GroovyRun
import org.jboss.marshalling.Marshaller
import org.jboss.marshalling.MarshallerFactory
import org.jboss.marshalling.Marshalling
import org.jboss.marshalling.MarshallingConfiguration
import org.jboss.marshalling.Unmarshaller

import javax.jms.*;
import org.apache.activemq.artemis.jms.client.*


file = arg[0]
method = arg[1]
version = arg[2]

// Get the factory for the "river" marshalling protocol
final MarshallerFactory factory = Marshalling.getProvidedMarshallerFactory("river");

// Create a configuration
final MarshallingConfiguration configuration = new MarshallingConfiguration();
// Use version 3
configuration.setVersion(3);

if (method.equals("write")) {
    cf = new ActiveMQConnectionFactory("tcp://localhost:61616?confirmationWindowSize=1048576&blockOnDurableSend=false");
    queue = new ActiveMQQueue("queue");
    topic = new ActiveMQTopic("topic")
    temporary = ActiveMQDestination.createTemporaryQueue("whatever")
    temporaryTopic = ActiveMQDestination.createTemporaryTopic("whatever")
    if (version.equals("ARTEMIS-155")) {
        destination = new ActiveMQDestination("address", "name", false, true, null)
    } else {
        destination = new ActiveMQDestination("address", "name", ActiveMQDestination.TYPE.DESTINATION, null)
    }
    Marshaller marshaller = factory.createMarshaller(configuration)
    FileOutputStream fileOutputStream = new FileOutputStream(file)
    marshaller.start(Marshalling.createByteOutput(fileOutputStream));
    marshaller.writeObject(cf);
    marshaller.writeObject(queue)
    marshaller.writeObject(topic)
    marshaller.writeObject(temporary)
    marshaller.writeObject(temporaryTopic)
    marshaller.writeObject(destination)
    marshaller.finish()
    fileOutputStream.close();
} else {
    Unmarshaller unmarshaller = factory.createUnmarshaller(configuration)
    FileInputStream inputfile = new FileInputStream(file)
    unmarshaller.start(Marshalling.createByteInput(inputfile))
    cf = unmarshaller.readObject();
    queue = unmarshaller.readObject()
    topic = unmarshaller.readObject()
    temporary = unmarshaller.readObject()
    temporaryTopic = unmarshaller.readObject()
    destination = unmarshaller.readObject()
}

GroovyRun.assertTrue(!cf.getServerLocator().isBlockOnDurableSend());
GroovyRun.assertEquals(1048576, cf.getServerLocator().getConfirmationWindowSize());
GroovyRun.assertEquals(destination.getName(), "name")
GroovyRun.assertEquals(destination.getAddress(), "address")

Connection connection = cf.createConnection();
Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
ActiveMQDestination queueDest = (ActiveMQDestination)queue;
GroovyRun.assertTrue(queueDest.isQueue())
GroovyRun.assertFalse(queueDest.isTemporary())

ActiveMQDestination topicDest = (ActiveMQDestination)topic
GroovyRun.assertFalse(topicDest.isQueue())
GroovyRun.assertFalse(queueDest.isTemporary())

ActiveMQDestination temporaryDest = (ActiveMQDestination)temporary
GroovyRun.assertTrue(temporaryDest.isQueue())
GroovyRun.assertTrue(temporaryDest.isTemporary())

temporaryDest = (ActiveMQDestination)temporaryTopic
GroovyRun.assertFalse(temporaryDest.isQueue())
GroovyRun.assertTrue(temporaryDest.isTemporary())

MessageConsumer consumer = session.createConsumer(queue);
MessageProducer topicProducer = session.createProducer(topic)
connection.close();


