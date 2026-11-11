/*
 * Copyright 2016 Ognyan Bankov
 * <p>
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.protocol.amqp.sasl.scram;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;

import org.apache.activemq.artemis.spi.core.security.scram.ScramException;
import org.apache.activemq.artemis.spi.core.security.scram.ScramUtils;
import org.apache.activemq.artemis.spi.core.security.scram.UserData;

/**
 * Provides building blocks for creating SCRAM authentication server
 */
public class ScramServerFunctionalityImpl implements ScramServerFunctionality {
   private static final Pattern CLIENT_FIRST_MESSAGE =
            Pattern.compile("^(([pny])=?([^,]*),([^,]*),)(m?=?[^,]*,?n=([^,]*),r=([^,]*),?.*)$");
   private static final Pattern CLIENT_FINAL_MESSAGE = Pattern.compile("(c=([^,]*),r=([^,]*)),p=(.*)$");

   private final String mServerPartNonce;

   private boolean mIsSuccessful = false;
   private State mState = State.INITIAL;
   private String mClientFirstMessageBare;
   private String mNonce;
   private String mServerFirstMessage;
   private UserData mUserData;
   private final MessageDigest digest;
   private final Mac hmac;

   /**
    * Creates new ScramServerFunctionalityImpl
    *
    * @param digestName Digest to be used
    * @param hmacName   HMAC to be used
    */
   public ScramServerFunctionalityImpl(String digestName, String hmacName) throws NoSuchAlgorithmException {
      this(digestName, hmacName, UUID.randomUUID().toString());
   }

   /**
    * Creates new ScramServerFunctionalityImpl
    *
    * @param digestName      Digest to be used
    * @param hmacName        HMAC to be used
    * @param serverPartNonce Server's part of the nonce
    */
   public ScramServerFunctionalityImpl(String digestName, String hmacName,
                                       String serverPartNonce) throws NoSuchAlgorithmException {
      digest = MessageDigest.getInstance(ScramUtils.requireNonNullAndNotEmpty(digestName, "digestName"));
      hmac = Mac.getInstance(ScramUtils.requireNonNullAndNotEmpty(hmacName, "hmacName"));
      mServerPartNonce = ScramUtils.requireNonNullAndNotEmpty(serverPartNonce, "serverPartNonce");
   }

   /**
    * Handles client's first message
    *
    * @param message Client's first message
    * @return username extracted from the client message
    */
   @Override
   public String handleClientFirstMessage(String message) throws ScramException {
      Matcher m = CLIENT_FIRST_MESSAGE.matcher(message);
      if (!m.matches()) {
         mState = State.ENDED;
         throw new ScramException("Invalid message received");
      }

      mClientFirstMessageBare = m.group(5);
      String username = m.group(6);
      String clientNonce = m.group(7);
      mNonce = clientNonce + mServerPartNonce;

      mState = State.FIRST_CLIENT_MESSAGE_HANDLED;

      return username;
   }

   @Override
   public String prepareFirstMessage(UserData userData) {
      mUserData = userData;
      mState = State.PREPARED_FIRST;
      mServerFirstMessage = String.format("r=%s,s=%s,i=%d", mNonce, userData.salt, userData.iterations);

      return mServerFirstMessage;
   }

   @Override
   public String prepareFinalMessage(String clientFinalMessage) throws ScramException {
      String finalMessage = prepareFinalMessageUnchecked(clientFinalMessage);
      if (!mIsSuccessful) {
         throw new ScramException("client credentials missmatch");
      }
      return finalMessage;
   }

   public String prepareFinalMessageUnchecked(String clientFinalMessage) throws ScramException {
      mState = State.ENDED;
      Matcher m = CLIENT_FINAL_MESSAGE.matcher(clientFinalMessage);
      if (!m.matches()) {
         throw new ScramException("Invalid message received");
      }

      String clientFinalMessageWithoutProof = m.group(1);
      String clientNonce = m.group(3);
      String proof = m.group(4);

      if (!mNonce.equals(clientNonce)) {
         throw new ScramException("Nonce mismatch");
      }

      String authMessage = mClientFirstMessageBare + "," + mServerFirstMessage + "," + clientFinalMessageWithoutProof;

      byte[] storedKeyArr = Base64.getDecoder().decode(mUserData.storedKey);
      byte[] clientSignature = ScramUtils.computeHmac(storedKeyArr, hmac, authMessage);
      byte[] serverSignature =
               ScramUtils.computeHmac(Base64.getDecoder().decode(mUserData.serverKey), hmac, authMessage);
      byte[] clientKey = clientSignature.clone();
      byte[] decodedProof = Base64.getDecoder().decode(proof);
      for (int i = 0; i < clientKey.length; i++) {
         clientKey[i] ^= decodedProof[i];
      }

      byte[] resultKey = digest.digest(clientKey);
      mIsSuccessful = Arrays.equals(storedKeyArr, resultKey);
      return "v=" + Base64.getEncoder().encodeToString(serverSignature);
   }

   @Override
   public boolean isSuccessful() {
      if (mState == State.ENDED) {
         return mIsSuccessful;
      } else {
         throw new IllegalStateException("You cannot call this method before authentication is ended. " +
                  "Use isEnded() to check that");
      }
   }

   @Override
   public boolean isEnded() {
      return mState == State.ENDED;
   }

   @Override
   public State getState() {
      return mState;
   }

   @Override
   public MessageDigest getDigest() {
      try {
         return (MessageDigest) digest.clone();
      } catch (CloneNotSupportedException cns) {
         try {
            return MessageDigest.getInstance(digest.getAlgorithm());
         } catch (NoSuchAlgorithmException nsa) {
            throw new AssertionError(nsa);
         }
      }
   }

   @Override
   public Mac getHmac() {
      try {
         return (Mac) hmac.clone();
      } catch (CloneNotSupportedException cns) {
         try {
            return Mac.getInstance(hmac.getAlgorithm());
         } catch (NoSuchAlgorithmException nsa) {
            throw new AssertionError(nsa);
         }
      }
   }
}
