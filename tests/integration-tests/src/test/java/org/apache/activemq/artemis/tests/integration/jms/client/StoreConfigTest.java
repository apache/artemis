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
package org.apache.activemq.artemis.tests.integration.jms.client;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class StoreConfigTest extends JMSTestBase {

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Test
   public void testCreateCF() throws Exception {
      server.getConfiguration().getConnectorConfigurations().put("tst", new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      server.getConfiguration().getConnectorConfigurations().put("np", new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      List<String> transportConfigurations = new ArrayList<>();
      transportConfigurations.add("tst");
      ConnectionFactoryConfigurationImpl factCFG = (ConnectionFactoryConfigurationImpl) new ConnectionFactoryConfigurationImpl().setName("tst").setConnectorNames(transportConfigurations);

      jmsServer.createConnectionFactory(true, factCFG, "/someCF", "/someCF2");

      ConnectionFactoryConfigurationImpl nonPersisted = (ConnectionFactoryConfigurationImpl) new ConnectionFactoryConfigurationImpl().setName("np").setConnectorNames(transportConfigurations);

      jmsServer.createConnectionFactory(false, nonPersisted, "/nonPersisted");

      try {
         jmsServer.addConnectionFactoryToBindingRegistry("np", "/someCF");
         fail("Failure expected and the API let duplicates");
      } catch (NamingException expected) {
         // expected
      }

      openCon("/someCF");
      openCon("/someCF2");
      openCon("/nonPersisted");

      jmsServer.stop();

      jmsServer.start();

      openCon("/someCF");
      openCon("/someCF2");
      assertNullJNDI("/nonPersisted");

      jmsServer.stop();

      jmsServer.start();

      jmsServer.addConnectionFactoryToBindingRegistry("tst", "/newJNDI");
      try {
         jmsServer.addConnectionFactoryToBindingRegistry("tst", "/newJNDI");
         fail("Failure expected and the API let duplicates");
      } catch (NamingException expected) {
         // expected
      }
      openCon("/someCF");
      openCon("/someCF2");
      openCon("/newJNDI");
      assertNullJNDI("/nonPersisted");

      jmsServer.stop();

      assertNullJNDI("/newJNDI");

      jmsServer.start();

      openCon("/someCF");
      openCon("/someCF2");
      openCon("/newJNDI");
   }

   @Test
   public void testCreateTopic() throws Exception {
      server.getConfiguration().getConnectorConfigurations().put("tst", new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      server.getConfiguration().getConnectorConfigurations().put("np", new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      List<String> transportConfigurations = new ArrayList<>();
      transportConfigurations.add("tst");

      ConnectionFactoryConfigurationImpl factCFG = (ConnectionFactoryConfigurationImpl) new ConnectionFactoryConfigurationImpl().setName("tst").setConnectorNames(transportConfigurations);

      jmsServer.createConnectionFactory(true, factCFG, "/someCF");

      assertTrue(jmsServer.createTopic(true, "topicOne", "/t1", "/t.1"));

      assertTrue(jmsServer.createTopic(false, "topicTwo", "/t2", "/t.2"));

      assertFalse(jmsServer.createTopic(false, "topicOne", "/z1", "z2"));

      assertNullJNDI("/z1");
      assertNullJNDI("/z2");

      checkDestination("/t1");
      checkDestination("/t.1");

      checkDestination("/t2");
      checkDestination("/t.2");

      jmsServer.stop();

      assertNullJNDI("/t1");
      assertNullJNDI("/t.1");

      assertNullJNDI("/t2");
      assertNullJNDI("/t.2");

      jmsServer.start();

      checkDestination("/t1");
      checkDestination("/t.1");

      assertNullJNDI("/t2");
      assertNullJNDI("/t.2");

      jmsServer.addTopicToBindingRegistry("topicOne", "/tI");

      jmsServer.stop();
      jmsServer.start();

      checkDestination("/tI");
      checkDestination("/t1");
      checkDestination("/t.1");

      assertNullJNDI("/t2");
      assertNullJNDI("/t.2");

      assertTrue(jmsServer.removeTopicFromBindingRegistry("topicOne", "/tI"));

      assertFalse(jmsServer.removeTopicFromBindingRegistry("topicOne", "nothing"));
      assertFalse(jmsServer.removeTopicFromBindingRegistry("nothing", "nothing"));
      assertFalse(jmsServer.removeTopicFromBindingRegistry("nothing"));

      assertNullJNDI("/tI");
      checkDestination("/t1");
      checkDestination("/t.1");

      jmsServer.stop();

      jmsServer.start();

      assertNullJNDI("/tI");
      checkDestination("/t1");
      checkDestination("/t.1");

      jmsServer.removeTopicFromBindingRegistry("topicOne");

      assertTrue(jmsServer.createTopic(true, "topicOne", "/topicx.1", "/topicx.2"));

      jmsServer.stop();

      jmsServer.start();

      checkDestination("/topicx.1");
      checkDestination("/topicx.2");
   }

   private void checkDestination(String name) throws Exception {
      ConnectionFactory cf = (ConnectionFactory) namingContext.lookup("/someCF");
      Connection conn = cf.createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination dest = (Destination) namingContext.lookup(name);

      conn.start();
      MessageConsumer cons = sess.createConsumer(dest);

      MessageProducer prod = sess.createProducer(dest);
      prod.send(sess.createMessage());
      assertNotNull(cons.receiveNoWait());
      conn.close();

   }

   @Test
   public void testCreateQueue() throws Exception {
      server.getConfiguration().getConnectorConfigurations().put("tst", new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      //      server.getConfiguration().getConnectorConfigurations().put("np", new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      List<String> transportConfigurations = new ArrayList<>();
      transportConfigurations.add("tst");

      ConnectionFactoryConfigurationImpl factCFG = (ConnectionFactoryConfigurationImpl) new ConnectionFactoryConfigurationImpl().setName("tst").setConnectorNames(transportConfigurations);

      jmsServer.createConnectionFactory(true, factCFG, "/someCF");

      assertTrue(jmsServer.createQueue(true, "queue1", null, true, "/q1", "/q.1"));

      assertFalse(jmsServer.createQueue(true, "queue1", "someWeirdThing", true, "/qx", "/qz"));

      assertNullJNDI("/qx");

      assertNullJNDI("/qz");

      assertTrue(jmsServer.createQueue(false, "queue2", null, true, "/q2", "/q.2"));

      checkDestination("/q1");
      checkDestination("/q.1");

      checkDestination("/q2");
      checkDestination("/q.2");

      jmsServer.stop();

      assertNullJNDI("/q1");
      assertNullJNDI("/q1.1");
      assertNullJNDI("/qI");
      assertNullJNDI("/q2");
      assertNullJNDI("/q.2");

      jmsServer.start();

      checkDestination("/q1");
      checkDestination("/q.1");

      assertNullJNDI("/q2");
      assertNullJNDI("/q.2");

      jmsServer.addQueueToBindingRegistry("queue1", "/qI");

      jmsServer.stop();
      jmsServer.start();

      checkDestination("/qI");
      checkDestination("/q1");
      checkDestination("/q.1");

      assertNullJNDI("/q2");
      assertNullJNDI("/q.2");

      assertTrue(jmsServer.removeQueueFromBindingRegistry("queue1", "/q1"));

      assertFalse(jmsServer.removeQueueFromBindingRegistry("queue1", "nothing"));

      assertNullJNDI("/q1");
      checkDestination("/q.1");
      checkDestination("/qI");

      jmsServer.stop();

      jmsServer.start();

      assertNullJNDI("/q1");
      checkDestination("/q.1");
      checkDestination("/qI");

      jmsServer.removeQueueFromBindingRegistry("queue1");

      assertTrue(jmsServer.createQueue(true, "queue1", null, true, "/newq1", "/newq.1"));
      assertNullJNDI("/q1");
      assertNullJNDI("/q.1");
      assertNullJNDI("/qI");

      checkDestination("/newq1");
      checkDestination("newq.1");

      jmsServer.stop();
   }

   private void assertNullJNDI(String name) {
      Object obj = null;
      try {
         obj = namingContext.lookup(name);
      } catch (Exception expected) {
      }

      assertNull(obj);
   }

   private void openCon(String name) throws NamingException, JMSException {
      ConnectionFactory cf = (ConnectionFactory) namingContext.lookup(name);

      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      conn.close();
   }

}
