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
package org.apache.activemq.artemis.tests.unit.ra.referenceable;

import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DestinationObjectFactoryTest extends ActiveMQTestBase {



   @Test
   public void testReference() throws Exception {
      ActiveMQDestination queue = (ActiveMQDestination) ActiveMQJMSClient.createQueue(RandomUtil.randomUUIDString());
      Reference reference = queue.getReference();
      String factoryName = reference.getFactoryClassName();
      ObjectFactory factory = (ObjectFactory) Class.forName(factoryName).getDeclaredConstructor().newInstance();
      Object object = factory.getObjectInstance(reference, null, null, null);
      assertNotNull(object);
      assertInstanceOf(ActiveMQDestination.class, object);
      assertEquals(queue, object);
   }


}
