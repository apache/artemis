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

public class ConnectionViewTest extends ViewTest {

   @Test
   public void testDefaultViewNullOptions() {
      ConnectionView connectionView = new ConnectionView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      connectionView.setOptions(null);
   }

   @Test
   public void testDefaultViewEmptyOptions() {
      ConnectionView connectionView = new ConnectionView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      connectionView.setOptions("");
   }

   @Test
   public void testViewLegacySort() {
      ConnectionView view = new ConnectionView(Mockito.mock(ActiveMQServer.class));
      assertNotEquals("protocol", view.getDefaultOrderColumn());
      view.setOptions(createLegacyJsonFilter("protocol", "EQUALS", "123", "protocol", "asc"));
      assertEquals("protocol", view.getSortField());
   }
}
