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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnection;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple AMQP Bridge aggregation tool used to simplify broker connection management of multiple bridges
 */
public class AMQPBridgeManagers {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Collection<AMQPBridgeManager> bridgeManagers = ConcurrentHashMap.newKeySet();
   private final AMQPBrokerConnection brokerConnection;

   public AMQPBridgeManagers(AMQPBrokerConnection brokerConnection) {
      this.brokerConnection = brokerConnection;
   }

   /**
    * Shutdown all bridge managers ignoring any thrown exceptions and clears state
    * from all previously registered manager, new managers can be added after a
    * reset.
    */
   public void shutdown() {
      bridgeManagers.forEach(bridgeManager -> {
         try {
            bridgeManager.shutdown();
         } catch (Exception e) {
            logger.trace("Ignoring exception thrown during bridge manager shutdown: ", e);
         }
      });

      bridgeManagers.clear();
   }

   /**
    * Starts each bridge manager registered in the managers collection.
    *
    * @throws Exception if an error occurs while starting any bridge manager.
    */
   public void start() throws Exception {
      for (AMQPBridgeManager manager : bridgeManagers) {
         try {
            manager.start();
         } catch (Exception e) {
            logger.debug("Exception thrown during bridge manager start: ", e);
            throw e;
         }
      }
   }

   /**
    * Stops each bridge manager registered in the managers collection.
    *
    * @throws Exception if an error occurs while stopping any bridge manager.
    */
   public void stop() throws Exception {
      Exception firstError = null;

      for (AMQPBridgeManager manager : bridgeManagers) {
         try {
            manager.stop();
         } catch (Exception e) {
            logger.debug("Exception thrown during bridge manager stop: ", e);

            if (firstError == null) {
               firstError = e;
            }
         }
      }

      if (firstError != null) {
         throw firstError;
      }
   }

   /**
    * Adds a new bridge to the collection of managed bridges and starts the new {@link AMQPBridgeManager}
    *
    * @param configuration
    *    The configuration to use when creating a new bridge manager.
    */
   public void addBridgeManager(AMQPBridgeBrokerConnectionElement configuration) throws ActiveMQException {
      final AMQPBridgeManager bridgeManager = AMQPBridgeSupport.createManager(brokerConnection, configuration);

      try {
         bridgeManager.initialize();
      } catch (ActiveMQException e) {
         logger.debug("Error caught and re-thrown while initializing configured bridge connection:", e);
         throw e;
      }

      bridgeManagers.add(bridgeManager);
   }

   /**
    * Signal all bridge managers that the connection has been restored
    *
    * @param session
    *    The session in which the bridge manager resources will reside.
    *
    * @throws ActiveMQException if an error occurs during connection restoration.
    */
   public void connectionRestored(AMQPSessionContext session) throws ActiveMQException {
      for (AMQPBridgeManager bridgeManager : bridgeManagers) {
         try {
            bridgeManager.connectionRestored(session.getAMQPConnectionContext(), session);
         } catch (ActiveMQException e) {
            logger.trace("AMQP Bridge connection {} threw an error on handling of connection restored: ", bridgeManager.getName(), e);
            throw e;
         }
      }
   }

   /**
    * Signals all bridges that the current connection has dropped, exceptions are ignored.
    */
   public void connectionInterrupted() {
      for (AMQPBridgeManager bridgeManager : bridgeManagers) {
         if (bridgeManager != null) {
            try {
               bridgeManager.connectionInterrupted();
            } catch (ActiveMQException e) {
               logger.trace("AMQP Bridge connection {} threw an error on handling of connection interrupted", bridgeManager.getName());
            }
         }
      }
   }
}
