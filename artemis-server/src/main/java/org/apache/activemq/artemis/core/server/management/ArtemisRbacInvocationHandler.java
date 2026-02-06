/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.core.server.management;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.management.impl.ManagementRemotingConnection;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public class ArtemisRbacInvocationHandler implements GuardInvocationHandler {

   private final List<String> mBeanServerCheckedMethods = List.of("invoke", "getAttribute", "getAttributes", "setAttribute", "setAttributes", "queryMBeans", "queryNames");
   private final List<String> uncheckedDomains = List.of("hawtio");

   private final MBeanServer delegate;
   private volatile ActiveMQServer activeMQServer;
   String brokerDomain;
   Pattern viewPermissionMatcher;
   SimpleString rbacPrefix;
   SimpleString mBeanServerRbacAddressPrefix;

   ArtemisRbacInvocationHandler(MBeanServer mbeanServer) {
      delegate = mbeanServer;
   }

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

      initAuditLoggerContext();

      if (mBeanServerCheckedMethods.contains(method.getName())) {

         if (activeMQServer != null) {
            // happily initialised, do our check
            securityCheck(method, args);

         } else {
            // initialisation pending registration of broker control with a check operation

            if (method.getName().startsWith("query")) {
               // restrict result in favour of an exception
               return null;
            }

            if (!isUncheckedDomain(args)) {
               throw new IllegalStateException("initialisation pending");
            }
         }
      } else {
         initializeFromFirstServerMBeanRegistration(method, args);
      }

      Object result;
      try {
         result = method.invoke(delegate, args);
      } catch (InvocationTargetException ite) {
         throw ite.getCause();
      }

      // filter query results based on RBAC
      if (method.getName().startsWith("query") && result instanceof Collection<?> collection) {
         collection.removeIf(this::viewPermissionCheckFails);
      }

      return result;
   }

   private boolean isUncheckedDomain(Object[] args) {
      final ObjectName objectName = objectNameFrom(args);
      return isUncheckedDomain(objectName);
   }

   private boolean isUncheckedDomain(ObjectName objectName) {
      if (objectName != null) {
         return uncheckedDomains.contains(objectName.getDomain());
      }
      return false;
   }

   private ObjectName objectNameFrom(Object[] args) {
      return (args != null && args.length > 0 && args[0] instanceof ObjectName on) ? on : null;
   }

   @Override
   public boolean canInvoke(String name, String operationName) {
      boolean okInvoke = false;
      try {
         final ObjectName objectName = ObjectName.getInstance(name);
         if (!isUncheckedDomain(objectName)) {
            if (operationName != null) {
               // Strip the parameter list from operationName.
               int paramListIndex = operationName.indexOf('(');
               if (paramListIndex > 0) {
                  operationName = operationName.substring(0, paramListIndex);
               }

               final SimpleString rbacAddress = addressFrom(objectName, operationName);
               securityStoreCheck(rbacAddress, permissionFrom(operationName));
            } else {
               // When operationName is null, canInvoke checks if the user has any access to the MBean.
               // We use VIEW permission rather than EDIT because hawtio-react's hasInvokeRights method
               // checks both canInvoke(mbean, null) AND canInvoke(mbean, method). This allows users with
               // VIEW permission to see MBeans and then determine which specific operations they can invoke.
               final SimpleString rbacAddress = addressFrom(objectName, null);
               securityStoreCheck(rbacAddress, CheckType.VIEW);
            }
         }
         okInvoke = true;
      } catch (Throwable expectedOnCheckFailOrInvalidObjectName) {
         // denied
      }

      return okInvoke;
   }

   private void initializeFromFirstServerMBeanRegistration(Method method, Object[] args) {
      if (activeMQServer == null && method.getName().equals("registerMBean")) {
         if (args != null && args[0] instanceof ActiveMQServerControlImpl serverControl) {
            activeMQServer = serverControl.getServer();
            brokerDomain = activeMQServer.getConfiguration().getJMXDomain();

            viewPermissionMatcher = Pattern.compile(activeMQServer.getConfiguration().getViewPermissionMethodMatchPattern());
            rbacPrefix = SimpleString.of(activeMQServer.getConfiguration().getManagementRbacPrefix());
            mBeanServerRbacAddressPrefix = rbacPrefix.concat(".mbeanserver.");

            serverControl.getServer().registerActivateCallback(new ActivateCallback() {
               @Override
               public void shutdown(ActiveMQServer server) {
                  try {
                     activeMQServer.getManagementService().unregisterHawtioSecurity();
                  } catch (Exception bestEffortToTidyOnShutdown) {
                  }
                  activeMQServer = null;
               }
            });
            try {
               activeMQServer.getManagementService().registerHawtioSecurity(this);
            } catch (Exception bestEffort) {
               bestEffort.printStackTrace();
            }
         }
      }
   }

   private void initAuditLoggerContext() {
      //if this is invoked via jolokia the address will be set by the filter
      //if not we can deduct it from RMI, or it must be internal
      if (AuditLogger.isAnyLoggingEnabled() && AuditLogger.getRemoteAddress() == null) {
         String url = "internal";
         final String name = Thread.currentThread().getName();
         if (name.startsWith("RMI TCP Connection")) {
            url = name.substring(name.indexOf('-') + 1);
         }
         AuditLogger.setRemoteAddress(url);
      }
   }

   // derive address to check from the method and args and then check relevant permission
   void securityCheck(Method method, Object[] args) {

      if (isUncheckedDomain(args)) {
         return;
      }

      try {

         final String methodName = method.getName();

         if ("getAttribute".equals(methodName)) {
            handleGetAttribute(delegate, (ObjectName) args[0], (String) args[1]);
         } else if ("getAttributes".equals(methodName)) {
            handleGetAttributes(delegate, (ObjectName) args[0], (String[]) args[1]);
         } else if ("setAttribute".equals(methodName)) {
            handleSetAttribute(delegate, (ObjectName) args[0], (Attribute) args[1]);
         } else if ("setAttributes".equals(methodName)) {
            handleSetAttributes(delegate, (ObjectName) args[0], (AttributeList) args[1]);
         } else if ("invoke".equals(methodName)) {
            handleInvoke((ObjectName) args[0], (String) args[1]);
         } else if (method.getName().startsWith("query")) {

            final SimpleString rbacAddress = mBeanServerRbacAddressPrefix.concat(methodName);
            securityStoreCheck(rbacAddress, permissionFrom(methodName));
         }

      } catch (Exception e) {
         throw new SecurityException(e.getMessage());
      }
   }

   private void handleSetAttributes(MBeanServer delegate, ObjectName objectName, AttributeList attributeList) throws Exception {
      for (Attribute attributeName : attributeList.asList()) {
         handleSetAttribute(delegate, objectName, attributeName);
      }
   }

   private void handleSetAttribute(MBeanServer delegate, ObjectName objectName, Attribute attributeName) throws Exception {
      handleInvoke(objectName, "set" + attributeName);
   }

   private void handleGetAttributes(MBeanServer delegate, ObjectName objectName, String[] attributes) throws Exception {
      for (String attribute : attributes) {
         handleGetAttribute(delegate, objectName, attribute);
      }
   }

   private void handleGetAttribute(MBeanServer delegate, ObjectName objectName, String attributeName) throws Exception {
      MBeanInfo info = delegate.getMBeanInfo(objectName);
      String prefix = "get";
      for (MBeanAttributeInfo attr : info.getAttributes()) {
         if (attr.getName().equals(attributeName)) {
            prefix = attr.isIs() ? "is" : "get";
            break;
         }
      }
      handleInvoke(objectName, prefix + attributeName);
   }

   private void handleInvoke(ObjectName objectName, String operationName) throws Exception {
      final SimpleString rbacAddress = addressFrom(objectName, operationName);
      final CheckType permission =  permissionFrom(operationName);
      securityStoreCheck(rbacAddress, permission);
   }

   CheckType permissionFrom(String methodName) {
      if (methodName != null && viewPermissionMatcher.matcher(methodName).matches()) {
         return CheckType.VIEW;
      }
      return CheckType.EDIT;
   }

   String removeQuotes(String key) {
      if (key != null && key.endsWith("\"")) {
         return key.replace("\"", "");
      }
      return key;
   }

   SimpleString addressFrom(ObjectName objectName) {
      return addressFrom(objectName, null);
   }

   // depends on ObjectNameBuilder impl that makes ObjectNames for control objects
   // jmx.<domain><.type>[.component][.name][.operation]
   SimpleString addressFrom(ObjectName objectName, String methodName) {

      String name = removeQuotes(objectName.getKeyProperty("name"));
      String component = removeQuotes(objectName.getKeyProperty("component"));
      String type = null;
      SimpleString rbacAddress = rbacPrefix;

      if (brokerDomain.equals(objectName.getDomain())) {
         if (component != null) {
            if ("addresses".equals(component)) {
               component = "address";

               final String subComponent = objectName.getKeyProperty("subcomponent");
               if ("diverts".equals(subComponent)) {
                  component = "divert";
               } else if ("queues".equals(subComponent)) {
                  component = "queue";
               }
               name = removeQuotes(objectName.getKeyProperty(component));
            }
         } else {
            // broker component, server control - identified by attribute with no component
            final String brokerName = removeQuotes(objectName.getKeyProperty("broker"));
            if (brokerName != null) {
               component = "broker";
            }
         }
      } else {
         // non artemis broker domain, prefix with domain
         rbacAddress = rbacAddress.concat('.').concat(objectName.getDomain());
         type = removeQuotes(objectName.getKeyProperty("type"));
      }

      if (type != null) {
         rbacAddress = rbacAddress.concat('.').concat(type);
      }
      if (component != null) {
         rbacAddress = rbacAddress.concat('.').concat(component);
      }
      if (name != null) {
         rbacAddress = rbacAddress.concat('.').concat(name);
      }
      if (methodName != null) {
         rbacAddress = rbacAddress.concat('.').concat(methodName);
      }

      return rbacAddress;
   }

   private boolean viewPermissionCheckFails(Object candidate) {
      boolean failed = false;
      ObjectName objectName = candidate instanceof ObjectInstance oi ? oi.getObjectName() : (ObjectName) candidate;
      if (!isUncheckedDomain(objectName)) {
         try {
            final SimpleString rbacAddress = addressFrom(objectName);
            securityStoreCheck(rbacAddress, CheckType.VIEW);
         } catch (Exception checkFailed) {
            failed = true;
         }
      }
      return failed;
   }

   private void securityStoreCheck(SimpleString rbacAddress, CheckType checkType) throws Exception {
      // use accessor as security store can be updated on config reload
      activeMQServer.getSecurityStore().check(rbacAddress, checkType, delegateToAccessController);
   }

   // sufficiently empty to delegate to use of AccessController
   // ideally AccessController should be the source of truth
   private final SecurityAuth delegateToAccessController = new SecurityAuth() {

      final ManagementRemotingConnection managementRemotingConnection = new ManagementRemotingConnection() {
         @Override
         public Subject getSubject() {
            return SecurityManagerShim.currentSubject();
         }
      };

      @Override
      public String getUsername() {
         return null;
      }

      @Override
      public String getPassword() {
         return null;
      }

      @Override
      public RemotingConnection getRemotingConnection() {
         return managementRemotingConnection;
      }

      @Override
      public String getSecurityDomain() {
         return null;
      }
   };
}
