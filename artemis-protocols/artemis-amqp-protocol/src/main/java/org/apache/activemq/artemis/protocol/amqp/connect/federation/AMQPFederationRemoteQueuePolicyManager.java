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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import java.util.function.Consumer;

import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationType;

/**
 * Policy manager that manages state data for remote AMQP Federation Queue policies and their associated senders. These
 * managers are a result either of a local federation configuration that sender federation policies to the remote side
 * of the connection or at the remote target they appear when the remote is consuming messages from the target based on
 * local federation configurations.
 */
public final class AMQPFederationRemoteQueuePolicyManager extends AMQPFederationRemotePolicyManager {

   /**
    * Name used when the remote queue policy name is not present due to having connected to an older broker instance
    * that does not fill in the link property that carries the policy name.
    */
   public static final String DEFAULT_REMOTE_QUEUE_POLICY_NAME = "<unknown-remote-queue-policy>";

   public AMQPFederationRemoteQueuePolicyManager(AMQPFederation federation, AMQPFederationMetrics metrics, String policyName) {
      super(federation, metrics, policyName, FederationType.QUEUE_FEDERATION);
   }

   @Override
   protected AMQPFederationSenderController createSenderController(Consumer<AMQPFederationSenderController> closedListener) throws ActiveMQAMQPException {
      return new AMQPFederationQueueSenderController(this, metrics.newProducerMetrics(), closedListener);
   }
}
