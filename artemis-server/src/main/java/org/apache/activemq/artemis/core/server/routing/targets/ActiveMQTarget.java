/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.routing.targets;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQManagementProxy;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.server.routing.ConnectionRouter;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ActiveMQTarget extends AbstractTarget implements FailureListener {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean connected = false;

   private final ServerLocator serverLocator;

   private ClientSessionFactory sessionFactory;
   private RemotingConnection remotingConnection;
   private ActiveMQManagementProxy managementProxy;

   @Override
   public boolean isLocal() {
      return false;
   }

   @Override
   public boolean isConnected() {
      return connected;
   }

   public ActiveMQTarget(TransportConfiguration connector, String nodeID) {
      super(connector, nodeID);

      serverLocator = ActiveMQClient.createServerLocatorWithoutHA(connector);
   }


   @Override
   public void connect() throws Exception {
      sessionFactory = serverLocator.createSessionFactory(getUsername(), getPassword());

      remotingConnection = sessionFactory.getConnection();
      remotingConnection.addFailureListener(this);

      managementProxy = new ActiveMQManagementProxy(sessionFactory.createSession(getUsername(), getPassword(),
         false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE,
         ConnectionRouter.CLIENT_ID_PREFIX + UUIDGenerator.getInstance().generateStringUUID()).start());

      if (getNodeID() == null) {
         setNodeID(getAttribute(ResourceNames.BROKER, "NodeID", String.class, 3000));
      }

      connected = true;

      fireConnectedEvent();
   }

   @Override
   public void disconnect() throws Exception {
      if (connected) {
         connected = false;

         managementProxy.close();

         remotingConnection.removeFailureListener(this);

         sessionFactory.close();

         fireDisconnectedEvent();
      }
   }

   @Override
   public boolean checkReadiness() {
      try {
         return getAttribute(ResourceNames.BROKER, "Active", Boolean.class, 3000);
      } catch (Exception e) {
         logger.warn("Error on check readiness", e);
      }

      return false;
   }

   @Override
   public <T> T getAttribute(String resourceName, String attributeName, Class<T> attributeClass, int timeout) throws Exception {
      return managementProxy.getAttribute(resourceName, attributeName, attributeClass, timeout);
   }

   @Override
   public <T> T invokeOperation(String resourceName, String operationName, Object[] operationParams, Class<T> operationClass, int timeout) throws Exception {
      return managementProxy.invokeOperation(resourceName, operationName, operationParams, operationClass, timeout);
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      connectionFailed(exception, failedOver, null);
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
      try {
         if (connected) {
            disconnect();
         }
      } catch (Exception e) {
         logger.debug("Exception on disconnecting: ", e);
      }
   }
}
