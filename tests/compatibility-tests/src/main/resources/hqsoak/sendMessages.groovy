package hqsoak

import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.*
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
producers = Integer.parseInt(arg[1])
numberOfMessages = Integer.parseInt(arg[2])

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

if (clientType.startsWith("ARTEMIS")) {
    // Can't depend directly on artemis, otherwise it wouldn't compile in hornetq
    GroovyRun.evaluate("clients/artemisClient.groovy", "serverArg", "ARTEMIS");
} else {
    // Can't depend directly on hornetq, otherwise it wouldn't compile in artemis
    GroovyRun.evaluate("clients/hornetqClient.groovy", "serverArg");
}

errorsProducer = new AtomicInteger(0);
final AtomicInteger ran = new AtomicInteger(0);

StringBuffer bufferStr = new StringBuffer();
for (int i = 0; i < 200 * 1024; i++) {
    bufferStr.append(" ");
}

largeMessageBody = bufferStr.toString();

final def cfLocal = cf;
final def topicNameLocal = topicName;
final def numberOfMessagesLocal = numberOfMessages;
final def multiplyFactorLocal = multiplyFactor;
final def reusableLatchLocal = reusableLatch;
final def largeMessageBodyLocal = largeMessageBody;
final def errorsProducerLocal = errorsProducer;

for (int i = 0; i < producers; i++) {
    Runnable r = new Runnable() {
        @Override
        void run() {
            try {
                Connection connection = cfLocal.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(topicNameLocal);
                MessageProducer producer = session.createProducer(topic);

                for (int m = 0; m < numberOfMessagesLocal; m++) {

                    for (int j = 0; j < multiplyFactorLocal; j++) {
                        reusableLatchLocal.countUp()
                    }

                    if (m % 10 == 0) {
                        producer.send(session.createTextMessage(largeMessageBodyLocal));
                        //System.out.println("Sending regular ")
                    } else {
                        producer.send(session.createTextMessage("This is a regular message"));
                        //System.out.println("Sending large ")
                    }

                    reusableLatchLocal.await(10, TimeUnit.SECONDS)
                }

                connection.close();
            }
            catch (Exception e) {
                e.printStackTrace()
                errorsProducerLocal.incrementAndGet();
            } finally {
                ran.incrementAndGet();
            }
        }
    }
    Thread t = new Thread(r);
    t.start();
}

return ran;

