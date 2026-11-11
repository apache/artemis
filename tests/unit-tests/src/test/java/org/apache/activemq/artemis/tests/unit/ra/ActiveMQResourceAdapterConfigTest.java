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
package org.apache.activemq.artemis.tests.unit.ra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.XmlProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * This test is used to generate the commented out configs in the src/config/ra.xml. If you add a setter to the
 * ActiveMQResourceAdapter this test should fail, if it does paste the new commented out configs into the ra.xml file
 * and in here. don't forget to add a description for each new property added and try and put it in the config some
 * where appropriate.
 */
public class ActiveMQResourceAdapterConfigTest extends ActiveMQTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String config = """
      <config-property>
         <description>
            The transport type. Multiple connectors can be configured by using a comma separated list,
            i.e. org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory,org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory.
         </description>
         <config-property-name>ConnectorClassName</config-property-name>
         <config-property-type>java.lang.String</config-property-type>
         <config-property-value>org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory</config-property-value>
      </config-property>
      <config-property>
         <description>The transport configuration. These values must be in the form of key=val;key=val;,
            if multiple connectors are used then each set must be separated by a comma i.e. host=host1;port=61616,host=host2;port=61617.
            Each set of params maps to the connector classname specified.
         </description>
         <config-property-name>ConnectionParameters</config-property-name>
         <config-property-type>java.lang.String</config-property-type>
         <config-property-value>server-id=0</config-property-value>
      </config-property>""";

   private static String commentedOutConfigs = """
            <config-property>
              <description>Does we support HA</description>
              <config-property-name>HA</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value>false</config-property-value>
            </config-property>
            <config-property>
              <description>Use A local Transaction instead of XA?</description>
              <config-property-name>UseLocalTx</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value>false</config-property-value>
            </config-property>
            <config-property>
              <description>The user name used to login to the JMS server</description>
              <config-property-name>UserName</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The password used to login to the JMS server</description>
              <config-property-name>Password</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The jndi params to use to look up the jms resources if local jndi is not to be used</description>
              <config-property-name>JndiParams</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value>java.naming.factory.initial=org.jnp.interfaces.NamingContextFactory;java.naming.provider.url=jnp://localhost:1199;java.naming.factory.url.pkgs=org.jboss.naming:org.jnp.interfaces</config-property-value>
            </config-property>
            <config-property>
              <description>The jndi entries to use to look up the connection factory</description>
              <config-property-name>Entries</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value>["java://jmsXA"]</config-property-value>
            </config-property>
            <config-property>
              <description>The discovery group address</description>
              <config-property-name>DiscoveryLocalBindAddress</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The discovery group local bind address</description>
              <config-property-name>DiscoveryAddress</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The discovery group port</description>
              <config-property-name>DiscoveryPort</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The discovery refresh timeout</description>
              <config-property-name>DiscoveryRefreshTimeout</config-property-name>
              <config-property-type>long</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The discovery initial wait timeout</description>
              <config-property-name>DiscoveryInitialWaitTimeout</config-property-name>
              <config-property-type>long</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>The class to use for load balancing connections</description>
               <config-property-name>ConnectionLoadBalancingPolicyClassName</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>number of reconnect attempts for connections after failover occurs</description>
               <config-property-name>ReconnectAttempts</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The client failure check period</description>
              <config-property-name>ClientFailureCheckPeriod</config-property-name>
              <config-property-type>long</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The connection TTL</description>
              <config-property-name>ConnectionTTL</config-property-name>
              <config-property-type>long</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The call timeout</description>
              <config-property-name>CallTimeout</config-property-name>
              <config-property-type>long</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The dups ok batch size</description>
              <config-property-name>DupsOKBatchSize</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The transaction batch size</description>
              <config-property-name>TransactionBatchSize</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The consumer window size</description>
              <config-property-name>ConsumerWindowSize</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The consumer max rate</description>
              <config-property-name>ConsumerMaxRate</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The confirmation window size</description>
              <config-property-name>ConfirmationWindowSize</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The producer max rate</description>
              <config-property-name>ProducerMaxRate</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The min large message size</description>
              <config-property-name>MinLargeMessageSize</config-property-name>
              <config-property-type>int</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The block on acknowledge</description>
              <config-property-name>BlockOnAcknowledge</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The block on non durable send</description>
              <config-property-name>BlockOnNonDurableSend</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The block on durable send</description>
              <config-property-name>BlockOnDurableSend</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The auto group</description>
              <config-property-name>AutoGroup</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The pre acknowledge</description>
              <config-property-name>PreAcknowledge</config-property-name>
              <config-property-type>boolean</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The retry interval</description>
              <config-property-name>RetryInterval</config-property-name>
              <config-property-type>long</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The retry interval multiplier</description>
              <config-property-name>RetryIntervalMultiplier</config-property-name>
              <config-property-type>java.lang.Double</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
              <description>The client id</description>
              <config-property-name>ClientID</config-property-name>
              <config-property-type>java.lang.String</config-property-type>
              <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>use global pools for client</description>
               <config-property-name>UseGlobalPools</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>Cache destinations per session</description>
               <config-property-name>CacheDestinations</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>max number of threads for scheduled thread pool</description>
               <config-property-name>ScheduledThreadPoolMaxSize</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>max number of threads in pool</description>
               <config-property-name>ThreadPoolMaxSize</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>whether to use jndi for looking up destinations etc</description>
               <config-property-name>UseJNDI</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>how long in milliseconds to wait before retry on failed MDB setup</description>
               <config-property-name>SetupInterval</config-property-name>
               <config-property-type>long</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>How many attempts should be made when connecting the MDB</description>
               <config-property-name>SetupAttempts</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>Add a new managed connection factory</description>
               <config-property-name>ManagedConnectionFactory</config-property-name>
               <config-property-type>org.apache.activemq.artemis.ra.ActiveMQRAManagedConnectionFactory</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>Whether to use password mask, default false</description>
               <config-property-name>UseMaskedPassword</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value>false</config-property-value>
            </config-property>
            <config-property>
               <description>The class definition (full qualified name and its properties) used to encrypt the password</description>
               <config-property-name>PasswordCodec</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value>org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;key=clusterpassword;algorithm=ssss</config-property-value>
            </config-property>
            <config-property>
               <description>Whether the resource adapter must use auto recovery</description>
               <config-property-name>UseAutoRecovery</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value>true</config-property-value>
            </config-property>
            <config-property>
               <description>The initial size for message packet</description>
               <config-property-name>InitialMessagePacketSize</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value>1500</config-property-value>
            </config-property>
            <config-property>
               <description>the Group ID</description>
               <config-property-name>GroupID</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>Whether the resource adapter must failover on the initial connection</description>
               <config-property-name>FailoverOnInitialConnection</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value>false</config-property-value>
            </config-property>
            <config-property>
               <description>Whether the resource adapter compress large messages and send them as regular when possible</description>
               <config-property-name>CompressLargeMessage</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value>false</config-property-value>
            </config-property>
            <config-property>
               <description>The level of compression to use. Must be -1 or between 0-9</description>
               <config-property-name>CompressionLevel</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value>-1</config-property-value>
            </config-property>
            <config-property>
               <description>The timeout in milliseconds for failover call (or -1 for infinite)</description>
               <config-property-name>CallFailoverTimeout</config-property-name>
               <config-property-type>long</config-property-type>
               <config-property-value>-1</config-property-value>
            </config-property>
            <config-property>
               <description>The maximum interval (in milliseconds) between reconnection attempts</description>
               <config-property-name>MaxRetryInterval</config-property-name>
               <config-property-type>long</config-property-type>
               <config-property-value>2000</config-property-value>
            </config-property>
            <config-property>
               <description>Whether the resource adapter must cache large messages</description>
               <config-property-name>CacheLargeMessagesClient</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value>false</config-property-value>
            </config-property>
            <config-property>
               <description>The producer window size (in bytes)</description>
               <config-property-name>ProducerWindowSize</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value>65536</config-property-value>
            </config-property>
            <config-property>
               <description>The number of attempts for the initial connection</description>
               <config-property-name>InitialConnectAttempts</config-property-name>
               <config-property-type>int</config-property-type>
               <config-property-value>1</config-property-value>
            </config-property>
            <config-property>
               <description>my-channel</description>
               <config-property-name>JgroupsChannelName</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>my-file</description>
               <config-property-name>JgroupsFile</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>Jgroups Channel Locator Class</description>
               <config-property-name>JgroupsChannelLocatorClass</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>Name to locate a JChannel by JGroups Channel locator class</description>
               <config-property-name>JgroupsChannelRefName</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>ProtocolManagerConfig</description>
               <config-property-name>ProtocolManagerFactoryStr</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>List of package/class names against which matching objects are permitted to be deserialized</description>
               <config-property-name>DeserializationWhiteList</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>List of package/class names against which matching objects are permitted to be deserialized</description>
               <config-property-name>DeserializationAllowList</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>List of package/class names against which matching objects are forbidden to be deserialized</description>
               <config-property-name>DeserializationBlackList</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>List of package/class names against which matching objects are forbidden to be deserialized</description>
               <config-property-name>DeserializationDenyList</config-property-name>
               <config-property-type>java.lang.String</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>***add***</description>
               <config-property-name>IgnoreJTA</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>***add***</description>
               <config-property-name>Enable1xPrefixes</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
            <config-property>
               <description>***add***</description>
               <config-property-name>UseTopologyForLoadBalancing</config-property-name>
               <config-property-type>boolean</config-property-type>
               <config-property-value></config-property-value>
            </config-property>
      """;

   private static String rootConfig = "<root>" + config + commentedOutConfigs + "</root>";

   @Test
   public void testConfiguration() throws Exception {
      Method[] methods = ActiveMQResourceAdapter.class.getMethods();
      Map<String, Method> methodList = new HashMap<>();
      for (Method method : methods) {
         if (method.getName().startsWith("set")) {
            methodList.put(method.getName(), method);
         }
      }
      DocumentBuilder db = XmlProvider.newDocumentBuilder();
      InputStream io = new ByteArrayInputStream(rootConfig.getBytes());
      Document dom = db.parse(new InputSource(io));

      Element docEle = dom.getDocumentElement();

      NodeList nl = docEle.getElementsByTagName("config-property");

      for (int i = 0; i < nl.getLength(); i++) {
         Element el = (Element) nl.item(i);
         NodeList elementsByTagName = el.getElementsByTagName("config-property-name");
         assertEquals(1, elementsByTagName.getLength(), el.toString());
         Node configPropertyNameNode = elementsByTagName.item(0);
         String configPropertyName = configPropertyNameNode.getTextContent();
         Method setter = methodList.remove("set" + configPropertyName);
         assertNotNull(setter, "setter " + configPropertyName + " does not exist");
         Class c = lookupType(setter);
         elementsByTagName = el.getElementsByTagName("config-property-type");
         assertEquals(1, elementsByTagName.getLength(), "setter " + configPropertyName + " has no type set");
         Node configPropertyTypeNode = elementsByTagName.item(0);
         String configPropertyTypeName = configPropertyTypeNode.getTextContent();
         assertEquals(configPropertyTypeName, c.getName());
      }
      if (!methodList.isEmpty()) {
         StringBuilder newConfig = new StringBuilder(commentedOutConfigs);
         newConfig.append("\n");
         for (Method method : methodList.values()) {
            newConfig.append("         \"      <config-property>\" + \n");
            newConfig.append("         \"         <description>***add***</description>\" + \n");
            newConfig.append("         \"         <config-property-name>").append(method.getName().substring(3)).append("</config-property-name>\" + \n");
            newConfig.append("         \"         <config-property-type>").append(lookupType(method).getName()).append("</config-property-type>\" + \n");
            newConfig.append("         \"         <config-property-value></config-property-value>\" + \n");
            newConfig.append("         \"      </config-property>\" + \n");
         }
         logger.debug(newConfig.toString());
         fail("methods not shown please see previous and add");
      }
   }

   private Class<?> lookupType(Method setter) {
      Class<?> clzz = setter.getParameterTypes()[0];

      if (clzz == Boolean.class) {
         return Boolean.TYPE;
      } else if (clzz == Long.class) {
         return Long.TYPE;
      } else if (clzz == Integer.class) {
         return Integer.TYPE;
      } else {
         return clzz;
      }
   }
}
