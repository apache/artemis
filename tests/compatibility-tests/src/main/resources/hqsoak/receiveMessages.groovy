package hqsoak

import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
String clientType = arg[0];
int consumers = Integer.parseInt(arg[1])
final int messagesPerConsumer = Integer.parseInt(arg[2])

try {
    legacyOption = legacy;
} catch (Throwable e) {
    legacyOption = false;
}


if (legacyOption) {
    queueName = "jms.queue.queue"
    topicName = "jms.topic.topic"
} else {
    queueName = "queue";
    topicName = "topic";
}

String textBody = "a rapadura e doce mas nao e mole nao";


if (clientType.startsWith("ARTEMIS")) {
    // Can't depend directly on artemis, otherwise it wouldn't compile in hornetq
    GroovyRun.evaluate("clients/artemisClient.groovy", "serverArg", "ARTEMIS");
} else {
    // Can't depend directly on hornetq, otherwise it wouldn't compile in artemis
    GroovyRun.evaluate("clients/hornetqClient.groovy", "serverArg");
}

errorsConsumer = new AtomicInteger(0);
final AtomicInteger running = new AtomicInteger(0);
CountDownLatch latchStarted = new CountDownLatch(consumers);

for (int i = 0; i < consumers; i++) {
    Runnable r = new Runnable() {
        @Override
        void run() {
            try {
                int threadid = running.incrementAndGet();
                Connection connection = cf.createConnection();
                connection.setClientID(clientType + threadid)
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(topicName);
                connection.start();
                latchStarted.countDown();
                MessageConsumer consumer = session.createDurableSubscriber(topic, "test")

                for (int m = 0; m < messagesPerConsumer; m++) {
                    TextMessage msg = consumer.receive(5000);
                    if (msg == null) {
                        errorsConsumer.incrementAndGet();
                        System.err.println("Could not receive message")
                        break;
                    }
                    reusableLatch.countDown();
                }
                connection.close();
            }
            catch (Exception e) {
                e.printStackTrace()
                errorsConsumer.incrementAndGet();
            } finally {
                running.decrementAndGet();
            }
        }
    }
    Thread t = new Thread(r);
    t.start();
}
if (!latchStarted.await(10, TimeUnit.SECONDS)) {
    System.err.println("Could not start consumers")
    errorsConsumer.incrementAndGet()
}
return running;

