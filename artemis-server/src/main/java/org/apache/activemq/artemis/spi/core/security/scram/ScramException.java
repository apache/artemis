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

package org.apache.activemq.artemis.spi.core.security.scram;

import java.security.GeneralSecurityException;

/**
 * Indicates error while processing SCRAM sequence
 */
public class ScramException extends Exception {

   /**
    * Creates new ScramException
    *
    * @param message Exception message
    */
   public ScramException(String message) {
      super(message);
   }

   public ScramException(String message, GeneralSecurityException e) {
      super(message, e);
   }

   /**
    * Creates new ScramException
    *
    * @param cause Throwable
    */
   public ScramException(Throwable cause) {
      super(cause);
   }
}
