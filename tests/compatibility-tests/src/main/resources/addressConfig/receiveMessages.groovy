package addressConfig

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
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


ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory();
Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
Queue queue = session.createQueue("myQueue");

MessageConsumer consumer = session.createConsumer(queue)
connection.start()
for (int i = 0; i < 500; i++) {
    BytesMessage bytesMessage = (BytesMessage) consumer.receive(5000);
    GroovyRun.assertNotNull(bytesMessage)
    if (i % 100) {
        session.commit();
    }
}
session.commit();
connection.close();
cf.close();

// Defined on AddressConfigTest.java at the test with setVariable
latch.countDown();



