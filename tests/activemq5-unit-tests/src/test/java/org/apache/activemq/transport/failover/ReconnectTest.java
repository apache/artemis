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

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.transport.mock.MockTransport;
import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconnectTest extends OpenwireArtemisBaseTest {

   public static final int MESSAGES_PER_ITTERATION = 10;
   public static final int WORKER_COUNT = 10;

   private static final Logger LOG = LoggerFactory.getLogger(ReconnectTest.class);

   private EmbeddedJMS bs;
   private URI tcpUri;
   private final AtomicInteger resumedCount = new AtomicInteger();
   private final AtomicInteger interruptedCount = new AtomicInteger();
   private Worker[] workers;

   class Worker implements Runnable {

      public AtomicInteger iterations = new AtomicInteger();
      public CountDownLatch stopped = new CountDownLatch(1);

      private final ActiveMQConnection connection;
      private final AtomicBoolean stop = new AtomicBoolean(false);
      private Throwable error;
      private final String name;

      public Worker(final String name) throws URISyntaxException, JMSException {
         this.name = name;
         URI uri = new URI("failover://(mock://(" + tcpUri + "))?updateURIsSupported=false");
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(uri);
         connection = (ActiveMQConnection) factory.createConnection();
         connection.addTransportListener(new TransportListener() {
            @Override
            public void onCommand(Object command) {
            }

            @Override
            public void onException(IOException error) {
               setError(error);
            }

            @Override
            public void transportInterupted() {
               LOG.info("Worker " + name + " was interrupted...");
               interruptedCount.incrementAndGet();
            }

            @Override
            public void transportResumed() {
               LOG.info("Worker " + name + " was resummed...");
               resumedCount.incrementAndGet();
            }
         });
         connection.start();
      }

      public void failConnection() {
         MockTransport mockTransport = connection.getTransportChannel().narrow(MockTransport.class);
         mockTransport.onException(new IOException("Simulated error"));
      }

      public void start() {
         new Thread(this, name).start();
      }

      public void stop() {
         stop.set(true);
         try {
            if (!stopped.await(5, TimeUnit.SECONDS)) {
               connection.close();
               stopped.await(5, TimeUnit.SECONDS);
            } else {
               connection.close();
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      @Override
      public void run() {
         try {
            ActiveMQQueue queue = new ActiveMQQueue("FOO_" + name);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(queue);
            MessageProducer producer = session.createProducer(queue);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            while (!stop.get()) {

               for (int i = 0; i < MESSAGES_PER_ITTERATION; i++) {
                  TextMessage text = session.createTextMessage(name + " TEST:" + i);
                  text.setStringProperty("myprop", name + " TEST:" + i);
                  producer.send(text);
               }

               for (int i = 0; i < MESSAGES_PER_ITTERATION; i++) {
                  TextMessage m = (TextMessage) consumer.receive();
               }

               iterations.incrementAndGet();
            }
            session.close();
         } catch (JMSException e) {
            setError(e);
         } finally {
            stopped.countDown();
         }
      }

      public synchronized Throwable getError() {
         return error;
      }

      public synchronized void setError(Throwable error) {
         this.error = error;
      }

      public synchronized void assertNoErrors() {
         if (error != null) {
            error.printStackTrace();
            Assert.fail("Worker " + name + " got Exception: " + error);
         }
      }
   }

   @Test
   public void testReconnects() throws Exception {

      for (int k = 1; k < 10; k++) {
         LOG.info("Test run: " + k);

         // Wait for at least one iteration to occur...
         for (int i = 0; i < WORKER_COUNT; i++) {
            int c = 0;
            for (int j = 0; j < 30; j++) {
               c = workers[i].iterations.getAndSet(0);
               if (c != 0) {
                  break;
               }
               workers[i].assertNoErrors();
               LOG.info("Test run " + k + ": Waiting for worker " + i + " to finish an iteration.");
               Thread.sleep(1000);
            }
            Assert.assertTrue("Test run " + k + ": Worker " + i + " never completed an interation.", c != 0);
            workers[i].assertNoErrors();
         }

         LOG.info("Simulating transport error to cause reconnect.");

         // Simulate a transport failure.
         for (int i = 0; i < WORKER_COUNT; i++) {
            workers[i].failConnection();
         }

         Assert.assertTrue("Timed out waiting for all connections to be interrupted.", Wait.waitFor(() -> {
            LOG.debug("Test run waiting for connections to get interrupted.. at: " + interruptedCount.get());
            return interruptedCount.get() == WORKER_COUNT;
         }, TimeUnit.SECONDS.toMillis(60)));

         // Wait for the connections to re-establish...
         Assert.assertTrue("Timed out waiting for all connections to be resumed.", Wait.waitFor(() -> {
            LOG.debug("Test run waiting for connections to get resumed.. at: " + resumedCount.get());
            return resumedCount.get() >= WORKER_COUNT;
         }, TimeUnit.SECONDS.toMillis(60)));

         // Reset the counters..
         interruptedCount.set(0);
         resumedCount.set(0);
         for (int i = 0; i < WORKER_COUNT; i++) {
            workers[i].iterations.set(0);
         }

         Thread.sleep(1000);
      }
   }

   @Before
   public void setUp() throws Exception {
      Configuration config = createConfig(0);
      bs = new EmbeddedJMS().setConfiguration(config).setJmsConfiguration(new JMSConfigurationImpl());
      bs.start();
      tcpUri = new URI(newURI(0));

      workers = new Worker[WORKER_COUNT];
      for (int i = 0; i < WORKER_COUNT; i++) {
         workers[i] = new Worker("worker-" + i);
         workers[i].start();
      }
   }

   @After
   public void tearDown() throws Exception {
      for (int i = 0; i < WORKER_COUNT; i++) {
         workers[i].stop();
      }
      bs.stop();
   }
}
