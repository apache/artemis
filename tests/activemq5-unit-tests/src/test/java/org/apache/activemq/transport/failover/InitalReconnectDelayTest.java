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

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.transport.TransportListener;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InitalReconnectDelayTest extends OpenwireArtemisBaseTest {

   private static final transient Logger LOG = LoggerFactory.getLogger(InitalReconnectDelayTest.class);
   protected EmbeddedJMS server1;
   protected EmbeddedJMS server2;

   @Test
   public void testInitialReconnectDelay() throws Exception {

      String uriString = "failover://(" + newURI(1) +
         "," + newURI(2) +
         ")?randomize=false&initialReconnectDelay=15000";

      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(uriString);
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue destination = session.createQueue("foo");
      MessageProducer producer = session.createProducer(destination);

      long start = (new Date()).getTime();
      producer.send(session.createTextMessage("TEST"));
      long end = (new Date()).getTime();

      //Verify we can send quickly
      assertTrue((end - start) < 2000);

      //Halt the broker1...
      LOG.info("Stopping the Broker1...");
      start = (new Date()).getTime();
      server1.stop();

      LOG.info("Attempting to send... failover should kick in...");
      producer.send(session.createTextMessage("TEST"));
      end = (new Date()).getTime();

      //Initial reconnection should kick in and be darned close to what we expected
      LOG.info("Failover took " + (end - start) + " ms.");
      assertTrue("Failover took " + (end - start) + " ms and should be > 14000.", (end - start) > 14000);
      connection.close();
   }

   @Test
   public void testNoSuspendedCallbackOnNoReconnect() throws Exception {

      String uriString = "failover://(" + newURI(1) +
         "," + newURI(2) +
         ")?randomize=false&maxReconnectAttempts=0";

      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(uriString);
      final AtomicInteger calls = new AtomicInteger(0);
      connectionFactory.setTransportListener(new TransportListener() {
         @Override
         public void onCommand(Object command) {
         }

         @Override
         public void onException(IOException error) {
            LOG.info("on exception: " + error);
            calls.set(0x01 | calls.intValue());
         }

         @Override
         public void transportInterupted() {
            LOG.info("on transportInterupted");
            calls.set(0x02 | calls.intValue());
         }

         @Override
         public void transportResumed() {
            LOG.info("on transportResumed");
            calls.set(0x04 | calls.intValue());
         }
      });
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue destination = session.createQueue("foo");
      MessageProducer producer = session.createProducer(destination);

      final Message message = session.createTextMessage("TEST");
      producer.send(message);

      // clear listener state
      calls.set(0);

      LOG.info("Stopping the Broker1...");
      server1.stop();

      LOG.info("Attempting to send... failover should throw on disconnect");
      try {
         producer.send(destination, message);
         fail("Expect IOException to bubble up on send");
      } catch (javax.jms.IllegalStateException producerClosed) {
      }

      assertEquals("Only an exception is reported to the listener", 0x1, calls.get());
   }

   @Before
   public void setUp() throws Exception {

      Configuration config1 = createConfig(1);
      Configuration config2 = createConfig(2);

      deployClusterConfiguration(config1, 2);
      deployClusterConfiguration(config2, 1);

      server1 = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      server2 = new EmbeddedJMS().setConfiguration(config2).setJmsConfiguration(new JMSConfigurationImpl());

      server1.start();
      server2.start();
      Assert.assertTrue(server1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(server2.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));

   }

   protected String getSlaveXml() {
      return "org/apache/activemq/broker/ft/sharedFileSlave.xml";
   }

   protected String getMasterXml() {
      return "org/apache/activemq/broker/ft/sharedFileMaster.xml";
   }

   @After
   public void tearDown() throws Exception {
      server1.stop();
      server2.stop();
   }

}
