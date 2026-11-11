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
package org.apache.activemq.artemis.tests.performance.sends;

import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;

/**
 * Client-ack time
 */
public abstract class AbstractSendReceivePerfTest extends JMSTestBase {

   protected static final String Q_NAME = "test-queue-01";
   private Queue queue;

   protected AtomicBoolean running = new AtomicBoolean(true);

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      jmsServer.createQueue(false, Q_NAME, null, true, Q_NAME);
      queue = ActiveMQJMSClient.createQueue(Q_NAME);

      AddressSettings settings = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK).setMaxSizeBytes(Long.MAX_VALUE);
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", settings);

   }

   @Override
   protected void registerConnectionFactory() throws Exception {
      List<TransportConfiguration> connectorConfigs = new ArrayList<>();
      connectorConfigs.add(new TransportConfiguration(NETTY_CONNECTOR_FACTORY));

      createCF(connectorConfigs, "/cf");

      cf = (ConnectionFactory) namingContext.lookup("/cf");
   }

   private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(AbstractSendReceivePerfTest.class.getName());

   // Subclasses can add a test which calls this
   protected void doSendReceiveTestImpl() throws Exception {
      long numberOfSamples = Long.getLong("HORNETQ_TEST_SAMPLES", 1000);

      MessageReceiver receiver = new MessageReceiver(Q_NAME, numberOfSamples);
      receiver.start();
      MessageSender sender = new MessageSender(Q_NAME);
      sender.start();

      receiver.join();
      sender.join();

      assertFalse(receiver.failed);
      assertFalse(sender.failed);

   }

   final Semaphore pendingCredit = new Semaphore(5000);

   /**
    * to be called after a message is consumed so the flow control of the test kicks in.
    */
   protected final void afterConsume(Message message) {
      if (message != null) {
         pendingCredit.release();
      }
   }

   protected final void beforeSend() {
      while (running.get()) {
         try {
            if (pendingCredit.tryAcquire(1, TimeUnit.SECONDS)) {
               return;
            } else {
               System.out.println("Couldn't get credits!");
            }
         } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
         }
      }
   }

   private class MessageReceiver extends Thread {

      private final String qName;
      private final long numberOfSamples;

      public boolean failed = false;

      private MessageReceiver(String qname, long numberOfSamples) throws Exception {
         super("Receiver " + qname);
         this.qName = qname;
         this.numberOfSamples = numberOfSamples;
      }

      @Override
      public void run() {
         try {
            LOGGER.info("Receiver: Connecting");
            Connection c = cf.createConnection();

            consumeMessages(c, qName);

            c.close();
         } catch (Exception e) {
            e.printStackTrace();
            failed = true;
         } finally {
            running.set(false);
         }
      }
   }

   protected abstract void consumeMessages(Connection c, String qName) throws Exception;

   private class MessageSender extends Thread {

      protected String qName;

      public boolean failed = false;

      private MessageSender(String qname) throws Exception {
         super("Sender " + qname);

         this.qName = qname;
      }

      @Override
      public void run() {
         try {
            LOGGER.info("Sender: Connecting");
            Connection c = cf.createConnection();

            sendMessages(c, qName);

            c.close();

         } catch (Exception e) {
            failed = true;
            if (e instanceof InterruptedException) {
               LOGGER.info("Sender done.");
            } else {
               e.printStackTrace();
            }
         }
      }
   }

   // This will by default send non persistent messages
   protected void sendMessages(Connection c, String qName) throws JMSException {
      Session s = null;
      s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
      LOGGER.info("Sender: Using AUTO-ACK session");

      Queue q = s.createQueue(qName);
      MessageProducer producer = s.createProducer(null);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      long sent = 0;
      while (running.get()) {
         beforeSend();
         producer.send(q, s.createTextMessage("Message_" + (sent++)));
      }
   }
}
