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
package org.apache.activemq.artemis.osgi;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration.StoreType;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings({"unchecked", "rawtypes"})
@Component(configurationPid = "org.apache.activemq.artemis", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class OsgiBroker {

   private String name;
   private String configurationUrl;
   private String rolePrincipalClass;
   private String userPrincipalClass;
   private Map<String, ActiveMQComponent> components;
   private Map<String, ServiceRegistration<?>> registrations;
   private ServiceTracker tracker;
   private ServiceTracker dataSourceTracker;

   @Activate
   public void activate(ComponentContext cctx) throws Exception {
      final BundleContext context = cctx.getBundleContext();
      final Dictionary<String, Object> properties = cctx.getProperties();
      configurationUrl = getMandatory(properties, "config");
      name = getMandatory(properties, "name");
      rolePrincipalClass = (String) properties.get("rolePrincipalClass");
      userPrincipalClass = (String) properties.get("userPrincipalClass");

      String domain = getMandatory(properties, "domain");
      ActiveMQJAASSecurityManager security = new ActiveMQJAASSecurityManager(domain);
      if (rolePrincipalClass != null) {
         security.setRolePrincipalClass(rolePrincipalClass);
      }
      if (userPrincipalClass != null) {
         security.setUserPrincipalClass(userPrincipalClass);
      }
      String brokerInstance = null;

      String artemisDataDir = System.getProperty("artemis.data");
      if (artemisDataDir != null) {
         brokerInstance = artemisDataDir + "/artemis/" + name;
      } else {
         String karafDataDir = System.getProperty("karaf.data");
         if (karafDataDir != null) {
            brokerInstance = karafDataDir + "/artemis/" + name;
         }
      }


      // todo if we start to pullout more configs from the main config then we
      // should pull out the configuration objects from factories if available
      FileConfiguration configuration = new FileConfiguration();
      if (brokerInstance != null) {
         configuration.setBrokerInstance(new File(brokerInstance));
      }
      FileJMSConfiguration jmsConfiguration = new FileJMSConfiguration();

      FileDeploymentManager fileDeploymentManager = new FileDeploymentManager(configurationUrl);
      fileDeploymentManager.addDeployable(configuration).addDeployable(jmsConfiguration).readConfiguration();

      components = fileDeploymentManager.buildService(security, ManagementFactory.getPlatformMBeanServer(), null);

      final ActiveMQServer server = (ActiveMQServer) components.get("core");

      String[] requiredProtocols = getRequiredProtocols(server.getConfiguration().getAcceptorConfigurations());
      ServerTrackerCallBack callback = new ServerTrackerCallBackImpl(server, context, properties);

      StoreConfiguration storeConfiguration = server.getConfiguration().getStoreConfiguration();
      String dataSourceName = String.class.cast(properties.get("dataSourceName"));

      if (storeConfiguration != null &&
          storeConfiguration.getStoreType() == StoreType.DATABASE && dataSourceName != null &&
               !dataSourceName.isEmpty()) {
         callback.setDataSourceDependency(true);
         String filter = "(&(objectClass=javax.sql.DataSource)(osgi.jndi.service.name=" + dataSourceName + "))";
         DataSourceTracker trackerCust =
                  new DataSourceTracker(name, context, DatabaseStorageConfiguration.class.cast(storeConfiguration),
                                        callback);
         dataSourceTracker = new ServiceTracker(context, context.createFilter(filter), trackerCust);
         dataSourceTracker.open();
      }

      ProtocolTracker trackerCust = new ProtocolTracker(name, context, requiredProtocols, callback);
      tracker = new ServiceTracker(context, ProtocolManagerFactory.class, trackerCust);
      tracker.open();
   }

   private String getMandatory(Dictionary<String, ?> properties, String key) {
      String value = (String) properties.get(key);
      if (value == null) {
         throw new IllegalStateException("Property " + key + " must be set");
      }
      return value;
   }

   private String[] getRequiredProtocols(Set<TransportConfiguration> acceptors) {
      List<String> protocols = new ArrayList<>();
      for (TransportConfiguration acceptor : acceptors) {
         Object protocolsFromAcceptor = acceptor.getParams().get(TransportConstants.PROTOCOLS_PROP_NAME);
         if (protocolsFromAcceptor != null) {
            String[] protocolsSplit = protocolsFromAcceptor.toString().split(",");
            for (String protocol : protocolsSplit) {
               if (!protocols.contains(protocol)) {
                  protocols.add(protocol);
               }
            }
         }
      }
      return protocols.toArray(new String[protocols.size()]);
   }

   @Deactivate
   public void stop() throws Exception {
      tracker.close();
      if (dataSourceTracker != null) {
         dataSourceTracker.close();
      }
   }

   public Map<String, ActiveMQComponent> getComponents() {
      return components;
   }

   /*
    * this makes sure the components are started in the correct order. Its
    * simple at the mo as e only have core and jms but will need impproving if
    * we get more.
    */
   public List<ActiveMQComponent> getComponentsByStartOrder(Map<String, ActiveMQComponent> components) {
      List<ActiveMQComponent> activeMQComponents = new ArrayList<>();
      ActiveMQComponent jmsComponent = components.get("jms");
      if (jmsComponent != null) {
         activeMQComponents.add(jmsComponent);
      }
      activeMQComponents.add(components.get("core"));
      return activeMQComponents;
   }

   public void register(BundleContext context, Dictionary<String, ?> properties) {
      registrations = new HashMap<>();
      for (Map.Entry<String, ActiveMQComponent> component : getComponents().entrySet()) {
         String[] classes = getInterfaces(component.getValue());
         Hashtable<String, Object> props = new Hashtable<>();
         for (Enumeration<String> keyEnum = properties.keys(); keyEnum.hasMoreElements(); ) {
            String key = keyEnum.nextElement();
            Object val = properties.get(key);
            props.put(key, val);
         }
         ServiceRegistration<?> registration = context.registerService(classes, component.getValue(), props);
         registrations.put(component.getKey(), registration);
      }
   }

   private String[] getInterfaces(ActiveMQComponent value) {
      Set<String> interfaces = new HashSet<>();
      getInterfaces(value.getClass(), interfaces);
      return interfaces.toArray(new String[interfaces.size()]);
   }

   private void getInterfaces(Class<?> clazz, Set<String> interfaces) {
      for (Class<?> itf : clazz.getInterfaces()) {
         if (interfaces.add(itf.getName())) {
            getInterfaces(itf, interfaces);
         }
      }
      if (clazz.getSuperclass() != null) {
         getInterfaces(clazz.getSuperclass(), interfaces);
      }
   }

   public void unregister() {
      if (registrations != null) {
         for (ServiceRegistration<?> reg : registrations.values()) {
            reg.unregister();
         }
      }
   }

   private class ServerTrackerCallBackImpl implements ServerTrackerCallBack {

      private volatile boolean dataSourceDependency = false;

      private final ActiveMQServer server;
      private final BundleContext context;
      private final Dictionary<String, Object> properties;

      ServerTrackerCallBackImpl(ActiveMQServer server, BundleContext context,
                                         Dictionary<String, Object> properties) {
         this.server = server;
         this.context = context;
         this.properties = properties;
      }

      @Override
      public void addFactory(ProtocolManagerFactory<Interceptor> pmf) {
         server.addProtocolManagerFactory(pmf);
      }

      @Override
      public void removeFactory(ProtocolManagerFactory<Interceptor> pmf) {
         server.removeProtocolManagerFactory(pmf);
      }

      @Override
      public void stop() throws Exception {
         ActiveMQComponent[] mqComponents = new ActiveMQComponent[components.size()];
         components.values().toArray(mqComponents);
         for (int i = mqComponents.length - 1; i >= 0; i--) {
            mqComponents[i].stop();
         }
         unregister();
      }

      @Override
      public void start() throws Exception {
         if (!dataSourceDependency) {
            List<ActiveMQComponent> componentsByStartOrder = getComponentsByStartOrder(components);
            for (ActiveMQComponent component : componentsByStartOrder) {
               component.start();
            }
            register(context, properties);
         }
      }

      @Override
      public boolean isStarted() {
         return server.isStarted();
      }



      @Override
      public void setDataSourceDependency(boolean dataSourceDependency) {
         this.dataSourceDependency = dataSourceDependency;
      }


   }

}
