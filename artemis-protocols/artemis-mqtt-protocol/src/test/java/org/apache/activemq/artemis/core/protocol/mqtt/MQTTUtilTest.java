/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class MQTTUtilTest {
   @Test
   public void testDecompose() {
      String shareName = RandomUtil.randomUUIDString();
      String topicFilter = RandomUtil.randomUUIDString();

      Pair<String, String> decomposed = MQTTUtil.decomposeSharedSubscriptionTopicFilter(MQTTUtil.SHARED_SUBSCRIPTION_PREFIX + shareName + MQTTUtil.SLASH + topicFilter);
      assertEquals(shareName, decomposed.getA());
      assertEquals(topicFilter, decomposed.getB());
   }

   @Test
   public void testGetCoreQueueFromMqttTopic() {
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic(null, null, null));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic(null, null, new WildcardConfiguration()));

      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic("", null, null));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic("", null, new WildcardConfiguration()));

      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic("", "", null));

      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic(null, "", null));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreQueueFromMqttTopic(null, "", new WildcardConfiguration()));

      final String clientId = RandomUtil.randomUUIDString().replace("-", "");

      WildcardConfiguration defaultWildCardConfig = new WildcardConfiguration();
      assertEquals(clientId + ".a.b.c", MQTTUtil.getCoreQueueFromMqttTopic("a/b/c", clientId, defaultWildCardConfig));
      assertEquals(clientId + ".a.*.c", MQTTUtil.getCoreQueueFromMqttTopic("a/+/c", clientId, defaultWildCardConfig));
      assertEquals(clientId + ".a.*.#", MQTTUtil.getCoreQueueFromMqttTopic("a/+/#", clientId, defaultWildCardConfig));
      assertEquals(clientId + ".1\\.0.device", MQTTUtil.getCoreQueueFromMqttTopic("1.0/device", clientId, defaultWildCardConfig));
      assertEquals(clientId + ".*", MQTTUtil.getCoreQueueFromMqttTopic("+", clientId, defaultWildCardConfig));
      assertEquals(clientId + "..", MQTTUtil.getCoreQueueFromMqttTopic("/", clientId, defaultWildCardConfig));
      assertEquals(clientId + ".#", MQTTUtil.getCoreQueueFromMqttTopic("#", clientId, defaultWildCardConfig));

      WildcardConfiguration customWildCardConfig = new WildcardConfiguration().setDelimiter('|').setSingleWord('$').setAnyWords('!');
      assertEquals(clientId + ".a|b|c", MQTTUtil.getCoreQueueFromMqttTopic("a/b/c", clientId, customWildCardConfig));
      assertEquals(clientId + ".a|$|c", MQTTUtil.getCoreQueueFromMqttTopic("a/+/c", clientId, customWildCardConfig));
      assertEquals(clientId + ".a|$|!", MQTTUtil.getCoreQueueFromMqttTopic("a/+/#", clientId, customWildCardConfig));
      assertEquals(clientId + ".1.0|device", MQTTUtil.getCoreQueueFromMqttTopic("1.0/device", clientId, customWildCardConfig));
      assertEquals(clientId + ".$", MQTTUtil.getCoreQueueFromMqttTopic("+", clientId, customWildCardConfig));
      assertEquals(clientId + ".|", MQTTUtil.getCoreQueueFromMqttTopic("/", clientId, customWildCardConfig));
      assertEquals(clientId + ".!", MQTTUtil.getCoreQueueFromMqttTopic("#", clientId, customWildCardConfig));

      // ensure dots in the client ID are escaped
      assertEquals("x\\.y.a.b.c", MQTTUtil.getCoreQueueFromMqttTopic("a/b/c", "x.y", defaultWildCardConfig));
      assertEquals("x\\.y\\.z.a.b.c", MQTTUtil.getCoreQueueFromMqttTopic("a/b/c", "x.y.z", defaultWildCardConfig));
      assertEquals("x\\.y.a|b|c", MQTTUtil.getCoreQueueFromMqttTopic("a/b/c", "x.y", customWildCardConfig));
      assertEquals("x\\.y\\.z.a|b|c", MQTTUtil.getCoreQueueFromMqttTopic("a/b/c", "x.y.z", customWildCardConfig));

      // ensure wildcard characters in the topic are escaped
      assertEquals(clientId + ".\\.\\*", MQTTUtil.getCoreQueueFromMqttTopic(".*", clientId, defaultWildCardConfig));
      assertEquals(clientId + ".\\|\\$\\!", MQTTUtil.getCoreQueueFromMqttTopic("|$!", clientId, customWildCardConfig));
   }

   @Test
   public void testGetCoreQueueFromMqttTopicWithSharedSubscription() {
      final String clientId = RandomUtil.randomUUIDString().replace("-", "");

      WildcardConfiguration defaultWildCardConfig = new WildcardConfiguration();
      assertEquals("shareName.a.b.c", MQTTUtil.getCoreQueueFromMqttTopic("$share/shareName/a/b/c", clientId, defaultWildCardConfig));

      WildcardConfiguration customWildCardConfig = new WildcardConfiguration().setDelimiter('|').setSingleWord('$').setAnyWords('!');
      assertEquals("shareName.a|b|c", MQTTUtil.getCoreQueueFromMqttTopic("$share/shareName/a/b/c", clientId, customWildCardConfig));

      // ensure dots in the share name are escaped
      assertEquals("x\\.y.a.b.c", MQTTUtil.getCoreQueueFromMqttTopic("$share/x.y/a/b/c", clientId, defaultWildCardConfig));
      assertEquals("x\\.y\\.z.a.b.c", MQTTUtil.getCoreQueueFromMqttTopic("$share/x.y.z/a/b/c", clientId, defaultWildCardConfig));
      assertEquals("x\\.y.a|b|c", MQTTUtil.getCoreQueueFromMqttTopic("$share/x.y/a/b/c", clientId, customWildCardConfig));
      assertEquals("x\\.y\\.z.a|b|c", MQTTUtil.getCoreQueueFromMqttTopic("$share/x.y.z/a/b/c", clientId, customWildCardConfig));

      // ensure wildcard characters in the topic are escaped
      assertEquals("shareName.\\.\\*", MQTTUtil.getCoreQueueFromMqttTopic("$share/shareName/.*", clientId, defaultWildCardConfig));
      assertEquals("shareName.\\|\\$\\!", MQTTUtil.getCoreQueueFromMqttTopic("$share/shareName/|$!", clientId, customWildCardConfig));
   }

   @Test
   public void testGetCoreAddressFromMqttTopic() {
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreAddressFromMqttTopic(null, null));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreAddressFromMqttTopic(null, new WildcardConfiguration()));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreAddressFromMqttTopic("", null));

      WildcardConfiguration defaultWildCardConfig = new WildcardConfiguration();
      assertEquals("a.b.c", MQTTUtil.getCoreAddressFromMqttTopic("a/b/c", defaultWildCardConfig));
      assertEquals("a.*.c", MQTTUtil.getCoreAddressFromMqttTopic("a/+/c", defaultWildCardConfig));
      assertEquals("a.*.#", MQTTUtil.getCoreAddressFromMqttTopic("a/+/#", defaultWildCardConfig));
      assertEquals("1\\.0.device", MQTTUtil.getCoreAddressFromMqttTopic("1.0/device", defaultWildCardConfig));
      assertEquals("*", MQTTUtil.getCoreAddressFromMqttTopic("+", defaultWildCardConfig));
      assertEquals(".", MQTTUtil.getCoreAddressFromMqttTopic("/", defaultWildCardConfig));
      assertEquals("#", MQTTUtil.getCoreAddressFromMqttTopic("#", defaultWildCardConfig));

      WildcardConfiguration customWildCardConfig = new WildcardConfiguration().setDelimiter('|').setSingleWord('$').setAnyWords('!');
      assertEquals("a|b|c", MQTTUtil.getCoreAddressFromMqttTopic("a/b/c", customWildCardConfig));
      assertEquals("a|$|c", MQTTUtil.getCoreAddressFromMqttTopic("a/+/c", customWildCardConfig));
      assertEquals("a|$|!", MQTTUtil.getCoreAddressFromMqttTopic("a/+/#", customWildCardConfig));
      assertEquals("1.0|device", MQTTUtil.getCoreAddressFromMqttTopic("1.0/device", customWildCardConfig));
      assertEquals("$", MQTTUtil.getCoreAddressFromMqttTopic("+", customWildCardConfig));
      assertEquals("|", MQTTUtil.getCoreAddressFromMqttTopic("/", customWildCardConfig));
      assertEquals("!", MQTTUtil.getCoreAddressFromMqttTopic("#", customWildCardConfig));
   }

   @Test
   public void testGetCoreRetainAddressFromMqttTopic() {
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreRetainAddressFromMqttTopic(null, null));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreRetainAddressFromMqttTopic(null, new WildcardConfiguration()));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getCoreRetainAddressFromMqttTopic("", null));

      final String retainPrefix = "$sys.mqtt.retain.";
      WildcardConfiguration defaultWildCardConfig = new WildcardConfiguration();
      assertEquals(retainPrefix + "a.b.c", MQTTUtil.getCoreRetainAddressFromMqttTopic("a/b/c", defaultWildCardConfig));
   }

   @Test
   public void testGetMqttTopicFromCoreAddress() {
      assertThrows(NullPointerException.class, () -> MQTTUtil.getMqttTopicFromCoreAddress(null, null));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getMqttTopicFromCoreAddress(null, new WildcardConfiguration()));
      assertThrows(NullPointerException.class, () -> MQTTUtil.getMqttTopicFromCoreAddress("", null));

      WildcardConfiguration defaultWildCardConfig = new WildcardConfiguration();
      assertEquals("a/b/c", MQTTUtil.getMqttTopicFromCoreAddress("a.b.c", defaultWildCardConfig));
      assertEquals("a/+/c", MQTTUtil.getMqttTopicFromCoreAddress("a.*.c", defaultWildCardConfig));
      assertEquals("a/+/#", MQTTUtil.getMqttTopicFromCoreAddress("a.*.#", defaultWildCardConfig));
      assertEquals("1.0/device", MQTTUtil.getMqttTopicFromCoreAddress("1\\.0.device", defaultWildCardConfig));
      assertEquals("+", MQTTUtil.getMqttTopicFromCoreAddress("*", defaultWildCardConfig));
      assertEquals("/", MQTTUtil.getMqttTopicFromCoreAddress(".", defaultWildCardConfig));
      assertEquals("#", MQTTUtil.getMqttTopicFromCoreAddress("#", defaultWildCardConfig));

      WildcardConfiguration customWildCardConfig = new WildcardConfiguration().setDelimiter('|').setSingleWord('$').setAnyWords('!');
      assertEquals("a/b/c", MQTTUtil.getMqttTopicFromCoreAddress("a|b|c", customWildCardConfig));
      assertEquals("a/+/c", MQTTUtil.getMqttTopicFromCoreAddress("a|$|c", customWildCardConfig));
      assertEquals("a/+/#", MQTTUtil.getMqttTopicFromCoreAddress("a|$|!", customWildCardConfig));
      assertEquals("1.0/device", MQTTUtil.getMqttTopicFromCoreAddress("1.0|device", customWildCardConfig));
      assertEquals("+", MQTTUtil.getMqttTopicFromCoreAddress("$", customWildCardConfig));
      assertEquals("/", MQTTUtil.getMqttTopicFromCoreAddress("|", customWildCardConfig));
      assertEquals("#", MQTTUtil.getMqttTopicFromCoreAddress("!", customWildCardConfig));
   }
}
