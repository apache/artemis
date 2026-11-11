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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

/**
 * Stress test using netty with NIO and many JMS clients concurrently, to try and induce a deadlock.
 * <p>
 * A large number of JMS clients are started concurrently. Some produce to queue 1 over one connection, others consume
 * from queue 1 and produce to queue 2 over a second connection, and others consume from queue 2 over a third
 * connection.
 * <p>
 * Each operation is done in a JMS transaction, sending/consuming one message per transaction.
 * <p>
 * The server is set up with netty, with only one NIO worker and 1 activemq server worker. This increases the chance for
 * the deadlock to occur.
 * <p>
 * If the deadlock occurs, all threads will block/die. A simple transaction counting strategy is used to verify that the
 * count has reached the expected value.
 */
public class JmsNettyNioStressTest extends ActiveMQTestBase {

   // Remove this method to re-enable those tests
   @Test
   public void testStressSendNetty() throws Exception {
      doTestStressSend(true);
   }

   public void doTestStressSend(final boolean netty) throws Exception {
      // first set up the server
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, 61616);
      params.put(TransportConstants.HOST_PROP_NAME, "localhost");
      params.put(TransportConstants.USE_NIO_PROP_NAME, true);
      // minimize threads to maximize possibility for deadlock
      params.put(TransportConstants.NIO_REMOTING_THREADS_PROPNAME, 1);
      params.put(TransportConstants.BATCH_DELAY, 50);
      TransportConfiguration transportConfig = new TransportConfiguration(ActiveMQTestBase.NETTY_ACCEPTOR_FACTORY, params);
      Configuration config = createBasicConfig().setJMXManagementEnabled(false).clearAcceptorConfigurations().addAcceptorConfiguration(transportConfig);
      ActiveMQServer server = createServer(true, config);

