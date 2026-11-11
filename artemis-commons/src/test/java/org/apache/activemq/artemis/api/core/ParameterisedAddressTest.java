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

package org.apache.activemq.artemis.api.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ParameterisedAddressTest {

   @Test
   public void testExtractParameters() {
      assertEquals(Collections.emptyMap(), ParameterisedAddress.extractParameters(null));
      assertEquals(Collections.emptyMap(), ParameterisedAddress.extractParameters("noParams"));
      assertNotEquals(Collections.emptyMap(), ParameterisedAddress.extractParameters("noParams?param=X"));

      final Map<String, String> params = ParameterisedAddress.extractParameters("noParams?param1=X&param2=Y");

      assertEquals(2, params.size());

      assertEquals("X", params.get("param1"));
      assertEquals("Y", params.get("param2"));
   }

   @Test
   public void testExtractAddress() {
      assertNull(ParameterisedAddress.extractAddress((String) null));
      assertEquals("noParams", ParameterisedAddress.extractAddress("noParams"));
      assertEquals("noParams", ParameterisedAddress.extractAddress("noParams?param=X"));
   }
}
