package multicastTest

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

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQConnection
import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*

ActiveMQConnectionFactory consumerFactory = new ActiveMQConnectionFactory("tcp://localhost:61616")
Connection connection = consumerFactory.createConnection()
connection.start()

Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

Destination topicDest = session.createTopic("myAddress")
MessageProducer producer = session.createProducer(topicDest)
String textBody = "multicast compatibility test message"
producer.send(session.createTextMessage(textBody))
producer.close()

Destination queueDest = session.createQueue("myAddress::myQueue")
MessageConsumer consumer = session.createConsumer(queueDest)
TextMessage message = (TextMessage) consumer.receive(5000)
GroovyRun.assertNotNull(message)
GroovyRun.assertEquals(textBody, message.getText())
println("Received Message :: " + message.getText())
consumer.close()

println(">> Consumer Closed")

((ActiveMQConnection) connection).getSessionFactory().getConnection().destroy()
println(">> END >>")
connection.close()
