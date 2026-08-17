package selectorWildcard

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

ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616?confirmationWindowSize=1048576&blockOnDurableSend=false&ha=true&reconnectAttempts=-1&retryInterval=100")

Connection connection = cf.createConnection()

try {
   connection.setClientID("selectorWildcardClient")
   connection.start()
   Session session = connection.createSession(true, Session.SESSION_TRANSACTED)
   Topic topic = session.createTopic("selectorWildcardTopic")


   TopicSubscriber subscriber = session.createDurableSubscriber(topic, "my-wildcard-sub", "prop LIKE '%a%a%a%a%a%a'", false)

   MessageProducer producer = session.createProducer(topic)
   producer.setDeliveryMode(DeliveryMode.PERSISTENT)

   int numMessages = 10
   for (int i = 0; i < numMessages; i++) {
      TextMessage msg = session.createTextMessage("msg" + i)
      msg.setStringProperty("prop", "XaXaXaXaXaXa")
      producer.send(msg)
   }
   session.commit()

   TopicSubscriber subscriberConsecutive = session.createDurableSubscriber(topic, "my-consecutive-wildcard-sub", "prop LIKE '%%%S'", false)

   for (int i = 0; i < numMessages; i++) {
      TextMessage msg = session.createTextMessage("consecutive-msg" + i)
      msg.setStringProperty("prop", "endsWithS");
      producer.send(msg)
   }
   session.commit()

// Verify messages are received before stop
   for (int i = 0; i < numMessages; i++) {
      TextMessage m = (TextMessage) subscriber.receive(5000)
      GroovyRun.assertNotNull(m)
      GroovyRun.assertEquals("msg" + i, m.getText())
   }
   session.rollback()

   for (int i = 0; i < numMessages; i++) {
      TextMessage m = (TextMessage) subscriberConsecutive.receive(5000)
      GroovyRun.assertNotNull(m)
      GroovyRun.assertEquals("consecutive-msg" + i, m.getText())
   }
   session.rollback()

   subscriber.close()
   subscriberConsecutive.close()
} finally {
   connection.close()
}
