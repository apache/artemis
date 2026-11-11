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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.state.ConsumerState;
import org.apache.activemq.state.SessionState;
import org.junit.jupiter.api.Test;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Collection;
import java.util.Set;

public class DisableAdvisoryTest extends BasicOpenWireTest {

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      Set<TransportConfiguration> acceptors = serverConfig.getAcceptorConfigurations();
      for (TransportConfiguration tc : acceptors) {
         if (tc.getName().equals("netty")) {
            tc.getExtraParams().put("supportAdvisory", "false");
            break;
         }
      }
   }

   @Test
   public void testAdvisoryCosnumerRemoveWarning() throws Exception {

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = session.createQueue(queueName);

      MessageProducer producer = session.createProducer(queue);

      TextMessage message = session.createTextMessage("This is a text message");

      producer.send(message);

      MessageConsumer messageConsumer = session.createConsumer(queue);

      connection.start();

      TextMessage messageReceived = (TextMessage) messageConsumer.receive(5000);
      //Openwire client will create advisory consumers to the broker.
      //The broker is configured supportAdvisory=false and therefore doesn't
      //actually create a server consumer. However the consumer info should be
      //kept in the connection state so that when client closes the consumer
      //it won't cause the broker to throw an exception because of the missing
      //consumer info.
      //See OpenWireConnection.CommandProcessor.processRemoveConsumer
      RemotingService remotingService = server.getRemotingService();
      Set<RemotingConnection> conns = remotingService.getConnections();
      assertEquals(1, conns.size());
      OpenWireConnection owconn = (OpenWireConnection) conns.iterator().next();
      Collection<SessionState> sstates = owconn.getState().getSessionStates();
      //there must be 2 sessions, one is normal, the other is for advisories
      assertEquals(2, sstates.size());
      boolean hasAdvisoryConsumer = false;
      for (SessionState state : sstates) {
         Collection<ConsumerState> cstates = state.getConsumerStates();
         for (ConsumerState cs : cstates) {
            cs.getInfo().getDestination();
            if (AdvisorySupport.isAdvisoryTopic(cs.getInfo().getDestination())) {
               hasAdvisoryConsumer = true;
               break;
            }
         }
      }
      assertTrue(hasAdvisoryConsumer);
   }
}
