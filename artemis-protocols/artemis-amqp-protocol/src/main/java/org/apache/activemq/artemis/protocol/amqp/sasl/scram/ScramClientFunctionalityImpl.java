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

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
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
import org.apache.activemq.artemis.spi.core.security.scram.StringPrep;

/**
 * Provides building blocks for creating SCRAM authentication client
 */
@SuppressWarnings("unused")
public class ScramClientFunctionalityImpl implements ScramClientFunctionality {
   private static final Pattern SERVER_FIRST_MESSAGE = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$");
   private static final Pattern SERVER_FINAL_MESSAGE = Pattern.compile("v=([^,]*)$");

   private static final String GS2_HEADER = "n,,";
   private static final Charset ASCII = Charset.forName("ASCII");

   private final String mDigestName;
   private final String mHmacName;
   private final String mClientNonce;
   private String mClientFirstMessageBare;

   private final boolean mIsSuccessful = false;
   private byte[] mSaltedPassword;
   private String mAuthMessage;

   private State mState = State.INITIAL;

   /**
    * Create new ScramClientFunctionalityImpl
    *
    * @param digestName Digest to be used
    * @param hmacName   HMAC to be used
    */
   public ScramClientFunctionalityImpl(String digestName, String hmacName) {
      this(digestName, hmacName, UUID.randomUUID().toString());
   }

   /**
    * Create new ScramClientFunctionalityImpl
    *
    * @param digestName  Digest to be used
    * @param hmacName    HMAC to be used
    * @param clientNonce Client nonce to be used
    */
   public ScramClientFunctionalityImpl(String digestName, String hmacName, String clientNonce) {
      mDigestName = ScramUtils.requireNonNullAndNotEmpty(digestName, "digestName");
      mHmacName = ScramUtils.requireNonNullAndNotEmpty(hmacName, "hmacName");
      mClientNonce = ScramUtils.requireNonNullAndNotEmpty(clientNonce, "clientNonce");
   }

   /**
    * Prepares first client message You may want to use {@link StringPrep#isContainingProhibitedCharacters(String)} in
    * order to check if the username contains only valid characters
    *
    * @param username Username
    * @return prepared first message
    * @throws ScramException if {@code username} contains prohibited characters
    */
   @Override
   public String prepareFirstMessage(String username) throws ScramException {
      if (mState != State.INITIAL) {
         throw new IllegalStateException("You can call this method only once");
      }

      try {
         mClientFirstMessageBare = "n=" + StringPrep.prepAsQueryString(username) + ",r=" + mClientNonce;
         mState = State.FIRST_PREPARED;
         return GS2_HEADER + mClientFirstMessageBare;
      } catch (StringPrep.StringPrepError e) {
         mState = State.ENDED;
         throw new ScramException("Username contains prohibited character");
      }
   }

   @Override
   public String prepareFinalMessage(String password, String serverFirstMessage) throws ScramException {
      if (mState != State.FIRST_PREPARED) {
         throw new IllegalStateException("You can call this method once only after " + "calling prepareFirstMessage()");
      }

      Matcher m = SERVER_FIRST_MESSAGE.matcher(serverFirstMessage);
      if (!m.matches()) {
         mState = State.ENDED;
         return null;
      }

      String nonce = m.group(1);

      if (!nonce.startsWith(mClientNonce)) {
         mState = State.ENDED;
         return null;
      }

      String salt = m.group(2);
      String iterationCountString = m.group(3);
      int iterations = Integer.parseInt(iterationCountString);
      if (iterations <= 0) {
         mState = State.ENDED;
         return null;
      }

      try {
         mSaltedPassword = ScramUtils.generateSaltedPassword(password, Base64.getDecoder().decode(salt), iterations,
                                                             Mac.getInstance(mHmacName));

         String clientFinalMessageWithoutProof =
                  "c=" + Base64.getEncoder().encodeToString(GS2_HEADER.getBytes(ASCII)) + ",r=" + nonce;

         mAuthMessage = mClientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;

         byte[] clientKey = ScramUtils.computeHmac(mSaltedPassword, mHmacName, "Client Key");
         byte[] storedKey = MessageDigest.getInstance(mDigestName).digest(clientKey);

         byte[] clientSignature = ScramUtils.computeHmac(storedKey, mHmacName, mAuthMessage);

         byte[] clientProof = clientKey.clone();
         for (int i = 0; i < clientProof.length; i++) {
            clientProof[i] ^= clientSignature[i];
         }

         mState = State.FINAL_PREPARED;
         return clientFinalMessageWithoutProof + ",p=" + Base64.getEncoder().encodeToString(clientProof);
      } catch (InvalidKeyException | NoSuchAlgorithmException e) {
         mState = State.ENDED;
         throw new ScramException(e);
      }
   }

   @Override
   public void checkServerFinalMessage(String serverFinalMessage) throws ScramException {
      if (mState != State.FINAL_PREPARED) {
         throw new IllegalStateException("You can call this method only once after " + "calling prepareFinalMessage()");
      }

      Matcher m = SERVER_FINAL_MESSAGE.matcher(serverFinalMessage);
      if (!m.matches()) {
         mState = State.ENDED;
         throw new ScramException("invalid message format");
      }

      byte[] serverSignature = Base64.getDecoder().decode(m.group(1));

      mState = State.ENDED;
      if (!Arrays.equals(serverSignature, getExpectedServerSignature())) {
         throw new ScramException("Server signature missmatch");
      }
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

   private byte[] getExpectedServerSignature() throws ScramException {
      try {
         byte[] serverKey = ScramUtils.computeHmac(mSaltedPassword, mHmacName, "Server Key");
         return ScramUtils.computeHmac(serverKey, mHmacName, mAuthMessage);
      } catch (InvalidKeyException | NoSuchAlgorithmException e) {
         mState = State.ENDED;
         throw new ScramException(e);
      }
   }
}
