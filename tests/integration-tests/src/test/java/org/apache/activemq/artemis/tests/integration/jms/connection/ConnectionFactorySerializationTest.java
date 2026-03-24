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

import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.JGroupsPropertiesBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConnectionFactorySerializationTest extends JMSTestBase {

   protected static ActiveMQConnectionFactory cf;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
   }


   @Test
   public void testConnectionFactoryUDP() throws Exception {
      createDiscoveryFactoryUDP();
      cf = (ActiveMQConnectionFactory) namingContext.lookup("/MyConnectionFactory");

      // apparently looking up the connection factory with the org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory
      // is not enough to actually serialize it so we serialize it manually
      byte[] x = serialize(cf);
      ActiveMQConnectionFactory y = deserialize(x, ActiveMQConnectionFactory.class);
      checkEquals(cf, y);
      DiscoveryGroupConfiguration dgc = y.getDiscoveryGroupConfiguration();
      Assert.assertEquals(dgc.getName(), "dg1");
      Assert.assertEquals(dgc.getDiscoveryInitialWaitTimeout(), 5000);
      Assert.assertEquals(dgc.getRefreshTimeout(), 5000);
      Assert.assertTrue(dgc.getBroadcastEndpointFactory() instanceof UDPBroadcastEndpointFactory);
      UDPBroadcastEndpointFactory befc = (UDPBroadcastEndpointFactory) dgc.getBroadcastEndpointFactory();
      Assert.assertEquals(Integer.parseInt(System.getProperty("org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory.localBindPort", "-1")), befc.getLocalBindPort());
      Assert.assertEquals(System.getProperty("org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory.localBindAddress"), befc.getLocalBindAddress());
      Assert.assertEquals(getUDPDiscoveryPort(), befc.getGroupPort());
      Assert.assertEquals(getUDPDiscoveryAddress(), befc.getGroupAddress());
   }

   @Test
   public void testConnectionFactoryJgroupsFile() throws Exception {
      createDiscoveryFactoryJGroupsFile();
      cf = (ActiveMQConnectionFactory) namingContext.lookup("/MyConnectionFactory");

      // apparently looking up the connection factory with the org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory
      // is not enough to actually serialize it so we serialize it manually
      byte[] x = serialize(cf);
      ActiveMQConnectionFactory y = deserialize(x, ActiveMQConnectionFactory.class);
      checkEquals(cf, y);
      DiscoveryGroupConfiguration dgc = y.getDiscoveryGroupConfiguration();
      Assert.assertEquals(dgc.getName(), "dg1");
      Assert.assertEquals(dgc.getDiscoveryInitialWaitTimeout(), 5000);
      Assert.assertEquals(dgc.getRefreshTimeout(), 5000);
      Assert.assertTrue(dgc.getBroadcastEndpointFactory() instanceof JGroupsFileBroadcastEndpointFactory);
      JGroupsFileBroadcastEndpointFactory befc = (JGroupsFileBroadcastEndpointFactory) dgc.getBroadcastEndpointFactory();
      Assert.assertEquals("myChannel", befc.getChannelName());
      Assert.assertEquals("/META-INF/myfile.xml", befc.getFile());
   }

   @Test
   public void testConnectionFactoryJgroupsProperties() throws Exception {
      createDiscoveryFactoryJGroupsProperties();
      cf = (ActiveMQConnectionFactory) namingContext.lookup("/MyConnectionFactory");

      // apparently looking up the connection factory with the org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory
      // is not enough to actually serialize it so we serialize it manually
      byte[] x = serialize(cf);
      ActiveMQConnectionFactory y = deserialize(x, ActiveMQConnectionFactory.class);
      checkEquals(cf, y);
      DiscoveryGroupConfiguration dgc = y.getDiscoveryGroupConfiguration();
      Assert.assertEquals(dgc.getName(), "dg1");
      Assert.assertEquals(dgc.getDiscoveryInitialWaitTimeout(), 5000);
      Assert.assertEquals(dgc.getRefreshTimeout(), 5000);
      Assert.assertTrue(dgc.getBroadcastEndpointFactory() instanceof JGroupsPropertiesBroadcastEndpointFactory);
      JGroupsPropertiesBroadcastEndpointFactory befc = (JGroupsPropertiesBroadcastEndpointFactory) dgc.getBroadcastEndpointFactory();
      Assert.assertEquals("myChannel", befc.getChannelName());
      Assert.assertEquals("param=1,param2=2", befc.getProperties());
   }

   @Test
   public void testConnectionFactoryStatic1() throws Exception {
      createStaticFactory(true);
      cf = (ActiveMQConnectionFactory) namingContext.lookup("/MyConnectionFactory");

      // apparently looking up the connection factory with the org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory
      // is not enough to actually serialize it so we serialize it manually
      byte[] x = serialize(cf);
      ActiveMQConnectionFactory y = deserialize(x, ActiveMQConnectionFactory.class);
      checkEquals(cf, y);
      Assert.assertEquals(cf.isHA(), y.isHA());
      TransportConfiguration[] staticConnectors = y.getStaticConnectors();
      Assert.assertEquals(staticConnectors.length, 2);
      TransportConfiguration tc0 = cf.getStaticConnectors()[0];
      TransportConfiguration y0 = y.getStaticConnectors()[0];
      Map<String, Object> ctParams = tc0.getParams();
      Map<String, Object> y0Params = y0.getParams();
      Assert.assertEquals(ctParams.size(), y0Params.size());
      for (String key : y0Params.keySet()) {
         Assert.assertEquals(ctParams.get(key), y0Params.get(key));
      }
   }

   @Test
   public void testConnectionFactoryEncodeDecode() throws Exception {
      jmsServer.getActiveMQServer().getConfiguration().addConnectorConfiguration("foo", "tcp://localhost:1234");

      ArrayList<String> connectorNames = new ArrayList<>();
      connectorNames.add("foo");
      ConnectionFactoryConfiguration cfc1 = new ConnectionFactoryConfigurationImpl()
         .setName("MyConnectionFactory")
         .setConnectorNames(connectorNames)
         .setUseTopologyForLoadBalancing(false)
         .setEnableSharedClientID(true);

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      cfc1.encode(buffer);
      byte[] bytes = new byte[buffer.readableBytes()];
      buffer.readBytes(bytes);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      outputStream.write(bytes);
      ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
      buffer = ActiveMQBuffers.dynamicBuffer(1024);
      while (true) {
         int byteRead = inputStream.read();
         if (byteRead < 0) {
            break;
         }

         buffer.writeByte((byte)byteRead);
      }
      ConnectionFactoryConfigurationImpl cfc2 = new ConnectionFactoryConfigurationImpl();
      cfc2.decode(buffer);

      assertEquals(cfc1.getUseTopologyForLoadBalancing(), cfc2.getUseTopologyForLoadBalancing());
      assertEquals(cfc1.isEnableSharedClientID(), cfc2.isEnableSharedClientID());
   }

   private void createDiscoveryFactoryUDP() throws Exception {
      // Deploy a connection factory with discovery
      List<String> bindings = new ArrayList<>();
      bindings.add("MyConnectionFactory");
      final String groupAddress = getUDPDiscoveryAddress();
      final int port = getUDPDiscoveryPort();
      String localBindAddress = getLocalHost().getHostAddress();

      UDPBroadcastEndpointFactory config = new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(port).setLocalBindAddress(localBindAddress).setLocalBindPort(8580);

      DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(config);

      jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().put(dcConfig.getName(), dcConfig);

      jmsServer.createConnectionFactory("MyConnectionFactory", false, JMSFactoryType.CF, dcConfig.getName(), "/MyConnectionFactory");
   }

   private void createDiscoveryFactoryJGroupsFile() throws Exception {
      // Deploy a connection factory with discovery
      List<String> bindings = new ArrayList<>();
      bindings.add("MyConnectionFactory");

      JGroupsFileBroadcastEndpointFactory config = new JGroupsFileBroadcastEndpointFactory().setChannelName("myChannel").setFile("/META-INF/myfile.xml");

      DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(config);

      jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().put(dcConfig.getName(), dcConfig);

      jmsServer.createConnectionFactory("MyConnectionFactory", false, JMSFactoryType.CF, dcConfig.getName(), "/MyConnectionFactory");
   }

   private void createDiscoveryFactoryJGroupsProperties() throws Exception {
      // Deploy a connection factory with discovery
      List<String> bindings = new ArrayList<>();
      bindings.add("MyConnectionFactory");

      JGroupsPropertiesBroadcastEndpointFactory config = new JGroupsPropertiesBroadcastEndpointFactory().setChannelName("myChannel").setProperties("param=1,param2=2");

      DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(config);

      jmsServer.getActiveMQServer().getConfiguration().getDiscoveryGroupConfigurations().put(dcConfig.getName(), dcConfig);

      jmsServer.createConnectionFactory("MyConnectionFactory", false, JMSFactoryType.CF, dcConfig.getName(), "/MyConnectionFactory");
   }

   private void createStaticFactory(boolean b) throws Exception {
      HashMap<String, Object> params = new HashMap<>();
      Set<String> allowableConnectorKeys = TransportConstants.ALLOWABLE_CONNECTOR_KEYS;
      for (String allowableConnectorKey : allowableConnectorKeys) {
         String value = RandomUtil.randomString();
         params.put(allowableConnectorKey, value);
      }
      params.put("host", "localhost0");
      params.put("port", "1234");

      TransportConfiguration main = new TransportConfiguration(NETTY_CONNECTOR_FACTORY, params);

      jmsServer.getActiveMQServer().getConfiguration().getConnectorConfigurations().put(main.getName(), main);

      HashMap<String, Object> params2 = new HashMap<>();
      for (String allowableConnectorKey : allowableConnectorKeys) {
         String value = RandomUtil.randomString();
         params2.put(allowableConnectorKey, value);
      }
      params2.put("host", "localhost1");
      params2.put("port", "5678");

      TransportConfiguration main2 = new TransportConfiguration(NETTY_CONNECTOR_FACTORY, params2);

      jmsServer.getActiveMQServer().getConfiguration().getConnectorConfigurations().put(main2.getName(), main2);

      ArrayList<String> connectorNames = new ArrayList<>();
      connectorNames.add(main.getName());
      connectorNames.add(main2.getName());
      ConnectionFactoryConfiguration configuration = new ConnectionFactoryConfigurationImpl().setName("MyConnectionFactory").setHA(b).setConnectorNames(connectorNames).setClientID("clientID").setClientFailureCheckPeriod(-1).setConnectionTTL(-2).setFactoryType(JMSFactoryType.CF).setCallTimeout(-3).setCallFailoverTimeout(-4).setCacheLargeMessagesClient(b).setMinLargeMessageSize(-5).setConsumerWindowSize(-6).setConsumerMaxRate(-7).setConfirmationWindowSize(-8).setProducerWindowSize(-9).setProducerMaxRate(-10).setBlockOnAcknowledge(b).setBlockOnDurableSend(b).setBlockOnNonDurableSend(b).setAutoGroup(b).setPreAcknowledge(b).setLoadBalancingPolicyClassName("foobar").setTransactionBatchSize(-11).setDupsOKBatchSize(-12).setUseGlobalPools(b).setScheduledThreadPoolMaxSize(-13).setThreadPoolMaxSize(-14).setRetryInterval(-15).setRetryIntervalMultiplier(-16).setMaxRetryInterval(-17).setReconnectAttempts(-18).setGroupID("groupID")
              .setInitialMessagePacketSize(1499);

      jmsServer.createConnectionFactory(false, configuration, "/MyConnectionFactory");
   }

   private void populate(StringBuilder sb,
                         BeanUtilsBean bean,
                         ActiveMQConnectionFactory factory) throws IllegalAccessException, InvocationTargetException {
      PropertyDescriptor[] descriptors = bean.getPropertyUtils().getPropertyDescriptors(factory);
      for (PropertyDescriptor descriptor : descriptors) {
         if (descriptor.getWriteMethod() != null && descriptor.getReadMethod() != null) {
            if (descriptor.getPropertyType() == String.class) {
               String value = RandomUtil.randomString();
               bean.setProperty(factory, descriptor.getName(), value);
               sb.append("&").append(descriptor.getName()).append("=").append(value);
            } else if (descriptor.getPropertyType() == int.class) {
               int value = RandomUtil.randomPositiveInt();
               bean.setProperty(factory, descriptor.getName(), value);
               sb.append("&").append(descriptor.getName()).append("=").append(value);
            } else if (descriptor.getPropertyType() == long.class) {
               long value = RandomUtil.randomPositiveLong();
               bean.setProperty(factory, descriptor.getName(), value);
               sb.append("&").append(descriptor.getName()).append("=").append(value);
            } else if (descriptor.getPropertyType() == double.class) {
               double value = RandomUtil.randomDouble();
               bean.setProperty(factory, descriptor.getName(), value);
               sb.append("&").append(descriptor.getName()).append("=").append(value);
            }
         }
      }
   }

   private static void checkEquals(Object factory,
                                   Object factory2) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
      BeanUtilsBean bean = new BeanUtilsBean();
      PropertyDescriptor[] descriptors = bean.getPropertyUtils().getPropertyDescriptors(factory);
      for (PropertyDescriptor descriptor : descriptors) {
         if (descriptor.getWriteMethod() != null && descriptor.getReadMethod() != null) {
            Assert.assertEquals(descriptor.getName() + " incorrect", bean.getProperty(factory, descriptor.getName()), bean.getProperty(factory2, descriptor.getName()));
         }
      }
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



   protected static InetAddress getLocalHost() throws UnknownHostException {
      InetAddress addr;
      try {
         addr = InetAddress.getLocalHost();
      } catch (ArrayIndexOutOfBoundsException e) {  //this is workaround for mac osx bug see AS7-3223 and JGRP-1404
         addr = InetAddress.getByName(null);
      }
      return addr;
   }


}
