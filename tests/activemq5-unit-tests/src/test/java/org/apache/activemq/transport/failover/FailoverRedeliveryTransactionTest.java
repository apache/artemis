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
package org.apache.activemq.transport.failover;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class FailoverRedeliveryTransactionTest extends FailoverTransactionTest {

   @Override
   public void configureConnectionFactory(ActiveMQConnectionFactory factory) {
      super.configureConnectionFactory(factory);
      factory.setTransactedIndividualAck(true);
   }

   @Override
   public EmbeddedJMS createBroker() throws Exception {
      EmbeddedJMS brokerService = super.createBroker();
      PolicyMap policyMap = new PolicyMap();
      PolicyEntry defaultEntry = new PolicyEntry();
      defaultEntry.setPersistJMSRedelivered(true);
      policyMap.setDefaultEntry(defaultEntry);
      //revisit: do we support sth like persistJMSRedelivered?
      //brokerService.setDestinationPolicy(policyMap);
      return brokerService;
   }

   // no point rerunning these
   @Override
   @Test
   public void testFailoverProducerCloseBeforeTransaction() throws Exception {
   }

   @Override
   public void testFailoverCommitReplyLost() throws Exception {
   }

   @Override
   public void testFailoverCommitReplyLostWithDestinationPathSeparator() throws Exception {
   }

   @Override
   public void testFailoverSendReplyLost() throws Exception {
   }

   @Override
   public void testFailoverConnectionSendReplyLost() throws Exception {
   }

   @Override
   public void testFailoverProducerCloseBeforeTransactionFailWhenDisabled() throws Exception {
   }

   @Override
   public void testFailoverMultipleProducerCloseBeforeTransaction() throws Exception {
   }
}
