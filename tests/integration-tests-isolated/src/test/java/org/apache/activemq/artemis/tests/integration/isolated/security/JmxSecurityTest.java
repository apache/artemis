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
package org.apache.activemq.artemis.tests.integration.isolated.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.JMX;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import jdk.management.jfr.FlightRecorderMXBean;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.tests.extensions.SubjectDotCallAsExtension;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmxSecurityTest {

   public static Subject subject = new Subject();
   static {
      // before any call to ManagementFactory.getPlatformMBeanServer()
      System.setProperty("javax.management.builder.initial", ArtemisRbacMBeanServerBuilder.class.getCanonicalName());
      subject.getPrincipals().add(new UserPrincipal("first"));
      subject.getPrincipals().add(new RolePrincipal("programmers"));
   }

   @RegisterExtension
   public SubjectDotCallAsExtension callAs = new SubjectDotCallAsExtension(subject);

   ActiveMQServer server;

   @BeforeEach
   public void setUp() {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      Configuration configuration = new ConfigurationImpl().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()));
      configuration.setManagementRbacPrefix("jmx");
      server = ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, false);
   }

   @AfterEach
   public void tearDown() throws Exception {
      server.stop();
   }

   @Test
   public void testJmxAuthBroker() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.broker.getActivationSequence", roles);
      server.start();

      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), server.getConfiguration().getName(), true);
      ActiveMQServerControl activeMQServerControl = JMX.newMBeanProxy(ManagementFactory.getPlatformMBeanServer(), objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

      activeMQServerControl.getActivationSequence();
   }

   @Test
   public void testJxmAuthUpdateAddressNegative() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("activemq.notifications", roles);
      server.start();

      AddressControl addressControl = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getAddressObjectName(SimpleString.of("activemq.notifications")), AddressControl.class, false);

      try {
         addressControl.sendMessage(null, 1, "hi", false, null, null);
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }
   }

   @Test
   public void testJxmAuthUpdateAddress() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", true, false, false, false, true, false, false, false, false, false, true, true));
      server.getConfiguration().putSecurityRoles("jmx.address.activemq.notifications.getUnRoutedMessageCount", roles);
      server.getConfiguration().putSecurityRoles("jmx.address.activemq.notifications.sendMessage", roles);
      server.getConfiguration().putSecurityRoles("activemq.notifications", roles); // the real address Send permission
      server.getConfiguration().putSecurityRoles("jmx.address.activemq.notifications.pause", roles);
      server.getConfiguration().putSecurityRoles("jmx.address.activemq.notifications.resume", roles);
      server.getConfiguration().putSecurityRoles("jmx.address.activemq.notifications.isPaging", roles);
      server.start();

      AddressControl addressControl = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getAddressObjectName(SimpleString.of("activemq.notifications")), AddressControl.class, false);

      long unRoutedMessageCount = addressControl.getUnRoutedMessageCount();
      assertEquals(0L, unRoutedMessageCount);

      addressControl.sendMessage(null, 1, "hi", false, null, null);

      Wait.assertEquals(3L, () -> addressControl.getUnRoutedMessageCount(), 2000, 50);

      assertFalse(addressControl.isPaging());

      addressControl.pause();
      addressControl.resume();
   }

   @Test
   public void testJmxAuthQueue() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.queue.Q1.*", roles);

      CoreAddressConfiguration address = new CoreAddressConfiguration();
      address.setName("Q1").addQueueConfig(QueueConfiguration.of("Q1").setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().getAddressConfigurations().add(address);

      server.start();

      QueueControl queueControl = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getQueueObjectName(SimpleString.of("Q1"), SimpleString.of("Q1"), RoutingType.ANYCAST), QueueControl.class, false);
      queueControl.getDurableMessageCount();

      queueControl.browse();
      queueControl.countMessages();
      queueControl.listGroupsAsJSON();

      try {
         queueControl.sendMessage(null, 1, "hi", false, null, null);
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }

      try {
         queueControl.disable();
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }

      try {
         queueControl.enable();
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }

      try {
         queueControl.pause(true);
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }

      try {
         queueControl.flushExecutor();
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }
   }

   @Test
   public void testJxmAuthAcceptor() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.acceptors.*.isStarted", roles);
      server.getConfiguration().putSecurityRoles("jmx.acceptors.*.getParameters", roles);

      server.start();

      AcceptorControl control = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getAcceptorObjectName(server.getConfiguration().getAcceptorConfigurations().stream().findFirst().get().getName()), AcceptorControl.class, false);

      control.isStarted();
      control.getParameters();

      try {
         control.stop();
         fail("need Update permission");
      } catch (Exception expected) {
         assertTrue(expected.getMessage().contains("first"));
         assertTrue(expected.getMessage().contains("EDIT"));
      }
   }

   @Test
   public void testJxmAuthJvmRuntime() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.java.lang.Runtime.*", roles);

      server.start();

      ObjectName runtimeName = new ObjectName("java.lang", "type", "Runtime");
      RuntimeMXBean runtime = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(), runtimeName, RuntimeMXBean.class, false);
      runtime.getVmVersion();
   }

   @Test
   public void testJxmAuthFlightRecorder() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.jdk.management.#", roles);

      server.start();

      ObjectName runtimeName = new ObjectName("jdk.management.jfr", "type", "FlightRecorder");
      FlightRecorderMXBean fr = JMX.newMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), runtimeName, FlightRecorderMXBean.class, false);
      fr.getConfigurations();
   }

   @Test
   public void testJxmAuthFlightRecorderWithCustomWildcardDelimiter() throws Exception {

      server.getConfiguration().getWildcardConfiguration().setDelimiter('-');
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", false, false, false, false, true, false, false, false, false, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx-jdk.management.jfr-#", roles);

      server.start();

      ObjectName runtimeName = new ObjectName("jdk.management.jfr", "type", "FlightRecorder");
      FlightRecorderMXBean fr = JMX.newMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), runtimeName, FlightRecorderMXBean.class, false);
      fr.getConfigurations();
   }

   @Test
   public void testQueueAuthorization() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString QUEUE_A = SimpleString.of("a");
      final SimpleString QUEUE_B = SimpleString.of("b");

      Set<Role> aRoles = new HashSet<>();
      aRoles.add(new Role(QUEUE_A.toString(), false, true, true, false, false, false, false, false, true, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.queue." + QUEUE_A + ".countMessages", aRoles);

      Set<Role> bRoles = new HashSet<>();
      bRoles.add(new Role(QUEUE_B.toString(), false, true, true, false, false, false, false, false, true, false, true, false));
      server.getConfiguration().putSecurityRoles("jmx.queue." + QUEUE_B + ".countMessages", bRoles);

      server.start();

      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(QUEUE_A).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(QUEUE_B).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      QueueControl queueControlA = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getQueueObjectName(ADDRESS, QUEUE_A, RoutingType.ANYCAST), QueueControl.class, false);

      QueueControl queueControlB = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getQueueObjectName(ADDRESS, QUEUE_B, RoutingType.ANYCAST), QueueControl.class, false);

      Subject subjectA = new Subject();
      subjectA.getPrincipals().add(new UserPrincipal("a"));
      subjectA.getPrincipals().add(new RolePrincipal("a"));

      Subject subjectB = new Subject();
      subjectB.getPrincipals().add(new UserPrincipal("b"));
      subjectB.getPrincipals().add(new RolePrincipal("b"));

      // client A View queue A
      assertEquals(Long.valueOf(0), SecurityManagerShim.callAs(subjectA, (Callable<Long>) () -> queueControlA.countMessages()));

      // client B view queue A
      try {
         assertEquals(Long.valueOf(0), SecurityManagerShim.callAs(subjectB, (Callable<Long>) () -> queueControlA.countMessages()));
         fail("should throw exception here");
      } catch (CompletionException ce) {
         assertInstanceOf(SecurityException.class, ce.getCause());
      }

      // client B View queue B
      assertEquals(Long.valueOf(0), SecurityManagerShim.callAs(subjectB, (Callable<Long>) () -> queueControlB.countMessages()));


      // client A View queue B
      try {
         assertEquals(Long.valueOf(0), SecurityManagerShim.callAs(subjectA, (Callable<Long>) () -> queueControlB.countMessages()));
         fail("should throw exception here");
      } catch (CompletionException ce) {
         assertInstanceOf(SecurityException.class, ce.getCause());
      }
   }

}
