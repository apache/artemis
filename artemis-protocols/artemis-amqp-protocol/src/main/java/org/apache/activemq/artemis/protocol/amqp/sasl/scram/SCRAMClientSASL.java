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
package org.apache.activemq.artemis.protocol.amqp.sasl.scram;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.scram.ScramClientFunctionality.State;
import org.apache.activemq.artemis.spi.core.security.scram.SCRAM;
import org.apache.activemq.artemis.spi.core.security.scram.ScramException;
import org.apache.qpid.proton.codec.DecodeException;

/**
 * implements the client part of SASL-SCRAM for broker interconnect
 */
public class SCRAMClientSASL implements ClientSASL {
   private final SCRAM scramType;
   private final ScramClientFunctionalityImpl client;
   private final String username;
   private final String password;

   /**
    * @param scram    the SCRAM mechanism to use
    * @param username the username for authentication
    * @param password the password for authentication
    */
   public SCRAMClientSASL(SCRAM scram, String username, String password) {
      this(scram, username, password, UUID.randomUUID().toString());
   }

   protected SCRAMClientSASL(SCRAM scram, String username, String password, String nonce) {
      Objects.requireNonNull(scram);
      Objects.requireNonNull(username);
      Objects.requireNonNull(password);
      this.username = username;
      this.password = password;
      this.scramType = scram;
      client = new ScramClientFunctionalityImpl(scram.getDigest(), scram.getHmac(), nonce);
   }

   @Override
   public String getName() {
      return scramType.getName();
   }

   @Override
   public byte[] getInitialResponse() {
      try {
         String firstMessage = client.prepareFirstMessage(username);
         return firstMessage.getBytes(StandardCharsets.US_ASCII);
      } catch (ScramException e) {
         throw new DecodeException("prepareFirstMessage failed", e);
      }
   }

   @Override
   public byte[] getResponse(byte[] challenge) {
      String msg = new String(challenge, StandardCharsets.US_ASCII);
      if (client.getState() == State.FIRST_PREPARED) {
         try {
            String finalMessage = client.prepareFinalMessage(password, msg);
            return finalMessage.getBytes(StandardCharsets.US_ASCII);
         } catch (ScramException e) {
            throw new DecodeException("prepareFinalMessage failed", e);
         }
      } else if (client.getState() == State.FINAL_PREPARED) {
         try {
            client.checkServerFinalMessage(msg);
         } catch (ScramException e) {
            throw new DecodeException("checkServerFinalMessage failed", e);
         }
      }
      return new byte[0];
   }

   public static boolean isApplicable(String username, String password) {
      return username != null && !username.isEmpty() && password != null && !password.isEmpty();
   }

}
