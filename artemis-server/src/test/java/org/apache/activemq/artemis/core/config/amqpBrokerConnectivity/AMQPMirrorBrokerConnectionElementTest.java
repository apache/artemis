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

package org.apache.activemq.artemis.core.config.amqpBrokerConnectivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.junit.jupiter.api.Test;

/**
 * Test for the AMQPMirrorBrokerConnectionElement basic API
 */
public class AMQPMirrorBrokerConnectionElementTest {

   @Test
   public void testEquals() {
      AMQPMirrorBrokerConnectionElement mirror1 = new AMQPMirrorBrokerConnectionElement();
      AMQPMirrorBrokerConnectionElement mirror2 = new AMQPMirrorBrokerConnectionElement();

      // Durable
      mirror1.setDurable(!mirror1.isDurable());
      assertNotEquals(mirror1, mirror2);
      mirror2.setDurable(mirror1.isDurable());
      assertEquals(mirror1, mirror2);

      // Queue Create
      mirror1.setQueueCreation(!mirror1.isQueueCreation());
      assertNotEquals(mirror1, mirror2);
      mirror2.setQueueCreation(mirror1.isQueueCreation());
      assertEquals(mirror1, mirror2);

      // Queue Remove
      mirror1.setQueueRemoval(!mirror1.isQueueRemoval());
      assertNotEquals(mirror1, mirror2);
      mirror2.setQueueRemoval(mirror1.isQueueRemoval());
      assertEquals(mirror1, mirror2);

      // Message Acknowledgement
      mirror1.setMessageAcknowledgements(!mirror1.isMessageAcknowledgements());
      assertNotEquals(mirror1, mirror2);
      mirror2.setMessageAcknowledgements(mirror1.isMessageAcknowledgements());
      assertEquals(mirror1, mirror2);

      // Sync
      mirror1.setSync(!mirror1.isSync());
      assertNotEquals(mirror1, mirror2);
      mirror2.setSync(mirror1.isSync());
      assertEquals(mirror1, mirror2);

      // Mirror SNF
      mirror1.setMirrorSNF(SimpleString.of("test"));
      assertNotEquals(mirror1, mirror2);
      mirror2.setMirrorSNF(SimpleString.of("test"));
      assertEquals(mirror1, mirror2);

      // Address Filter
      mirror1.setAddressFilter("test");
      assertNotEquals(mirror1, mirror2);
      mirror2.setAddressFilter("test");
      assertEquals(mirror1, mirror2);
   }

   @Test
   public void testHashCode() {
      AMQPMirrorBrokerConnectionElement mirror1 = new AMQPMirrorBrokerConnectionElement();
      AMQPMirrorBrokerConnectionElement mirror2 = new AMQPMirrorBrokerConnectionElement();

      // Durable
      mirror1.setDurable(!mirror1.isDurable());
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setDurable(mirror1.isDurable());
      assertEquals(mirror1.hashCode(), mirror2.hashCode());

      // Queue Create
      mirror1.setQueueCreation(!mirror1.isQueueCreation());
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setQueueCreation(mirror1.isQueueCreation());
      assertEquals(mirror1.hashCode(), mirror2.hashCode());

      // Queue Remove
      mirror1.setQueueRemoval(!mirror1.isQueueRemoval());
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setQueueRemoval(mirror1.isQueueRemoval());
      assertEquals(mirror1.hashCode(), mirror2.hashCode());

      // Message Acknowledgement
      mirror1.setMessageAcknowledgements(!mirror1.isMessageAcknowledgements());
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setMessageAcknowledgements(mirror1.isMessageAcknowledgements());
      assertEquals(mirror1.hashCode(), mirror2.hashCode());

      // Sync
      mirror1.setSync(!mirror1.isSync());
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setSync(mirror1.isSync());
      assertEquals(mirror1.hashCode(), mirror2.hashCode());

      // Mirror SNF
      mirror1.setMirrorSNF(SimpleString.of("test"));
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setMirrorSNF(SimpleString.of("test"));
      assertEquals(mirror1.hashCode(), mirror2.hashCode());

      // Address Filter
      mirror1.setAddressFilter("test");
      assertNotEquals(mirror1.hashCode(), mirror2.hashCode());
      mirror2.setAddressFilter("test");
      assertEquals(mirror1.hashCode(), mirror2.hashCode());
   }
}
