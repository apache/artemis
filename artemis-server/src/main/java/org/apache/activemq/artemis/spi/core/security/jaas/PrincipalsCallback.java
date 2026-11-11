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
package org.apache.activemq.artemis.spi.core.security.jaas;

import java.security.Principal;

import javax.security.auth.callback.Callback;

/**
 * A Callback for getting the peer principals.
 */
public class PrincipalsCallback implements Callback {

   Principal[] peerPrincipals;

   /**
    * Setter for peer Principals.
    *
    * @param principal The certificates to be returned.
    */
   public void setPeerPrincipals(Principal[] principal) {
      peerPrincipals = principal;
   }

   /**
    * Getter for peer Principals.
    *
    * @return The principal being carried
    */
   public Principal[] getPeerPrincipals() {
      return peerPrincipals;
   }
}
