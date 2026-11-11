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
package org.apache.activemq.artemis.protocol.amqp.converter;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPIllegalStateException;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;

/**
 * Helper class for identifying and converting message-id and correlation-id values between the AMQP types and the
 * Strings values used by JMS.
 * <p>
 * AMQP messages allow for 4 types of message-id/correlation-id: message-id-string, message-id-binary, message-id-uuid,
 * or message-id-ulong. In order to accept or return a string representation of these for interoperability with other
 * AMQP clients, the following encoding can be used after removing or before adding the "ID:" prefix used for a
 * JMSMessageID value:
 * <ul>
 * <li>{@literal "AMQP_BINARY:<hex representation of binary content>"}
 * <li>{@literal "AMQP_UUID:<string representation of uuid>"}
 * <li>{@literal "AMQP_ULONG:<string representation of ulong>"}
 * <li>{@literal "AMQP_STRING:<string>"}
 * </ul>
 * The AMQP_STRING encoding exists only for escaping message-id-string values
 * that happen to begin with one of the encoding prefixes (including AMQP_STRING
 * itself). It MUST NOT be used otherwise.
 * <p>
 * When provided a string for conversion which attempts to identify itself as an
 * encoded binary, uuid, or ulong but can't be converted into the indicated
 * format, an exception will be thrown.
 */
public class AMQPMessageIdHelper {

   public static final AMQPMessageIdHelper INSTANCE = new AMQPMessageIdHelper();

   public static final String AMQP_STRING_PREFIX = "AMQP_STRING:";
   public static final String AMQP_UUID_PREFIX = "AMQP_UUID:";
   public static final String AMQP_ULONG_PREFIX = "AMQP_ULONG:";
   public static final String AMQP_BINARY_PREFIX = "AMQP_BINARY:";
   public static final String AMQP_NO_PREFIX = "AMQP_NO_PREFIX:";
   public static final String JMS_ID_PREFIX = "ID:";

   private static final String AMQP_PREFIX = "AMQP_";
   private static final int JMS_ID_PREFIX_LENGTH = JMS_ID_PREFIX.length();
   private static final int AMQP_UUID_PREFIX_LENGTH = AMQP_UUID_PREFIX.length();
   private static final int AMQP_ULONG_PREFIX_LENGTH = AMQP_ULONG_PREFIX.length();
   private static final int AMQP_STRING_PREFIX_LENGTH = AMQP_STRING_PREFIX.length();
   private static final int AMQP_BINARY_PREFIX_LENGTH = AMQP_BINARY_PREFIX.length();
   private static final int AMQP_NO_PREFIX_LENGTH = AMQP_NO_PREFIX.length();
   private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

   /**
    * Checks whether the given string begins with "ID:" prefix used to denote a JMSMessageID
    *
    * @param string the string to check
    * @return {@code true} if and only id the string begins with "ID:"
    */
   public boolean hasMessageIdPrefix(String string) {
      if (string == null) {
         return false;
      }

      return string.startsWith(JMS_ID_PREFIX);
   }

   public String toMessageIdString(Object idObject) {
      if (idObject instanceof String stringId) {

         boolean hasMessageIdPrefix = hasMessageIdPrefix(stringId);
         if (!hasMessageIdPrefix) {
            // For JMSMessageID, has no "ID:" prefix, we need to record
            // that for later use as a JMSCorrelationID.
            return JMS_ID_PREFIX + AMQP_NO_PREFIX + stringId;
         } else if (hasTypeEncodingPrefix(stringId, JMS_ID_PREFIX_LENGTH)) {
            // We are for a JMSMessageID value, but have 'ID:' followed by
            // one of the encoding prefixes. Need to escape the entire string
            // to preserve for later re-use as a JMSCorrelationID.
            return JMS_ID_PREFIX + AMQP_STRING_PREFIX + stringId;
         } else {
            // It has "ID:" prefix and doesn't have encoding prefix, use it as-is.
            return stringId;
         }
      } else {
         // Not a string, convert it
         return convertToIdString(idObject);
      }
   }

