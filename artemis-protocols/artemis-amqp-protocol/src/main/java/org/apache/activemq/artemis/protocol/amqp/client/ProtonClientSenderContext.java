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
package org.apache.activemq.artemis.protocol.amqp.client;

import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.qpid.proton.engine.Sender;

/**
 * TODO: Merge {@link ProtonServerSenderContext} and {@link ProtonClientSenderContext} once we support 'global' link names. The split is a workaround for outgoing links
 */
public class ProtonClientSenderContext extends ProtonServerSenderContext {
   public ProtonClientSenderContext(AMQPConnectionContext connection, Sender sender, AMQPSessionContext amqpSessionContext, AMQPSessionCallback sessionSPI) {
      super(connection, sender, amqpSessionContext, sessionSPI);
   }

   @Override
   protected String getClientId() {
      return connection.getContainer();
   }
}
