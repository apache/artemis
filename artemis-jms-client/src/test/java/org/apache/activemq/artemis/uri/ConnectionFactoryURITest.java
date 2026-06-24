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

package org.apache.activemq.artemis.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQQueueConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTopicConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQXAQueueConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQXATopicConnectionFactory;
import org.apache.activemq.artemis.api.config.ServerLocatorConfigTestAccessor;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ConnectionFactoryURITest extends ArtemisTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ConnectionFactoryParser parser = new ConnectionFactoryParser();

   private static final String[] V6IPs = {"fe80::baf6:b1ff:fe12:daf7%eth0", "2620:db8:1:2::1%em1"};

   private static Set<String> ignoreList = new HashSet<>();

   static {
      ignoreList.add("protocolManagerFactoryStr");
      ignoreList.add("incomingInterceptorList");
      ignoreList.add("outgoingInterceptorList");
   }

   protected void enableDiscoveryForTest() {
      ServerLocatorConfigTestAccessor.setDiscoveryEnabled(true);
      runAfter(() -> ServerLocatorConfigTestAccessor.setDiscoveryEnabled(null));
   }

   @Test
   public void testIPv6() throws Exception {
      for (String IPV6 : V6IPs) {
         Map<String, Object> params = new HashMap<>();
         params.put("host", IPV6);
         params.put("port", 5445);
         TransportConfiguration transport = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
         ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transport);

         persistIP6(IPV6, factory);
      }
   }

   @Test
   public void testWeirdEncodingsOnIP() throws Exception {
      // This is to make things worse. Having & and = on the property shouldn't break it
      final String BROKEN_PROPERTY = "e80::56ee:75ff:fe53:e6a7%25enp0s25&host=[fe80::56ee:75ff:fe53:e6a7]#";

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.LOCAL_ADDRESS_PROP_NAME, BROKEN_PROPERTY);

      TransportConfiguration configuration = new TransportConfiguration(NettyConnector.class.getName(), params);

      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, configuration);

      URI uri = factory.toURI();

      ActiveMQConnectionFactory newFactory = ActiveMQJMSClient.createConnectionFactory(uri.toString(), "somefactory");

      TransportConfiguration[] initialConnectors = ((ServerLocatorImpl) newFactory.getServerLocator()).getInitialConnectors();

      assertEquals(1, initialConnectors.length);

      assertEquals(BROKEN_PROPERTY, initialConnectors[0].getParams().get(TransportConstants.LOCAL_ADDRESS_PROP_NAME).toString());
   }

   @Test
   public void testIPv6NewURI() throws Exception {
      for (String IPV6 : V6IPs) {
         persistIP6(IPV6, new ActiveMQConnectionFactory("tcp://[" + IPV6 + "]:5445"));
      }
   }

   private void persistIP6(String ipv6, ActiveMQConnectionFactory factory) throws IOException, ClassNotFoundException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream outStream = new ObjectOutputStream(baos)) {
         outStream.writeObject(factory);
      } finally {
         baos.close();
      }
      try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
           ObjectInputStream in = new ObjectInputStream(bais)) {
         factory = (ActiveMQConnectionFactory) in.readObject();
      }
      assertEquals("[" + ipv6 + "]", factory.getStaticConnectors()[0].getParams().get("host"));
   }

   @Test
   public void testQUEUE_XA_CF() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030?ha=true&type=QUEUE_XA_CF"), null);

      assertEquals(ActiveMQXAQueueConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testTOPICXA_CF() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030?ha=true&type=TOPIC_XA_CF"), null);

      assertEquals(ActiveMQXATopicConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test

   public void testQUEUE_CF() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030?ha=true&type=QUEUE_CF"), null);

      assertEquals(ActiveMQQueueConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testTOPIC_CF() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030?ha=true&type=TOPIC_CF"), null);

      assertEquals(ActiveMQTopicConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testCF() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030?ha=true&type=CF"), null);

      assertEquals(ActiveMQJMSConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testNoCF() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030?ha=true"), null);

      assertEquals(ActiveMQJMSConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testTCPAllProperties() throws Exception {
      ignoreList.add("deserializationBlackList");
      ignoreList.add("deserializationWhiteList");
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("tcp://localhost:3030?ha=true");
         BeanUtilsBean bean = new BeanUtilsBean();
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(true, (TransportConfiguration) null);
         populate(sb, bean, factory);
         ActiveMQConnectionFactory factory2 = parser.newObject(new URI(sb.toString()), null);
         checkEquals(bean, factory, factory2);
      } finally {
         ignoreList.remove("deserializationBlackList");
         ignoreList.remove("deserializationWhiteList");
      }
   }

   @Test
   public void testTCPAllPropertiesWithDeprecatedProps() throws Exception {
      ignoreList.add("deserializationDenyList");
      ignoreList.add("deserializationAllowList");
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("tcp://localhost:3030?ha=true");
         BeanUtilsBean bean = new BeanUtilsBean();
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(true, (TransportConfiguration) null);
         populate(sb, bean, factory);
         ActiveMQConnectionFactory factory2 = parser.newObject(new URI(sb.toString()), null);
         checkEquals(bean, factory, factory2);
      } finally {
         ignoreList.remove("deserializationDenyList");
         ignoreList.remove("deserializationAllowList");
      }
   }

   @Test
   public void testTCPAllNettyConnectorProperties() throws Exception {
      Map<String, Object> props = new HashMap<>();
      Set<String> allowableConnectorKeys = TransportConstants.ALLOWABLE_CONNECTOR_KEYS;
      StringBuilder sb = new StringBuilder();
      sb.append("tcp://localhost:3030?ha=true");
      populateConnectorParams(props, allowableConnectorKeys, sb);
      ActiveMQConnectionFactory factory = parser.newObject(new URI(sb.toString()), null);

      Map<String, Object> params = factory.getStaticConnectors()[0].getParams();
      assertEquals("localhost", params.get("host"));
      assertEquals("3030", params.get("port"));
      for (Map.Entry<String, Object> entry : params.entrySet()) {
         if (!entry.getKey().equals("host") && !entry.getKey().equals("port")) {
            assertEquals(entry.getValue(), props.get(entry.getKey()));
         }
      }
   }

   @Test
   public void testTCPAllNettyConnectorPropertiesMultiple() throws Exception {
      Map<String, Object> props = new HashMap<>();
      Set<String> allowableConnectorKeys = TransportConstants.ALLOWABLE_CONNECTOR_KEYS;
      StringBuilder sb = new StringBuilder();
      sb.append("(tcp://localhost0:61616?");//localhost1:61617,localhost2:61618,localhost3:61619)&ha=true");
      populateConnectorParams(props, allowableConnectorKeys, sb);
      Map<String, Object> props2 = new HashMap<>();
      sb.append(",tcp://localhost1:61617?");
      populateConnectorParams(props2, allowableConnectorKeys, sb);
      Map<String, Object> props3 = new HashMap<>();
      sb.append(",tcp://localhost2:61618?");
      populateConnectorParams(props3, allowableConnectorKeys, sb);
      sb.append(")?ha=true&clientID=myID");

      ActiveMQConnectionFactory factory = parser.newObject(parser.expandURI(sb.toString()), null);

      TransportConfiguration[] staticConnectors = factory.getStaticConnectors();
      assertEquals(3, staticConnectors.length);
      checkTC(props, staticConnectors[0], 0);
      checkTC(props2, staticConnectors[1], 1);
      checkTC(props3, staticConnectors[2], 2);
   }

   @Test
   public void testTCPEncryptedNettyConnectorPropertiesMultiple() throws Exception {
      final String trustStorePath = "truststore.jks";
      final String trustStorePassword = "ENC(3a34fd21b82bf2a822fa49a8d8fa115d)";

      ActiveMQConnectionFactory factory = parser.newObject(parser.expandURI(
         "(tcp://localhost0:61616?,tcp://localhost1:61617?,tcp://localhost2:61618?)" +
            "?ha=true&clientID=myID&trustStorePath=" + trustStorePath +
            "&trustStorePassword=" + trustStorePassword), null);

      TransportConfiguration[] staticConnectors = factory.getStaticConnectors();
      assertEquals(3, staticConnectors.length);

      for (int i = 0; i < 3; i++) {
         assertEquals(trustStorePath, staticConnectors[i].getParams().get("trustStorePath"));
         assertEquals(trustStorePassword, staticConnectors[0].getParams().get("trustStorePassword"));
      }
   }

   private void checkTC(Map<String, Object> props, TransportConfiguration staticConnector, int offfSet) {
      TransportConfiguration connector = staticConnector;
      assertEquals(connector.getParams().get("host"), "localhost" + offfSet);
      assertEquals(connector.getParams().get("port"), "" + (61616 + offfSet));
      Map<String, Object> params = connector.getParams();
      for (Map.Entry<String, Object> entry : params.entrySet()) {
         if (!entry.getKey().equals("host") && !entry.getKey().equals("port")) {
            assertEquals(entry.getValue(), props.get(entry.getKey()));
         }
      }
   }

   private void populateConnectorParams(Map<String, Object> props,
                                        Set<String> allowableConnectorKeys,
                                        StringBuilder sb) {
      for (String allowableConnectorKey : allowableConnectorKeys) {
         if (!allowableConnectorKey.equals("host") && !allowableConnectorKey.equals("port")) {
            String value = RandomUtil.randomUUIDString();
            props.put(allowableConnectorKey, value);
            sb.append("&").append(allowableConnectorKey).append("=").append(value);
         }
      }
   }

   @Test
   public void testTCPURI() throws Exception {
      TransportConfiguration tc = new TransportConfiguration(NettyConnectorFactory.class.getName());
      Map<String, Object> params = new HashMap<>();
      params.put("host", "localhost1");
      params.put("port", 61617);
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
      Map<String, Object> params2 = new HashMap<>();
      params2.put("host", "localhost2");
      params2.put("port", 61618);
      TransportConfiguration tc3 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      ActiveMQConnectionFactory connectionFactoryWithHA = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, tc, tc2, tc3);
      URI tcp = parser.createSchema("tcp", connectionFactoryWithHA);
      ActiveMQConnectionFactory factory = parser.newObject(tcp, null);
      BeanUtilsBean bean = new BeanUtilsBean();
      checkEquals(bean, connectionFactoryWithHA, factory);
   }

   @Test
   public void testUDP() throws Exception {
      enableDiscoveryForTest();
      ActiveMQConnectionFactory factory = parser.newObject(new URI("udp://localhost:3030?ha=true&type=QUEUE_XA_CF"), null);

      assertEquals(ActiveMQXAQueueConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testUDPAllProperties() throws Exception {
      enableDiscoveryForTest();
      ignoreList.add("deserializationBlackList");
      ignoreList.add("deserializationWhiteList");
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("udp://localhost:3030?ha=true");
         BeanUtilsBean bean = new BeanUtilsBean();
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(true, (TransportConfiguration) null);
         populate(sb, bean, factory);
         ActiveMQConnectionFactory factory2 = parser.newObject(new URI(sb.toString()), null);
         checkEquals(bean, factory, factory2);
      } finally {
         ignoreList.remove("deserializationBlackList");
         ignoreList.remove("deserializationWhiteList");
      }
   }

   @Test
   public void testUDPAllPropertiesWithDeprecatedListProps() throws Exception {
      enableDiscoveryForTest();
      ignoreList.add("deserializationDenyList");
      ignoreList.add("deserializationAllowList");
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("udp://localhost:3030?ha=true");
         BeanUtilsBean bean = new BeanUtilsBean();
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(true, (TransportConfiguration) null);
         populate(sb, bean, factory);
         ActiveMQConnectionFactory factory2 = parser.newObject(new URI(sb.toString()), null);
         checkEquals(bean, factory, factory2);
      } finally {
         ignoreList.remove("deserializationDenyList");
         ignoreList.remove("deserializationAllowList");
      }
   }

   @Test
   public void testUDPURI() throws Exception {
      enableDiscoveryForTest();
      DiscoveryGroupConfiguration discoveryGroupConfiguration = new DiscoveryGroupConfiguration();
      UDPBroadcastEndpointFactory endpoint = new UDPBroadcastEndpointFactory();
      endpoint.setGroupPort(3333).setGroupAddress("wahey").setLocalBindPort(555).setLocalBindAddress("uhuh");
      discoveryGroupConfiguration.setName("foo").setRefreshTimeout(12345).setDiscoveryInitialWaitTimeout(5678).setBroadcastEndpointFactory(endpoint);
      ActiveMQConnectionFactory connectionFactoryWithHA = ActiveMQJMSClient.createConnectionFactoryWithHA(discoveryGroupConfiguration, JMSFactoryType.CF);
      URI tcp = parser.createSchema("udp", connectionFactoryWithHA);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(tcp.toString());
      DiscoveryGroupConfiguration dgc = factory.getDiscoveryGroupConfiguration();
      assertNotNull(dgc);
      BroadcastEndpointFactory befc = dgc.getBroadcastEndpointFactory();
      assertNotNull(befc);
      assertInstanceOf(UDPBroadcastEndpointFactory.class, befc);
      UDPBroadcastEndpointFactory ubgc = (UDPBroadcastEndpointFactory) befc;
      assertEquals("wahey", ubgc.getGroupAddress());
      assertEquals(3333, ubgc.getGroupPort());

      //these 2 are transient
      // These will take the System.properties used on the testsuite,
      // for that reason we take them as != instead of checking for null
      assertNotEquals("uhuh", ubgc.getLocalBindAddress());
      assertNotEquals(555, ubgc.getLocalBindPort());

      assertEquals("foo", dgc.getName());
      assertEquals(5678, dgc.getDiscoveryInitialWaitTimeout());
      assertEquals(12345, dgc.getRefreshTimeout());

      BeanUtilsBean bean = new BeanUtilsBean();
      checkEquals(bean, connectionFactoryWithHA, factory);
   }

   @Test
   public void testInvalidCFType() throws Exception {
      enableDiscoveryForTest();
      ActiveMQConnectionFactory factory = parser.newObject(new URI("udp://localhost:3030?ha=true&type=QUEUE_XA_CFInvalid"), null);

      assertEquals(ActiveMQJMSConnectionFactory.class.getName(), factory.getClass().getName());
   }

   @Test
   public void testJGroupsFile() throws Exception {
      enableDiscoveryForTest();
      ActiveMQConnectionFactory factory = parser.newObject(new URI("jgroups://channel-name?file=/path/to/some/file/channel-file.xml&test=33"), null);

      assertEquals(ActiveMQJMSConnectionFactory.class.getName(), factory.getClass().getName());
      JGroupsFileBroadcastEndpointFactory broadcastEndpointFactory = (JGroupsFileBroadcastEndpointFactory) factory.getDiscoveryGroupConfiguration().getBroadcastEndpointFactory();
      assertEquals("/path/to/some/file/channel-file.xml", broadcastEndpointFactory.getFile());
      assertEquals("channel-name", broadcastEndpointFactory.getChannelName());
   }

   @Test
   public void testJGroupsAllProperties() throws Exception {
      enableDiscoveryForTest();
      ignoreList.add("deserializationBlackList");
      ignoreList.add("deserializationWhiteList");
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("jgroups://?file=param=value;param=value&channelName=channelName&ha=true");
         BeanUtilsBean bean = new BeanUtilsBean();
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(true, (TransportConfiguration) null);
         populate(sb, bean, factory);
         ActiveMQConnectionFactory factory2 = parser.newObject(new URI(sb.toString()), null);
         checkEquals(bean, factory, factory2);
      } finally {
         ignoreList.remove("deserializationBlackList");
         ignoreList.remove("deserializationWhiteList");
      }
   }

   @Test
   public void testJGroupsAllPropertiesWithDeprecatedListProps() throws Exception {
      enableDiscoveryForTest();
      ignoreList.add("deserializationDenyList");
      ignoreList.add("deserializationAllowList");
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("jgroups://?file=param=value;param=value&channelName=channelName&ha=true");
         BeanUtilsBean bean = new BeanUtilsBean();
         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(true, (TransportConfiguration) null);
         populate(sb, bean, factory);
         ActiveMQConnectionFactory factory2 = parser.newObject(new URI(sb.toString()), null);
         checkEquals(bean, factory, factory2);
      } finally {
         ignoreList.remove("deserializationDenyList");
         ignoreList.remove("deserializationAllowList");
      }
   }

   @Test
   public void testJGroupsFileURI() throws Exception {
      enableDiscoveryForTest();
      DiscoveryGroupConfiguration discoveryGroupConfiguration = new DiscoveryGroupConfiguration();
      JGroupsFileBroadcastEndpointFactory endpointFactory = new JGroupsFileBroadcastEndpointFactory().setChannelName("channel-name").setFile("channel-file.xml");
      discoveryGroupConfiguration.setName("foo").setRefreshTimeout(12345).setDiscoveryInitialWaitTimeout(5678).setBroadcastEndpointFactory(endpointFactory);
      ActiveMQConnectionFactory connectionFactoryWithHA = ActiveMQJMSClient.createConnectionFactoryWithHA(discoveryGroupConfiguration, JMSFactoryType.CF);
      URI tcp = parser.createSchema("jgroups", connectionFactoryWithHA);
      ActiveMQConnectionFactory factory = parser.newObject(tcp, null);
      DiscoveryGroupConfiguration dgc = factory.getDiscoveryGroupConfiguration();
      assertNotNull(dgc);
      BroadcastEndpointFactory befc = dgc.getBroadcastEndpointFactory();
      assertNotNull(befc);
      assertInstanceOf(JGroupsFileBroadcastEndpointFactory.class, befc);
      assertEquals("foo", dgc.getName());
      assertEquals(5678, dgc.getDiscoveryInitialWaitTimeout());
      assertEquals(12345, dgc.getRefreshTimeout());
      JGroupsFileBroadcastEndpointFactory fileBroadcastEndpointFactory = (JGroupsFileBroadcastEndpointFactory) befc;
      assertEquals("channel-file.xml", fileBroadcastEndpointFactory.getFile());
      assertEquals("channel-name", fileBroadcastEndpointFactory.getChannelName());

      BeanUtilsBean bean = new BeanUtilsBean();
      checkEquals(bean, connectionFactoryWithHA, factory);
   }

   @Test
   public void testCacheDestinations() throws Exception {
      ActiveMQConnectionFactory factory = parser.newObject(new URI("tcp://localhost:3030"), null);

      assertFalse(factory.isCacheDestinations());

      factory = parser.newObject(new URI("tcp://localhost:3030?cacheDestinations=true"), null);

      assertTrue(factory.isCacheDestinations());

   }

   private void populate(StringBuilder sb,
                         BeanUtilsBean bean,
                         ActiveMQConnectionFactory factory) throws IllegalAccessException, InvocationTargetException {
      PropertyDescriptor[] descriptors = bean.getPropertyUtils().getPropertyDescriptors(factory);
      for (PropertyDescriptor descriptor : descriptors) {
         if (ignoreList.contains(descriptor.getName())) {
            continue;
         }
         logger.info("name::{}", descriptor.getName());
         if (descriptor.getWriteMethod() != null && descriptor.getReadMethod() != null) {
            if (descriptor.getPropertyType() == String.class) {
               String value = RandomUtil.randomUUIDString();
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

   private void checkEquals(BeanUtilsBean bean,
                            ActiveMQConnectionFactory factory,
                            ActiveMQConnectionFactory factory2) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
      PropertyDescriptor[] descriptors = bean.getPropertyUtils().getPropertyDescriptors(factory);
      for (PropertyDescriptor descriptor : descriptors) {
         if (descriptor.getWriteMethod() != null && descriptor.getReadMethod() != null) {
            assertEquals(bean.getProperty(factory, descriptor.getName()), bean.getProperty(factory2, descriptor.getName()), descriptor.getName() + " incorrect");
         }
      }
   }
}
