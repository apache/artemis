package meshTest

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

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;

String operation = arg[2];

queueName = "jms.queue.queue"

Map<String, Object> connectionParams = new HashMap<String, Object>();
connectionParams.put(org.hornetq.core.remoting.impl.netty.TransportConstants.HOST_PROP_NAME, "127.0.0.1");
connectionParams.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, 61616);


ServerLocator serverLocator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName(), connectionParams));
ClientSessionFactory sf = serverLocator.createSessionFactory();

ClientSession session = null;
session = sf.createSession(true, true);
session.start();

if (operation.equals("sendMessages")) {
    ClientProducer producer = session.createProducer(queueName);

    ClientMessage bm = session.createMessage(false);
    byte[] body = new byte[40000];
    new Random().nextBytes(body);
    bm.getBodyBuffer().writeBytes(body);

    producer.send(bm);
    ClientConsumer messageConsumer = session.createConsumer(queueName);

    ClientMessage messageReceived = null;

    messageReceived = messageConsumer.receive();
    ByteArrayOutputStream bao = new ByteArrayOutputStream();
    messageReceived.saveToOutputStream(bao);

    messageReceived.acknowledge();
    messageConsumer.close();

    body = new byte[40000];
    new Random().nextBytes(body);
    bm.getBodyBuffer().writeBytes(body);
    producer.send(bm);
} else {

    ClientConsumer messageConsumer = session.createConsumer(queueName);
    ClientMessage messageReceived = messageConsumer.receive();
    ByteArrayOutputStream bao = new ByteArrayOutputStream();
    messageReceived.saveToOutputStream(bao);

    messageReceived.acknowledge();

}

session.close();
sf.close();
serverLocator.close();


