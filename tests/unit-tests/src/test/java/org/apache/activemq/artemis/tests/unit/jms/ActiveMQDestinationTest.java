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
package org.apache.activemq.artemis.tests.unit.jms;

import javax.jms.Destination;
import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.jms.client.ActiveMQDestination.QUEUE_QUALIFIED_PREFIX;
import static org.apache.activemq.artemis.jms.client.ActiveMQDestination.TOPIC_QUALIFIED_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ActiveMQDestinationTest extends ActiveMQTestBase {



   @Test
   public void testEquals() throws Exception {
      String destinationName = RandomUtil.randomUUIDString();
      String address = QUEUE_QUALIFIED_PREFIX + destinationName;
      ActiveMQDestination destination = ActiveMQDestination.fromPrefixedName(address);
      ActiveMQDestination sameDestination = ActiveMQDestination.fromPrefixedName(address);
      ActiveMQDestination differentDestination = ActiveMQDestination.fromPrefixedName(address + RandomUtil.randomUUIDString());

      assertFalse(destination.equals(null));
      assertTrue(destination.equals(destination));
      assertTrue(destination.equals(sameDestination));
      assertFalse(destination.equals(differentDestination));
   }

   @Test
   public void testFromAddressWithQueueAddressPrefix() throws Exception {
      String destinationName = RandomUtil.randomUUIDString();
      String address = QUEUE_QUALIFIED_PREFIX + destinationName;
      ActiveMQDestination destination = ActiveMQDestination.fromPrefixedName(address);
      assertInstanceOf(Queue.class, destination);
      assertEquals(destinationName, ((Queue) destination).getQueueName());
   }

   @Test
   public void testFromAddressWithTopicAddressPrefix() throws Exception {
      String destinationName = RandomUtil.randomUUIDString();
      String address = TOPIC_QUALIFIED_PREFIX + destinationName;
      ActiveMQDestination destination = ActiveMQDestination.fromPrefixedName(address);
      assertInstanceOf(Topic.class, destination);
      assertEquals(destinationName, ((Topic) destination).getTopicName());
   }

   @Test
   public void testFromAddressWithInvalidPrefix() throws Exception {
      String invalidPrefix = "junk";
      String destinationName = RandomUtil.randomUUIDString();
      String address = invalidPrefix + destinationName;
      ActiveMQDestination destination = ActiveMQDestination.fromPrefixedName(address);
      assertInstanceOf(Destination.class, destination);
   }

   @Test
   public void testQueueToStringNPE() {
      ActiveMQDestination destination = new ActiveMQQueue();
      try {
         System.out.println("Destination: " + destination.toString());
      } catch (NullPointerException npe) {
         fail("Caught NPE!");
      }
   }

   @Test
   public void testTopicToStringNPE() {
      ActiveMQDestination destination = new ActiveMQTopic();
      try {
         System.out.println("Destination: " + destination.toString());
      } catch (NullPointerException npe) {
         fail("Caught NPE!");
      }
   }


}
