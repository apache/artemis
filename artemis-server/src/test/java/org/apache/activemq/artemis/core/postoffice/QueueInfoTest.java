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
package org.apache.activemq.artemis.core.postoffice;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class QueueInfoTest {

   private static QueueInfo queueInfo(final String address) {
      return new QueueInfo(SimpleString.of("routing"), SimpleString.of("cluster"), SimpleString.of(address), null, 1L, 0);
   }

   @Test
   public void matchesAddressRespectsPrefixAndFilterList() {
      QueueInfo info = queueInfo("eu.orders");
      Assertions.assertTrue(info.matchesAddress(SimpleString.of("eu")));
      Assertions.assertTrue(info.matchesAddress(SimpleString.of("eu.orders")));
      // "us" does not match the queue address, so the info must be filtered out
      Assertions.assertFalse(info.matchesAddress(SimpleString.of("us")));
      Assertions.assertTrue(info.matchesAddress(SimpleString.of("us,eu")));
      Assertions.assertFalse(info.matchesAddress(SimpleString.of("us,na")));
   }

   @Test
   public void matchesAddressReturnsFalseForNull() {
      Assertions.assertFalse(queueInfo("eu.orders").matchesAddress(null));
   }
}
