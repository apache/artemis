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

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.broker.AmqpInterceptor;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPRoutingHandler;
import org.apache.activemq.artemis.protocol.amqp.sasl.ServerSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ServerSASLFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.security.jaas.DigestCallback;
import org.apache.activemq.artemis.spi.core.security.jaas.HmacCallback;
import org.apache.activemq.artemis.spi.core.security.jaas.SCRAMMechanismCallback;
import org.apache.activemq.artemis.spi.core.security.scram.SCRAM;
import org.apache.activemq.artemis.spi.core.security.scram.UserData;
import org.slf4j.Logger;

/**
 * abstract class that implements the SASL-SCRAM authentication scheme, concrete implementations must supply the
 * {@link SCRAM} type to use and be register via SPI
 */
public abstract class SCRAMServerSASLFactory implements ServerSASLFactory {

   private final Logger logger;
   private final SCRAM scramType;

   public SCRAMServerSASLFactory(SCRAM scram, Logger logger) {
      this.scramType = scram;
      this.logger = logger;
   }

   @Override
   public String getMechanism() {
      return scramType.getName();
   }

   @Override
   public boolean isDefaultPermitted() {
      return false;
   }

   @Override
   public ServerSASL create(ActiveMQServer server, ProtocolManager<AmqpInterceptor, AMQPRoutingHandler> manager, Connection connection,
                            RemotingConnection remotingConnection) {
      try {
         if (manager instanceof ProtonProtocolManager protonProtocolManager) {
            String loginConfigScope = protonProtocolManager.getSaslLoginConfigScope();
            return new JAASSCRAMServerSASL(scramType, loginConfigScope, logger);
         }
      } catch (NoSuchAlgorithmException e) {
         // can't be used then...
      }
      return null;
   }

   private static final class JAASSCRAMServerSASL extends SCRAMServerSASL {

      private final String loginConfigScope;
      private LoginContext loginContext = null;
      private Subject loginSubject;
      private final Logger logger;

      JAASSCRAMServerSASL(SCRAM scram, String loginConfigScope, Logger logger) throws NoSuchAlgorithmException {
         super(scram);
         this.loginConfigScope = loginConfigScope;
         this.logger = logger;
      }

      @Override
      protected UserData aquireUserData(String userName) throws LoginException {
         loginContext = new LoginContext(loginConfigScope, callbacks -> {
            for (Callback callback : callbacks) {
               if (callback instanceof NameCallback nameCallback) {
                  nameCallback.setName(userName);
               } else if (callback instanceof SCRAMMechanismCallback scramMechanismCallback) {
                  scramMechanismCallback.setMechanism(mechanism.getName());
               } else if (callback instanceof DigestCallback digestCallback) {
                  digestCallback.setDigest(scram.getDigest());
               } else if (callback instanceof HmacCallback hmacCallback) {
                  hmacCallback.setHmac(scram.getHmac());
               } else {
                  throw new UnsupportedCallbackException(callback, "Unrecognized Callback " +
                           callback.getClass().getSimpleName());
               }
            }
         });
         loginContext.login();
         loginSubject = loginContext.getSubject();
         Iterator<UserData> credentials = loginSubject.getPublicCredentials(UserData.class).iterator();
         if (credentials.hasNext()) {
            return credentials.next();
         }
         throw new LoginException("can't aquire user data through configured login config scope (" + loginConfigScope +
                  ")");
      }

      @Override
      protected Subject createSaslSubject(String userName, UserData userData) {
         if (loginSubject != null) {
            return new Subject(true, loginSubject.getPrincipals(), loginSubject.getPublicCredentials(),
                                              loginSubject.getPrivateCredentials());
         }
         return super.createSaslSubject(userName, userData);
      }

      @Override
      public void done() {
         if (loginContext != null) {
            try {
               loginContext.logout();
            } catch (LoginException e1) {
               // we can't do anything useful then...
            }
         }
         loginContext = null;
         loginSubject = null;
      }

      @Override
      protected void failed(Exception e) {
         logger.warn("SASL-SCRAM Authentication failed", e);
      }

   }

}
