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
package org.apache.activemq.artemis.protocol.amqp.connect;

import org.apache.qpid.proton.amqp.Symbol;

/**
 * A collection of constants used for AMQP broker connections.
 */
public abstract class AMQPBrokerConnectionConstants {

   /**
    * Property name used to embed a nested map of properties meant to be conveyed to the remote peer describing
    * attributes assigned to the AMQP broker connection
    */
   public static final Symbol BROKER_CONNECTION_INFO = Symbol.getSymbol("broker-connection-info");

   /**
    * Map entry key used to carry the AMQP broker connection name to the remote peer in the information map sent in the
    * AMQP connection properties.
    */
   public static final String CONNECTION_NAME = "connectionName";

   /**
    * Map entry key used to carry the Node ID of the server where the AMQP broker connection originates from to the
    * remote peer in the information map sent in the AMQP connection properties.
    */
   public static final String NODE_ID = "nodeId";

   private AMQPBrokerConnectionConstants() {
      // Hidden
   }
}
