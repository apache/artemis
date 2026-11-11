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
package org.apache.activemq.artemis.core.protocol.hornetq.client;

import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.AddressQueryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQConsumerContext;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQSessionContext;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCreateConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.TokenBucketLimiterImpl;

public class HornetQClientSessionContext extends ActiveMQSessionContext {

   public HornetQClientSessionContext(String name,
                                      RemotingConnection remotingConnection,
                                      Channel sessionChannel,
                                      int serverVersion,
                                      int confirmationWindow) {
      super(name, remotingConnection, sessionChannel, serverVersion, confirmationWindow);
   }

   @Override
   public ClientSession.QueueQuery queueQuery(final SimpleString queueName) throws ActiveMQException {
      SessionQueueQueryMessage request = new SessionQueueQueryMessage(queueName);
      SessionQueueQueryResponseMessage response = (SessionQueueQueryResponseMessage) getSessionChannel().sendBlocking(request, PacketImpl.SESS_QUEUEQUERY_RESP);

      return response.toQueueQuery();
   }

   @Override
   protected CreateSessionMessage newCreateSession(String username,
                                                   String password,
                                                   int minLargeMessageSize,
                                                   boolean xa,
                                                   boolean autoCommitSends,
                                                   boolean autoCommitAcks,
                                                   boolean preAcknowledge) {
      return new CreateSessionMessage(getName(), getSessionChannel().getID(), 123, username, password, minLargeMessageSize, xa, autoCommitSends, autoCommitAcks, preAcknowledge, getConfirmationWindow(), null);
   }

   @Override
   public ClientSession.AddressQuery addressQuery(final SimpleString address) throws ActiveMQException {
      SessionBindingQueryResponseMessage response = (SessionBindingQueryResponseMessage) getSessionChannel().sendBlocking(new SessionBindingQueryMessage(address), PacketImpl.SESS_BINDINGQUERY_RESP);

      return new AddressQueryImpl(response.isExists(), response.getQueueNames(), false, false, ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), ActiveMQDefaultConfiguration.getDefaultExclusive(), ActiveMQDefaultConfiguration.getDefaultLastValue(), ActiveMQDefaultConfiguration.getDefaultLastValueKey(), ActiveMQDefaultConfiguration.getDefaultNonDestructive(), ActiveMQDefaultConfiguration.getDefaultConsumersBeforeDispatch(), ActiveMQDefaultConfiguration.getDefaultDelayBeforeDispatch(), true, true);
   }

   @Override
   public ClientConsumerInternal createConsumer(SimpleString queueName,
                                                SimpleString filterString,
                                                int priority,
                                                int windowSize,
                                                int maxRate,
                                                int ackBatchSize,
                                                boolean browseOnly,
                                                Executor executor,
                                                Executor flowControlExecutor,
                                                int onMessageCloseTimeout) throws ActiveMQException {
      long consumerID = idGenerator.generateID();

      ActiveMQConsumerContext consumerContext = new ActiveMQConsumerContext(consumerID);

      SessionCreateConsumerMessage request = new SessionCreateConsumerMessage(consumerID, queueName, filterString, priority, browseOnly, true);

      SessionQueueQueryResponseMessage queueInfo = (SessionQueueQueryResponseMessage) getSessionChannel().sendBlocking(request, PacketImpl.SESS_QUEUEQUERY_RESP);

      // The actual windows size that gets used is determined by the user since
      // could be overridden on the queue settings
      // The value we send is just a hint

      return new ClientConsumerImpl(session, consumerContext, queueName, filterString, priority, browseOnly, windowSize, calcWindowSize(windowSize), ackBatchSize, maxRate > 0 ? new TokenBucketLimiterImpl(maxRate, false) : null, executor, flowControlExecutor, this, queueInfo.toQueueQuery(), lookupTCCL(), onMessageCloseTimeout);
   }

}
