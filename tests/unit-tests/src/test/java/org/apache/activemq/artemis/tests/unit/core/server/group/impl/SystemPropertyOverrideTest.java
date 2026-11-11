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
package org.apache.activemq.artemis.tests.unit.core.server.group.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class SystemPropertyOverrideTest extends ActiveMQTestBase {

   @Test
   public void testSystemPropertyOverride() throws Exception {
      final String groupTimeoutPropertyValue = "1234";
      final String reaperPeriodPropertyValue = "5678";

      System.setProperty(GroupingHandlerConfiguration.GROUP_TIMEOUT_PROP_NAME, groupTimeoutPropertyValue);
      System.setProperty(GroupingHandlerConfiguration.REAPER_PERIOD_PROP_NAME, reaperPeriodPropertyValue);

      GroupingHandlerConfiguration groupingHandlerConfiguration = new GroupingHandlerConfiguration().setName(SimpleString.of("test")).setType(GroupingHandlerConfiguration.TYPE.LOCAL).setAddress(SimpleString.of("address"));

      assertEquals(groupingHandlerConfiguration.getGroupTimeout(), Long.parseLong(groupTimeoutPropertyValue));
      assertEquals(groupingHandlerConfiguration.getReaperPeriod(), Long.parseLong(reaperPeriodPropertyValue));
   }
}
