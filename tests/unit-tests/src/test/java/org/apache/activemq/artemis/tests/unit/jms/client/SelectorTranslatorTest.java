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

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.SelectorTranslator;
import org.junit.Assert;
import org.junit.Test;

public class SelectorTranslatorTest extends ActiveMQTestBase {

   @Test
   public void testParseNull() {
      Assert.assertNull(SelectorTranslator.convertToActiveMQFilterString(null));
   }

   @Test
   public void testParseSimple() {
      final String selector = "color = 'red'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));
   }

   @Test
   public void testParseMoreComplex() {
      final String selector = "color = 'red' OR cheese = 'stilton' OR (age = 3 AND shoesize = 12)";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));
   }

   @Test
   public void testParseJMSDeliveryMode() {
      String selector = "JMSDeliveryMode='NON_PERSISTENT'";

      Assert.assertEquals("AMQDurable='NON_DURABLE'", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "JMSDeliveryMode='PERSISTENT'";

      Assert.assertEquals("AMQDurable='DURABLE'", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "color = 'red' AND 'NON_PERSISTENT' = JMSDeliveryMode";

      Assert.assertEquals("color = 'red' AND 'NON_DURABLE' = AMQDurable", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "color = 'red' AND 'PERSISTENT' = JMSDeliveryMode";

      Assert.assertEquals("color = 'red' AND 'DURABLE' = AMQDurable", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSDeliveryMode");
   }

   @Test
   public void testParseJMSPriority() {
      String selector = "JMSPriority=5";

      Assert.assertEquals("AMQPriority=5", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSPriority = 7";

      Assert.assertEquals(" AMQPriority = 7", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSPriority = 7 OR 1 = JMSPriority AND (JMSPriority= 1 + 4)";

      Assert.assertEquals(" AMQPriority = 7 OR 1 = AMQPriority AND (AMQPriority= 1 + 4)", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSPriority");

      selector = "animal = 'lion' JMSPriority = 321 OR animal_name = 'xyzJMSPriorityxyz'";

      Assert.assertEquals("animal = 'lion' AMQPriority = 321 OR animal_name = 'xyzJMSPriorityxyz'", SelectorTranslator.convertToActiveMQFilterString(selector));

   }

   @Test
   public void testParseJMSMessageID() {
      String selector = "JMSMessageID='ID:AMQ-12435678";

      Assert.assertEquals("AMQUserID='ID:AMQ-12435678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSMessageID='ID:AMQ-12435678";

      Assert.assertEquals(" AMQUserID='ID:AMQ-12435678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSMessageID = 'ID:AMQ-12435678";

      Assert.assertEquals(" AMQUserID = 'ID:AMQ-12435678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSMessageID";

      Assert.assertEquals(" myHeader = AMQUserID", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSMessageID OR (JMSMessageID = 'ID-AMQ' + '12345')";

      Assert.assertEquals(" myHeader = AMQUserID OR (AMQUserID = 'ID-AMQ' + '12345')", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSMessageID");
   }

   @Test
   public void testParseJMSTimestamp() {
      String selector = "JMSTimestamp=12345678";

      Assert.assertEquals("AMQTimestamp=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSTimestamp=12345678";

      Assert.assertEquals(" AMQTimestamp=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSTimestamp=12345678 OR 78766 = JMSTimestamp AND (JMSTimestamp= 1 + 4878787)";

      Assert.assertEquals(" AMQTimestamp=12345678 OR 78766 = AMQTimestamp AND (AMQTimestamp= 1 + 4878787)", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSTimestamp");

      selector = "animal = 'lion' JMSTimestamp = 321 OR animal_name = 'xyzJMSTimestampxyz'";

      Assert.assertEquals("animal = 'lion' AMQTimestamp = 321 OR animal_name = 'xyzJMSTimestampxyz'", SelectorTranslator.convertToActiveMQFilterString(selector));

   }

   @Test
   public void testParseJMSExpiration() {
      String selector = "JMSExpiration=12345678";

      Assert.assertEquals("AMQExpiration=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSExpiration=12345678";

      Assert.assertEquals(" AMQExpiration=12345678", SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSExpiration=12345678 OR 78766 = JMSExpiration AND (JMSExpiration= 1 + 4878787)";

      Assert.assertEquals(" AMQExpiration=12345678 OR 78766 = AMQExpiration AND (AMQExpiration= 1 + 4878787)", SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSExpiration");

      selector = "animal = 'lion' JMSExpiration = 321 OR animal_name = 'xyzJMSExpirationxyz'";

      Assert.assertEquals("animal = 'lion' AMQExpiration = 321 OR animal_name = 'xyzJMSExpirationxyz'", SelectorTranslator.convertToActiveMQFilterString(selector));

   }

   @Test
   public void testParseJMSCorrelationID() {
      String selector = "JMSCorrelationID='ID:AMQ-12435678";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSCorrelationID='ID:AMQ-12435678";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSCorrelationID = 'ID:AMQ-12435678";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSCorrelationID";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSCorrelationID OR (JMSCorrelationID = 'ID-AMQ' + '12345')";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSCorrelationID");
   }

   @Test
   public void testParseJMSType() {
      String selector = "JMSType='aardvark'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSType='aardvark'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " JMSType = 'aardvark'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSType";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = " myHeader = JMSType OR (JMSType = 'aardvark' + 'sandwich')";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      checkNoSubstitute("JMSType");
   }

   @Test
   public void testConvertHQFilterString() {
      String selector = "HQUserID = 'ID:AMQ-12435678'";

      Assert.assertEquals("AMQUserID = 'ID:AMQ-12435678'", SelectorTranslator.convertHQToActiveMQFilterString(selector));

      selector = "HQUserID = 'HQUserID'";

      Assert.assertEquals("AMQUserID = 'HQUserID'", SelectorTranslator.convertHQToActiveMQFilterString(selector));

      selector = "HQUserID = 'ID:AMQ-12435678'";

      Assert.assertEquals("AMQUserID = 'ID:AMQ-12435678'", SelectorTranslator.convertHQToActiveMQFilterString(selector));

      selector = "HQDurable='NON_DURABLE'";

      Assert.assertEquals("AMQDurable='NON_DURABLE'", SelectorTranslator.convertHQToActiveMQFilterString(selector));

      selector = "HQPriority=5";

      Assert.assertEquals("AMQPriority=5", SelectorTranslator.convertHQToActiveMQFilterString(selector));

      selector = "HQTimestamp=12345678";

      Assert.assertEquals("AMQTimestamp=12345678", SelectorTranslator.convertHQToActiveMQFilterString(selector));

      selector = "HQExpiration=12345678";

      Assert.assertEquals("AMQExpiration=12345678", SelectorTranslator.convertHQToActiveMQFilterString(selector));
   }

   private void checkNoSubstitute(final String fieldName) {
      String selector = "Other" + fieldName + " = 767868";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "cheese = 'cheddar' AND Wrong" + fieldName + " = 54";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "fruit = 'pomegranate' AND " + fieldName + "NotThisOne = 'tuesday'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = '" + fieldName + "'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = ' " + fieldName + "'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = ' " + fieldName + " '";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = 'xyz " + fieldName + "'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = 'xyz" + fieldName + "'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = '" + fieldName + "xyz'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));

      selector = "animal = 'lion' AND animal_name = 'xyz" + fieldName + "xyz'";

      Assert.assertEquals(selector, SelectorTranslator.convertToActiveMQFilterString(selector));
   }

}
