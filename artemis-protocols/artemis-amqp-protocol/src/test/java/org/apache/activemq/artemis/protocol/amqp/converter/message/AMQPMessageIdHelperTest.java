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
package org.apache.activemq.artemis.protocol.amqp.converter.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;

import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageIdHelper;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AMQPMessageIdHelperTest {

   private AMQPMessageIdHelper messageIdHelper;

   @BeforeEach
   public void setUp() throws Exception {
      messageIdHelper = AMQPMessageIdHelper.INSTANCE;
   }

   /**
    * Test that {@link AMQPMessageIdHelper#hasMessageIdPrefix(String)} returns {@code true} for strings that begin
    * "ID:"
    */
   @Test
   public void testHasIdPrefixWithPrefix() {
      String myId = "ID:something";
      assertTrue(messageIdHelper.hasMessageIdPrefix(myId), "'ID:' prefix should have been identified");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#hasMessageIdPrefix(String)} returns false for string beings "ID" without
    * colon.
    */
   @Test
   public void testHasIdPrefixWithIDButNoColonPrefix() {
      String myIdNoColon = "IDsomething";
      assertFalse(messageIdHelper.hasMessageIdPrefix(myIdNoColon), "'ID' prefix should not have been identified without trailing colon");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#hasMessageIdPrefix(String)} returns false for null
    */
   @Test
   public void testHasIdPrefixWithNull() {
      String nullString = null;
      assertFalse(messageIdHelper.hasMessageIdPrefix(nullString), "null string should not result in identification as having the prefix");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#hasMessageIdPrefix(String)} returns false for strings that doesnt have "ID:"
    * anywhere
    */
   @Test
   public void testHasIdPrefixWithoutPrefix() {
      String myNonId = "something";
      assertFalse(messageIdHelper.hasMessageIdPrefix(myNonId), "string without 'ID:' anywhere should not have been identified as having the prefix");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#hasMessageIdPrefix(String)} returns false for strings has lowercase "id:"
    * prefix
    */
   @Test
   public void testHasIdPrefixWithLowercaseID() {
      String myLowerCaseNonId = "id:something";
      assertFalse(messageIdHelper.hasMessageIdPrefix(myLowerCaseNonId), "lowercase 'id:' prefix should not result in identification as having 'ID:' prefix");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns null if given null
    */
   @Test
   public void testToMessageIdStringWithNull() {
      assertNull(messageIdHelper.toMessageIdString(null), "null string should have been returned");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} throws an IAE if given an unexpected object type.
    */
   @Test
   public void testToMessageIdStringThrowsIAEWithUnexpectedType() {
      try {
         messageIdHelper.toMessageIdString(new Object());
         fail("expected exception not thrown");
      } catch (IllegalArgumentException iae) {
         // expected
      }
   }

   private void doToMessageIdTestImpl(Object idObject, String expected) {
      String idString = messageIdHelper.toMessageIdString(idObject);
      assertNotNull(idString, "null string should not have been returned");
      assertEquals(expected, idString, "expected id string was not returned");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns the given basic "ID:content" string
    * unchanged.
    */
   @Test
   public void testToMessageIdStringWithString() {
      String stringId = "ID:myIdString";

      doToMessageIdTestImpl(stringId, stringId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns the given basic string with the 'no
    * prefix' prefix and "ID:" prefix.
    */
   @Test
   public void testToMessageIdStringWithStringNoPrefix() {
      String stringId = "myIdStringNoPrefix";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + stringId;

      doToMessageIdTestImpl(stringId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating lack of "ID:" prefix,
    * when the given string happens to begin with the {@link AMQPMessageIdHelper#AMQP_UUID_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithEncodingPrefixForUUID() {
      String uuidStringMessageId = AMQPMessageIdHelper.AMQP_UUID_PREFIX + UUID.randomUUID();
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + uuidStringMessageId;

      doToMessageIdTestImpl(uuidStringMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating lack of "ID:" prefix,
    * when the given string happens to begin with the {@link AMQPMessageIdHelper#AMQP_ULONG_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithEncodingPrefixForLong() {
      String longStringMessageId = AMQPMessageIdHelper.AMQP_ULONG_PREFIX + 123456789L;
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + longStringMessageId;

      doToMessageIdTestImpl(longStringMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating lack of "ID:" prefix,
    * when the given string happens to begin with the {@link AMQPMessageIdHelper#AMQP_BINARY_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithEncodingPrefixForBinary() {
      String binaryStringMessageId = AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "0123456789ABCDEF";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + binaryStringMessageId;

      doToMessageIdTestImpl(binaryStringMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating lack of "ID:" prefix,
    * when the given string happens to begin with the {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithEncodingPrefixForString() {
      String stringMessageId = AMQPMessageIdHelper.AMQP_STRING_PREFIX + "myStringId";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + stringMessageId;

      doToMessageIdTestImpl(stringMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating lack of "ID:" prefix,
    * effectively twice, when the given string happens to begin with the {@link AMQPMessageIdHelper#AMQP_NO_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithEncodingPrefixForNoIdPrefix() {
      String stringMessageId = AMQPMessageIdHelper.AMQP_NO_PREFIX + "myStringId";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + stringMessageId;

      doToMessageIdTestImpl(stringMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an AMQP encoded UUID
    * when given a UUID object.
    */
   @Test
   public void testToMessageIdStringWithUUID() {
      UUID uuidMessageId = UUID.randomUUID();
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_UUID_PREFIX + uuidMessageId.toString();

      doToMessageIdTestImpl(uuidMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an AMQP encoded ulong
    * when given a UnsignedLong object.
    */
   @Test
   public void testToMessageIdStringWithUnsignedLong() {
      UnsignedLong uLongMessageId = UnsignedLong.valueOf(123456789L);
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_ULONG_PREFIX + uLongMessageId.toString();

      doToMessageIdTestImpl(uLongMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an AMQP encoded binary
    * when given a Binary object.
    */
   @Test
   public void testToMessageIdStringWithBinary() {
      byte[] bytes = new byte[] {(byte) 0x00, (byte) 0xAB, (byte) 0x09, (byte) 0xFF};
      Binary binary = new Binary(bytes);

      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "00AB09FF";

      doToMessageIdTestImpl(binary, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an escaped string,
    * when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in this case
    * the {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithIdAndEncodingPrefixForString() {
      String unescapedStringPrefixMessageId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + "id-content";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedStringPrefixMessageId;

      doToMessageIdTestImpl(unescapedStringPrefixMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an escaped string,
    * when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in this case
    * the {@link AMQPMessageIdHelper#AMQP_UUID_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithIdAndEncodingPrefixForUUID() {
      String unescapedUuidPrefixMessageId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_UUID_PREFIX + UUID.randomUUID();
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedUuidPrefixMessageId;

      doToMessageIdTestImpl(unescapedUuidPrefixMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an escaped string,
    * when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in this case
    * the {@link AMQPMessageIdHelper#AMQP_ULONG_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithIdAndEncodingPrefixForUlong() {
      String unescapedUlongPrefixMessageId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_ULONG_PREFIX + "42";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedUlongPrefixMessageId;

      doToMessageIdTestImpl(unescapedUlongPrefixMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an escaped string,
    * when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in this case
    * the {@link AMQPMessageIdHelper#AMQP_BINARY_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithIdAndEncodingPrefixForBinary() {
      String unescapedBinaryPrefixMessageId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "ABCDEF";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedBinaryPrefixMessageId;

      doToMessageIdTestImpl(unescapedBinaryPrefixMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toMessageIdString(Object)} returns a string indicating an escaped string,
    * when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in this case
    * the {@link AMQPMessageIdHelper#AMQP_NO_PREFIX}.
    */
   @Test
   public void testToMessageIdStringWithStringBeginningWithIdAndEncodingPrefixForNoIDPrefix() {
      String unescapedNoPrefixPrefixedMessageId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + "id-content";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedNoPrefixPrefixedMessageId;

      doToMessageIdTestImpl(unescapedNoPrefixPrefixedMessageId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns {@code null} if given
    * {@code null}
    */
   @Test
   public void testToCorrelationIdStringWithNull() {
      assertNull(messageIdHelper.toCorrelationIdStringOrBytes(null), "null string should have been returned");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} throws an IAE if given an unexpected
    * object type.
    */
   @Test
   public void testToCorrelationIdStringThrowsIAEWithUnexpectedType() {
      try {
         messageIdHelper.toCorrelationIdStringOrBytes(new Object());
         fail("expected exception not thrown");
      } catch (IllegalArgumentException iae) {
         // expected
      }
   }

   private void doToCorrelationIDTestImpl(Object idObject, String expected) {
      String idString = (String) messageIdHelper.toCorrelationIdStringOrBytes(idObject);
      assertNotNull(idString, "null string should not have been returned");
      assertEquals(expected, idString, "expected id string was not returned");
   }

   private void doToCorrelationIDBytesTestImpl(Object idObject, byte[] expected) {
      byte[] idBytes = (byte[]) messageIdHelper.toCorrelationIdStringOrBytes(idObject);
      assertNotNull(idBytes, "null byte[] should not have been returned");
      assertArrayEquals(expected, idBytes, "expected id byte[] was not returned");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns the given basic string
    * unchanged when it has the "ID:" prefix (but no others).
    */
   @Test
   public void testToCorrelationIdStringWithString() {
      String stringId = "ID:myCorrelationIdString";

      doToCorrelationIDTestImpl(stringId, stringId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns the given basic string
    * unchanged when it lacks the "ID:" prefix (and any others)
    */
   @Test
   public void testToCorrelationIdStringWithStringNoPrefix() {
      String stringNoId = "myCorrelationIdString";

      doToCorrelationIDTestImpl(stringNoId, stringNoId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string unchanged when it
    * lacks the "ID:" prefix but happens to already begin with the {@link AMQPMessageIdHelper#AMQP_UUID_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithEncodingPrefixForUUID() {
      String uuidPrefixStringCorrelationId = AMQPMessageIdHelper.AMQP_UUID_PREFIX + UUID.randomUUID();

      doToCorrelationIDTestImpl(uuidPrefixStringCorrelationId, uuidPrefixStringCorrelationId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string unchanged when it
    * lacks the "ID:" prefix but happens to already begin with the {@link AMQPMessageIdHelper#AMQP_ULONG_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithEncodingPrefixForLong() {
      String ulongPrefixStringCorrelationId = AMQPMessageIdHelper.AMQP_ULONG_PREFIX + 123456789L;

      doToCorrelationIDTestImpl(ulongPrefixStringCorrelationId, ulongPrefixStringCorrelationId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string unchanged when it
    * lacks the "ID:" prefix but happens to already begin with the {@link AMQPMessageIdHelper#AMQP_BINARY_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithEncodingPrefixForBinary() {
      String binaryPrefixStringCorrelationId = AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "0123456789ABCDEF";

      doToCorrelationIDTestImpl(binaryPrefixStringCorrelationId, binaryPrefixStringCorrelationId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string unchanged when it
    * lacks the "ID:" prefix but happens to already begin with the {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithEncodingPrefixForString() {
      String stringPrefixCorrelationId = AMQPMessageIdHelper.AMQP_STRING_PREFIX + "myStringId";

      doToCorrelationIDTestImpl(stringPrefixCorrelationId, stringPrefixCorrelationId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string unchanged when it
    * lacks the "ID:" prefix but happens to already begin with the {@link AMQPMessageIdHelper#AMQP_NO_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithEncodingPrefixForNoIdPrefix() {
      String noPrefixStringCorrelationId = AMQPMessageIdHelper.AMQP_NO_PREFIX + "myStringId";

      doToCorrelationIDTestImpl(noPrefixStringCorrelationId, noPrefixStringCorrelationId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an AMQP
    * encoded UUID when given a UUID object.
    */
   @Test
   public void testToCorrelationIdStringWithUUID() {
      UUID uuidCorrelationId = UUID.randomUUID();
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_UUID_PREFIX + uuidCorrelationId.toString();

      doToCorrelationIDTestImpl(uuidCorrelationId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an AMQP
    * encoded ulong when given a UnsignedLong object.
    */
   @Test
   public void testToCorrelationIdStringWithUnsignedLong() {
      UnsignedLong uLongCorrelationId = UnsignedLong.valueOf(123456789L);
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_ULONG_PREFIX + uLongCorrelationId.toString();

      doToCorrelationIDTestImpl(uLongCorrelationId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a byte[] when given a Binary
    * object.
    */
   @Test
   public void testToCorrelationIdByteArrayWithBinary() {
      byte[] bytes = new byte[] {(byte) 0x00, (byte) 0xAB, (byte) 0x09, (byte) 0xFF};
      Binary binary = new Binary(bytes);

      doToCorrelationIDBytesTestImpl(binary, bytes);
   }

   @Test
   public void testToCorrelationIdByteArrayWithBinaryWithOffset() {
      byte[] bytes = new byte[] {(byte) 0x00, (byte) 0xAB, (byte) 0x09, (byte) 0xFF};
      Binary binary = new Binary(bytes, 2, 2);

      doToCorrelationIDBytesTestImpl(binary, new byte[] {(byte) 0x09, (byte) 0xFF});
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an escaped
    * string, when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in
    * this case the {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithIdAndEncodingPrefixForString() {
      String unescapedStringPrefixCorrelationId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + "id-content";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedStringPrefixCorrelationId;

      doToCorrelationIDTestImpl(unescapedStringPrefixCorrelationId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an escaped
    * string, when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in
    * this case the {@link AMQPMessageIdHelper#AMQP_UUID_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithIdAndEncodingPrefixForUUID() {
      String unescapedUuidPrefixCorrelationId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_UUID_PREFIX + UUID.randomUUID();
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedUuidPrefixCorrelationId;

      doToCorrelationIDTestImpl(unescapedUuidPrefixCorrelationId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an escaped
    * string, when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in
    * this case the {@link AMQPMessageIdHelper#AMQP_ULONG_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithIdAndEncodingPrefixForUlong() {
      String unescapedUlongPrefixCorrelationId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_ULONG_PREFIX + "42";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedUlongPrefixCorrelationId;

      doToCorrelationIDTestImpl(unescapedUlongPrefixCorrelationId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an escaped
    * string, when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in
    * this case the {@link AMQPMessageIdHelper#AMQP_BINARY_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithIdAndEncodingPrefixForBinary() {
      String unescapedBinaryPrefixCorrelationId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "ABCDEF";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedBinaryPrefixCorrelationId;

      doToCorrelationIDTestImpl(unescapedBinaryPrefixCorrelationId, expected);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toCorrelationIdStringOrBytes(Object)} returns a string indicating an escaped
    * string, when given an input string that already has the "ID:" prefix, but follows it with an encoding prefix, in
    * this case the {@link AMQPMessageIdHelper#AMQP_NO_PREFIX}.
    */
   @Test
   public void testToCorrelationIdStringWithStringBeginningWithIdAndEncodingPrefixForNoIDPrefix() {
      String unescapedNoPrefixCorrelationId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + "id-content";
      String expected = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + unescapedNoPrefixCorrelationId;

      doToCorrelationIDTestImpl(unescapedNoPrefixCorrelationId, expected);
   }

   private void doToIdObjectTestImpl(String idString, Object expected) throws ActiveMQAMQPIllegalStateException {
      Object idObject = messageIdHelper.toIdObject(idString);
      assertNotNull(idObject, "null object should not have been returned");
      assertEquals(expected, idObject, "expected id object was not returned");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns an UnsignedLong when given a string indicating an
    * encoded AMQP ulong id.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithEncodedUlong() throws Exception {
      UnsignedLong longId = UnsignedLong.valueOf(123456789L);
      String provided = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_ULONG_PREFIX + "123456789";

      doToIdObjectTestImpl(provided, longId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns a Binary when given a string indicating an
    * encoded AMQP binary id, using upper case hex characters
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithEncodedBinaryUppercaseHexString() throws Exception {
      byte[] bytes = new byte[] {(byte) 0x00, (byte) 0xAB, (byte) 0x09, (byte) 0xFF};
      Binary binaryId = new Binary(bytes);

      String provided = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "00AB09FF";

      doToIdObjectTestImpl(provided, binaryId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns null when given null.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithNull() throws Exception {
      assertNull(messageIdHelper.toIdObject(null), "null object should have been returned");
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns a Binary when given a string indicating an
    * encoded AMQP binary id, using lower case hex characters.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithEncodedBinaryLowercaseHexString() throws Exception {
      byte[] bytes = new byte[] {(byte) 0x00, (byte) 0xAB, (byte) 0x09, (byte) 0xFF};
      Binary binaryId = new Binary(bytes);

      String provided = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "00ab09ff";

      doToIdObjectTestImpl(provided, binaryId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns a UUID when given a string indicating an encoded
    * AMQP uuid id.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithEncodedUuid() throws Exception {
      UUID uuid = UUID.randomUUID();
      String provided = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_UUID_PREFIX + uuid.toString();

      doToIdObjectTestImpl(provided, uuid);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns a string unchanged when given a string without
    * any prefix.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithAppSpecificString() throws Exception {
      String stringId = "myStringId";

      doToIdObjectTestImpl(stringId, stringId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns a string unchanged when given a string with only
    * the 'ID:' prefix.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithSimplIdString() throws Exception {
      String stringId = "ID:myStringId";

      doToIdObjectTestImpl(stringId, stringId);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns the remainder of the provided string after
    * removing the 'ID:' and {@link AMQPMessageIdHelper#AMQP_NO_PREFIX} prefix used to indicate it originally had no
    * 'ID:' prefix [when arriving as a message id].
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithStringContainingEncodingPrefixForNoIdPrefix() throws Exception {
      String suffix = "myStringSuffix";
      String stringId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_NO_PREFIX + suffix;

      doToIdObjectTestImpl(stringId, suffix);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} returns the remainder of the provided string after
    * removing the {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX} prefix.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithStringContainingIdStringEncodingPrefix() throws Exception {
      String suffix = "myStringSuffix";
      String stringId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + suffix;

      doToIdObjectTestImpl(stringId, suffix);
   }

   /**
    * Test that when given a string with with the {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX} prefix and then
    * additionally the {@link AMQPMessageIdHelper#AMQP_UUID_PREFIX}, the {@link AMQPMessageIdHelper#toIdObject(String)}
    * method returns the remainder of the provided string after removing the
    * {@link AMQPMessageIdHelper#AMQP_STRING_PREFIX} prefix.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testToIdObjectWithStringContainingIdStringEncodingPrefixAndThenUuidPrefix() throws Exception {
      String encodedUuidString = AMQPMessageIdHelper.AMQP_UUID_PREFIX + UUID.randomUUID().toString();
      String stringId = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_STRING_PREFIX + encodedUuidString;

      doToIdObjectTestImpl(stringId, encodedUuidString);
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} throws an {@link IdConversionException} when presented
    * with an encoded binary hex string of uneven length (after the prefix) that thus can't be converted due to each
    * byte using 2 characters
    */
   @Test
   public void testToIdObjectWithStringContainingBinaryHexThrowsICEWithUnevenLengthString() {
      String unevenHead = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + "123";

      try {
         messageIdHelper.toIdObject(unevenHead);
         fail("expected exception was not thrown");
      } catch (ActiveMQAMQPIllegalStateException iae) {
         // expected
         String msg = iae.getMessage();
         assertTrue(msg.contains("even length"), "Message was not as expected: " + msg);
      }
   }

   /**
    * Test that {@link AMQPMessageIdHelper#toIdObject(String)} throws an {@link IdConversionException} when presented
    * with an encoded binary hex string (after the prefix) that contains characters other than 0-9 and A-F and a-f, and
    * thus can't be converted
    */
   @Test
   public void testToIdObjectWithStringContainingBinaryHexThrowsICEWithNonHexCharacters() {

      // char before '0'
      char nonHexChar = '/';
      String nonHexString = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + nonHexChar + nonHexChar;

      try {
         messageIdHelper.toIdObject(nonHexString);
         fail("expected exception was not thrown");
      } catch (ActiveMQAMQPIllegalStateException ice) {
         // expected
         String msg = ice.getMessage();
         assertTrue(msg.contains("non-hex"), "Message was not as expected: " + msg);
      }

      // char after '9', before 'A'
      nonHexChar = ':';
      nonHexString = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + nonHexChar + nonHexChar;

      try {
         messageIdHelper.toIdObject(nonHexString);
         fail("expected exception was not thrown");
      } catch (ActiveMQAMQPIllegalStateException iae) {
         // expected
         String msg = iae.getMessage();
         assertTrue(msg.contains("non-hex"), "Message was not as expected: " + msg);
      }

      // char after 'F', before 'a'
      nonHexChar = 'G';
      nonHexString = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + nonHexChar + nonHexChar;

      try {
         messageIdHelper.toIdObject(nonHexString);
         fail("expected exception was not thrown");
      } catch (ActiveMQAMQPIllegalStateException iae) {
         // expected
         String msg = iae.getMessage();
         assertTrue(msg.contains("non-hex"), "Message was not as expected: " + msg);
      }

      // char after 'f'
      nonHexChar = 'g';
      nonHexString = AMQPMessageIdHelper.JMS_ID_PREFIX + AMQPMessageIdHelper.AMQP_BINARY_PREFIX + nonHexChar + nonHexChar;

      try {
         messageIdHelper.toIdObject(nonHexString);
         fail("expected exception was not thrown");
      } catch (ActiveMQAMQPIllegalStateException ice) {
         // expected
         String msg = ice.getMessage();
         assertTrue(msg.contains("non-hex"), "Message was not as expected: " + msg);
      }
   }
}
