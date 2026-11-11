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

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class AdressInfoJSonTest {


   @Test
   public void testJSONparsing() {
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      AddressInfo info = new AddressInfo(name);
      info.setAutoCreated(RandomUtil.randomBoolean());
      info.setId(RandomUtil.randomLong());
      info.setInternal(RandomUtil.randomBoolean());
      info.addRoutingType(RoutingType.ANYCAST);

      AddressInfo newInfo = AddressInfo.fromJSON(info.toJSON());

      assertEquals(info.toString(), newInfo.toString());
      assertEquals(info.getRoutingType(), newInfo.getRoutingType());

      info.addRoutingType(RoutingType.ANYCAST);

      newInfo = AddressInfo.fromJSON(info.toJSON());
      assertEquals(info.toString(), newInfo.toString());
      assertEquals(info.getRoutingType(), newInfo.getRoutingType());
   }
}
