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
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailoverUpdateURIsTest extends OpenwireArtemisBaseTest {

   private static final String QUEUE_NAME = "test.failoverupdateuris";
   private static final Logger LOG = LoggerFactory.getLogger(FailoverUpdateURIsTest.class);

   String firstTcpUri = newURI(0);
   String secondTcpUri = newURI(10);
   Connection connection = null;
   EmbeddedJMS server0 = null;
   EmbeddedJMS server1 = null;

   @After
   public void tearDown() throws Exception {
      if (connection != null) {
         connection.close();
      }
      if (server0 != null) {
         server0.stop();
      }
      if (server1 != null) {
         server1.stop();
      }
   }

   @Test
   public void testUpdateURIsViaFile() throws Exception {

      String targetDir = "target/testUpdateURIsViaFile";
      new File(targetDir).mkdir();
      File updateFile = new File(targetDir + "/updateURIsFile.txt");
      LOG.info(updateFile.toString());
      LOG.info("" + updateFile.toURI());
      LOG.info("" + updateFile.getAbsoluteFile());
      LOG.info("" + updateFile.getAbsoluteFile().toURI());
      FileOutputStream out = new FileOutputStream(updateFile);
      out.write(firstTcpUri.getBytes());
      out.close();

      Configuration config0 = createConfig(0);
      server0 = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      server0.start();

      // no failover uri's to start with, must be read from file...
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:()?updateURIsURL=file:///" + updateFile.getAbsoluteFile());
      connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue theQueue = session.createQueue(QUEUE_NAME);
      MessageProducer producer = session.createProducer(theQueue);
      MessageConsumer consumer = session.createConsumer(theQueue);
      Message message = session.createTextMessage("Test message");
      producer.send(message);
      Message msg = consumer.receive(2000);
      Assert.assertNotNull(msg);

      server0.stop();
      server0 = null;

      Configuration config1 = createConfig(10);
      server1 = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      server1.start();

      // add the transport uri for broker number 2
      out = new FileOutputStream(updateFile, true);
      out.write(",".getBytes());
      out.write(secondTcpUri.getBytes());
      out.close();

      producer.send(message);
      msg = consumer.receive(2000);
      Assert.assertNotNull(msg);
   }

   @Test
   public void testAutoUpdateURIs() throws Exception {
      Map<String, String> params = new HashMap<>();
      params.put("updateClusterClients", "true");
      Configuration config0 = createConfig("localhost", 0, params);
      deployClusterConfiguration(config0, 10);
      server0 = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      server0.start();
      Assert.assertTrue(server0.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 1));

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + firstTcpUri + ")");
      connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue theQueue = session.createQueue(QUEUE_NAME);
      MessageProducer producer = session.createProducer(theQueue);
      MessageConsumer consumer = session.createConsumer(theQueue);
      Message message = session.createTextMessage("Test message");
      producer.send(message);
      Message msg = consumer.receive(4000);
      Assert.assertNotNull(msg);

      Configuration config1 = createConfig(10);
      deployClusterConfiguration(config1, 0);
      server1 = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      server1.start();
      Assert.assertTrue(server0.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));
      Assert.assertTrue(server1.waitClusterForming(100, TimeUnit.MILLISECONDS, 20, 2));

      TimeUnit.SECONDS.sleep(4);

      LOG.info("stopping brokerService 1");
      server0.stop();
      server0 = null;

      producer.send(message);
      msg = consumer.receive(4000);
      Assert.assertNotNull(msg);
   }
}
