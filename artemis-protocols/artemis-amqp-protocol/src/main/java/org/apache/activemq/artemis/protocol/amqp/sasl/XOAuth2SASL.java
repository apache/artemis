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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

import java.util.Base64;

public class XOAuth2SASL extends ServerSASLToken {

   // https://developers.google.com/workspace/gmail/imap/xoauth2-protocol
   // marked OBSOLETE at https://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml
   // Wikipedia mentions XOAUTH2 only here: https://en.wikipedia.org/wiki/SMTP_Authentication
   static final String MECHANISM_NAME = "XOAUTH2";

   public XOAuth2SASL(SecurityStore securityStore, String securityDomain, RemotingConnection remotingConnection) {
      super(securityStore, securityDomain, remotingConnection);
   }

   @Override
   public String getName() {
      return MECHANISM_NAME;
   }

   @Override
   public byte[] processSASL(byte[] bytes) {
      // expecting `user=<username>\x01auth=Bearer <token>\x01\x01`
      // according to https://developers.google.com/workspace/gmail/imap/xoauth2-protocol
      if (bytes == null || bytes.length == 0) {
         result = new TokenSASLResult(false, null, null);
      } else {
         String userPart = null;
         String tokenPart = null;
         int from = 0, to = 0;
         while (to < bytes.length && bytes[to] != '\001') {
            to++;
         }
         if (to - from >= 5) {
            userPart = new String(bytes, from, to - from);
            ++to;
            from = to;
         }
         while (to < bytes.length && bytes[to] != '\001') {
            to++;
         }
         if (to - from >= 12) {
            tokenPart = new String(bytes, from, to - from);
         }
         boolean valid = userPart != null && tokenPart != null;
         if (valid) {
            valid = userPart.startsWith("user=");
            valid &= tokenPart.startsWith("auth=Bearer ");
            valid &= to == bytes.length - 2; // trailing { 0x01, 0x01 }
         }
         if (valid) {
            String user = userPart.substring(5);
            String token = tokenPart.substring(12);
            boolean success = authenticate(user, token);
            result = new TokenSASLResult(success, user, token);
         } else {
            result = new TokenSASLResult(false, null, null);
         }
      }

      if (result.isSuccess()) {
         return null;
      }

      // see https://developers.google.com/workspace/gmail/imap/xoauth2-protocol#error_response
      String address = remotingConnection.getTransportLocalAddress();
      return Base64.getEncoder().encode(String.format("{\"status\":\"401\",\"schemes\":\"bearer\",\"scope\":\"%s\"}", address).getBytes());
   }

}
