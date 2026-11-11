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

import javax.crypto.Mac;
import java.security.MessageDigest;

import org.apache.activemq.artemis.spi.core.security.scram.ScramException;
import org.apache.activemq.artemis.spi.core.security.scram.UserData;

/**
 * Provides building blocks for creating SCRAM authentication server
 */
public interface ScramServerFunctionality {

   /**
    * Handles client's first message
    *
    * @param message Client's first message
    * @return username extracted from the client message
    */
   String handleClientFirstMessage(String message) throws ScramException;

   /**
    * Prepares server's first message
    *
    * @param userData user data needed to prepare the message
    * @return Server's first message
    */
   String prepareFirstMessage(UserData userData);

   /**
    * Prepares server's final message
    *
    * @param clientFinalMessage Client's final message
    * @return Server's final message
    */
   String prepareFinalMessage(String clientFinalMessage) throws ScramException;

   /**
    * Checks if authentication is completed, either successfully or not. Authentication is completed if
    * {@link #getState()} returns ENDED.
    *
    * @return true if authentication has ended
    */
   boolean isSuccessful();

   /**
    * Checks if authentication is completed, either successfully or not. Authentication is completed if
    * {@link #getState()} returns ENDED.
    *
    * @return true if authentication has ended
    */
   boolean isEnded();

   /**
    * Gets the state of the authentication procedure
    *
    * @return Current state
    */
   State getState();

   /**
    * State of the authentication procedure
    */
   enum State {
      /**
       * Initial state
       */
      INITIAL,
      /**
       * First client message is handled (username is extracted)
       */
      FIRST_CLIENT_MESSAGE_HANDLED,
      /**
       * First server message is prepared
       */
      PREPARED_FIRST,
      /**
       * Authentication is completes, either successfully or not
       */
      ENDED
   }

   MessageDigest getDigest();

   Mac getHmac();
}
