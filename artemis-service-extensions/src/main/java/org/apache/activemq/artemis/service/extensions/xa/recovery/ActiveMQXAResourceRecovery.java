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
package org.apache.activemq.artemis.service.extensions.xa.recovery;

import javax.transaction.xa.XAResource;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A XAResourceRecovery instance that can be used to recover any JMS provider.
 * <p>
 * In reality only recover, rollback and commit will be called but we still need to be implement all methods just in
 * case.
 * <p>
 * To enable this add the following to the jbossts-properties file
 * <pre>
 * &lt;property name="com.arjuna.ats.jta.recovery.XAResourceRecovery.ACTIVEMQ1"
 *                 value="org.apache.activemq.artemis.jms.server.recovery.ActiveMQXAResourceRecovery;org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory"/&gt;
 * </pre>
 * <p>
 * you'll need something like this if the Apache Artemis Server is remote
 * <pre>
 *      &lt;property name="com.arjuna.ats.jta.recovery.XAResourceRecovery.ACTIVEMQ2"
 *                  value="org.apache.activemq.artemis.jms.server.recovery.ActiveMQXAResourceRecovery;org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost,port=61616"/&gt;
 * </pre>
 * <p>
 * you'll need something like this if the Apache Artemis Server is remote and has failover configured
 * <pre>
 *             &lt;property name="com.arjuna.ats.jta.recovery.XAResourceRecovery.ACTIVEMQ2"
 *                       value="org.apache.activemq.artemis.jms.server.recovery.ActiveMQXAResourceRecovery;org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost,port=61616;org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost2,port=61617"/&gt;
 * </pre>
 */
public class ActiveMQXAResourceRecovery {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean hasMore;

   private ActiveMQXAResourceWrapper res;

   public ActiveMQXAResourceRecovery() {
      logger.trace("Constructing ActiveMQXAResourceRecovery");
   }

   public boolean initialise(final String config) {
      logger.trace("{} initialise: {}", this, config);

      String[] configs = config.split(";");
      XARecoveryConfig[] xaRecoveryConfigs = new XARecoveryConfig[configs.length];
      for (int i = 0, configsLength = configs.length; i < configsLength; i++) {
         String s = configs[i];
         ConfigParser parser = new ConfigParser(s);
         String connectorFactoryClassName = parser.getConnectorFactoryClassName();
         Map<String, Object> connectorParams = parser.getConnectorParameters();
         String username = parser.getUsername();
         String password = parser.getPassword();
         TransportConfiguration transportConfiguration = new TransportConfiguration(connectorFactoryClassName, connectorParams);
         xaRecoveryConfigs[i] = new XARecoveryConfig(false, new TransportConfiguration[]{transportConfiguration}, username, password, null, null);
      }

      res = new ActiveMQXAResourceWrapper(xaRecoveryConfigs);

      logger.trace("{} initialised", this);

      return true;
   }

   public boolean hasMoreResources() {
      logger.trace("{} hasMoreResources", this);

      /*
       * The way hasMoreResources is supposed to work is as follows:
       * For each "sweep" the recovery manager will call hasMoreResources, then if it returns
       * {@code true} it will call getXAResource.
       * It will repeat that until hasMoreResources returns false.
       * Then the sweep is over.
       * For the next sweep hasMoreResources should return {@code true}, etc.
       *
       * In our case where we only need to return one XAResource per sweep,
       * hasMoreResources should basically alternate between {@code true} and false.
       */

      hasMore = !hasMore;

      return hasMore;
   }

   public XAResource getXAResource() {
      logger.trace("{} getXAResource", this);

      return res;
   }

   public XAResource[] getXAResources() {
      return new XAResource[]{res};
   }

   public static class ConfigParser {

      private final String connectorFactoryClassName;

      private final Map<String, Object> connectorParameters;

      private String username;

      private String password;

      public ConfigParser(final String config) {
         if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Must specify provider connector factory class name in config");
         }

         String[] strings = config.split(",");

         // First (mandatory) param is the connector factory class name
         if (strings.length < 1) {
            throw new IllegalArgumentException("Must specify provider connector factory class name in config");
         }

         connectorFactoryClassName = strings[0].trim();

         // Next two (optional) parameters are the username and password to use for creating the session for recovery

         if (strings.length >= 2) {

            username = strings[1].trim();
            if (username.isEmpty()) {
               username = null;
            }

            if (strings.length == 2) {
               throw new IllegalArgumentException("If username is specified, password must be specified too");
            }

            password = strings[2].trim();
            if (password.isEmpty()) {
               password = null;
            }
         }

         // other tokens are for connector configurations
         connectorParameters = new HashMap<>();
         if (strings.length >= 3) {
            for (int i = 3; i < strings.length; i++) {
               String[] str = strings[i].split("=");
               if (str.length == 2) {
                  connectorParameters.put(str[0].trim(), str[1].trim());
               }
            }
         }
      }

      public String getConnectorFactoryClassName() {
         return connectorFactoryClassName;
      }

      public Map<String, Object> getConnectorParameters() {
         return connectorParameters;
      }

      public String getUsername() {
         return username;
      }

      public String getPassword() {
         return password;
      }
   }
}
