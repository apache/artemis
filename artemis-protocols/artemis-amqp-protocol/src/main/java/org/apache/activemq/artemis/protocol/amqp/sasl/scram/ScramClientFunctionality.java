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

import org.apache.activemq.artemis.spi.core.security.scram.ScramException;

/**
 * Provides building blocks for creating SCRAM authentication client
 */
@SuppressWarnings("unused")
public interface ScramClientFunctionality {

   /**
    * Prepares the first client message
    *
    * @param username Username of the user
    * @return First client message
    * @throws ScramException if username contains prohibited characters
    */
   String prepareFirstMessage(String username) throws ScramException;

   /**
    * Prepares client's final message
    *
    * @param password           User password
    * @param serverFirstMessage Server's first message
    * @return Client's final message
    * @throws ScramException if there is an error processing server's message, i.e. it violates the protocol
    */
   String prepareFinalMessage(String password, String serverFirstMessage) throws ScramException;

   /**
    * Checks if the server's final message is valid
    *
    * @param serverFinalMessage Server's final message
    * @throws ScramException if there is an error processing server's message, i.e. it violates the protocol
    */
   void checkServerFinalMessage(String serverFinalMessage) throws ScramException;

   /**
    * Checks if authentication is successful. You can call this method only if authentication is completed. Ensure that
    * using {@link #isEnded()}
    *
    * @return true if successful, false otherwise
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
       * State after first message is prepared
       */
      FIRST_PREPARED,
      /**
       * State after final message is prepared
       */
      FINAL_PREPARED,
      /**
       * Authentication is completes, either successfully or not
       */
      ENDED
   }
}
