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
import org.junit.Assert;
import org.junit.Test;

import static org.apache.activemq.artemis.jms.client.ActiveMQDestination.QUEUE_QUALIFIED_PREFIX;
import static org.apache.activemq.artemis.jms.client.ActiveMQDestination.TOPIC_QUALIFIED_PREFIX;

public class ActiveMQDestinationTest extends ActiveMQTestBase {



   @Test
   public void testEquals() throws Exception {
      String destinationName = RandomUtil.randomString();
      String address = QUEUE_QUALIFIED_PREFIX + destinationName;
      ActiveMQDestination destination = (ActiveMQDestination) ActiveMQDestination.fromPrefixedName(address);
      ActiveMQDestination sameDestination = (ActiveMQDestination) ActiveMQDestination.fromPrefixedName(address);
      ActiveMQDestination differentDestination = (ActiveMQDestination) ActiveMQDestination.fromPrefixedName(address + RandomUtil.randomString());

      Assert.assertFalse(destination.equals(null));
      Assert.assertTrue(destination.equals(destination));
      Assert.assertTrue(destination.equals(sameDestination));
      Assert.assertFalse(destination.equals(differentDestination));
   }

   @Test
   public void testFromAddressWithQueueAddressPrefix() throws Exception {
      String destinationName = RandomUtil.randomString();
      String address = QUEUE_QUALIFIED_PREFIX + destinationName;
      ActiveMQDestination destination = (ActiveMQDestination) ActiveMQDestination.fromPrefixedName(address);
      Assert.assertTrue(destination instanceof Queue);
      Assert.assertEquals(destinationName, ((Queue) destination).getQueueName());
   }

   @Test
   public void testFromAddressWithTopicAddressPrefix() throws Exception {
      String destinationName = RandomUtil.randomString();
      String address = TOPIC_QUALIFIED_PREFIX + destinationName;
      ActiveMQDestination destination = (ActiveMQDestination) ActiveMQDestination.fromPrefixedName(address);
      Assert.assertTrue(destination instanceof Topic);
      Assert.assertEquals(destinationName, ((Topic) destination).getTopicName());
   }

   @Test
   public void testFromAddressWithInvalidPrefix() throws Exception {
      String invalidPrefix = "junk";
      String destinationName = RandomUtil.randomString();
      String address = invalidPrefix + destinationName;
      ActiveMQDestination destination = (ActiveMQDestination) ActiveMQDestination.fromPrefixedName(address);
      Assert.assertTrue(destination instanceof Destination);
   }

   @Test
   public void testQueueToStringNPE() {
      ActiveMQDestination destination = new ActiveMQQueue();
      try {
         System.out.println("Destination: " + destination.toString());
      } catch (NullPointerException npe) {
         Assert.fail("Caught NPE!");
      }
   }

   @Test
   public void testTopicToStringNPE() {
      ActiveMQDestination destination = new ActiveMQTopic();
      try {
         System.out.println("Destination: " + destination.toString());
      } catch (NullPointerException npe) {
         Assert.fail("Caught NPE!");
      }
   }


}