   public Object toCorrelationIdStringOrBytes(Object idObject) {
      if (idObject instanceof String stringId) {

         boolean hasMessageIdPrefix = hasMessageIdPrefix(stringId);
         if (!hasMessageIdPrefix) {
            // For JMSCorrelationID, has no "ID:" prefix, use it as-is.
            return stringId;
         } else if (hasTypeEncodingPrefix(stringId, JMS_ID_PREFIX_LENGTH)) {
            // We are for a JMSCorrelationID value, but have 'ID:' followed by
            // one of the encoding prefixes. Need to escape the entire string
            // to preserve for later re-use as a JMSCorrelationID.
            return JMS_ID_PREFIX + AMQP_STRING_PREFIX + stringId;
         } else {
            // It has "ID:" prefix and doesn't have encoding prefix, use it as-is.
            return stringId;
         }
      } else if (idObject instanceof Binary binary) {
         ByteBuffer dup = binary.asByteBuffer();
         byte[] bytes = new byte[dup.remaining()];
         dup.get(bytes);
         return bytes;
      } else {
         // Not a string, convert it
         return convertToIdString(idObject);
      }
   }

   /**
    * Takes the provided non-String AMQP message-id/correlation-id object, and convert it it to a String usable as
    * either a JMSMessageID or JMSCorrelationID value, encoding the type information as a prefix to convey for later use
    * in reversing the process if used to set JMSCorrelationID on a message.
    *
    * @param idObject the object to process
    * @return string to be used for the actual JMS ID
    */
   private String convertToIdString(Object idObject) {
      if (idObject == null) {
         return null;
      }

      if (idObject instanceof UUID) {
         return JMS_ID_PREFIX + AMQP_UUID_PREFIX + idObject.toString();
      } else if (idObject instanceof UnsignedLong) {
         return JMS_ID_PREFIX + AMQP_ULONG_PREFIX + idObject.toString();
      } else if (idObject instanceof Binary binary) {
         ByteBuffer dup = binary.asByteBuffer();

         byte[] bytes = new byte[dup.remaining()];
         dup.get(bytes);

         String hex = convertBinaryToHexString(bytes);

         return JMS_ID_PREFIX + AMQP_BINARY_PREFIX + hex;
      } else {
         throw new IllegalArgumentException("Unsupported type provided: " + idObject.getClass());
      }
   }

   private boolean hasTypeEncodingPrefix(String stringId, int offset) {
      if (!stringId.startsWith(AMQP_PREFIX, offset)) {
         return false;
      }

      return hasAmqpBinaryPrefix(stringId, offset) || hasAmqpUuidPrefix(stringId, offset) || hasAmqpUlongPrefix(stringId, offset)
         || hasAmqpStringPrefix(stringId, offset) || hasAmqpNoPrefix(stringId, offset);
   }

   private boolean hasAmqpStringPrefix(String stringId, int offset) {
      return stringId.startsWith(AMQP_STRING_PREFIX, offset);
   }

   private boolean hasAmqpUlongPrefix(String stringId, int offset) {
      return stringId.startsWith(AMQP_ULONG_PREFIX, offset);
   }

   private boolean hasAmqpUuidPrefix(String stringId, int offset) {
      return stringId.startsWith(AMQP_UUID_PREFIX, offset);
   }

   private boolean hasAmqpBinaryPrefix(String stringId, int offset) {
      return stringId.startsWith(AMQP_BINARY_PREFIX, offset);
   }

   private boolean hasAmqpNoPrefix(String stringId, int offset) {
      return stringId.startsWith(AMQP_NO_PREFIX, offset);
   }

