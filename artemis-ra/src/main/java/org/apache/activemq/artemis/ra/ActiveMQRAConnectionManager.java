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
package org.apache.activemq.artemis.ra;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The connection manager used in non-managed environments.
 */
public class ActiveMQRAConnectionManager implements ConnectionManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   static final long serialVersionUID = 4409118162975011014L;

   public ActiveMQRAConnectionManager() {
      logger.trace("constructor()");
   }

   transient Set<ManagedConnection> connections = ConcurrentHashMap.newKeySet();

   /**
    * Allocates a connection
    *
    * @param mcf           The managed connection factory
    * @param cxRequestInfo The connection request information
    * @return The connection
    * @throws ResourceException Thrown if there is a problem obtaining the connection
    */
   @Override
   public Object allocateConnection(final ManagedConnectionFactory mcf,
                                    final ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      logger.trace("allocateConnection({}, {})", mcf, cxRequestInfo);

      ManagedConnection mc = mcf.createManagedConnection(null, cxRequestInfo);
      Object c = mc.getConnection(null, cxRequestInfo);

      logger.trace("Allocated connection: {}, with managed connection: {}", c, mc);

      connections.add(mc);
      return c;
   }

   public void stop() {
      for (ManagedConnection conn : connections) {
         try {
            conn.destroy();
         } catch (Throwable e) {

         }
      }
   }

   /*
    * Java serialization needs this in order to intialize transient fields
    */
   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      connections = ConcurrentHashMap.newKeySet();
   }
}
