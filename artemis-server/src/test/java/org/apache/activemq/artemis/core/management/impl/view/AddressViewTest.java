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

public class AddressViewTest extends ViewTest {

   @Test
   public void testDefaultViewNullOptions() {
      AddressView addressView = new AddressView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      addressView.setOptions(null);
   }

   @Test
   public void testDefaultViewEmptyOptions() {
      AddressView addressView = new AddressView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      addressView.setOptions("");
   }

   @Test
   public void testViewLegacySort() {
      AddressView view = new AddressView(Mockito.mock(ActiveMQServer.class));
      assertNotEquals("name", view.getDefaultOrderColumn());
      view.setOptions(createLegacyJsonFilter("name", "EQUALS", "123", "name", "asc"));
      assertEquals("name", view.getSortField());
   }
}