      server.getAddressSettingsRepository().clear();
      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(AddressSettings.DEFAULT_PAGE_SIZE).
         setMaxSizeBytes(AddressSettings.DEFAULT_MAX_SIZE_BYTES).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).
         setAutoDeleteAddresses(false).setAutoCreateAddresses(true).setAutoCreateQueues(false);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      server.getConfiguration().setThreadPoolMaxSize(2);
      server.start();

      // now the client side
      Map<String, Object> connectionParams = new HashMap<>();
      connectionParams.put(TransportConstants.PORT_PROP_NAME, 61616);
      connectionParams.put(TransportConstants.HOST_PROP_NAME, "localhost");
      connectionParams.put(TransportConstants.USE_NIO_PROP_NAME, true);
      connectionParams.put(TransportConstants.BATCH_DELAY, 50);
      connectionParams.put(TransportConstants.NIO_REMOTING_THREADS_PROPNAME, 6);
      final TransportConfiguration transpConf = new TransportConfiguration(NettyConnectorFactory.class.getName(), connectionParams);
      final ServerLocator locator = createNonHALocator(netty);

      // each thread will do this number of transactions
      final int numberOfMessages = 100;

      // these must all be the same
      final int numProducers = 5;
      final int numConsumerProducers = 5;
      final int numConsumers = 5;

      // each produce, consume+produce and consume increments this counter
      final AtomicInteger totalCount = new AtomicInteger(0);

      // the total we expect if all producers, consumer-producers and
      // consumers complete normally
      int totalExpectedCount = (numProducers + numConsumerProducers + numConsumerProducers) * numberOfMessages;

      // each group gets a separate connection
      final Connection connectionProducer;
      final Connection connectionConsumerProducer;
      final Connection connectionConsumer;

      // create the 2 queues used in the test
      ClientSessionFactory sf = locator.createSessionFactory(transpConf);
      ClientSession session = sf.createTransactedSession();
      session.createAddress(SimpleString.of("queue"), RoutingType.ANYCAST, false);
      session.createAddress(SimpleString.of("queue2"), RoutingType.ANYCAST, false);

      assertTrue(session.addressQuery(SimpleString.of("queue")).isExists());
      assertTrue(session.addressQuery(SimpleString.of("queue2")).isExists());
      session.createQueue(QueueConfiguration.of("queue").setRoutingType(RoutingType.ANYCAST));
      session.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.ANYCAST));
      assertTrue(session.addressQuery(SimpleString.of("queue")).isExists());
      assertTrue(session.addressQuery(SimpleString.of("queue2")).isExists());
      session.commit();
      sf.close();
      session.close();
      locator.close();

      // create and start JMS connections
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transpConf);
      connectionProducer = cf.createConnection();
      connectionProducer.start();

      connectionConsumerProducer = cf.createConnection();
      connectionConsumerProducer.start();

      connectionConsumer = cf.createConnection();
      connectionConsumer.start();

      session.close();
      // these threads produce messages on the the first queue
      for (int i = 0; i < numProducers; i++) {
         new Thread(() -> {

            Session session1 = null;
            try {
               session1 = connectionProducer.createSession(true, Session.SESSION_TRANSACTED);
               MessageProducer messageProducer = session1.createProducer(ActiveMQDestination.createQueue("queue"));
               messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

               for (int i1 = 0; i1 < numberOfMessages; i1++) {
                  BytesMessage message = session1.createBytesMessage();
                  message.writeBytes(new byte[3000]);
                  message.setStringProperty("Service", "LoadShedService");
                  message.setStringProperty("Action", "testAction");

                  messageProducer.send(message);
                  session1.commit();

                  totalCount.incrementAndGet();
               }
            } catch (Exception e) {
               throw new RuntimeException(e);
            } finally {
               if (session1 != null) {
                  try {
                     session1.close();
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }
            }
         }).start();
      }

      // these threads just consume from the one and produce on a second queue
      for (int i = 0; i < numConsumerProducers; i++) {
         new Thread(() -> {
            Session session13 = null;
            try {
               session13 = connectionConsumerProducer.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer consumer = session13.createConsumer(ActiveMQDestination.createQueue("queue"));
               MessageProducer messageProducer = session13.createProducer(ActiveMQDestination.createQueue("queue2"));
               messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
               for (int i13 = 0; i13 < numberOfMessages; i13++) {
                  BytesMessage message = (BytesMessage) consumer.receive(5000);
                  if (message == null) {
                     return;
                  }
                  message = session13.createBytesMessage();
                  message.writeBytes(new byte[3000]);
                  message.setStringProperty("Service", "LoadShedService");
                  message.setStringProperty("Action", "testAction");
                  messageProducer.send(message);
                  session13.commit();

                  totalCount.incrementAndGet();
               }
            } catch (Exception e) {
               throw new RuntimeException(e);
            } finally {
               if (session13 != null) {
                  try {
                     session13.close();
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }
            }
         }).start();
      }

      // these threads consume from the second queue
      for (int i = 0; i < numConsumers; i++) {
         new Thread(() -> {
            Session session12 = null;
            try {
               session12 = connectionConsumer.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer consumer = session12.createConsumer(ActiveMQDestination.createQueue("queue2"));
               for (int i12 = 0; i12 < numberOfMessages; i12++) {
                  BytesMessage message = (BytesMessage) consumer.receive(5000);
                  if (message == null) {
                     return;
                  }
                  session12.commit();

                  totalCount.incrementAndGet();
               }
            } catch (Exception e) {
               throw new RuntimeException(e);
            } finally {
               if (session12 != null) {
                  try {
                     session12.close();
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }
            }
         }).start();
      }

      Wait.waitFor(() -> totalExpectedCount == totalCount.get(), 60000, 100);

      assertEquals(totalExpectedCount, totalCount.get(), "Possible deadlock");

      // attempt cleaning up (this is not in a finally, still needs some work)
      connectionProducer.close();
      connectionConsumerProducer.close();
      connectionConsumer.close();

      server.stop();
   }
}
