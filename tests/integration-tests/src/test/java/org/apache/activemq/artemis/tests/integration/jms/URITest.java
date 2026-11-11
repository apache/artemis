/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.jms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

public class URITest {

   @Test
   public void testParseURIs() throws Throwable {
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactory("(tcp://localhost:61616,tcp://localhost:61617)?blockOnDurableSend=false", "some name");
      assertEquals(2, ((ServerLocatorImpl) factory.getServerLocator()).getInitialConnectors().length);

      ActiveMQConnectionFactory factory2 = new ActiveMQConnectionFactory("(tcp://localhost:61614,tcp://localhost:61616)?blockOnDurableSend=false");
      assertEquals(2, ((ServerLocatorImpl) factory2.getServerLocator()).getInitialConnectors().length);

      ServerLocator locator = ServerLocatorImpl.newLocator("(tcp://localhost:61616,tcp://localhost:61617)?blockOnDurableSend=false");
      assertEquals(2, ((ServerLocatorImpl) locator).getInitialConnectors().length);

      ServerLocator locator2 = ActiveMQClient.createServerLocator("(tcp://localhost:61616,tcp://localhost:61617)?blockOnDurableSend=false");
      assertEquals(2, ((ServerLocatorImpl) locator2).getInitialConnectors().length);

   }

}
