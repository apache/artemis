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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.junit.jupiter.api.Test;

public class ConnectMessageTest {

   static final int TEST_VERSION = 123;

   @Test
   public void testRoundTrip() {
      ConnectMessage encoded = new ConnectMessage("node-1", TEST_VERSION);

      ConnectMessage decoded = roundTrip(encoded);

      assertEquals("node-1", decoded.getNodeID());
      assertEquals(TEST_VERSION, decoded.getClientVersion());
      assertNull(decoded.getAuthMechanism());
      assertNull(decoded.getAuthData());
   }

   @Test
   public void testRoundTripPlainWithCredentials() {
      ConnectMessage encoded = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "cluster-user", "cluster-pass");

      ConnectMessage decoded = roundTrip(encoded);

      assertEquals("node-1", decoded.getNodeID());
      assertEquals(TEST_VERSION, decoded.getClientVersion());
      assertEquals(ConnectMessage.MECHANISM_PLAIN, decoded.getAuthMechanism());
      assertPlainAuthData(decoded.getAuthData(), "cluster-user", "cluster-pass");
   }

   @Test
   public void testRoundTripPlainWithNullOrEmptyPassword() {
      // Both null and "" password must encode identically as an empty passwd field.
      for (String password : new String[]{null, ""}) {
         ConnectMessage encoded = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "cluster-user", password);
         ConnectMessage decoded = roundTrip(encoded);
         assertEquals(ConnectMessage.MECHANISM_PLAIN, decoded.getAuthMechanism());
         assertPlainAuthData(decoded.getAuthData(), "cluster-user", "");
      }
   }

   @Test
   public void testLegacyMessageNoMechanism() {
      // A message with no auth mechanism simulates a legacy client — authMechanism must be null.
      ConnectMessage decoded = new ConnectMessage();
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(256);
      decoded.encodeRest(buffer); // encode the empty no-op message
      ConnectMessage redecoded = new ConnectMessage();
      redecoded.decodeRest(buffer);

      assertNull(redecoded.getAuthMechanism(), "legacy client must yield null authMechanism");
      assertNull(redecoded.getAuthData());
   }

   @Test
   public void testDecodeIgnoresUnknownFutureSections() {
      ConnectMessage encoded = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "cluster-user", "cluster-pass");
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(256);
      encoded.encodeRest(buffer);
      buffer.writeByte((byte) 99);
      buffer.writeInt(42);

      ConnectMessage decoded = new ConnectMessage();
      decoded.decodeRest(buffer);

      assertEquals("node-1", decoded.getNodeID());
      assertEquals(TEST_VERSION, decoded.getClientVersion());
      assertEquals(ConnectMessage.MECHANISM_PLAIN, decoded.getAuthMechanism());
      assertPlainAuthData(decoded.getAuthData(), "cluster-user", "cluster-pass");
   }

   @Test
   public void testDecodePlainAuthDataEmptyUsername() {
      ConnectMessage msg = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "", "mypass");
      String[] decoded = msg.decodePlainAuthData();
      assertEquals("", decoded[0]);
      assertEquals("mypass", decoded[1]);
   }

   @Test
   public void testDecodePlainAuthDataBothEmpty() {
      ConnectMessage msg = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "", "");
      String[] decoded = msg.decodePlainAuthData();
      assertEquals("", decoded[0]);
      assertEquals("", decoded[1]);
   }

   @Test
   public void testDecodePlainAuthDataNullAuthData() {
      // decodePlainAuthData on a message with no auth data returns empty strings
      ConnectMessage msg = new ConnectMessage("node-1", TEST_VERSION);
      String[] decoded = msg.decodePlainAuthData();
      assertEquals("", decoded[0], "username must be empty");
      assertEquals("", decoded[1], "password must be empty");
   }

   @Test
   public void testDecodePlainAuthDataAfterRoundTrip() {
      ConnectMessage msg = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "cluster-user", "cluster-pass");
      ConnectMessage decoded = roundTrip(msg);
      String[] credentials = decoded.decodePlainAuthData();
      assertEquals("cluster-user", credentials[0]);
      assertEquals("cluster-pass", credentials[1]);
   }

   @Test
   public void testRfc4616WireStructure() {
      // Verify the raw bytes follow RFC 4616: authzid NUL authcid NUL passwd
      // With empty authzid the bytes must be: NUL "alice" NUL "secret"
      ConnectMessage msg = ConnectMessage.withPlainCredentials("node-1", TEST_VERSION, "alice", "secret");
      byte[] data = msg.getAuthData();
      byte[] expected = new byte[]{0, 'a', 'l', 'i', 'c', 'e', 0, 's', 'e', 'c', 'r', 'e', 't'};
      assertArrayEquals(expected, data, "authData must be RFC 4616 authzid NUL authcid NUL passwd");
   }

   @Test
   public void testDecodePlainAuthDataFromRfc4616Bytes() {
      // Manually construct RFC 4616 bytes: authzid="" NUL authcid="bob" NUL passwd="pw"
      // and verify decodePlainAuthData extracts authcid and passwd correctly.
      ConnectMessage msg = new ConnectMessage("node-1", TEST_VERSION);
      msg.setAuthData(new byte[]{0, 'b', 'o', 'b', 0, 'p', 'w'});
      String[] credentials = msg.decodePlainAuthData();
      assertEquals("bob", credentials[0], "authcid must be extracted from RFC 4616 bytes");
      assertEquals("pw", credentials[1], "passwd must be extracted from RFC 4616 bytes");
   }

   @Test
   public void testDecodePlainAuthDataFromRfc4616BytesWithNonEmptyAuthzid() {
      // RFC 4616 allows a non-empty authzid: authzid="proxy" NUL authcid="bob" NUL passwd="pw"
      // decodePlainAuthData must ignore authzid and still return [authcid, passwd].
      ConnectMessage msg = new ConnectMessage("node-1", TEST_VERSION);
      msg.setAuthData(("proxy" + "\0" + "bob" + "\0" + "pw").getBytes(StandardCharsets.UTF_8));
      String[] credentials = msg.decodePlainAuthData();
      assertEquals("bob", credentials[0], "authcid must be extracted even when authzid is non-empty");
      assertEquals("pw", credentials[1], "passwd must be extracted even when authzid is non-empty");
   }

   @Test
   public void testDecodePlainAuthDataMalformedNoNul() {
      // No NUL at all — not a valid RFC 4616 payload.
      // Decoder treats the entire bytes as authzid (fields[0] only), so authcid and passwd default to empty.
      ConnectMessage msg = new ConnectMessage("node-1", TEST_VERSION);
      msg.setAuthData("justtext".getBytes(StandardCharsets.UTF_8));
      String[] credentials = msg.decodePlainAuthData();
      assertEquals("", credentials[0], "malformed: no NUL — authcid must be empty");
      assertEquals("", credentials[1], "malformed: no NUL — passwd must be empty");
   }

   @Test
   public void testDecodePlainAuthDataMalformedOnlyOneNul() {
      // Only one NUL: authzid NUL authcid — passwd field is missing.
      // Decoder returns authcid from fields[1] and empty string for the missing passwd.
      ConnectMessage msg = new ConnectMessage("node-1", TEST_VERSION);
      msg.setAuthData(("authzid" + "\0" + "authcid").getBytes(StandardCharsets.UTF_8));
      String[] credentials = msg.decodePlainAuthData();
      assertEquals("authcid", credentials[0], "malformed: one NUL — authcid must be fields[1]");
      assertEquals("", credentials[1], "malformed: one NUL — missing passwd must be empty");
   }

   @Test
   public void testDecodePlainAuthDataMalformedLeadingNulOnly() {
      // Leading NUL but no second NUL: authzid="" NUL authcid — passwd field is missing.
      ConnectMessage msg = new ConnectMessage("node-1", TEST_VERSION);
      msg.setAuthData(("\0" + "authcid").getBytes(StandardCharsets.UTF_8));
      String[] credentials = msg.decodePlainAuthData();
      assertEquals("authcid", credentials[0], "malformed: leading NUL only — authcid must be fields[1]");
      assertEquals("", credentials[1], "malformed: leading NUL only — missing passwd must be empty");
   }

   /**
    * Asserts that {@code authData} matches the RFC 4616 SASL PLAIN wire format:
    * {@code authzid NUL authcid NUL passwd} where {@code authzid} is always empty.
    */
   private static void assertPlainAuthData(byte[] authData, String expectedUser, String expectedPass) {
      // RFC 4616: authzid NUL authcid NUL passwd  (authzid is empty)
      byte[] expected = ("" + "\0" + expectedUser + "\0" + expectedPass).getBytes(StandardCharsets.UTF_8);
      assertArrayEquals(expected, authData);
   }

   private static ConnectMessage roundTrip(ConnectMessage encoded) {
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(256);
      encoded.encodeRest(buffer);

      ConnectMessage decoded = new ConnectMessage();
      decoded.decodeRest(buffer);
      return decoded;
   }
}
