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
package org.apache.activemq.artemis.core.management.impl.view;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ProducerViewTest extends ViewTest {

   @Test
   public void testDefaultViewNullOptions() {
      ProducerView producerView = new ProducerView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      producerView.setOptions(null);
   }

   @Test
   public void testDefaultViewEmptyOptions() {
      ProducerView producerView = new ProducerView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      producerView.setOptions("");
   }

   @Test
   public void testViewLegacySort() {
      ProducerView view = new ProducerView(Mockito.mock(ActiveMQServer.class));
      assertNotEquals("user", view.getDefaultOrderColumn());
      view.setOptions(createLegacyJsonFilter("user", "EQUALS", "123", "user", "asc"));
      assertEquals("user", view.getSortField());
   }
}
