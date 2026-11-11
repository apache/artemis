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
package org.apache.activemq.artemis.tests.integration.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

public class SpringIntegrationTest extends ActiveMQTestBase {

   @Test
   public void testSpring() throws Exception {
      ApplicationContext context = null;
      try {
         context = new ClassPathXmlApplicationContext(new String[]{"spring-jms-beans.xml"});
         MessageSender sender = (MessageSender) context.getBean("MessageSender");
         ExampleListener.latch.countUp();
         sender.send("Hello world");
         ExampleListener.latch.await(10, TimeUnit.SECONDS);
         Thread.sleep(500);
         assertEquals("Hello world", ExampleListener.lastMessage);
         ((ActiveMQConnectionFactory) sender.getConnectionFactory()).close();
      } finally {
         try {
            if (context != null) {
               DefaultMessageListenerContainer container = (DefaultMessageListenerContainer) context.getBean("listenerContainer");
               container.stop();
            }
         } catch (Throwable ignored) {
            ignored.printStackTrace();
         }
         try {
            if (context != null) {
               EmbeddedActiveMQ jms = (EmbeddedActiveMQ) context.getBean("EmbeddedActiveMQ");
               jms.stop();
            }
         } catch (Throwable ignored) {
            ignored.printStackTrace();
         }
      }

   }
}
