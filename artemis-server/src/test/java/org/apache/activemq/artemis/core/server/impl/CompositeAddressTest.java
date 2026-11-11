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
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.utils.CompositeAddress;
import org.junit.jupiter.api.Test;

public class CompositeAddressTest {

   @Test
   public void testSplit() throws Exception {
      String name = "someQueue";
      String name2 = "someAddress::someQueue";
      String qname = CompositeAddress.extractQueueName(name);
      assertEquals(name, qname);
      qname = CompositeAddress.extractQueueName(name2);
      assertEquals(name, qname);
      assertEquals("", CompositeAddress.extractQueueName("address::"));
      assertEquals("", CompositeAddress.extractQueueName("::"));
      assertEquals("queue", CompositeAddress.extractQueueName("::queue"));
      assertEquals("address", CompositeAddress.extractAddressName("address::"));
   }
}
