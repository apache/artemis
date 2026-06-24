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
package org.apache.activemq.artemis.tests.integration.jms.connection;

import javax.jms.Queue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.ChannelBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.jgroups.JChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConnectionFactoryWithJGroupsSerializationTest extends JMSTestBase {

   protected static ActiveMQConnectionFactory jmsCf1;
   protected static ActiveMQConnectionFactory jmsCf2;

   JChannel channel = null;
   Queue testQueue = null;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      try {
         enableDiscoveryForTest();
         super.setUp();

         channel = new JChannel("udp.xml");

         String channelName1 = "channel1";
         String channelName2 = "channel2";

         BroadcastEndpointFactory jgroupsBroadcastCfg1 = new ChannelBroadcastEndpointFactory(channel, channelName1);
         BroadcastEndpointFactory jgroupsBroadcastCfg2 = new JGroupsFileBroadcastEndpointFactory().setChannelName(channelName2).setFile("udp.xml");

         DiscoveryGroupConfiguration dcConfig1 = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(jgroupsBroadcastCfg1);

         DiscoveryGroupConfiguration dcConfig2 = new DiscoveryGroupConfiguration().setName("dg2").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(jgroupsBroadcastCfg2);

         jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().put(dcConfig1.getName(), dcConfig1);
         jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().put(dcConfig2.getName(), dcConfig2);

         jmsServer.createConnectionFactory("ConnectionFactory1", false, JMSFactoryType.CF, dcConfig1.getName(), "/ConnectionFactory1");

         jmsServer.createConnectionFactory("ConnectionFactory2", false, JMSFactoryType.CF, dcConfig2.getName(), "/ConnectionFactory2");

         testQueue = createQueue("testQueueFor1389");
      } catch (Exception e) {
         e.printStackTrace();
         throw e;
      }
   }

   //Here we deploy two Connection Factories with JGroups discovery groups. The first one uses a runtime JChannel
   // object, which is the case before the fix. The second one uses the raw jgroups config string, which is the case
   // after fix. So the first one will get serialization exception in the test while the second will not.
   @Test
   public void testSerialization() throws Exception {
      jmsCf1 = (ActiveMQConnectionFactory) namingContext.lookup("/ConnectionFactory1");
      jmsCf2 = (ActiveMQConnectionFactory) namingContext.lookup("/ConnectionFactory2");

      try {
         serialize(jmsCf1);
      } catch (java.io.NotSerializableException e) {
         //this is expected
      }

      //now cf2 should be OK
      byte[] x = serialize(jmsCf2);
      ActiveMQConnectionFactory jmsCf2Copy = deserialize(x, ActiveMQConnectionFactory.class);
      assertNotNull(jmsCf2Copy);
      BroadcastEndpointFactory broadcastEndpoint = jmsCf2Copy.getDiscoveryGroupConfiguration().getBroadcastEndpointFactory();
      assertInstanceOf(JGroupsFileBroadcastEndpointFactory.class, broadcastEndpoint);
   }

   @Test
   public void testCopyConfiguration() throws Exception {
      assertEquals(2, jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().size());
      Configuration copiedconfig = jmsServer.getActiveMQServer().getConfiguration().copy();
      assertEquals(2, copiedconfig.getDiscoveryGroupConfigurations().size());
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      // small hack, the channel here is cached, so checking that it's not closed by any endpoint
      BroadcastEndpoint broadcastEndpoint = jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().get("dg1").getBroadcastEndpointFactory().createBroadcastEndpoint();
      broadcastEndpoint.close(true);
      if (channel != null) {
         assertFalse(channel.isClosed());
         channel.close();
      }

      super.tearDown();
   }

   private static <T extends Serializable> byte[] serialize(T obj) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(obj);
      oos.close();
      return baos.toByteArray();
   }

   private static <T extends Serializable> T deserialize(byte[] b,
                                                         Class<T> cl) throws IOException, ClassNotFoundException {
      ByteArrayInputStream bais = new ByteArrayInputStream(b);
      ObjectInputStream ois = new ObjectInputStream(bais);
      Object o = ois.readObject();
      return cl.cast(o);
   }

}