   /**
    * Takes the provided id string and return the appropriate amqp messageId style object. Converts the type based on
    * any relevant encoding information found as a prefix.
    *
    * @param origId the object to be converted
    * @return the AMQP messageId style object
    * @throws ActiveMQAMQPIllegalStateException if the provided baseId String indicates an encoded type but can't be
    *                                           converted to that type.
    */
   public Object toIdObject(final String origId) throws ActiveMQAMQPIllegalStateException {
      if (origId == null) {
         return null;
      }

      if (!AMQPMessageIdHelper.INSTANCE.hasMessageIdPrefix(origId)) {
         // We have a string without any "ID:" prefix, it is an
         // application-specific String, use it as-is.
         return origId;
      }

      try {
         if (hasAmqpNoPrefix(origId, JMS_ID_PREFIX_LENGTH)) {
            // Prefix telling us there was originally no "ID:" prefix,
            // strip it and return the remainder
            return origId.substring(JMS_ID_PREFIX_LENGTH + AMQP_NO_PREFIX_LENGTH);
         } else if (hasAmqpUuidPrefix(origId, JMS_ID_PREFIX_LENGTH)) {
            String uuidString = origId.substring(JMS_ID_PREFIX_LENGTH + AMQP_UUID_PREFIX_LENGTH);
            return UUID.fromString(uuidString);
         } else if (hasAmqpUlongPrefix(origId, JMS_ID_PREFIX_LENGTH)) {
            String ulongString = origId.substring(JMS_ID_PREFIX_LENGTH + AMQP_ULONG_PREFIX_LENGTH);
            return UnsignedLong.valueOf(ulongString);
         } else if (hasAmqpStringPrefix(origId, JMS_ID_PREFIX_LENGTH)) {
            return origId.substring(JMS_ID_PREFIX_LENGTH + AMQP_STRING_PREFIX_LENGTH);
         } else if (hasAmqpBinaryPrefix(origId, JMS_ID_PREFIX_LENGTH)) {
            String hexString = origId.substring(JMS_ID_PREFIX_LENGTH + AMQP_BINARY_PREFIX_LENGTH);
            byte[] bytes = convertHexStringToBinary(hexString);
            return new Binary(bytes);
         } else {
            // We have a string without any encoding prefix needing processed,
            // so transmit it as-is, including the "ID:"
            return origId;
         }
      } catch (IllegalArgumentException iae) {
         throw new ActiveMQAMQPIllegalStateException(iae.getMessage());
      }
   }

   /**
    * Convert the provided hex-string into a binary representation where each byte represents two characters of the hex
    * string.
    * <p>
    * The hex characters may be upper or lower case.
    *
    * @param hexString string to convert
    * @return a byte array containing the binary representation
    * @throws IllegalArgumentException if the provided String is a non-even length or contains non-hex characters
    */
   public byte[] convertHexStringToBinary(String hexString) throws IllegalArgumentException {
      int length = hexString.length();

      // As each byte needs two characters in the hex encoding, the string must
      // be an even length.
      if (length % 2 != 0) {
         throw new IllegalArgumentException("The provided hex String must be an even length, but was of length " + length + ": " + hexString);
      }

      byte[] binary = new byte[length / 2];

      for (int i = 0; i < length; i += 2) {
         char highBitsChar = hexString.charAt(i);
         char lowBitsChar = hexString.charAt(i + 1);

         int highBits = hexCharToInt(highBitsChar, hexString) << 4;
         int lowBits = hexCharToInt(lowBitsChar, hexString);

         binary[i / 2] = (byte) (highBits + lowBits);
      }

      return binary;
   }

   private int hexCharToInt(char ch, String orig) throws IllegalArgumentException {
      if (ch >= '0' && ch <= '9') {
         // subtract '0' to get difference in position as an int
         return ch - '0';
      } else if (ch >= 'A' && ch <= 'F') {
         // subtract 'A' to get difference in position as an int
         // and then add 10 for the offset of 'A'
         return ch - 'A' + 10;
      } else if (ch >= 'a' && ch <= 'f') {
         // subtract 'a' to get difference in position as an int
         // and then add 10 for the offset of 'a'
         return ch - 'a' + 10;
      }

      throw new IllegalArgumentException("The provided hex string contains non-hex character '" + ch + "': " + orig);
   }

   /**
    * Convert the provided binary into a hex-string representation where each character represents 4 bits of the
    * provided binary, i.e each byte requires two characters.
    * <p>
    * The returned hex characters are upper-case.
    *
    * @param bytes binary to convert
    * @return a String containing a hex representation of the bytes
    */
   public String convertBinaryToHexString(byte[] bytes) {
      // Each byte is represented as 2 chars
      StringBuilder builder = new StringBuilder(bytes.length * 2);

      for (byte b : bytes) {
         // The byte will be expanded to int before shifting, replicating the
         // sign bit, so mask everything beyond the first 4 bits afterwards
         int highBitsInt = (b >> 4) & 0xF;
         // We only want the first 4 bits
         int lowBitsInt = b & 0xF;

         builder.append(HEX_CHARS[highBitsInt]);
         builder.append(HEX_CHARS[lowBitsInt]);
      }

      return builder.toString();
   }
}
