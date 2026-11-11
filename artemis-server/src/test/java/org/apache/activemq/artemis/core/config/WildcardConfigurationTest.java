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
package org.apache.activemq.artemis.core.config;

import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WildcardConfigurationTest {

   private static final WildcardConfiguration MQTT_WILDCARD = new WildcardConfiguration().setDelimiter('/').setAnyWords('#').setSingleWord('+');
   private static final WildcardConfiguration DEFAULT_WILDCARD = new WildcardConfiguration();

   @Test
   public void testDefaultWildcard() {
      assertEquals('.', DEFAULT_WILDCARD.getDelimiter());
      assertEquals('*', DEFAULT_WILDCARD.getSingleWord());
      assertEquals('#', DEFAULT_WILDCARD.getAnyWords());
   }

   @Test
   public void testToFromCoreMQTT() {
      testToFromCoreMQTT("foo.foo", "foo/foo");
      testToFromCoreMQTT("foo.*.foo", "foo/+/foo");
      testToFromCoreMQTT("foo.#", "foo/#");
      testToFromCoreMQTT("foo.*.foo.#", "foo/+/foo/#");
      testToFromCoreMQTT("foo\\.foo.foo", "foo.foo/foo");
   }

   private void testToFromCoreMQTT(String coreAddress, String mqttTopicFilter) {
      assertEquals(coreAddress, MQTT_WILDCARD.convert(mqttTopicFilter, DEFAULT_WILDCARD));
      assertEquals(mqttTopicFilter, DEFAULT_WILDCARD.convert(coreAddress, MQTT_WILDCARD));
   }

   @Test
   public void testEquality() {
      WildcardConfiguration a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      WildcardConfiguration b = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');

      assertEquals(a, b);
      assertEquals(b, a);
      assertEquals(a.hashCode(), b.hashCode());

      String toConvert = RandomUtil.randomUUIDString();
      assertSame(toConvert, a.convert(toConvert, b));
      assertSame(toConvert, a.convert(toConvert, a));
   }

   @Test
   public void testEqualityNegative() {
      WildcardConfiguration a;
      WildcardConfiguration b;

      // none equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('x').setAnyWords('y').setSingleWord('z');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());

      // only delimiter equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('a').setAnyWords('y').setSingleWord('z');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());

      // only anyWords equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('x').setAnyWords('b').setSingleWord('z');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());

      // only singleWord equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('x').setAnyWords('y').setSingleWord('c');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());

      // only delimiter not equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('x').setAnyWords('b').setSingleWord('c');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());

      // only anyWords not equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('a').setAnyWords('y').setSingleWord('c');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());

      // only singleWord not equal
      a = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('c');
      b = new WildcardConfiguration().setDelimiter('a').setAnyWords('b').setSingleWord('z');

      assertNotEquals(a, b);
      assertNotEquals(b, a);
      assertNotEquals(a.hashCode(), b.hashCode());
   }

   @Test
   public void testIsWild() {
      assertFalse(DEFAULT_WILDCARD.isWild(null));
      assertFalse(DEFAULT_WILDCARD.isWild(""));

      assertFalse(DEFAULT_WILDCARD.isWild("a"));
      assertFalse(DEFAULT_WILDCARD.isWild("a.b"));
      assertFalse(DEFAULT_WILDCARD.isWild("a\\.\\#"));
      assertFalse(DEFAULT_WILDCARD.isWild("a\\.\\*"));
      assertFalse(DEFAULT_WILDCARD.isWild("\\*"));
      assertFalse(DEFAULT_WILDCARD.isWild("\\#"));

      assertTrue(DEFAULT_WILDCARD.isWild("*"));
      assertTrue(DEFAULT_WILDCARD.isWild("#"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.*"));
      assertTrue(DEFAULT_WILDCARD.isWild("*.b"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.*.c"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.#"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.b.#"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.*.#"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.*.\\#"));
      assertTrue(DEFAULT_WILDCARD.isWild("a.\\*.#"));
   }

   @Test
   public void testContains() {
      assertFalse(DEFAULT_WILDCARD.contains('x'));
      assertFalse(DEFAULT_WILDCARD.contains('!'));

      assertTrue(DEFAULT_WILDCARD.contains('#'));
      assertTrue(DEFAULT_WILDCARD.contains('*'));
      assertTrue(DEFAULT_WILDCARD.contains('.'));

      assertTrue(new WildcardConfiguration().setAnyWords('!').contains('!'));
      assertTrue(new WildcardConfiguration().setDelimiter('!').contains('!'));
      assertTrue(new WildcardConfiguration().setSingleWord('!').contains('!'));
   }
}