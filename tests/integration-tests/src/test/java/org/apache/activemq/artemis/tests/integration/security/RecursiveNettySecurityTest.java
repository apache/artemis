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
package org.apache.activemq.artemis.tests.integration.security;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.security.auth.Subject;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.NoCacheLoginException;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecursiveNettySecurityTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   public void testRecursiveSecurity() throws Exception {
      RecursiveNettySecurityManager securityManager = new RecursiveNettySecurityManager();
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultNettyConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.start();

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      try {
         Connection connection = connectionFactory.createConnection("first", "secret");
         connection.close();
      } catch (JMSException e) {
         e.printStackTrace();
         fail("should not throw exception");
      }
   }

   class RecursiveNettySecurityManager implements ActiveMQSecurityManager5 {

      @Override
      public boolean validateUser(String user, String password) {
         return false;
      }

      @Override
      public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
         return false;
      }

      @Override
      public Subject authenticate(String user,
                                  String password,
                                  RemotingConnection remotingConnection,
                                  String securityDomain) throws NoCacheLoginException {
         NettyConnection nettyConnection = (NettyConnection) remotingConnection.getTransportConnection();
         CountDownLatch latch = new CountDownLatch(1);
         nettyConnection.getChannel().eventLoop().execute(latch::countDown);
         try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
               logger.warn("Cannot complete oepration in time", new Exception("timeout"));
               throw new NoCacheLoginException("Can't complete operation in time");
            }
         } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
            throw new NoCacheLoginException(e.getMessage());
         }
         Subject authenticatedSubject = new Subject();
         authenticatedSubject.getPrincipals().add(new UserPrincipal(user));
         return authenticatedSubject;
      }

      @Override
      public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
         return true;
      }
   }
}