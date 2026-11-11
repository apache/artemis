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


import javax.jms.Connection
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;

{
    final String url = "(tcp://localhost:61616)?ha=true&initialConnectAttempts=-1&reconnectAttempts=-1&retryInterval=1000&retryIntervalMultiplier=1.0"
    final String queueName = "Test"
    final int startMessages = Integer.parseInt(arg[0]);
    final int numberOfMessages = Integer.parseInt(arg[1]);

    Connection c;

    try {
        final ConnectionFactory cf = new ActiveMQJMSConnectionFactory(url);

        c = cf.createConnection();

        final Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Queue q = s.createQueue(queueName);

        final MessageProducer p = s.createProducer(q);

        for (int i = startMessages; i < numberOfMessages; i++) {
            final TextMessage m = s.createTextMessage("m" + i);
            p.send(m);
        }
    }
    finally {
        if (c != null) c.close();
    }
}