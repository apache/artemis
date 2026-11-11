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
package org.apache.activemq.artemis.core.server.plugin;

import java.util.Map;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;

public interface ActiveMQServerSessionPlugin extends ActiveMQServerBasePlugin {

   /**
    * Before a session is created.
    */
   default void beforeCreateSession(String name, String username, int minLargeMessageSize,
                                    RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge,
                                    boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context,
                                    Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {

   }

   /**
    * After a session has been created.
    *
    * @param session The newly created session
    */
   default void afterCreateSession(ServerSession session) throws ActiveMQException {

   }

   /**
    * Before a session is closed
    */
   default void beforeCloseSession(ServerSession session, boolean failed) throws ActiveMQException {

   }

   /**
    * After a session is closed
    */
   default void afterCloseSession(ServerSession session, boolean failed) throws ActiveMQException {

   }

   /**
    * Before session metadata is added to the session
    */
   default void beforeSessionMetadataAdded(ServerSession session, String key, String data) throws ActiveMQException {

   }

   /**
    * Called when adding session metadata fails because the metadata is a duplicate
    */
   default void duplicateSessionMetadataFailure(ServerSession session, String key, String data) throws ActiveMQException {

   }

   /**
    * After session metadata is added to the session
    */
   default void afterSessionMetadataAdded(ServerSession session, String key, String data) throws ActiveMQException {

   }
}
