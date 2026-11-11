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
package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.junit.jupiter.api.Test;

public class MirrorAddressFilterTest {

   @Test
   public void testAddressFilter() {
      assertTrue(new MirrorAddressFilter("").match(SimpleString.of("any")));
      assertTrue(new MirrorAddressFilter("test").match(SimpleString.of("test123")));
      assertTrue(new MirrorAddressFilter("a,b").match(SimpleString.of("b")));
      assertTrue(new MirrorAddressFilter("!c").match(SimpleString.of("a")));
      assertTrue(new MirrorAddressFilter("!a,!").match(SimpleString.of("b123")));
      assertFalse(new MirrorAddressFilter("a,b,!ab").match(SimpleString.of("ab")));
      assertFalse(new MirrorAddressFilter("!a,!b").match(SimpleString.of("b123")));
      assertFalse(new MirrorAddressFilter("a,").match(SimpleString.of("b")));
   }
}
