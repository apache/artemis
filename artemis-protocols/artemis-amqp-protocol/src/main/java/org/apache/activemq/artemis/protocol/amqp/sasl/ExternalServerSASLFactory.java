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

import java.security.Principal;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.broker.AmqpInterceptor;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPRoutingHandler;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ExternalServerSASLFactory implements ServerSASLFactory {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   public String getMechanism() {
      return ExternalServerSASL.NAME;
   }

   @Override
   public ServerSASL create(ActiveMQServer server, ProtocolManager<AmqpInterceptor, AMQPRoutingHandler> manager, Connection connection,
                            RemotingConnection remotingConnection) {
      // validate ssl cert present
      Principal principal = CertificateUtil.getPeerPrincipalFromConnection(remotingConnection);
      if (principal != null) {
         ExternalServerSASL externalServerSASL = new ExternalServerSASL();
         externalServerSASL.setPrincipal(principal);
         return externalServerSASL;
      }
      logger.debug("SASL EXTERNAL mechanism requires a TLS peer principal");
      return null;
   }

   @Override
   public int getPrecedence() {
      return 0;
   }

   @Override
   public boolean isDefaultPermitted() {
      return false;
   }

}
