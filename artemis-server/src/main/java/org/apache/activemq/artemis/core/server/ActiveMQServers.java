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
package org.apache.activemq.artemis.core.server;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.LegacyJMSConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;

/**
 * ActiveMQServers is a factory class for instantiating ActiveMQServer instances.
 * <p>
 * This class should be used when you want to instantiate an ActiveMQServer instance for embedding in your own
 * application, as opposed to directly instantiating an implementing instance.
 */
public final class ActiveMQServers {

   private ActiveMQServers() {
      // Utility class
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config, final boolean enablePersistence) {
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, ManagementFactory.getPlatformMBeanServer(), securityManager, enablePersistence);

      return server;
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config) {
      return ActiveMQServers.newActiveMQServer(config, config.isPersistenceEnabled());
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config,
                                                  final MBeanServer mbeanServer,
                                                  final boolean enablePersistence) {
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, mbeanServer, securityManager, enablePersistence);

      return server;
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config, final MBeanServer mbeanServer) {
      return ActiveMQServers.newActiveMQServer(config, mbeanServer, true);
   }

   public static ActiveMQServer newActiveMQServer(final String configURL,
                                                  final MBeanServer mbeanServer,
                                                  final ActiveMQSecurityManager securityManager) throws Exception {

      FileConfiguration config = new FileConfiguration();
      LegacyJMSConfiguration legacyJMSConfiguration = new LegacyJMSConfiguration(config);
      new FileDeploymentManager(configURL).addDeployable(config).addDeployable(legacyJMSConfiguration).readConfiguration();

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, mbeanServer, securityManager, config.isPersistenceEnabled());

      return server;
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config,
                                                  final MBeanServer mbeanServer,
                                                  final ActiveMQSecurityManager securityManager) {
      ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, mbeanServer, securityManager, true);

      return server;
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config,
                                                  final MBeanServer mbeanServer,
                                                  final ActiveMQSecurityManager securityManager,
                                                  final boolean enablePersistence) {
      config.setPersistenceEnabled(enablePersistence);

      ActiveMQServer server = new ActiveMQServerImpl(config, mbeanServer, securityManager);

      return server;
   }

   public static ActiveMQServer newActiveMQServer(Configuration config, String defUser, String defPass) {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      securityManager.getConfiguration().addUser(defUser, defPass);

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, ManagementFactory.getPlatformMBeanServer(), securityManager, config.isPersistenceEnabled());

      return server;
   }

   public static ActiveMQServer newActiveMQServer(final Configuration config,
                                                  final MBeanServer mbeanServer,
                                                  final boolean enablePersistence,
                                                  String user,
                                                  String password) {
      SecurityConfiguration securityConfiguration = new SecurityConfiguration();
      securityConfiguration.addUser(user, password);
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, mbeanServer, securityManager, enablePersistence);

      return server;
   }

}
