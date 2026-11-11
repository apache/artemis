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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class OAuthBearerSASL extends ServerSASLToken {

   // https://datatracker.ietf.org/doc/html/rfc7628#section-3
   static final String MECHANISM_NAME = "OAUTHBEARER";

   public OAuthBearerSASL(SecurityStore securityStore, String securityDomain, RemotingConnection remotingConnection) {
      super(securityStore, securityDomain, remotingConnection);
   }

   @Override
   public String getName() {
      return MECHANISM_NAME;
   }

   @Override
   public byte[] processSASL(byte[] bytes) {
      // expecting `n,a=<user>,\x01host=<host>\x01port=<port>\x01auth=Bearer <token>\x01\x01`
      // according to https://datatracker.ietf.org/doc/html/rfc7628#section-3.1
      // `n,a=<user>,` is from https://datatracker.ietf.org/doc/html/rfc5801
      //  - gs2-cb-flag: "n" = client does not support channel binding,
      //                 "p" = client supports and used channel binding
      //                 "y" = client supports CB, thinks the server does not
      //  - gs2-authzid: "a=<saslname>"
      if (bytes == null || bytes.length == 0) {
         result = new TokenSASLResult(false, null, null);
      } else {
         if (bytes.length < 2 || !(bytes[bytes.length - 2] == '\001' && bytes[bytes.length - 1] == '\001')) {
            result = new TokenSASLResult(false, null, null);
         } else {
            String data = new String(bytes, StandardCharsets.UTF_8);
            String[] segments = data.split("\001");
            if (segments.length < 2) {
               result = new TokenSASLResult(false, null, null);
            } else {
               String gs2Header = segments[0];
               String[] headersSegments = gs2Header.split(",");
               String user = null;
               for (String s : headersSegments) {
                  if (s.startsWith("a=")) {
                     user = s.substring(2);
                  }
               }

               String token = null;
               for (int i = 1; i < segments.length; i++) {
                  if (segments[i].startsWith("auth=Bearer ")) {
                     token = segments[i].substring(12);
                  }
               }

               if (token == null) {
                  result = new TokenSASLResult(false, null, null);
               } else {
                  boolean success = authenticate(user, token);
                  result = new TokenSASLResult(success, user, token);
               }
            }
         }
      }

      // TODO: validate host and port from the SASL OAUTHBEARER initial response?

      if (result.isSuccess()) {
         return null;
      }

      // see https://datatracker.ietf.org/doc/html/rfc7628#section-3.2.2
      return Base64.getEncoder().encode("{\"status\":\"invalid_token\"}".getBytes());
   }

}
