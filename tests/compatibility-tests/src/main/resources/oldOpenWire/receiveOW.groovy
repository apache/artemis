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


import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.MessageConsumer
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;

{
    final ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(tcp://localhost:61616)");
    connection = cf.createConnection();
    try {

        final int numberOfMessages = Integer.parseInt(arg[0])
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Queue queue = session.createQueue("Test");
        final MessageConsumer consumer = session.createConsumer(queue)
        connection.start();

        for (int i = 0; i < numberOfMessages; i++) {
            final TextMessage tm = (TextMessage) consumer.receive(1000);
            GroovyRun.assertNotNull(tm)
            GroovyRun.assertEquals("m" + i, tm.getText());
        }
    } finally {
        connection.close();
    }
}