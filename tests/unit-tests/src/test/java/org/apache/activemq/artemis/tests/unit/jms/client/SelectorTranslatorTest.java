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
package org.apache.activemq.artemis.tests.unit.jms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.SelectorTranslator;
import org.junit.jupiter.api.Test;

public class SelectorTranslatorTest extends ActiveMQTestBase {

   @Test
   public void testParseNull() {
      assertNull(SelectorTranslator.convertToActiveMQFilterString(null));
   }

   @Test
   public void testParseSimple() {
      final String selector = "color = 'red'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));
   }

   @Test
   public void testParseMoreComplex() {
      final String selector = "color = 'red' OR cheese = 'stilton' OR (age = 3 AND shoesize = 12)";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));
   }

   @Test
   public void testParseJMSDeliveryMode() {
      String selector = "JMSDeliveryMode='NON_PERSISTENT'";

      assertEquals("AMQDurable='NON_DURABLE'", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "JMSDeliveryMode='PERSISTENT'";

      assertEquals("AMQDurable='DURABLE'", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "color = 'red' AND 'NON_PERSISTENT' = JMSDeliveryMode";

      assertEquals("color = 'red' AND 'NON_DURABLE' = AMQDurable", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "color = 'red' AND 'PERSISTENT' = JMSDeliveryMode";

      assertEquals("color = 'red' AND 'DURABLE' = AMQDurable", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSDeliveryMode");
   }

   @Test
   public void testParseJMSPriority() {
      String selector = "JMSPriority=5";

      assertEquals("AMQPriority=5", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSPriority = 7";

      assertEquals(" AMQPriority = 7", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSPriority = 7 OR 1 = JMSPriority AND (JMSPriority= 1 + 4)";

      assertEquals(" AMQPriority = 7 OR 1 = AMQPriority AND (AMQPriority= 1 + 4)", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSPriority");

      selector = "animal = 'lion' JMSPriority = 321 OR animal_name = 'xyzJMSPriorityxyz'";

      assertEquals("animal = 'lion' AMQPriority = 321 OR animal_name = 'xyzJMSPriorityxyz'", SelectorTranslator.convertToActiveMQFilterString(selector));

   }

   @Test
   public void testParseJMSMessageID() {
      String selector = "JMSMessageID='ID:AMQ-12435678";

      assertEquals("AMQUserID='ID:AMQ-12435678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSMessageID='ID:AMQ-12435678";

      assertEquals(" AMQUserID='ID:AMQ-12435678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSMessageID = 'ID:AMQ-12435678";

      assertEquals(" AMQUserID = 'ID:AMQ-12435678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSMessageID";

      assertEquals(" myHeader = AMQUserID", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSMessageID OR (JMSMessageID = 'ID-AMQ' + '12345')";

      assertEquals(" myHeader = AMQUserID OR (AMQUserID = 'ID-AMQ' + '12345')", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSMessageID");
   }

   @Test
   public void testParseJMSTimestamp() {
      String selector = "JMSTimestamp=12345678";

      assertEquals("AMQTimestamp=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSTimestamp=12345678";

      assertEquals(" AMQTimestamp=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSTimestamp=12345678 OR 78766 = JMSTimestamp AND (JMSTimestamp= 1 + 4878787)";

      assertEquals(" AMQTimestamp=12345678 OR 78766 = AMQTimestamp AND (AMQTimestamp= 1 + 4878787)", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSTimestamp");

      selector = "animal = 'lion' JMSTimestamp = 321 OR animal_name = 'xyzJMSTimestampxyz'";

      assertEquals("animal = 'lion' AMQTimestamp = 321 OR animal_name = 'xyzJMSTimestampxyz'", SelectorTranslator.convertToActiveMQFilterString(selector));

   }

   @Test
   public void testParseJMSExpiration() {
      String selector = "JMSExpiration=12345678";

      assertEquals("AMQExpiration=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSExpiration=12345678";

      assertEquals(" AMQExpiration=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSExpiration=12345678 OR 78766 = JMSExpiration AND (JMSExpiration= 1 + 4878787)";

      assertEquals(" AMQExpiration=12345678 OR 78766 = AMQExpiration AND (AMQExpiration= 1 + 4878787)", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSExpiration");

      selector = "animal = 'lion' JMSExpiration = 321 OR animal_name = 'xyzJMSExpirationxyz'";

      assertEquals("animal = 'lion' AMQExpiration = 321 OR animal_name = 'xyzJMSExpirationxyz'", SelectorTranslator.convertToActiveMQFilterString(selector));

   }

   @Test
   public void testParseJMSCorrelationID() {
      String selector = "JMSCorrelationID='ID:AMQ-12435678";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSCorrelationID='ID:AMQ-12435678";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSCorrelationID = 'ID:AMQ-12435678";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSCorrelationID";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSCorrelationID OR (JMSCorrelationID = 'ID-AMQ' + '12345')";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSCorrelationID");
   }

   @Test
   public void testParseJMSType() {
      String selector = "JMSType='aardvark'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSType='aardvark'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSType = 'aardvark'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSType";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSType OR (JMSType = 'aardvark' + 'sandwich')";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSType");
   }

   private void checkNoSubstitute(final String fieldName) {
      String selector = "Other" + fieldName + " = 767868";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "cheese = 'cheddar' AND Wrong" + fieldName + " = 54";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "fruit = 'pomegranate' AND " + fieldName + "NotThisOne = 'tuesday'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = '" + fieldName + "'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = ' " + fieldName + "'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = ' " + fieldName + " '";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = 'xyz " + fieldName + "'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = 'xyz" + fieldName + "'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = '" + fieldName + "xyz'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = 'xyz" + fieldName + "xyz'";

      assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));
   }

}
