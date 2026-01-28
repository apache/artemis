/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.logs;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.GetLogger;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;

import javax.management.ObjectName;
import javax.security.auth.Subject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Logger Codes 600000 - 609999
 */
@LogBundle(projectCode = "AMQ", regexID = "60[0-9]{4}", retiredIDs = {601268, 601503, 601504, 601505, 601506, 601507, 601508})
public interface AuditLogger {

   AuditLogger BASE_LOGGER = BundleFactory.newBundle(AuditLogger.class, "org.apache.activemq.audit.base");
   AuditLogger RESOURCE_LOGGER = BundleFactory.newBundle(AuditLogger.class, "org.apache.activemq.audit.resource");
   AuditLogger MESSAGE_LOGGER = BundleFactory.newBundle(AuditLogger.class, "org.apache.activemq.audit.message");

   ThreadLocal<String> remoteAddress = new ThreadLocal<>();

   ThreadLocal<Subject> currentCaller = new ThreadLocal<>();

   @GetLogger
   Logger getLogger();

   static boolean isAnyLoggingEnabled() {
      return isBaseLoggingEnabled() || isMessageLoggingEnabled() || isResourceLoggingEnabled();
   }

   static boolean isBaseLoggingEnabled() {
      return BASE_LOGGER.getLogger().isInfoEnabled();
   }

   static boolean isResourceLoggingEnabled() {
      return RESOURCE_LOGGER.getLogger().isInfoEnabled();
   }

   static boolean isMessageLoggingEnabled() {
      return MESSAGE_LOGGER.getLogger().isInfoEnabled();
   }

   /**
    * {@return a String representing the "caller" in the format "user(role)@remoteAddress" using ThreadLocal values (if
    * set)}
    */
   static String getCaller() {
      Subject subject = SecurityManagerShim.currentSubject();
      if (subject == null) {
         subject = currentCaller.get();
      }
      return getCaller(subject, null);
   }

   /**
    * {@return a {@code String} representing the "caller" in the format "user(role)@remoteAddress"}
    *
    * @param subject       the Subject to be used instead of the corresponding ThreadLocal Subject
    * @param remoteAddress the remote address to use; if null use the corresponding ThreadLocal remote address (if set)
    */
   static String getCaller(Subject subject, String remoteAddress) {
      String user = "anonymous";
      String roles = "";
      List<String> principalRoles = new ArrayList<>();
      String url = remoteAddress == null ? Objects.requireNonNullElse(AuditLogger.remoteAddress.get(), "@unknown") : formatRemoteAddress(remoteAddress);
      if (subject != null) {
         Set<Principal> principals = subject.getPrincipals();
         for (Principal principal : principals) {
            if (principal.getClass().getName().endsWith("UserPrincipal")) {
               user = principal.getName();
            } else if (principal.getClass().getName().endsWith("RolePrincipal")) {
               principalRoles.add(principal.getName());
            }
         }
      }
      if (!principalRoles.isEmpty()) {
         Collections.sort(principalRoles);
         roles = "(" + String.join(",", principalRoles) + ")";
      }
      return user + roles + url;
   }

   static void setCurrentCaller(Subject caller) {
      currentCaller.set(caller);
   }

   static void setRemoteAddress(String remoteAddress) {
      AuditLogger.remoteAddress.set(formatRemoteAddress(remoteAddress));
   }

   static String formatRemoteAddress(String remoteAddress) {
      String actualAddress;
      if (remoteAddress.startsWith("/")) {
         actualAddress = "@" + remoteAddress.substring(1);
      } else {
         actualAddress = "@" + remoteAddress;
      }
      return actualAddress;
   }

   static String getRemoteAddress() {
      return remoteAddress.get();
   }

   static String parametersList(Object value) {
      if (value == null) return "";

      final String prefix = "with parameters: ";

      if (value instanceof long[] longs) {
         return prefix + Arrays.toString(longs);
      } else if (value instanceof int[] ints) {
         return prefix + Arrays.toString(ints);
      } else if (value instanceof char[] chars) {
         return prefix + Arrays.toString(chars);
      } else if (value instanceof byte[] bytes) {
         return prefix + Arrays.toString(bytes);
      } else if (value instanceof float[] floats) {
         return prefix + Arrays.toString(floats);
      } else if (value instanceof short[] shorts) {
         return prefix + Arrays.toString(shorts);
      } else if (value instanceof double[] doubles) {
         return prefix + Arrays.toString(doubles);
      } else if (value instanceof boolean[] booleans) {
         return prefix + Arrays.toString(booleans);
      } else if (value instanceof Object[] objects) {
         return prefix + Arrays.toString(objects);
      } else {
         return prefix + value.toString();
      }
   }

   static void getRoutingTypes(Object source) {
      BASE_LOGGER.getRoutingTypes(getCaller(), source);
   }

   @LogMessage(id = 601000, value = "User {} is getting routing type property on target resource: {}", level = LogMessage.Level.INFO)
   void getRoutingTypes(String user, Object source);

   static void getRoutingTypesAsJSON(Object source) {
      BASE_LOGGER.getRoutingTypesAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601001, value = "User {} is getting routing type property as json on target resource: {}", level = LogMessage.Level.INFO)
   void getRoutingTypesAsJSON(String user, Object source);

   static void getQueueNames(Object source, Object... args) {
      BASE_LOGGER.getQueueNames(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601002, value = "User {} is getting queue names on target resource: {} {}", level = LogMessage.Level.INFO)
   void getQueueNames(String user, Object source, String parameters);

   static void getBindingNames(Object source) {
      BASE_LOGGER.getBindingNames(getCaller(), source);
   }

   @LogMessage(id = 601003, value = "User {} is getting binding names on target resource: {}", level = LogMessage.Level.INFO)
   void getBindingNames(String user, Object source);

   static void getRoles(Object source, Object... args) {
      BASE_LOGGER.getRoles(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601004, value = "User {} is getting roles on target resource: {} {}", level = LogMessage.Level.INFO)
   void getRoles(String user, Object source, String parameters);

   static void getRolesAsJSON(Object source, Object... args) {
      BASE_LOGGER.getRolesAsJSON(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601005, value = "User {} is getting roles as json on target resource: {} {}", level = LogMessage.Level.INFO)
   void getRolesAsJSON(String user, Object source, String parameters);

   static void getNumberOfBytesPerPage(Object source) {
      BASE_LOGGER.getNumberOfBytesPerPage(getCaller(), source);
   }

   @LogMessage(id = 601006, value = "User {} is getting number of bytes per page on target resource: {}", level = LogMessage.Level.INFO)
   void getNumberOfBytesPerPage(String user, Object source);

   static void getAddressSize(Object source) {
      BASE_LOGGER.getAddressSize(getCaller(), source);
   }

   @LogMessage(id = 601007, value = "User {} is getting address size on target resource: {}", level = LogMessage.Level.INFO)
   void getAddressSize(String user, Object source);

   static void getNumberOfMessages(Object source) {
      BASE_LOGGER.getNumberOfMessages(getCaller(), source);
   }

   @LogMessage(id = 601008, value = "User {} is getting number of messages on target resource: {}", level = LogMessage.Level.INFO)
   void getNumberOfMessages(String user, Object source);

   static void isPaging(Object source) {
      BASE_LOGGER.isPaging(getCaller(), source);
   }

   @LogMessage(id = 601009, value = "User {} is getting isPaging on target resource: {}", level = LogMessage.Level.INFO)
   void isPaging(String user, Object source);

   static void getNumberOfPages(Object source) {
      BASE_LOGGER.getNumberOfPages(getCaller(), source);
   }

   @LogMessage(id = 601010, value = "User {} is getting number of pages on target resource: {}", level = LogMessage.Level.INFO)
   void getNumberOfPages(String user, Object source);

   static void getRoutedMessageCount(Object source) {
      BASE_LOGGER.getRoutedMessageCount(getCaller(), source);
   }

   @LogMessage(id = 601011, value = "User {} is getting routed message count on target resource: {}", level = LogMessage.Level.INFO)
   void getRoutedMessageCount(String user, Object source);

   static void getUnRoutedMessageCount(Object source) {
      BASE_LOGGER.getUnRoutedMessageCount(getCaller(), source);
   }

   @LogMessage(id = 601012, value = "User {} is getting unrouted message count on target resource: {}", level = LogMessage.Level.INFO)
   void getUnRoutedMessageCount(String user, Object source);

   static void sendMessageThroughManagement(Object source, Object... args) {
      BASE_LOGGER.sendMessageThroughManagement(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601013, value = "User {} is sending a message on target resource: {} {}", level = LogMessage.Level.INFO)
   void sendMessageThroughManagement(String user, Object source, String parameters);

   static void getName(Object source) {
      BASE_LOGGER.getName(getCaller(), source);
   }

   @LogMessage(id = 601014, value = "User {} is getting name on target resource: {}", level = LogMessage.Level.INFO)
   void getName(String user, Object source);

   static void getAddress(Object source) {
      BASE_LOGGER.getAddress(getCaller(), source);
   }

   @LogMessage(id = 601015, value = "User {} is getting address on target resource: {}", level = LogMessage.Level.INFO)
   void getAddress(String user, Object source);

   static void getFilter(Object source) {
      BASE_LOGGER.getFilter(getCaller(), source);
   }

   @LogMessage(id = 601016, value = "User {} is getting filter on target resource: {}", level = LogMessage.Level.INFO)
   void getFilter(String user, Object source);

   static void isDurable(Object source) {
      BASE_LOGGER.isDurable(getCaller(), source);
   }

   @LogMessage(id = 601017, value = "User {} is getting durable property on target resource: {}", level = LogMessage.Level.INFO)
   void isDurable(String user, Object source);

   static void getMessageCount(Object source) {
      BASE_LOGGER.getMessageCount(getCaller(), source);
   }

   @LogMessage(id = 601018, value = "User {} is getting message count on target resource: {}", level = LogMessage.Level.INFO)
   void getMessageCount(String user, Object source);

   static void getMBeanInfo(Object source) {
      BASE_LOGGER.getMBeanInfo(getCaller(), source);
   }

   @LogMessage(id = 601019, value = "User {} is getting mbean info on target resource: {}", level = LogMessage.Level.INFO)
   void getMBeanInfo(String user, Object source);

   static void getFactoryClassName(Object source) {
      BASE_LOGGER.getFactoryClassName(getCaller(), source);
   }

   @LogMessage(id = 601020, value = "User {} is getting factory class name on target resource: {}", level = LogMessage.Level.INFO)
   void getFactoryClassName(String user, Object source);

   static void getParameters(Object source) {
      BASE_LOGGER.getParameters(getCaller(), source);
   }

   @LogMessage(id = 601021, value = "User {} is getting parameters on target resource: {}", level = LogMessage.Level.INFO)
   void getParameters(String user, Object source);

   static void reload(Object source) {
      BASE_LOGGER.reload(getCaller(), source);
   }

   @LogMessage(id = 601022, value = "User {} is doing reload on target resource: {}", level = LogMessage.Level.INFO)
   void reload(String user, Object source);

   static void isStarted(Object source) {
      BASE_LOGGER.isStarted(getCaller(), source);
   }

   @LogMessage(id = 601023, value = "User {} is querying isStarted on target resource: {}", level = LogMessage.Level.INFO)
   void isStarted(String user, Object source);

   static void startAcceptor(Object source) {
      BASE_LOGGER.startAcceptor(getCaller(), source);
   }

   @LogMessage(id = 601024, value = "User {} is starting an acceptor on target resource: {}", level = LogMessage.Level.INFO)
   void startAcceptor(String user, Object source);

   static void stopAcceptor(Object source) {
      BASE_LOGGER.stopAcceptor(getCaller(), source);
   }

   @LogMessage(id = 601025, value = "User {} is stopping an acceptor on target resource: {}", level = LogMessage.Level.INFO)
   void stopAcceptor(String user, Object source);

   static void getVersion(Object source) {
      BASE_LOGGER.getVersion(getCaller(), source);
   }

   @LogMessage(id = 601026, value = "User {} is getting version on target resource: {}", level = LogMessage.Level.INFO)
   void getVersion(String user, Object source);

   static void isBackup(Object source) {
      BASE_LOGGER.isBackup(getCaller(), source);
   }

   @LogMessage(id = 601027, value = "User {} is querying isBackup on target resource: {}", level = LogMessage.Level.INFO)
   void isBackup(String user, Object source);

   static void isSharedStore(Object source) {
      BASE_LOGGER.isSharedStore(getCaller(), source);
   }

   @LogMessage(id = 601028, value = "User {} is querying isSharedStore on target resource: {}", level = LogMessage.Level.INFO)
   void isSharedStore(String user, Object source);

   static void getBindingsDirectory(Object source) {
      BASE_LOGGER.getBindingsDirectory(getCaller(), source);
   }

   @LogMessage(id = 601029, value = "User {} is getting bindings directory on target resource: {}", level = LogMessage.Level.INFO)
   void getBindingsDirectory(String user, Object source);

   static void getIncomingInterceptorClassNames(Object source) {
      BASE_LOGGER.getIncomingInterceptorClassNames(getCaller(), source);
   }

   @LogMessage(id = 601030, value = "User {} is getting incoming interceptor class names on target resource: {}", level = LogMessage.Level.INFO)
   void getIncomingInterceptorClassNames(String user, Object source);

   static void getOutgoingInterceptorClassNames(Object source) {
      BASE_LOGGER.getOutgoingInterceptorClassNames(getCaller(), source);
   }

   @LogMessage(id = 601031, value = "User {} is getting outgoing interceptor class names on target resource: {}", level = LogMessage.Level.INFO)
   void getOutgoingInterceptorClassNames(String user, Object source);

   static void getJournalBufferSize(Object source) {
      BASE_LOGGER.getJournalBufferSize(getCaller(), source);
   }

   @LogMessage(id = 601032, value = "User {} is getting journal buffer size on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalBufferSize(String user, Object source);

   static void getJournalBufferTimeout(Object source) {
      BASE_LOGGER.getJournalBufferTimeout(getCaller(), source);
   }

   @LogMessage(id = 601033, value = "User {} is getting journal buffer timeout on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalBufferTimeout(String user, Object source);

   static void setFailoverOnServerShutdown(Object source, Object... args) {
      BASE_LOGGER.setFailoverOnServerShutdown(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601034, value = "User {} is setting failover on server shutdown on target resource: {} {}", level = LogMessage.Level.INFO)
   void setFailoverOnServerShutdown(String user, Object source, String parameters);

   static void isFailoverOnServerShutdown(Object source) {
      BASE_LOGGER.isFailoverOnServerShutdown(getCaller(), source);
   }

   @LogMessage(id = 601035, value = "User {} is querying is-failover-on-server-shutdown on target resource: {}", level = LogMessage.Level.INFO)
   void isFailoverOnServerShutdown(String user, Object source);

   static void getJournalMaxIO(Object source) {
      BASE_LOGGER.getJournalMaxIO(getCaller(), source);
   }

   @LogMessage(id = 601036, value = "User {} is getting journal's max io on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalMaxIO(String user, Object source);

   static void getJournalDirectory(Object source) {
      BASE_LOGGER.getJournalDirectory(getCaller(), source);
   }

   @LogMessage(id = 601037, value = "User {} is getting journal directory on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalDirectory(String user, Object source);

   static void getJournalFileSize(Object source) {
      BASE_LOGGER.getJournalFileSize(getCaller(), source);
   }

   @LogMessage(id = 601038, value = "User {} is getting journal file size on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalFileSize(String user, Object source);

   static void getJournalMinFiles(Object source) {
      BASE_LOGGER.getJournalMinFiles(getCaller(), source);
   }

   @LogMessage(id = 601039, value = "User {} is getting journal min files on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalMinFiles(String user, Object source);

   static void getJournalCompactMinFiles(Object source) {
      BASE_LOGGER.getJournalCompactMinFiles(getCaller(), source);
   }

   @LogMessage(id = 601040, value = "User {} is getting journal compact min files on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalCompactMinFiles(String user, Object source);

   static void getJournalCompactPercentage(Object source) {
      BASE_LOGGER.getJournalCompactPercentage(getCaller(), source);
   }

   @LogMessage(id = 601041, value = "User {} is getting journal compact percentage on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalCompactPercentage(String user, Object source);

   static void isPersistenceEnabled(Object source) {
      BASE_LOGGER.isPersistenceEnabled(getCaller(), source);
   }

   @LogMessage(id = 601042, value = "User {} is querying persistence enabled on target resource: {}", level = LogMessage.Level.INFO)
   void isPersistenceEnabled(String user, Object source);

   static void getJournalType(Object source) {
      BASE_LOGGER.getJournalType(getCaller(), source);
   }

   @LogMessage(id = 601043, value = "User {} is getting journal type on target resource: {}", level = LogMessage.Level.INFO)
   void getJournalType(String user, Object source);

   static void getPagingDirectory(Object source) {
      BASE_LOGGER.getPagingDirectory(getCaller(), source);
   }

   @LogMessage(id = 601044, value = "User {} is getting paging directory on target resource: {}", level = LogMessage.Level.INFO)
   void getPagingDirectory(String user, Object source);

   static void getScheduledThreadPoolMaxSize(Object source) {
      BASE_LOGGER.getScheduledThreadPoolMaxSize(getCaller(), source);
   }

   @LogMessage(id = 601045, value = "User {} is getting scheduled threadpool max size on target resource: {}", level = LogMessage.Level.INFO)
   void getScheduledThreadPoolMaxSize(String user, Object source);

   static void getThreadPoolMaxSize(Object source) {
      BASE_LOGGER.getThreadPoolMaxSize(getCaller(), source);
   }

   @LogMessage(id = 601046, value = "User {} is getting threadpool max size on target resource: {}", level = LogMessage.Level.INFO)
   void getThreadPoolMaxSize(String user, Object source);

   static void getSecurityInvalidationInterval(Object source) {
      BASE_LOGGER.getSecurityInvalidationInterval(getCaller(), source);
   }

   @LogMessage(id = 601047, value = "User {} is getting security invalidation interval on target resource: {}", level = LogMessage.Level.INFO)
   void getSecurityInvalidationInterval(String user, Object source);

   static void isClustered(Object source) {
      BASE_LOGGER.isClustered(getCaller(), source);
   }

   @LogMessage(id = 601048, value = "User {} is querying is-clustered on target resource: {}", level = LogMessage.Level.INFO)
   void isClustered(String user, Object source);

   static void isCreateBindingsDir(Object source) {
      BASE_LOGGER.isCreateBindingsDir(getCaller(), source);
   }

   @LogMessage(id = 601049, value = "User {} is querying is-create-bindings-dir on target resource: {}", level = LogMessage.Level.INFO)
   void isCreateBindingsDir(String user, Object source);

   static void isCreateJournalDir(Object source) {
      BASE_LOGGER.isCreateJournalDir(getCaller(), source);
   }

   @LogMessage(id = 601050, value = "User {} is querying is-create-journal-dir on target resource: {}", level = LogMessage.Level.INFO)
   void isCreateJournalDir(String user, Object source);

   static void isJournalSyncNonTransactional(Object source) {
      BASE_LOGGER.isJournalSyncNonTransactional(getCaller(), source);
   }

   @LogMessage(id = 601051, value = "User {} is querying is-journal-sync-non-transactional on target resource: {}", level = LogMessage.Level.INFO)
   void isJournalSyncNonTransactional(String user, Object source);

   static void isJournalSyncTransactional(Object source) {
      BASE_LOGGER.isJournalSyncTransactional(getCaller(), source);
   }

   @LogMessage(id = 601052, value = "User {} is querying is-journal-sync-transactional on target resource: {}", level = LogMessage.Level.INFO)
   void isJournalSyncTransactional(String user, Object source);

   static void isSecurityEnabled(Object source) {
      BASE_LOGGER.isSecurityEnabled(getCaller(), source);
   }

   @LogMessage(id = 601053, value = "User {} is querying is-security-enabled on target resource: {}", level = LogMessage.Level.INFO)
   void isSecurityEnabled(String user, Object source);

   static void isAsyncConnectionExecutionEnabled(Object source) {
      BASE_LOGGER.isAsyncConnectionExecutionEnabled(getCaller(), source);
   }

   @LogMessage(id = 601054, value = "User {} is querying is-async-connection-execution-enabled on target resource: {}", level = LogMessage.Level.INFO)
   void isAsyncConnectionExecutionEnabled(String user, Object source);

   static void getDiskScanPeriod(Object source) {
      BASE_LOGGER.getDiskScanPeriod(getCaller(), source);
   }

   @LogMessage(id = 601055, value = "User {} is getting disk scan period on target resource: {}", level = LogMessage.Level.INFO)
   void getDiskScanPeriod(String user, Object source);

   static void getMaxDiskUsage(Object source) {
      BASE_LOGGER.getMaxDiskUsage(getCaller(), source);
   }

   @LogMessage(id = 601056, value = "User {} is getting max disk usage on target resource: {}", level = LogMessage.Level.INFO)
   void getMaxDiskUsage(String user, Object source);

   static void getGlobalMaxSize(Object source) {
      BASE_LOGGER.getGlobalMaxSize(getCaller(), source);
   }

   @LogMessage(id = 601057, value = "User {} is getting global max size on target resource: {}", level = LogMessage.Level.INFO)
   void getGlobalMaxSize(String user, Object source);

   static void getAddressMemoryUsage(Object source) {
      BASE_LOGGER.getAddressMemoryUsage(getCaller(), source);
   }

   @LogMessage(id = 601058, value = "User {} is getting address memory usage on target resource: {}", level = LogMessage.Level.INFO)
   void getAddressMemoryUsage(String user, Object source);

   static void getAddressMemoryUsagePercentage(Object source) {
      BASE_LOGGER.getAddressMemoryUsagePercentage(getCaller(), source);
   }

   @LogMessage(id = 601059, value = "User {} is getting address memory usage percentage on target resource: {}", level = LogMessage.Level.INFO)
   void getAddressMemoryUsagePercentage(String user, Object source);

   static void freezeReplication(Object source) {
      BASE_LOGGER.freezeReplication(getCaller(), source);
   }

   @LogMessage(id = 601060, value = "User {} is freezing replication on target resource: {}", level = LogMessage.Level.INFO)
   void freezeReplication(String user, Object source);

   static void createAddress(Object source, Object... args) {
      BASE_LOGGER.createAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601061, value = "User {} is creating an address on target resource: {} {}", level = LogMessage.Level.INFO)
   void createAddress(String user, Object source, String args);

   static void updateAddress(Object source, Object... args) {
      BASE_LOGGER.updateAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601062, value = "User {} is updating an address on target resource: {} {}", level = LogMessage.Level.INFO)
   void updateAddress(String user, Object source, String args);

   static void deleteAddress(Object source, Object... args) {
      BASE_LOGGER.deleteAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601063, value = "User {} is deleting an address on target resource: {} {}", level = LogMessage.Level.INFO)
   void deleteAddress(String user, Object source, String args);

   static void deployQueue(Object source, Object... args) {
      BASE_LOGGER.deployQueue(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601064, value = "User {} is creating a queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void deployQueue(String user, Object source, String args);

   static void createQueue(Object source, Subject user, String remoteAddress, Object... args) {
      RESOURCE_LOGGER.createQueue(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601065, value = "User {} is creating a queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void createQueue(String user, Object source, String args);

   static void updateQueue(Object source, Object... args) {
      BASE_LOGGER.updateQueue(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601066, value = "User {} is updating a queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void updateQueue(String user, Object source, String args);

   static void getClusterConnectionNames(Object source) {
      BASE_LOGGER.getClusterConnectionNames(getCaller(), source);
   }

   @LogMessage(id = 601067, value = "User {} is getting cluster connection names on target resource: {}", level = LogMessage.Level.INFO)
   void getClusterConnectionNames(String user, Object source);

   static void getUptime(Object source) {
      BASE_LOGGER.getUptime(getCaller(), source);
   }

   @LogMessage(id = 601068, value = "User {} is getting uptime on target resource: {}", level = LogMessage.Level.INFO)
   void getUptime(String user, Object source);

   static void getUptimeMillis(Object source) {
      BASE_LOGGER.getUptimeMillis(getCaller(), source);
   }

   @LogMessage(id = 601069, value = "User {} is getting uptime in milliseconds on target resource: {}", level = LogMessage.Level.INFO)
   void getUptimeMillis(String user, Object source);

   static void isReplicaSync(Object source) {
      BASE_LOGGER.isReplicaSync(getCaller(), source);
   }

   @LogMessage(id = 601070, value = "User {} is querying is-replica-sync on target resource: {}", level = LogMessage.Level.INFO)
   void isReplicaSync(String user, Object source);

   static void getAddressNames(Object source) {
      BASE_LOGGER.getAddressNames(getCaller(), source);
   }

   @LogMessage(id = 601071, value = "User {} is getting address names on target resource: {}", level = LogMessage.Level.INFO)
   void getAddressNames(String user, Object source);

   static void destroyQueue(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.destroyQueue(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601072, value = "User {} is deleting a queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void destroyQueue(String user, Object source, String args);

   static void getAddressInfo(Object source, Object... args) {
      BASE_LOGGER.getAddressInfo(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601073, value = "User {} is getting address info on target resource: {} {}", level = LogMessage.Level.INFO)
   void getAddressInfo(String user, Object source, String args);

   static void listBindingsForAddress(Object source, Object... args) {
      BASE_LOGGER.listBindingsForAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601074, value = "User {} is listing bindings for address on target resource: {} {}", level = LogMessage.Level.INFO)
   void listBindingsForAddress(String user, Object source, String args);

   static void listAddresses(Object source, Object... args) {
      BASE_LOGGER.listAddresses(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601075, value = "User {} is listing addresses on target resource: {} {}", level = LogMessage.Level.INFO)
   void listAddresses(String user, Object source, String args);

   static void getConnectionCount(Object source, Object... args) {
      BASE_LOGGER.getConnectionCount(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601076, value = "User {} is getting connection count on target resource: {} {}", level = LogMessage.Level.INFO)
   void getConnectionCount(String user, Object source, String args);

   static void getTotalConnectionCount(Object source) {
      BASE_LOGGER.getTotalConnectionCount(getCaller(), source);
   }

   @LogMessage(id = 601077, value = "User {} is getting total connection count on target resource: {}", level = LogMessage.Level.INFO)
   void getTotalConnectionCount(String user, Object source);

   static void getTotalMessageCount(Object source) {
      BASE_LOGGER.getTotalMessageCount(getCaller(), source);
   }

   @LogMessage(id = 601078, value = "User {} is getting total message count on target resource: {}", level = LogMessage.Level.INFO)
   void getTotalMessageCount(String user, Object source);

   static void getTotalMessagesAdded(Object source) {
      BASE_LOGGER.getTotalMessagesAdded(getCaller(), source);
   }

   @LogMessage(id = 601079, value = "User {} is getting total messages added on target resource: {}", level = LogMessage.Level.INFO)
   void getTotalMessagesAdded(String user, Object source);

   static void getTotalMessagesAcknowledged(Object source) {
      BASE_LOGGER.getTotalMessagesAcknowledged(getCaller(), source);
   }

   @LogMessage(id = 601080, value = "User {} is getting total messages acknowledged on target resource: {}", level = LogMessage.Level.INFO)
   void getTotalMessagesAcknowledged(String user, Object source);

   static void getTotalConsumerCount(Object source) {
      BASE_LOGGER.getTotalConsumerCount(getCaller(), source);
   }

   @LogMessage(id = 601081, value = "User {} is getting total consumer count on target resource: {}", level = LogMessage.Level.INFO)
   void getTotalConsumerCount(String user, Object source);

   static void enableMessageCounters(Object source) {
      BASE_LOGGER.enableMessageCounters(getCaller(), source);
   }

   @LogMessage(id = 601082, value = "User {} is enabling message counters on target resource: {}", level = LogMessage.Level.INFO)
   void enableMessageCounters(String user, Object source);

   static void disableMessageCounters(Object source) {
      BASE_LOGGER.disableMessageCounters(getCaller(), source);
   }

   @LogMessage(id = 601083, value = "User {} is disabling message counters on target resource: {}", level = LogMessage.Level.INFO)
   void disableMessageCounters(String user, Object source);

   static void resetAllMessageCounters(Object source) {
      BASE_LOGGER.resetAllMessageCounters(getCaller(), source);
   }

   @LogMessage(id = 601084, value = "User {} is resetting all message counters on target resource: {}", level = LogMessage.Level.INFO)
   void resetAllMessageCounters(String user, Object source);

   static void resetAllMessageCounterHistories(Object source) {
      BASE_LOGGER.resetAllMessageCounterHistories(getCaller(), source);
   }

   @LogMessage(id = 601085, value = "User {} is resetting all message counter histories on target resource: {}", level = LogMessage.Level.INFO)
   void resetAllMessageCounterHistories(String user, Object source);

   static void isMessageCounterEnabled(Object source) {
      BASE_LOGGER.isMessageCounterEnabled(getCaller(), source);
   }

   @LogMessage(id = 601086, value = "User {} is querying is-message-counter-enabled on target resource: {}", level = LogMessage.Level.INFO)
   void isMessageCounterEnabled(String user, Object source);

   static void getMessageCounterSamplePeriod(Object source) {
      BASE_LOGGER.getMessageCounterSamplePeriod(getCaller(), source);
   }

   @LogMessage(id = 601087, value = "User {} is getting message counter sample period on target resource: {}", level = LogMessage.Level.INFO)
   void getMessageCounterSamplePeriod(String user, Object source);

   static void setMessageCounterSamplePeriod(Object source, Object... args) {
      BASE_LOGGER.setMessageCounterSamplePeriod(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601088, value = "User {} is setting message counter sample period on target resource: {} {}", level = LogMessage.Level.INFO)
   void setMessageCounterSamplePeriod(String user, Object source, String args);

   static void getMessageCounterMaxDayCount(Object source) {
      BASE_LOGGER.getMessageCounterMaxDayCount(getCaller(), source);
   }

   @LogMessage(id = 601089, value = "User {} is getting message counter max day count on target resource: {}", level = LogMessage.Level.INFO)
   void getMessageCounterMaxDayCount(String user, Object source);

   static void setMessageCounterMaxDayCount(Object source, Object... args) {
      BASE_LOGGER.setMessageCounterMaxDayCount(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601090, value = "User {} is setting message counter max day count on target resource: {} {}", level = LogMessage.Level.INFO)
   void setMessageCounterMaxDayCount(String user, Object source, String args);

   static void listPreparedTransactions(Object source) {
      BASE_LOGGER.listPreparedTransactions(getCaller(), source);
   }

   @LogMessage(id = 601091, value = "User {} is listing prepared transactions on target resource: {}", level = LogMessage.Level.INFO)
   void listPreparedTransactions(String user, Object source);

   static void listPreparedTransactionDetailsAsJSON(Object source) {
      BASE_LOGGER.listPreparedTransactionDetailsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601092, value = "User {} is listing prepared transaction details as json on target resource: {}", level = LogMessage.Level.INFO)
   void listPreparedTransactionDetailsAsJSON(String user, Object source);

   static void listPreparedTransactionDetailsAsHTML(Object source, Object... args) {
      BASE_LOGGER.listPreparedTransactionDetailsAsHTML(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601093, value = "User {} is listing prepared transaction details as HTML on target resource: {} {}", level = LogMessage.Level.INFO)
   void listPreparedTransactionDetailsAsHTML(String user, Object source, String args);

   static void listHeuristicCommittedTransactions(Object source) {
      BASE_LOGGER.listHeuristicCommittedTransactions(getCaller(), source);
   }

   @LogMessage(id = 601094, value = "User {} is listing heuristic committed transactions on target resource: {}", level = LogMessage.Level.INFO)
   void listHeuristicCommittedTransactions(String user, Object source);

   static void listHeuristicRolledBackTransactions(Object source) {
      BASE_LOGGER.listHeuristicRolledBackTransactions(getCaller(), source);
   }

   @LogMessage(id = 601095, value = "User {} is listing heuristic rolled back transactions on target resource: {}", level = LogMessage.Level.INFO)
   void listHeuristicRolledBackTransactions(String user, Object source);

   static void commitPreparedTransaction(Object source, Object... args) {
      BASE_LOGGER.commitPreparedTransaction(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601096, value = "User {} is commiting prepared transaction on target resource: {} {}", level = LogMessage.Level.INFO)
   void commitPreparedTransaction(String user, Object source, String args);

   static void rollbackPreparedTransaction(Object source, Object... args) {
      BASE_LOGGER.rollbackPreparedTransaction(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601097, value = "User {} is rolling back prepared transaction on target resource: {} {}", level = LogMessage.Level.INFO)
   void rollbackPreparedTransaction(String user, Object source, String args);

   static void listRemoteAddresses(Object source, Object... args) {
      BASE_LOGGER.listRemoteAddresses(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601098, value = "User {} is listing remote addresses on target resource: {} {}", level = LogMessage.Level.INFO)
   void listRemoteAddresses(String user, Object source, String args);

   static void closeConnectionsForAddress(Object source, Object... args) {
      BASE_LOGGER.closeConnectionsForAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601099, value = "User {} is closing connections for address on target resource: {} {}", level = LogMessage.Level.INFO)
   void closeConnectionsForAddress(String user, Object source, String args);

   static void closeConsumerConnectionsForAddress(Object source, Object... args) {
      BASE_LOGGER.closeConsumerConnectionsForAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601100, value = "User {} is closing consumer connections for address on target resource: {} {}", level = LogMessage.Level.INFO)
   void closeConsumerConnectionsForAddress(String user, Object source, String args);

   static void closeConnectionsForUser(Object source, Object... args) {
      BASE_LOGGER.closeConnectionsForUser(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601101, value = "User {} is closing connections for user on target resource: {} {}", level = LogMessage.Level.INFO)
   void closeConnectionsForUser(String user, Object source, String args);

   static void closeConnectionWithID(Object source, Object... args) {
      BASE_LOGGER.closeConnectionWithID(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601102, value = "User {} is closing a connection by ID on target resource: {} {}", level = LogMessage.Level.INFO)
   void closeConnectionWithID(String user, Object source, String args);

   static void closeSessionWithID(Object source, Object... args) {
      BASE_LOGGER.closeSessionWithID(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601103, value = "User {} is closing session with id on target resource: {} {}", level = LogMessage.Level.INFO)
   void closeSessionWithID(String user, Object source, String args);

   static void closeConsumerWithID(Object source, Object... args) {
      BASE_LOGGER.closeConsumerWithID(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601104, value = "User {} is closing consumer with id on target resource: {} {}", level = LogMessage.Level.INFO)
   void closeConsumerWithID(String user, Object source, String args);

   static void listConnectionIDs(Object source) {
      BASE_LOGGER.listConnectionIDs(getCaller(), source);
   }

   @LogMessage(id = 601105, value = "User {} is listing connection IDs on target resource: {}", level = LogMessage.Level.INFO)
   void listConnectionIDs(String user, Object source);

   static void listSessions(Object source, Object... args) {
      BASE_LOGGER.listSessions(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601106, value = "User {} is listing sessions on target resource: {} {}", level = LogMessage.Level.INFO)
   void listSessions(String user, Object source, String args);

   static void listProducersInfoAsJSON(Object source) {
      BASE_LOGGER.listProducersInfoAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601107, value = "User {} is listing producers info as json on target resource: {}", level = LogMessage.Level.INFO)
   void listProducersInfoAsJSON(String user, Object source);

   static void listConnections(Object source, Object... args) {
      BASE_LOGGER.listConnections(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601108, value = "User {} is listing connections on target resource: {} {}", level = LogMessage.Level.INFO)
   void listConnections(String user, Object source, String args);

   static void listConsumers(Object source, Object... args) {
      BASE_LOGGER.listConsumers(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601109, value = "User {} is listing consumers on target resource: {} {}", level = LogMessage.Level.INFO)
   void listConsumers(String user, Object source, String args);

   static void listQueues(Object source, Object... args) {
      BASE_LOGGER.listQueues(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601110, value = "User {} is listing queues on target resource: {} {}", level = LogMessage.Level.INFO)
   void listQueues(String user, Object source, String arg);

   static void listProducers(Object source, Object... args) {
      BASE_LOGGER.listProducers(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601111, value = "User {} is listing producers on target resource: {} {}", level = LogMessage.Level.INFO)
   void listProducers(String user, Object source, String args);

   static void listConnectionsAsJSON(Object source) {
      BASE_LOGGER.listConnectionsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601112, value = "User {} is listing connections as json on target resource: {}", level = LogMessage.Level.INFO)
   void listConnectionsAsJSON(String user, Object source);

   static void listSessionsAsJSON(Object source, Object... args) {
      BASE_LOGGER.listSessionsAsJSON(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601113, value = "User {} is listing sessions as json on target resource: {} {}", level = LogMessage.Level.INFO)
   void listSessionsAsJSON(String user, Object source, String args);

   static void listAllSessionsAsJSON(Object source) {
      BASE_LOGGER.listAllSessionsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601114, value = "User {} is listing all sessions as json on target resource: {}", level = LogMessage.Level.INFO)
   void listAllSessionsAsJSON(String user, Object source);

   static void listConsumersAsJSON(Object source, Object... args) {
      BASE_LOGGER.listConsumersAsJSON(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601115, value = "User {} is listing consumers as json on target resource: {} {}", level = LogMessage.Level.INFO)
   void listConsumersAsJSON(String user, Object source, String args);

   static void listAllConsumersAsJSON(Object source) {
      BASE_LOGGER.listAllConsumersAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601116, value = "User {} is listing all consumers as json on target resource: {}", level = LogMessage.Level.INFO)
   void listAllConsumersAsJSON(String user, Object source);

   static void getConnectors(Object source) {
      BASE_LOGGER.getConnectors(getCaller(), source);
   }

   @LogMessage(id = 601117, value = "User {} is getting connectors on target resource: {}", level = LogMessage.Level.INFO)
   void getConnectors(String user, Object source);

   static void getConnectorsAsJSON(Object source) {
      BASE_LOGGER.getConnectorsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601118, value = "User {} is getting connectors as json on target resource: {}", level = LogMessage.Level.INFO)
   void getConnectorsAsJSON(String user, Object source);

   static void addSecuritySettings(Object source, Object... args) {
      BASE_LOGGER.addSecuritySettings(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601119, value = "User {} is adding security settings on target resource: {} {}", level = LogMessage.Level.INFO)
   void addSecuritySettings(String user, Object source, String args);

   static void removeSecuritySettings(Object source, Object... args) {
      BASE_LOGGER.removeSecuritySettings(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601120, value = "User {} is removing security settings on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeSecuritySettings(String user, Object source, String args);

   static void getAddressSettingsAsJSON(Object source, Object... args) {
      BASE_LOGGER.getAddressSettingsAsJSON(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601121, value = "User {} is getting address settings as json on target resource: {} {}", level = LogMessage.Level.INFO)
   void getAddressSettingsAsJSON(String user, Object source, String args);

   static void addAddressSettings(Object source, Object... args) {
      BASE_LOGGER.addAddressSettings(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601122, value = "User {} is adding addressSettings on target resource: {} {}", level = LogMessage.Level.INFO)
   void addAddressSettings(String user, Object source, String args);

   static void removeAddressSettings(Object source, Object... args) {
      BASE_LOGGER.removeAddressSettings(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601123, value = "User {} is removing address settings on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeAddressSettings(String user, Object source, String args);

   static void getDivertNames(Object source) {
      BASE_LOGGER.getDivertNames(getCaller(), source);
   }

   @LogMessage(id = 601124, value = "User {} is getting divert names on target resource: {}", level = LogMessage.Level.INFO)
   void getDivertNames(String user, Object source);

   static void createDivert(Object source, Object... args) {
      BASE_LOGGER.createDivert(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601125, value = "User {} is creating a divert on target resource: {} {}", level = LogMessage.Level.INFO)
   void createDivert(String user, Object source, String args);

   static void destroyDivert(Object source, Object... args) {
      BASE_LOGGER.destroyDivert(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601126, value = "User {} is destroying a divert on target resource: {} {}", level = LogMessage.Level.INFO)
   void destroyDivert(String user, Object source, String args);

   static void getBridgeNames(Object source) {
      BASE_LOGGER.getBridgeNames(getCaller(), source);
   }

   @LogMessage(id = 601127, value = "User {} is getting bridge names on target resource: {}", level = LogMessage.Level.INFO)
   void getBridgeNames(String user, Object source);

   static void createBridge(Object source, Object... args) {
      BASE_LOGGER.createBridge(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601128, value = "User {} is creating a bridge on target resource: {} {}", level = LogMessage.Level.INFO)
   void createBridge(String user, Object source, String args);

   static void destroyBridge(Object source, Object... args) {
      BASE_LOGGER.destroyBridge(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601129, value = "User {} is destroying a bridge on target resource: {} {}", level = LogMessage.Level.INFO)
   void destroyBridge(String user, Object source, String args);

   static void createConnectorService(Object source, Object... args) {
      BASE_LOGGER.createConnectorService(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601130, value = "User {} is creating connector service on target resource: {} {}", level = LogMessage.Level.INFO)
   void createConnectorService(String user, Object source, String args);

   static void destroyConnectorService(Object source, Object... args) {
      BASE_LOGGER.destroyConnectorService(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601131, value = "User {} is destroying connector service on target resource: {} {}", level = LogMessage.Level.INFO)
   void destroyConnectorService(String user, Object source, String args);

   static void getConnectorServices(Object source, Object... args) {
      BASE_LOGGER.getConnectorServices(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601132, value = "User {} is getting connector services on target resource: {} {}", level = LogMessage.Level.INFO)
   void getConnectorServices(String user, Object source, String args);

   static void forceFailover(Object source) {
      BASE_LOGGER.forceFailover(getCaller(), source);
   }

   @LogMessage(id = 601133, value = "User {} is forceing a failover on target resource: {}", level = LogMessage.Level.INFO)
   void forceFailover(String user, Object source);

   static void scaleDown(Object source, Object... args) {
      BASE_LOGGER.scaleDown(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601134, value = "User {} is performing scale down on target resource: {} {}", level = LogMessage.Level.INFO)
   void scaleDown(String user, Object source, String args);

   static void listNetworkTopology(Object source) {
      BASE_LOGGER.listNetworkTopology(getCaller(), source);
   }

   @LogMessage(id = 601135, value = "User {} is listing network topology on target resource: {}", level = LogMessage.Level.INFO)
   void listNetworkTopology(String user, Object source);

   static void removeNotificationListener(Object source, Object... args) {
      BASE_LOGGER.removeNotificationListener(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601136, value = "User {} is removing notification listener on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeNotificationListener(String user, Object source, String args);

   static void addNotificationListener(Object source, Object... args) {
      BASE_LOGGER.addNotificationListener(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601137, value = "User {} is adding notification listener on target resource: {} {}", level = LogMessage.Level.INFO)
   void addNotificationListener(String user, Object source, String args);

   static void getNotificationInfo(Object source) {
      BASE_LOGGER.getNotificationInfo(getCaller(), source);
   }

   @LogMessage(id = 601138, value = "User {} is getting notification info on target resource: {}", level = LogMessage.Level.INFO)
   void getNotificationInfo(String user, Object source);

   static void getConnectionTTLOverride(Object source) {
      BASE_LOGGER.getConnectionTTLOverride(getCaller(), source);
   }

   @LogMessage(id = 601139, value = "User {} is getting connection ttl override on target resource: {}", level = LogMessage.Level.INFO)
   void getConnectionTTLOverride(String user, Object source);

   static void getIDCacheSize(Object source) {
      BASE_LOGGER.getIDCacheSize(getCaller(), source);
   }

   @LogMessage(id = 601140, value = "User {} is getting ID cache size on target resource: {}", level = LogMessage.Level.INFO)
   void getIDCacheSize(String user, Object source);

   static void getLargeMessagesDirectory(Object source) {
      BASE_LOGGER.getLargeMessagesDirectory(getCaller(), source);
   }

   @LogMessage(id = 601141, value = "User {} is getting large message directory on target resource: {}", level = LogMessage.Level.INFO)
   void getLargeMessagesDirectory(String user, Object source);

   static void getManagementAddress(Object source) {
      BASE_LOGGER.getManagementAddress(getCaller(), source);
   }

   @LogMessage(id = 601142, value = "User {} is getting management address on target resource: {}", level = LogMessage.Level.INFO)
   void getManagementAddress(String user, Object source);

   static void getNodeID(Object source) {
      BASE_LOGGER.getNodeID(getCaller(), source);
   }

   @LogMessage(id = 601143, value = "User {} is getting node ID on target resource: {}", level = LogMessage.Level.INFO)
   void getNodeID(String user, Object source);

   static void getManagementNotificationAddress(Object source) {
      BASE_LOGGER.getManagementNotificationAddress(getCaller(), source);
   }

   @LogMessage(id = 601144, value = "User {} is getting management notification address on target resource: {}", level = LogMessage.Level.INFO)
   void getManagementNotificationAddress(String user, Object source);

   static void getMessageExpiryScanPeriod(Object source) {
      BASE_LOGGER.getMessageExpiryScanPeriod(getCaller(), source);
   }

   @LogMessage(id = 601145, value = "User {} is getting message expiry scan period on target resource: {}", level = LogMessage.Level.INFO)
   void getMessageExpiryScanPeriod(String user, Object source);

   static void getMessageExpiryThreadPriority(Object source) {
      BASE_LOGGER.getMessageExpiryThreadPriority(getCaller(), source);
   }

   @LogMessage(id = 601146, value = "User {} is getting message expiry thread priority on target resource: {}", level = LogMessage.Level.INFO)
   void getMessageExpiryThreadPriority(String user, Object source);

   static void getTransactionTimeout(Object source) {
      BASE_LOGGER.getTransactionTimeout(getCaller(), source);
   }

   @LogMessage(id = 601147, value = "User {} is getting transaction timeout on target resource: {}", level = LogMessage.Level.INFO)
   void getTransactionTimeout(String user, Object source);

   static void getTransactionTimeoutScanPeriod(Object source) {
      BASE_LOGGER.getTransactionTimeoutScanPeriod(getCaller(), source);
   }

   @LogMessage(id = 601148, value = "User {} is getting transaction timeout scan period on target resource: {}", level = LogMessage.Level.INFO)
   void getTransactionTimeoutScanPeriod(String user, Object source);

   static void isPersistDeliveryCountBeforeDelivery(Object source) {
      BASE_LOGGER.isPersistDeliveryCountBeforeDelivery(getCaller(), source);
   }

   @LogMessage(id = 601149, value = "User {} is querying is-persist-delivery-before-delivery on target resource: {}", level = LogMessage.Level.INFO)
   void isPersistDeliveryCountBeforeDelivery(String user, Object source);

   static void isPersistIDCache(Object source) {
      BASE_LOGGER.isPersistIDCache(getCaller(), source);
   }

   @LogMessage(id = 601150, value = "User {} is querying is-persist-id-cache on target resource: {}", level = LogMessage.Level.INFO)
   void isPersistIDCache(String user, Object source);

   static void isWildcardRoutingEnabled(Object source) {
      BASE_LOGGER.isWildcardRoutingEnabled(getCaller(), source);
   }

   @LogMessage(id = 601151, value = "User {} is querying is-wildcard-routing-enabled on target resource: {}", level = LogMessage.Level.INFO)
   void isWildcardRoutingEnabled(String user, Object source);

   static void addUser(Object source, Object... args) {
      BASE_LOGGER.addUser(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601152, value = "User {} is adding a user on target resource: {} {}", level = LogMessage.Level.INFO)
   void addUser(String user, Object source, String args);

   static void listUser(Object source, Object... args) {
      BASE_LOGGER.listUser(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601153, value = "User {} is listing a user on target resource: {} {}", level = LogMessage.Level.INFO)
   void listUser(String user, Object source, String args);

   static void removeUser(Object source, Object... args) {
      BASE_LOGGER.removeUser(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601154, value = "User {} is removing a user on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeUser(String user, Object source, String args);

   static void resetUser(Object source, Object... args) {
      BASE_LOGGER.resetUser(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601155, value = "User {} is resetting a user on target resource: {} {}", level = LogMessage.Level.INFO)
   void resetUser(String user, Object source, String args);

   static void getUser(Object source) {
      BASE_LOGGER.getUser(getCaller(), source);
   }

   @LogMessage(id = 601156, value = "User {} is getting user property on target resource: {}", level = LogMessage.Level.INFO)
   void getUser(String user, Object source);

   static void getRoutingType(Object source) {
      BASE_LOGGER.getRoutingType(getCaller(), source);
   }

   @LogMessage(id = 601157, value = "User {} is getting routing type property on target resource: {}", level = LogMessage.Level.INFO)
   void getRoutingType(String user, Object source);

   static void isTemporary(Object source) {
      BASE_LOGGER.isTemporary(getCaller(), source);
   }

   @LogMessage(id = 601158, value = "User {} is getting temporary property on target resource: {}", level = LogMessage.Level.INFO)
   void isTemporary(String user, Object source);

   static void getPersistentSize(Object source) {
      BASE_LOGGER.getPersistentSize(getCaller(), source);
   }

   @LogMessage(id = 601159, value = "User {} is getting persistent size on target resource: {}", level = LogMessage.Level.INFO)
   void getPersistentSize(String user, Object source);

   static void getDurableMessageCount(Object source) {
      BASE_LOGGER.getDurableMessageCount(getCaller(), source);
   }

   @LogMessage(id = 601160, value = "User {} is getting durable message count on target resource: {}", level = LogMessage.Level.INFO)
   void getDurableMessageCount(String user, Object source);

   static void getDurablePersistSize(Object source) {
      BASE_LOGGER.getDurablePersistSize(getCaller(), source);
   }

   @LogMessage(id = 601161, value = "User {} is getting durable persist size on target resource: {}", level = LogMessage.Level.INFO)
   void getDurablePersistSize(String user, Object source);

   static void getConsumerCount(Object source) {
      BASE_LOGGER.getConsumerCount(getCaller(), source);
   }

   @LogMessage(id = 601162, value = "User {} is getting consumer count on target resource: {}", level = LogMessage.Level.INFO)
   void getConsumerCount(String user, Object source);

   static void getDeliveringCount(Object source) {
      BASE_LOGGER.getDeliveringCount(getCaller(), source);
   }

   @LogMessage(id = 601163, value = "User {} is getting delivering count on target resource: {}", level = LogMessage.Level.INFO)
   void getDeliveringCount(String user, Object source);

   static void getDeliveringSize(Object source) {
      BASE_LOGGER.getDeliveringSize(getCaller(), source);
   }

   @LogMessage(id = 601164, value = "User {} is getting delivering size on target resource: {}", level = LogMessage.Level.INFO)
   void getDeliveringSize(String user, Object source);

   static void getDurableDeliveringCount(Object source) {
      BASE_LOGGER.getDurableDeliveringCount(getCaller(), source);
   }

   @LogMessage(id = 601165, value = "User {} is getting durable delivering count on target resource: {}", level = LogMessage.Level.INFO)
   void getDurableDeliveringCount(String user, Object source);

   static void getDurableDeliveringSize(Object source) {
      BASE_LOGGER.getDurableDeliveringSize(getCaller(), source);
   }

   @LogMessage(id = 601166, value = "User {} is getting durable delivering size on target resource: {}", level = LogMessage.Level.INFO)
   void getDurableDeliveringSize(String user, Object source);

   static void getMessagesAdded(Object source) {
      BASE_LOGGER.getMessagesAdded(getCaller(), source);
   }

   @LogMessage(id = 601167, value = "User {} is getting messages added on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesAdded(String user, Object source);

   static void getMessagesAcknowledged(Object source) {
      BASE_LOGGER.getMessagesAcknowledged(getCaller(), source);
   }

   @LogMessage(id = 601168, value = "User {} is getting messages acknowledged on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesAcknowledged(String user, Object source);

   static void getMessagesExpired(Object source) {
      BASE_LOGGER.getMessagesExpired(getCaller(), source);
   }

   @LogMessage(id = 601169, value = "User {} is getting messages expired on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesExpired(String user, Object source);

   static void getMessagesKilled(Object source) {
      BASE_LOGGER.getMessagesKilled(getCaller(), source);
   }

   @LogMessage(id = 601170, value = "User {} is getting messages killed on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesKilled(String user, Object source);

   static void getID(Object source) {
      BASE_LOGGER.getID(getCaller(), source);
   }

   @LogMessage(id = 601171, value = "User {} is getting ID on target resource: {}", level = LogMessage.Level.INFO)
   void getID(String user, Object source);

   static void getScheduledCount(Object source) {
      BASE_LOGGER.getScheduledCount(getCaller(), source);
   }

   @LogMessage(id = 601172, value = "User {} is getting scheduled count on target resource: {}", level = LogMessage.Level.INFO)
   void getScheduledCount(String user, Object source);

   static void getScheduledSize(Object source) {
      BASE_LOGGER.getScheduledSize(getCaller(), source);
   }

   @LogMessage(id = 601173, value = "User {} is getting scheduled size on target resource: {}", level = LogMessage.Level.INFO)
   void getScheduledSize(String user, Object source);

   static void getDurableScheduledCount(Object source) {
      BASE_LOGGER.getDurableScheduledCount(getCaller(), source);
   }

   @LogMessage(id = 601174, value = "User {} is getting durable scheduled count on target resource: {}", level = LogMessage.Level.INFO)
   void getDurableScheduledCount(String user, Object source);

   static void getDurableScheduledSize(Object source) {
      BASE_LOGGER.getDurableScheduledSize(getCaller(), source);
   }

   @LogMessage(id = 601175, value = "User {} is getting durable scheduled size on target resource: {}", level = LogMessage.Level.INFO)
   void getDurableScheduledSize(String user, Object source);

   static void getDeadLetterAddress(Object source) {
      BASE_LOGGER.getDeadLetterAddress(getCaller(), source);
   }

   @LogMessage(id = 601176, value = "User {} is getting dead letter address on target resource: {}", level = LogMessage.Level.INFO)
   void getDeadLetterAddress(String user, Object source);

   static void getExpiryAddress(Object source) {
      BASE_LOGGER.getExpiryAddress(getCaller(), source);
   }

   @LogMessage(id = 601177, value = "User {} is getting expiry address on target resource: {}", level = LogMessage.Level.INFO)
   void getExpiryAddress(String user, Object source);

   static void getMaxConsumers(Object source) {
      BASE_LOGGER.getMaxConsumers(getCaller(), source);
   }

   @LogMessage(id = 601178, value = "User {} is getting max consumers on target resource: {}", level = LogMessage.Level.INFO)
   void getMaxConsumers(String user, Object source);

   static void isPurgeOnNoConsumers(Object source) {
      BASE_LOGGER.isPurgeOnNoConsumers(getCaller(), source);
   }

   @LogMessage(id = 601179, value = "User {} is getting purge-on-consumers property on target resource: {}", level = LogMessage.Level.INFO)
   void isPurgeOnNoConsumers(String user, Object source);

   static void isConfigurationManaged(Object source) {
      BASE_LOGGER.isConfigurationManaged(getCaller(), source);
   }

   @LogMessage(id = 601180, value = "User {} is getting configuration-managed property on target resource: {}", level = LogMessage.Level.INFO)
   void isConfigurationManaged(String user, Object source);

   static void isExclusive(Object source) {
      BASE_LOGGER.isExclusive(getCaller(), source);
   }

   @LogMessage(id = 601181, value = "User {} is getting exclusive property on target resource: {}", level = LogMessage.Level.INFO)
   void isExclusive(String user, Object source);

   static void isLastValue(Object source) {
      BASE_LOGGER.isLastValue(getCaller(), source);
   }

   @LogMessage(id = 601182, value = "User {} is getting last-value property on target resource: {}", level = LogMessage.Level.INFO)
   void isLastValue(String user, Object source);

   static void listScheduledMessages(Object source) {
      BASE_LOGGER.listScheduledMessages(getCaller(), source);
   }

   @LogMessage(id = 601183, value = "User {} is listing scheduled messages on target resource: {}", level = LogMessage.Level.INFO)
   void listScheduledMessages(String user, Object source);

   static void listScheduledMessagesAsJSON(Object source) {
      BASE_LOGGER.listScheduledMessagesAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601184, value = "User {} is listing scheduled messages as json on target resource: {}", level = LogMessage.Level.INFO)
   void listScheduledMessagesAsJSON(String user, Object source);

   static void listDeliveringMessages(Object source) {
      BASE_LOGGER.listDeliveringMessages(getCaller(), source);
   }

   @LogMessage(id = 601185, value = "User {} is listing delivering messages on target resource: {}", level = LogMessage.Level.INFO)
   void listDeliveringMessages(String user, Object source);

   static void listDeliveringMessagesAsJSON(Object source) {
      BASE_LOGGER.listDeliveringMessagesAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601186, value = "User {} is listing delivering messages as json on target resource: {}", level = LogMessage.Level.INFO)
   void listDeliveringMessagesAsJSON(String user, Object source);

   static void listMessages(Object source, Object... args) {
      BASE_LOGGER.listMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601187, value = "User {} is listing messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void listMessages(String user, Object source, String args);

   static void listMessagesAsJSON(Object source) {
      BASE_LOGGER.listMessagesAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601188, value = "User {} is listing messages as json on target resource: {}", level = LogMessage.Level.INFO)
   void listMessagesAsJSON(String user, Object source);

   static void getFirstMessage(Object source) {
      BASE_LOGGER.getFirstMessage(getCaller(), source);
   }

   @LogMessage(id = 601189, value = "User {} is getting first message on target resource: {}", level = LogMessage.Level.INFO)
   void getFirstMessage(String user, Object source);

   static void getFirstMessageAsJSON(Object source) {
      BASE_LOGGER.getFirstMessageAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601190, value = "User {} is getting first message as json on target resource: {}", level = LogMessage.Level.INFO)
   void getFirstMessageAsJSON(String user, Object source);

   static void getFirstMessageTimestamp(Object source) {
      BASE_LOGGER.getFirstMessageTimestamp(getCaller(), source);
   }

   @LogMessage(id = 601191, value = "User {} is getting first message's timestamp on target resource: {}", level = LogMessage.Level.INFO)
   void getFirstMessageTimestamp(String user, Object source);

   static void getFirstMessageAge(Object source) {
      BASE_LOGGER.getFirstMessageAge(getCaller(), source);
   }

   @LogMessage(id = 601192, value = "User {} is getting first message's age on target resource: {}", level = LogMessage.Level.INFO)
   void getFirstMessageAge(String user, Object source);

   static void countMessages(Object source, Object... args) {
      BASE_LOGGER.countMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601193, value = "User {} is counting messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void countMessages(String user, Object source, String args);

   static void countDeliveringMessages(Object source, Object... args) {
      BASE_LOGGER.countDeliveringMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601194, value = "User {} is counting delivery messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void countDeliveringMessages(String user, Object source, String args);

   static void removeMessage(Object source, Object... args) {
      BASE_LOGGER.removeMessage(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601195, value = "User {} is removing a message on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeMessage(String user, Object source, String args);

   static void removeMessages(Object source, Object... args) {
      BASE_LOGGER.removeMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601196, value = "User {} is removing messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeMessages(String user, Object source, String args);

   static void expireMessage(Object source, Object... args) {
      BASE_LOGGER.expireMessage(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601197, value = "User {} is expiring messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void expireMessage(String user, Object source, String args);

   static void expireMessages(Object source, Object... args) {
      BASE_LOGGER.expireMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601198, value = "User {} is expiring messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void expireMessages(String user, Object source, String args);

   static void retryMessage(Object source, Object... args) {
      BASE_LOGGER.retryMessage(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601199, value = "User {} is retry sending message on target resource: {} {}", level = LogMessage.Level.INFO)
   void retryMessage(String user, Object source, String args);

   static void retryMessages(Object source) {
      BASE_LOGGER.retryMessages(getCaller(), source);
   }

   @LogMessage(id = 601200, value = "User {} is retry sending messages on target resource: {}", level = LogMessage.Level.INFO)
   void retryMessages(String user, Object source);

   static void moveMessage(Object source, Object... args) {
      BASE_LOGGER.moveMessage(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601201, value = "User {} is moving a message to another queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void moveMessage(String user, Object source, String args);

   static void moveMessages(Object source, Object... args) {
      BASE_LOGGER.moveMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601202, value = "User {} is moving messages to another queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void moveMessages(String user, Object source, String args);

   static void sendMessagesToDeadLetterAddress(Object source, Object... args) {
      BASE_LOGGER.sendMessagesToDeadLetterAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601203, value = "User {} is sending messages to dead letter address on target resource: {} {}", level = LogMessage.Level.INFO)
   void sendMessagesToDeadLetterAddress(String user, Object source, String args);

   static void sendMessageToDeadLetterAddress(Object source, Object... args) {
      BASE_LOGGER.sendMessageToDeadLetterAddress(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601204, value = "User {} is sending messages to dead letter address on target resource: {} {}", level = LogMessage.Level.INFO)
   void sendMessageToDeadLetterAddress(String user, Object source, String args);

   static void changeMessagesPriority(Object source, Object... args) {
      BASE_LOGGER.changeMessagesPriority(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601205, value = "User {} is changing message's priority on target resource: {} {}", level = LogMessage.Level.INFO)
   void changeMessagesPriority(String user, Object source, String args);

   static void changeMessagePriority(Object source, Object... args) {
      BASE_LOGGER.changeMessagePriority(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601206, value = "User {} is changing a message's priority on target resource: {} {}", level = LogMessage.Level.INFO)
   void changeMessagePriority(String user, Object source, String args);

   static void listMessageCounter(Object source) {
      BASE_LOGGER.listMessageCounter(getCaller(), source);
   }

   @LogMessage(id = 601207, value = "User {} is listing message counter on target resource: {}", level = LogMessage.Level.INFO)
   void listMessageCounter(String user, Object source);

   static void resetMessageCounter(Object source) {
      BASE_LOGGER.resetMessageCounter(getCaller(), source);
   }

   @LogMessage(id = 601208, value = "User {} is resetting message counter on target resource: {}", level = LogMessage.Level.INFO)
   void resetMessageCounter(String user, Object source);

   static void listMessageCounterAsHTML(Object source) {
      BASE_LOGGER.listMessageCounterAsHTML(getCaller(), source);
   }

   @LogMessage(id = 601209, value = "User {} is listing message counter as HTML on target resource: {}", level = LogMessage.Level.INFO)
   void listMessageCounterAsHTML(String user, Object source);

   static void listMessageCounterHistory(Object source) {
      BASE_LOGGER.listMessageCounterHistory(getCaller(), source);
   }

   @LogMessage(id = 601210, value = "User {} is listing message counter history on target resource: {}", level = LogMessage.Level.INFO)
   void listMessageCounterHistory(String user, Object source);

   static void listMessageCounterHistoryAsHTML(Object source) {
      BASE_LOGGER.listMessageCounterHistoryAsHTML(getCaller(), source);
   }

   @LogMessage(id = 601211, value = "User {} is listing message counter history as HTML on target resource: {}", level = LogMessage.Level.INFO)
   void listMessageCounterHistoryAsHTML(String user, Object source);

   static void pause(Object source, Object... args) {
      BASE_LOGGER.pause(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601212, value = "User {} is pausing on target resource: {} {}", level = LogMessage.Level.INFO)
   void pause(String user, Object source, String args);

   static void resume(Object source) {
      BASE_LOGGER.resume(getCaller(), source);
   }

   @LogMessage(id = 601213, value = "User {} is resuming on target resource: {}", level = LogMessage.Level.INFO)
   void resume(String user, Object source);

   static void isPaused(Object source) {
      BASE_LOGGER.isPaused(getCaller(), source);
   }

   @LogMessage(id = 601214, value = "User {} is getting paused property on target resource: {}", level = LogMessage.Level.INFO)
   void isPaused(String user, Object source);

   static void browse(Object source, Object... args) {
      BASE_LOGGER.browse(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601215, value = "User {} is browsing a queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void browse(String user, Object source, String args);

   static void flushExecutor(Object source) {
      BASE_LOGGER.flushExecutor(getCaller(), source);
   }

   @LogMessage(id = 601216, value = "User {} is flushing executor on target resource: {}", level = LogMessage.Level.INFO)
   void flushExecutor(String user, Object source);

   static void resetAllGroups(Object source) {
      BASE_LOGGER.resetAllGroups(getCaller(), source);
   }

   @LogMessage(id = 601217, value = "User {} is resetting all groups on target resource: {}", level = LogMessage.Level.INFO)
   void resetAllGroups(String user, Object source);

   static void resetGroup(Object source, Object... args) {
      BASE_LOGGER.resetGroup(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601218, value = "User {} is resetting group on target resource: {} {}", level = LogMessage.Level.INFO)
   void resetGroup(String user, Object source, String arg);

   static void getGroupCount(Object source, Object... args) {
      BASE_LOGGER.getGroupCount(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601219, value = "User {} is getting group count on target resource: {} {}", level = LogMessage.Level.INFO)
   void getGroupCount(String user, Object source, String args);

   static void listGroupsAsJSON(Object source) {
      BASE_LOGGER.listGroupsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601220, value = "User {} is listing groups as json on target resource: {}", level = LogMessage.Level.INFO)
   void listGroupsAsJSON(String user, Object source);

   static void resetMessagesAdded(Object source) {
      BASE_LOGGER.resetMessagesAdded(getCaller(), source);
   }

   @LogMessage(id = 601221, value = "User {} is resetting added messages on target resource: {}", level = LogMessage.Level.INFO)
   void resetMessagesAdded(String user, Object source);

   static void resetMessagesAcknowledged(Object source) {
      BASE_LOGGER.resetMessagesAcknowledged(getCaller(), source);
   }

   @LogMessage(id = 601222, value = "User {} is resetting acknowledged messages on target resource: {}", level = LogMessage.Level.INFO)
   void resetMessagesAcknowledged(String user, Object source);

   static void resetMessagesExpired(Object source) {
      BASE_LOGGER.resetMessagesExpired(getCaller(), source);
   }

   @LogMessage(id = 601223, value = "User {} is resetting expired messages on target resource: {}", level = LogMessage.Level.INFO)
   void resetMessagesExpired(String user, Object source);

   static void resetMessagesKilled(Object source) {
      BASE_LOGGER.resetMessagesKilled(getCaller(), source);
   }

   @LogMessage(id = 601224, value = "User {} is resetting killed messages on target resource: {}", level = LogMessage.Level.INFO)
   void resetMessagesKilled(String user, Object source);

   static void getStaticConnectors(Object source) {
      BASE_LOGGER.getStaticConnectors(getCaller(), source);
   }

   @LogMessage(id = 601225, value = "User {} is getting static connectors on target resource: {}", level = LogMessage.Level.INFO)
   void getStaticConnectors(String user, Object source);

   static void getForwardingAddress(Object source) {
      BASE_LOGGER.getForwardingAddress(getCaller(), source);
   }

   @LogMessage(id = 601226, value = "User {} is getting forwarding address on target resource: {}", level = LogMessage.Level.INFO)
   void getForwardingAddress(String user, Object source);

   static void getQueueName(Object source) {
      BASE_LOGGER.getQueueName(getCaller(), source);
   }

   @LogMessage(id = 601227, value = "User {} is getting the queue name on target resource: {}", level = LogMessage.Level.INFO)
   void getQueueName(String user, Object source);

   static void getDiscoveryGroupName(Object source) {
      BASE_LOGGER.getDiscoveryGroupName(getCaller(), source);
   }

   @LogMessage(id = 601228, value = "User {} is getting discovery group name on target resource: {}", level = LogMessage.Level.INFO)
   void getDiscoveryGroupName(String user, Object source);

   static void getFilterString(Object source) {
      BASE_LOGGER.getFilterString(getCaller(), source);
   }

   @LogMessage(id = 601229, value = "User {} is getting filter string on target resource: {}", level = LogMessage.Level.INFO)
   void getFilterString(String user, Object source);

   static void getReconnectAttempts(Object source) {
      BASE_LOGGER.getReconnectAttempts(getCaller(), source);
   }

   @LogMessage(id = 601230, value = "User {} is getting reconnect attempts on target resource: {}", level = LogMessage.Level.INFO)
   void getReconnectAttempts(String user, Object source);

   static void getRetryInterval(Object source) {
      BASE_LOGGER.getRetryInterval(getCaller(), source);
   }

   @LogMessage(id = 601231, value = "User {} is getting retry interval on target resource: {}", level = LogMessage.Level.INFO)
   void getRetryInterval(String user, Object source);

   static void getRetryIntervalMultiplier(Object source) {
      BASE_LOGGER.getRetryIntervalMultiplier(getCaller(), source);
   }

   @LogMessage(id = 601232, value = "User {} is getting retry interval multiplier on target resource: {}", level = LogMessage.Level.INFO)
   void getRetryIntervalMultiplier(String user, Object source);

   static void getTransformerClassName(Object source) {
      BASE_LOGGER.getTransformerClassName(getCaller(), source);
   }

   @LogMessage(id = 601233, value = "User {} is getting transformer class name on target resource: {}", level = LogMessage.Level.INFO)
   void getTransformerClassName(String user, Object source);

   static void getTransformerPropertiesAsJSON(Object source) {
      BASE_LOGGER.getTransformerPropertiesAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601234, value = "User {} is getting transformer properties as json on target resource: {}", level = LogMessage.Level.INFO)
   void getTransformerPropertiesAsJSON(String user, Object source);

   static void getTransformerProperties(Object source) {
      BASE_LOGGER.getTransformerProperties(getCaller(), source);
   }

   @LogMessage(id = 601235, value = "User {} is getting transformer properties on target resource: {}", level = LogMessage.Level.INFO)
   void getTransformerProperties(String user, Object source);

   static void isStartedBridge(Object source) {
      BASE_LOGGER.isStartedBridge(getCaller(), source);
   }

   @LogMessage(id = 601236, value = "User {} is checking if bridge started on target resource: {}", level = LogMessage.Level.INFO)
   void isStartedBridge(String user, Object source);

   static void isUseDuplicateDetection(Object source) {
      BASE_LOGGER.isUseDuplicateDetection(getCaller(), source);
   }

   @LogMessage(id = 601237, value = "User {} is querying use duplicate detection on target resource: {}", level = LogMessage.Level.INFO)
   void isUseDuplicateDetection(String user, Object source);

   static void isHA(Object source) {
      BASE_LOGGER.isHA(getCaller(), source);
   }

   @LogMessage(id = 601238, value = "User {} is querying isHA on target resource: {}", level = LogMessage.Level.INFO)
   void isHA(String user, Object source);

   static void startBridge(Object source) {
      BASE_LOGGER.startBridge(getCaller(), source);
   }

   @LogMessage(id = 601239, value = "User {} is starting a bridge on target resource: {}", level = LogMessage.Level.INFO)
   void startBridge(String user, Object source);

   static void stopBridge(Object source) {
      BASE_LOGGER.stopBridge(getCaller(), source);
   }

   @LogMessage(id = 601240, value = "User {} is stopping a bridge on target resource: {}", level = LogMessage.Level.INFO)
   void stopBridge(String user, Object source);

   static void getMessagesPendingAcknowledgement(Object source) {
      BASE_LOGGER.getMessagesPendingAcknowledgement(getCaller(), source);
   }

   @LogMessage(id = 601241, value = "User {} is getting messages pending acknowledgement on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesPendingAcknowledgement(String user, Object source);

   static void getMetrics(Object source) {
      BASE_LOGGER.getMetrics(getCaller(), source);
   }

   @LogMessage(id = 601242, value = "User {} is getting metrics on target resource: {}", level = LogMessage.Level.INFO)
   void getMetrics(String user, Object source);

   static void getBroadcastPeriod(Object source) {
      BASE_LOGGER.getBroadcastPeriod(getCaller(), source);
   }

   @LogMessage(id = 601243, value = "User {} is getting broadcast period on target resource: {}", level = LogMessage.Level.INFO)
   void getBroadcastPeriod(String user, Object source);

   static void getConnectorPairs(Object source) {
      BASE_LOGGER.getConnectorPairs(getCaller(), source);
   }

   @LogMessage(id = 601244, value = "User {} is getting connector pairs on target resource: {}", level = LogMessage.Level.INFO)
   void getConnectorPairs(String user, Object source);

   static void getConnectorPairsAsJSON(Object source) {
      BASE_LOGGER.getConnectorPairsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601245, value = "User {} is getting connector pairs as json on target resource: {}", level = LogMessage.Level.INFO)
   void getConnectorPairsAsJSON(String user, Object source);

   static void getGroupAddress(Object source) {
      BASE_LOGGER.getGroupAddress(getCaller(), source);
   }

   @LogMessage(id = 601246, value = "User {} is getting group address on target resource: {}", level = LogMessage.Level.INFO)
   void getGroupAddress(String user, Object source);

   static void getGroupPort(Object source) {
      BASE_LOGGER.getGroupPort(getCaller(), source);
   }

   @LogMessage(id = 601247, value = "User {} is getting group port on target resource: {}", level = LogMessage.Level.INFO)
   void getGroupPort(String user, Object source);

   static void getLocalBindPort(Object source) {
      BASE_LOGGER.getLocalBindPort(getCaller(), source);
   }

   @LogMessage(id = 601248, value = "User {} is getting local binding port on target resource: {}", level = LogMessage.Level.INFO)
   void getLocalBindPort(String user, Object source);

   static void startBroadcastGroup(Object source) {
      BASE_LOGGER.startBroadcastGroup(getCaller(), source);
   }

   @LogMessage(id = 601249, value = "User {} is starting broadcasting group on target resource: {}", level = LogMessage.Level.INFO)
   void startBroadcastGroup(String user, Object source);

   static void stopBroadcastGroup(Object source) {
      BASE_LOGGER.stopBroadcastGroup(getCaller(), source);
   }

   @LogMessage(id = 601250, value = "User {} is stopping broadcasting group on target resource: {}", level = LogMessage.Level.INFO)
   void stopBroadcastGroup(String user, Object source);

   static void getMaxHops(Object source) {
      BASE_LOGGER.getMaxHops(getCaller(), source);
   }

   @LogMessage(id = 601251, value = "User {} is getting max hops on target resource: {}", level = LogMessage.Level.INFO)
   void getMaxHops(String user, Object source);

   static void getStaticConnectorsAsJSON(Object source) {
      BASE_LOGGER.getStaticConnectorsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601252, value = "User {} is getting static connectors as json on target resource: {}", level = LogMessage.Level.INFO)
   void getStaticConnectorsAsJSON(String user, Object source);

   static void isDuplicateDetection(Object source) {
      BASE_LOGGER.isDuplicateDetection(getCaller(), source);
   }

   @LogMessage(id = 601253, value = "User {} is querying use duplicate detection on target resource: {}", level = LogMessage.Level.INFO)
   void isDuplicateDetection(String user, Object source);

   static void getMessageLoadBalancingType(Object source) {
      BASE_LOGGER.getMessageLoadBalancingType(getCaller(), source);
   }

   @LogMessage(id = 601254, value = "User {} is getting message loadbalancing type on target resource: {}", level = LogMessage.Level.INFO)
   void getMessageLoadBalancingType(String user, Object source);

   static void getTopology(Object source) {
      BASE_LOGGER.getTopology(getCaller(), source);
   }

   @LogMessage(id = 601255, value = "User {} is getting topology on target resource: {}", level = LogMessage.Level.INFO)
   void getTopology(String user, Object source);

   static void getNodes(Object source) {
      BASE_LOGGER.getNodes(getCaller(), source);
   }

   @LogMessage(id = 601256, value = "User {} is getting nodes on target resource: {}", level = LogMessage.Level.INFO)
   void getNodes(String user, Object source);

   static void startClusterConnection(Object source) {
      BASE_LOGGER.startClusterConnection(getCaller(), source);
   }

   @LogMessage(id = 601257, value = "User {} is start cluster connection on target resource: {}", level = LogMessage.Level.INFO)
   void startClusterConnection(String user, Object source);

   static void stopClusterConnection(Object source) {
      BASE_LOGGER.stopClusterConnection(getCaller(), source);
   }

   @LogMessage(id = 601258, value = "User {} is stop cluster connection on target resource: {}", level = LogMessage.Level.INFO)
   void stopClusterConnection(String user, Object source);

   static void getBridgeMetrics(Object source, Object... args) {
      BASE_LOGGER.getBridgeMetrics(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601259, value = "User {} is getting bridge metrics on target resource: {} {}", level = LogMessage.Level.INFO)
   void getBridgeMetrics(String user, Object source, String args);

   static void getRoutingName(Object source) {
      BASE_LOGGER.getRoutingName(getCaller(), source);
   }

   @LogMessage(id = 601260, value = "User {} is getting routing name on target resource: {}", level = LogMessage.Level.INFO)
   void getRoutingName(String user, Object source);

   static void getUniqueName(Object source) {
      BASE_LOGGER.getUniqueName(getCaller(), source);
   }

   @LogMessage(id = 601261, value = "User {} is getting unique name on target resource: {}", level = LogMessage.Level.INFO)
   void getUniqueName(String user, Object source);

   static void serverSessionCreateAddress(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.serverSessionCreateAddress2(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601262, value = "User {} is creating address on target resource: {} {}", level = LogMessage.Level.INFO)
   void serverSessionCreateAddress2(String user, Object source, String args);

   static void handleManagementMessage(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.handleManagementMessage2(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601263, value = "User {} is handling a management message on target resource {} {}", level = LogMessage.Level.INFO)
   void handleManagementMessage2(String user, Object source, String args);


   static void securityFailure(Subject subject, String remoteAddress, String reason, Exception cause) {
      BASE_LOGGER.securityFailure(getCaller(subject, remoteAddress), reason, cause);
   }

   @LogMessage(id = 601264, value = "User {} gets security check failure, reason = {}", level = LogMessage.Level.INFO)
   void securityFailure(String user, String reason, Throwable cause);


   static void createCoreConsumer(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.createCoreConsumer(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601265, value = "User {} is creating a core consumer on target resource {} {}", level = LogMessage.Level.INFO)
   void createCoreConsumer(String user, Object source, String args);

   static void createSharedQueue(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.createSharedQueue(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601266, value = "User {} is creating a shared queue on target resource {} {}", level = LogMessage.Level.INFO)
   void createSharedQueue(String user, Object source, String args);

   static void createCoreSession(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.createCoreSession(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601267, value = "User {} is creating a core session on target resource {} {}", level = LogMessage.Level.INFO)
   void createCoreSession(String user, Object source, String args);

   static void getAcknowledgeAttempts(Object source) {
      BASE_LOGGER.getMessagesAcknowledged(getCaller(), source);
   }

   @LogMessage(id = 601269, value = "User {} is getting messages acknowledged attempts on target resource: {} {}", level = LogMessage.Level.INFO)
   void getAcknowledgeAttempts(String user, Object source, String args);

   static void getRingSize(Object source, Object... args) {
      BASE_LOGGER.getRingSize(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601270, value = "User {} is getting ring size on target resource: {} {}", level = LogMessage.Level.INFO)
   void getRingSize(String user, Object source, String args);


   static void isRetroactiveResource(Object source) {
      BASE_LOGGER.isRetroactiveResource(getCaller(), source);
   }

   @LogMessage(id = 601271, value = "User {} is getting retroactiveResource property on target resource: {}", level = LogMessage.Level.INFO)
   void isRetroactiveResource(String user, Object source);

   static void getDiskStoreUsage(Object source) {
      BASE_LOGGER.getDiskStoreUsage(getCaller(), source);
   }

   @LogMessage(id = 601272, value = "User {} is getting disk store usage on target resource: {}", level = LogMessage.Level.INFO)
   void getDiskStoreUsage(String user, Object source);

   static void getDiskStoreUsagePercentage(Object source) {
      BASE_LOGGER.getDiskStoreUsagePercentage(getCaller(), source);
   }

   @LogMessage(id = 601273, value = "User {} is getting disk store usage percentage on target resource: {}", level = LogMessage.Level.INFO)
   void getDiskStoreUsagePercentage(String user, Object source);

   static void isGroupRebalance(Object source) {
      BASE_LOGGER.isGroupRebalance(getCaller(), source);
   }

   @LogMessage(id = 601274, value = "User {} is getting group rebalance property on target resource: {}", level = LogMessage.Level.INFO)
   void isGroupRebalance(String user, Object source);

   static void getGroupBuckets(Object source) {
      BASE_LOGGER.getGroupBuckets(getCaller(), source);
   }

   @LogMessage(id = 601275, value = "User {} is getting group buckets on target resource: {}", level = LogMessage.Level.INFO)
   void getGroupBuckets(String user, Object source);

   static void getGroupFirstKey(Object source) {
      BASE_LOGGER.getGroupFirstKey(getCaller(), source);
   }

   @LogMessage(id = 601276, value = "User {} is getting group first key on target resource: {}", level = LogMessage.Level.INFO)
   void getGroupFirstKey(String user, Object source);

   static void getCurrentDuplicateIdCacheSize(Object source) {
      BASE_LOGGER.getCurrentDuplicateIdCacheSize(getCaller(), source);
   }

   @LogMessage(id = 601509, value = "User {} is getting currentDuplicateIdCacheSize property on target resource: {}", level = LogMessage.Level.INFO)
   void getCurrentDuplicateIdCacheSize(String user, Object source);


   static void clearDuplicateIdCache(Object source) {
      BASE_LOGGER.clearDuplicateIdCache(getCaller(), source);
   }

   @LogMessage(id = 601510, value = "User {} is clearing duplicate ID cache on target resource: {}", level = LogMessage.Level.INFO)
   void clearDuplicateIdCache(String user, Object source);


   static void getChannelName(Object source) {
      BASE_LOGGER.getChannelName(getCaller(), source);
   }

   @LogMessage(id = 601511, value = "User {} is getting channelName property on target resource: {}", level = LogMessage.Level.INFO)
   void getChannelName(String user, Object source);

   static void getFileContents(Object source) {
      BASE_LOGGER.getFileContents(getCaller(), source);
   }

   @LogMessage(id = 601512, value = "User {} is getting fileContents property on target resource: {}", level = LogMessage.Level.INFO)
   void getFileContents(String user, Object source);

   static void getFile(Object source) {
      BASE_LOGGER.getFile(getCaller(), source);
   }

   @LogMessage(id = 601513, value = "User {} is getting file property on target resource: {}", level = LogMessage.Level.INFO)
   void getFile(String user, Object source);

   static void getPreparedTransactionMessageCount(Object source) {
      BASE_LOGGER.getPreparedTransactionMessageCount(getCaller(), source);
   }

   @LogMessage(id = 601514, value = "User {} is getting preparedTransactionMessageCount property on target resource: {}", level = LogMessage.Level.INFO)
   void getPreparedTransactionMessageCount(String user, Object source);

   // This logger is for message production and consumption and is on the hot path so enabled independently
   static void coreSendMessage(Subject user, String remoteAddress, String messageToString, Object context, String tx) {
      MESSAGE_LOGGER.coreSendMessage(getCaller(user, remoteAddress), messageToString, context, tx);
   }

   @LogMessage(id = 601500, value = "User {} sent a message {}, context: {}, transaction: {}", level = LogMessage.Level.INFO)
   void coreSendMessage(String user, String messageToString, Object context, String tx);

   //hot path log using a different logger
   static void coreConsumeMessage(Subject user, String remoteAddress, String queue, String message) {
      MESSAGE_LOGGER.consumeMessage(getCaller(user, remoteAddress), queue, message);
   }

   @LogMessage(id = 601501, value = "User {} is consuming a message from {}: {}", level = LogMessage.Level.INFO)
   void consumeMessage(String user, String address, String message);

   //hot path log using a different logger
   static void coreAcknowledgeMessage(Subject user, String remoteAddress, String queue, String message, String tx) {
      MESSAGE_LOGGER.coreAcknowledgeMessage(getCaller(user, remoteAddress), queue, message, tx);
   }

   @LogMessage(id = 601502, value = "User {} acknowledged message from {}: {}, transaction: {}", level = LogMessage.Level.INFO)
   void coreAcknowledgeMessage(String user, String queue, String message, String tx);

   // This logger is focused on user interaction from the console or thru resource specific functions in the management layer/JMX
   static void createAddressSuccess(String name, String routingTypes) {
      RESOURCE_LOGGER.createAddressSuccess(getCaller(), name, routingTypes);
   }

   @LogMessage(id = 601701, value = "User {} successfully created address: {} with routing types {}", level = LogMessage.Level.INFO)
   void createAddressSuccess(String user, String name, String routingTypes);

   static void createAddressFailure(String name, String routingTypes) {
      RESOURCE_LOGGER.createAddressFailure(getCaller(), name, routingTypes);
   }

   @LogMessage(id = 601702, value = "User {} failed to created address: {} with routing types {}", level = LogMessage.Level.INFO)
   void createAddressFailure(String user, String name, String routingTypes);

   static void updateAddressSuccess(String name, String routingTypes) {
      RESOURCE_LOGGER.updateAddressSuccess(getCaller(), name, routingTypes);
   }

   @LogMessage(id = 601703, value = "User {} successfully updated address: {} with routing types {}", level = LogMessage.Level.INFO)
   void updateAddressSuccess(String user, String name, String routingTypes);

   static void updateAddressFailure(String name, String routingTypes) {
      RESOURCE_LOGGER.updateAddressFailure(getCaller(), name, routingTypes);
   }

   @LogMessage(id = 601704, value = "User {} successfully updated address: {} with routing types {}", level = LogMessage.Level.INFO)
   void updateAddressFailure(String user, String name, String routingTypes);

   static void deleteAddressSuccess(String name) {
      RESOURCE_LOGGER.deleteAddressSuccess(getCaller(), name);
   }

   @LogMessage(id = 601705, value = "User {} successfully deleted address: {}", level = LogMessage.Level.INFO)
   void deleteAddressSuccess(String user, String name);


   static void deleteAddressFailure(String name) {
      RESOURCE_LOGGER.deleteAddressFailure(getCaller(), name);
   }

   @LogMessage(id = 601706, value = "User {} failed to deleted address: {}", level = LogMessage.Level.INFO)
   void deleteAddressFailure(String user, String name);

   static void createQueueSuccess(String name, String address, String routingType) {
      RESOURCE_LOGGER.createQueueSuccess(getCaller(), name, address, routingType);
   }

   @LogMessage(id = 601707, value = "User {} successfully created queue: {} on address: {} with routing type {}", level = LogMessage.Level.INFO)
   void createQueueSuccess(String user, String name, String address, String routingType);

   static void createQueueFailure(String name, String address, String routingType) {
      RESOURCE_LOGGER.createQueueFailure(getCaller(), name, address, routingType);
   }

   @LogMessage(id = 601708, value = "User {} failed to create queue: {} on address: {} with routing type {}", level = LogMessage.Level.INFO)
   void createQueueFailure(String user, String name, String address, String routingType);

   static void updateQueueSuccess(String name, String routingType) {
      RESOURCE_LOGGER.updateQueueSuccess(getCaller(), name, routingType);
   }

   @LogMessage(id = 601709, value = "User {} successfully updated queue: {} with routing type {}", level = LogMessage.Level.INFO)
   void updateQueueSuccess(String user, String name, String routingType);

   static void updateQueueFailure(String name, String routingType) {
      RESOURCE_LOGGER.updateQueueFailure(getCaller(), name, routingType);
   }

   @LogMessage(id = 601710, value = "User {} failed to update queue: {} with routing type {}", level = LogMessage.Level.INFO)
   void updateQueueFailure(String user, String name, String routingType);


   static void destroyQueueSuccess(String name) {
      RESOURCE_LOGGER.destroyQueueSuccess(getCaller(), name);
   }

   @LogMessage(id = 601711, value = "User {} successfully deleted queue: {}", level = LogMessage.Level.INFO)
   void destroyQueueSuccess(String user, String name);

   static void destroyQueueFailure(String name) {
      RESOURCE_LOGGER.destroyQueueFailure(getCaller(), name);
   }

   @LogMessage(id = 601712, value = "User {} failed to delete queue: {}", level = LogMessage.Level.INFO)
   void destroyQueueFailure(String user, String name);

   static void removeMessagesSuccess(int removed, String queue) {
      RESOURCE_LOGGER.removeMessagesSuccess(getCaller(), removed, queue);
   }

   @LogMessage(id = 601713, value = "User {} has removed {} messages from queue: {}", level = LogMessage.Level.INFO)
   void removeMessagesSuccess(String user, int removed, String queue);

   static void removeMessagesFailure(String queue) {
      RESOURCE_LOGGER.removeMessagesFailure(getCaller(), queue);
   }

   @LogMessage(id = 601714, value = "User {} failed to remove messages from queue: {}", level = LogMessage.Level.INFO)
   void removeMessagesFailure(String user, String queue);

   static void userSuccesfullyAuthenticatedInAudit(Subject subject, String remoteAddress, String connectionID) {
      RESOURCE_LOGGER.userSuccesfullyAuthenticated(getCaller(subject, remoteAddress), connectionID);
   }

   static void userSuccesfullyAuthenticatedInAudit(Subject subject) {
      userSuccesfullyAuthenticatedInAudit(subject, null, null);
   }

   @LogMessage(id = 601715, value = "User {} successfully authenticated on connection {}", level = LogMessage.Level.INFO)
   void userSuccesfullyAuthenticated(String caller, String connectionID);


   static void userFailedAuthenticationInAudit(String reason) {
      RESOURCE_LOGGER.userFailedAuthentication(getCaller(), null, reason);
   }

   static void userFailedAuthenticationInAudit(Subject subject, String reason, String connectionID) {
      RESOURCE_LOGGER.userFailedAuthentication(getCaller(subject, null), connectionID, reason);
   }

   @LogMessage(id = 601716, value = "User {} failed authentication on connection {}, reason: {}", level = LogMessage.Level.INFO)
   void userFailedAuthentication(String user, String connectionID, String reason);

   static void objectInvokedSuccessfully(ObjectName objectName, String operationName) {
      RESOURCE_LOGGER.objectInvokedSuccessfully(getCaller(), objectName, operationName);
   }

   @LogMessage(id = 601717, value = "User {} accessed {} on management object {}", level = LogMessage.Level.INFO)
   void objectInvokedSuccessfully(String caller, ObjectName objectName, String operationName);


   static void objectInvokedFailure(ObjectName objectName, String operationName) {
      RESOURCE_LOGGER.objectInvokedFailure(getCaller(), objectName, operationName);
   }

   @LogMessage(id = 601718, value = "User {} does not have correct role to access {} on management object {}", level = LogMessage.Level.INFO)
   void objectInvokedFailure(String caller, ObjectName objectName, String operationName);

   static void pauseQueueSuccess(String queueName) {
      RESOURCE_LOGGER.pauseQueueSuccess(getCaller(), queueName);
   }

   @LogMessage(id = 601719, value = "User {} has paused queue {}", level = LogMessage.Level.INFO)
   void pauseQueueSuccess(String user, String queueName);


   static void pauseQueueFailure(String queueName) {
      RESOURCE_LOGGER.pauseQueueFailure(getCaller(), queueName);
   }

   @LogMessage(id = 601720, value = "User {} failed to pause queue {}", level = LogMessage.Level.INFO)
   void pauseQueueFailure(String user, String queueName);


   static void resumeQueueSuccess(String queueName) {
      RESOURCE_LOGGER.resumeQueueSuccess(getCaller(), queueName);
   }

   @LogMessage(id = 601721, value = "User {} has resumed queue {}", level = LogMessage.Level.INFO)
   void resumeQueueSuccess(String user, String queueName);


   static void resumeQueueFailure(String queueName) {
      RESOURCE_LOGGER.pauseQueueFailure(getCaller(), queueName);
   }

   @LogMessage(id = 601722, value = "User {} failed to resume queue {}", level = LogMessage.Level.INFO)
   void resumeQueueFailure(String user, String queueName);

   static void sendMessageSuccess(String queueName, String user) {
      RESOURCE_LOGGER.sendMessageSuccess(getCaller(), queueName, user);
   }

   @LogMessage(id = 601723, value = "User {} sent message to {} as user {}", level = LogMessage.Level.INFO)
   void sendMessageSuccess(String user, String queueName, String sendUser);

   static void sendMessageFailure(String queueName, String user) {
      RESOURCE_LOGGER.sendMessageFailure(getCaller(), queueName, user);
   }

   @LogMessage(id = 601724, value = "User {} failed to send message to {} as user {}", level = LogMessage.Level.INFO)
   void sendMessageFailure(String user, String queueName, String sendUser);

   static void browseMessagesSuccess(String queueName, int numMessages) {
      RESOURCE_LOGGER.browseMessagesSuccess(getCaller(), numMessages, queueName);
   }

   @LogMessage(id = 601725, value = "User {} browsed {} messages from queue {}", level = LogMessage.Level.INFO)
   void browseMessagesSuccess(String user, int numMessages, String queueName);

   static void browseMessagesFailure(String queueName) {
      RESOURCE_LOGGER.browseMessagesFailure(getCaller(), queueName);
   }

   @LogMessage(id = 601726, value = "User {} failed to browse messages from queue {}", level = LogMessage.Level.INFO)
   void browseMessagesFailure(String user, String queueName);

   static void updateDivert(Object source, Object... args) {
      BASE_LOGGER.updateDivert(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601727, value = "User {} is updating a divert on target resource: {} {}", level = LogMessage.Level.INFO)
   void updateDivert(String user, Object source, String arg);

   static void isEnabled(Object source) {
      BASE_LOGGER.isEnabled(getCaller(), source);
   }

   @LogMessage(id = 601728, value = "User {} is getting enabled property on target resource: {}", level = LogMessage.Level.INFO)
   void isEnabled(String user, Object source);

   static void disable(Object source, Object... args) {
      BASE_LOGGER.disable(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601729, value = "User {} is disabling on target resource: {} {}", level = LogMessage.Level.INFO)
   void disable(String user, Object source, String arg);

   static void enable(Object source) {
      BASE_LOGGER.resume(getCaller(), source);
   }

   @LogMessage(id = 601730, value = "User {} is enabling on target resource: {}", level = LogMessage.Level.INFO)
   void enable(String user, Object source);

   static void pauseAddressSuccess(String queueName) {
      RESOURCE_LOGGER.pauseAddressSuccess(getCaller(), queueName);
   }

   @LogMessage(id = 601731, value = "User {} has paused address {}", level = LogMessage.Level.INFO)
   void pauseAddressSuccess(String user, String queueName);


   static void pauseAddressFailure(String queueName) {
      RESOURCE_LOGGER.pauseAddressFailure(getCaller(), queueName);
   }

   @LogMessage(id = 601732, value = "User {} failed to pause address {}", level = LogMessage.Level.INFO)
   void pauseAddressFailure(String user, String queueName);


   static void resumeAddressSuccess(String queueName) {
      RESOURCE_LOGGER.resumeAddressSuccess(getCaller(), queueName);
   }

   @LogMessage(id = 601733, value = "User {} has resumed address {}", level = LogMessage.Level.INFO)
   void resumeAddressSuccess(String user, String queueName);


   static void resumeAddressFailure(String queueName) {
      RESOURCE_LOGGER.resumeAddressFailure(getCaller(), queueName);
   }

   @LogMessage(id = 601734, value = "User {} failed to resume address {}", level = LogMessage.Level.INFO)
   void resumeAddressFailure(String user, String queueName);

   static void isGroupRebalancePauseDispatch(Object source) {
      BASE_LOGGER.isGroupRebalancePauseDispatch(getCaller(), source);
   }

   @LogMessage(id = 601735, value = "User {} is getting group rebalance pause dispatch property on target resource: {}", level = LogMessage.Level.INFO)
   void isGroupRebalancePauseDispatch(String user, Object source);

   static void getAuthenticationCacheSize(Object source) {
      BASE_LOGGER.getAuthenticationCacheSize(getCaller(), source);
   }

   @LogMessage(id = 601736, value = "User {} is getting authentication cache size on target resource: {}", level = LogMessage.Level.INFO)
   void getAuthenticationCacheSize(String user, Object source);

   static void getAuthorizationCacheSize(Object source) {
      BASE_LOGGER.getAuthorizationCacheSize(getCaller(), source);
   }

   @LogMessage(id = 601737, value = "User {} is getting authorization cache size on target resource: {}", level = LogMessage.Level.INFO)
   void getAuthorizationCacheSize(String user, Object source);

   static void listBrokerConnections() {
      BASE_LOGGER.listBrokerConnections(getCaller());
   }

   @LogMessage(id = 601738, value = "User {} is requesting a list of broker connections", level = LogMessage.Level.INFO)
   void listBrokerConnections(String user);

   static void stopBrokerConnection(String name) {
      BASE_LOGGER.stopBrokerConnection(getCaller(), name);
   }

   @LogMessage(id = 601739, value = "User {} is requesting to stop broker connection {}", level = LogMessage.Level.INFO)
   void stopBrokerConnection(String user, String name);

   static void startBrokerConnection(String name) {
      BASE_LOGGER.startBrokerConnection(getCaller(), name);
   }

   @LogMessage(id = 601740, value = "User {} is requesting to start broker connection {}", level = LogMessage.Level.INFO)
   void startBrokerConnection(String user, String name);

   static void getAddressCount(Object source) {
      BASE_LOGGER.getAddressCount(getCaller(), source);
   }

   @LogMessage(id = 601741, value = "User {} is getting address count on target resource: {}", level = LogMessage.Level.INFO)
   void getAddressCount(String user, Object source);

   static void getQueueCount(Object source) {
      BASE_LOGGER.getQueueCount(getCaller(), source);
   }

   @LogMessage(id = 601742, value = "User {} is getting the queue count on target resource: {}", level = LogMessage.Level.INFO)
   void getQueueCount(String user, Object source);

   static void lastValueKey(Object source) {
      BASE_LOGGER.lastValueKey(getCaller(), source);
   }

   @LogMessage(id = 601743, value = "User {} is getting last-value-key property on target resource: {}", level = LogMessage.Level.INFO)
   void lastValueKey(String user, Object source);

   static void consumersBeforeDispatch(Object source) {
      BASE_LOGGER.consumersBeforeDispatch(getCaller(), source);
   }

   @LogMessage(id = 601744, value = "User {} is getting consumers-before-dispatch property on target resource: {}", level = LogMessage.Level.INFO)
   void consumersBeforeDispatch(String user, Object source);

   static void delayBeforeDispatch(Object source) {
      BASE_LOGGER.delayBeforeDispatch(getCaller(), source);
   }

   @LogMessage(id = 601745, value = "User {} is getting delay-before-dispatch property on target resource: {}", level = LogMessage.Level.INFO)
   void delayBeforeDispatch(String user, Object source);

   static void isInternal(Object source) {
      BASE_LOGGER.isInternal(getCaller(), source);
   }

   @LogMessage(id = 601746, value = "User {} is getting internal property on target resource: {}", level = LogMessage.Level.INFO)
   void isInternal(String user, Object source);

   static void isAutoCreated(Object source) {
      BASE_LOGGER.isAutoCreated(getCaller(), source);
   }

   @LogMessage(id = 601747, value = "User {} is getting auto-created property on target resource: {}", level = LogMessage.Level.INFO)
   void isAutoCreated(String user, Object source);

   static void getMaxRetryInterval(Object source) {
      BASE_LOGGER.getMaxRetryInterval(getCaller(), source);
   }

   @LogMessage(id = 601748, value = "User {} is getting max retry interval on target resource: {}", level = LogMessage.Level.INFO)
   void getMaxRetryInterval(String user, Object source);

   static void getActivationSequence(Object source) {
      BASE_LOGGER.getActivationSequence(getCaller(), source);
   }

   @LogMessage(id = 601749, value = "User {} is getting activation sequence on target resource: {}", level = LogMessage.Level.INFO)
   void getActivationSequence(String user, Object source);

   static void purge(Object source) {
      RESOURCE_LOGGER.purge(getCaller(), source);
   }

   @LogMessage(id = 601750, value = "User {} is purging target resource: {}", level = LogMessage.Level.INFO)
   void purge(String user, Object source);


   static void purgeAddressSuccess(String queueName) {
      RESOURCE_LOGGER.purgeAddressSuccess(getCaller(), queueName);
   }

   @LogMessage(id = 601751, value = "User {} has purged address {}", level = LogMessage.Level.INFO)
   void purgeAddressSuccess(String user, String queueName);


   static void purgeAddressFailure(String queueName) {
      RESOURCE_LOGGER.purgeAddressFailure(getCaller(), queueName);
   }

   @LogMessage(id = 601752, value = "User {} failed to purge address {}", level = LogMessage.Level.INFO)
   void purgeAddressFailure(String user, String queueName);

   static void getAddressLimitPercent(Object source) {
      BASE_LOGGER.getAddressLimitPercent(getCaller(), source);
   }

   @LogMessage(id = 601753, value = "User {} is getting address limit %  on target resource: {}", level = LogMessage.Level.INFO)
   void getAddressLimitPercent(String user, Object source);

   static void block(Object source) {
      BASE_LOGGER.block(getCaller(), source);
   }

   @LogMessage(id = 601754, value = "User {} is blocking target resource: {}", level = LogMessage.Level.INFO)
   void block(String user, Object source);

   static void unBlock(Object source) {
      BASE_LOGGER.unBlock(getCaller(), source);
   }

   @LogMessage(id = 601755, value = "User {} is unblocking target resource: {}", level = LogMessage.Level.INFO)
   void unBlock(String user, Object source);

   static void getAcceptors(Object source) {
      BASE_LOGGER.getAcceptors(getCaller(), source);
   }

   @LogMessage(id = 601756, value = "User {} is getting acceptors on target resource: {}", level = LogMessage.Level.INFO)
   void getAcceptors(String user, Object source);

   static void getAcceptorsAsJSON(Object source) {
      BASE_LOGGER.getAcceptorsAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601757, value = "User {} is getting acceptors as json on target resource: {}", level = LogMessage.Level.INFO)
   void getAcceptorsAsJSON(String user, Object source);

   static void schedulePageCleanup(Object source) {
      BASE_LOGGER.schedulePageCleanup(getCaller(), source);
   }

   @LogMessage(id = 601758, value = "User {} is calling schedulePageCleanup on address: {}", level = LogMessage.Level.INFO)
   void schedulePageCleanup(String user, Object address);

   //hot path log using a different logger
   static void addAckToTransaction(Subject user, String remoteAddress, String queue, String message, String tx) {
      MESSAGE_LOGGER.addAckToTransaction(getCaller(user, remoteAddress), queue, message, tx);
   }

   @LogMessage(id = 601759, value = "User {} added acknowledgement of a message from {}: {} to transaction: {}", level = LogMessage.Level.INFO)
   void addAckToTransaction(String user, String queue, String message, String tx);

   //hot path log using a different logger
   static void addSendToTransaction(Subject user, String remoteAddress, String messageToString, String tx) {
      MESSAGE_LOGGER.addSendToTransaction(getCaller(user, remoteAddress), messageToString, tx);
   }

   @LogMessage(id = 601760, value = "User {} added a message send for: {} to transaction: {}", level = LogMessage.Level.INFO)
   void addSendToTransaction(String user, String messageToString, String tx);

   //hot path log using a different logger
   static void rolledBackTransaction(Subject user, String remoteAddress, String tx, String resource) {
      MESSAGE_LOGGER.rolledBackTransaction(getCaller(user, remoteAddress), tx, resource);
   }

   @LogMessage(id = 601761, value = "User {} rolled back transaction {} involving {}", level = LogMessage.Level.INFO)
   void rolledBackTransaction(String user, String tx, String resource);

   static void addConnector(Object source, Object... args) {
      BASE_LOGGER.addConnector(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601762, value = "User {} is adding a connector on target resource: {} {}", level = LogMessage.Level.INFO)
   void addConnector(String user, Object source, String args);

   static void removeConnector(Object source, Object... args) {
      BASE_LOGGER.removeConnector(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601763, value = "User {} is removing a connector on target resource: {} {}", level = LogMessage.Level.INFO)
   void removeConnector(String user, Object source, String args);

   static void deliverScheduledMessage(Object source, Object... args) {
      BASE_LOGGER.deliverScheduledMessage(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601764, value = "User {} is calling deliverScheduledMessage on queue: {} {}", level = LogMessage.Level.INFO)
   void deliverScheduledMessage(String user, Object source, String args);

   static void getStatus(Object source) {
      BASE_LOGGER.getStatus(getCaller(), source);
   }

   @LogMessage(id = 601765, value = "User {} is getting status on target resource: {}", level = LogMessage.Level.INFO)
   void getStatus(String user, Object source);

   static void isAutoDelete(Object source) {
      BASE_LOGGER.isAutoDelete(getCaller(), source);
   }

   @LogMessage(id = 601766, value = "User {} is getting auto-delete property on target resource: {}", level = LogMessage.Level.INFO)
   void isAutoDelete(String user, Object source);

   static void createdConnection(String protocol, Object connectionID, String remoteAddress) {
      RESOURCE_LOGGER.createdConnection(protocol, String.valueOf(connectionID), remoteAddress);
   }

   @LogMessage(id = 601767, value = "{} connection {} from {} created", level = LogMessage.Level.INFO)
   void createdConnection(String protocol, String connectionID, String remoteAddress);

   static void destroyedConnection(String protocol, Object connectionID, Subject subject, String remoteAddress) {
      RESOURCE_LOGGER.destroyedConnection(protocol, String.valueOf(connectionID), getCaller(subject, remoteAddress));
   }

   @LogMessage(id = 601768, value = "{} connection {} for user {} destroyed", level = LogMessage.Level.INFO)
   void destroyedConnection(String protocol, String connectionID, String user);

   static void clearAuthenticationCache(Object source) {
      BASE_LOGGER.clearAuthenticationCache(getCaller(), source);
   }

   @LogMessage(id = 601769, value = "User {} is clearing authentication cache on target resource: {}", level = LogMessage.Level.INFO)
   void clearAuthenticationCache(String user, Object source);

   static void clearAuthorizationCache(Object source) {
      BASE_LOGGER.clearAuthorizationCache(getCaller(), source);
   }

   @LogMessage(id = 601770, value = "User {} is clearing authorization cache on target resource: {}", level = LogMessage.Level.INFO)
   void clearAuthorizationCache(String user, Object source);

   static void getCurrentTimeMillis(Object source) {
      BASE_LOGGER.getCurrentTimeMillis(getCaller(), source);
   }

   @LogMessage(id = 601771, value = "User {} is getting name on target resource: {}", level = LogMessage.Level.INFO)
   void getCurrentTimeMillis(String user, Object source);

   static void getProducerWindowSize(Object source) {
      BASE_LOGGER.getProducerWindowSize(getCaller(), source);
   }

   @LogMessage(id = 601772, value = "User {} is getting producerWindowSize on target resource: {}", level = LogMessage.Level.INFO)
   void getProducerWindowSize(String user, Object source);

   static void peekFirstScheduledMessage(Object source) {
      BASE_LOGGER.peekFirstScheduledMessage(getCaller(), source);
   }

   @LogMessage(id = 601773, value = "User {} is getting first scheduled message on target resource: {}", level = LogMessage.Level.INFO)
   void peekFirstScheduledMessage(String user, Object source);

   static void peekFirstScheduledMessageAsJSON(Object source) {
      BASE_LOGGER.peekFirstScheduledMessageAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601774, value = "User {} is getting first scheduled message as json on target resource: {}", level = LogMessage.Level.INFO)
   void peekFirstScheduledMessageAsJSON(String user, Object source);

   static void peekFirstMessage(Object source) {
      BASE_LOGGER.peekFirstMessage(getCaller(), source);
   }

   @LogMessage(id = 601775, value = "User {} is getting first message on target resource: {}", level = LogMessage.Level.INFO)
   void peekFirstMessage(String user, Object source);

   static void peekFirstMessageAsJSON(Object source) {
      BASE_LOGGER.peekFirstMessageAsJSON(getCaller(), source);
   }

   @LogMessage(id = 601776, value = "User {} is getting first message as json on target resource: {}", level = LogMessage.Level.INFO)
   void peekFirstMessageAsJSON(String user, Object source);

   static void getBrokerPluginClassNames(Object source) {
      BASE_LOGGER.getBrokerPluginClassNames(getCaller(), source);
   }

   @LogMessage(id = 601777, value = "User {} is getting broker plugin class names on target resource: {}", level = LogMessage.Level.INFO)
   void getBrokerPluginClassNames(String user, Object source);

   static void getAuthenticationSuccessCount(Object source) {
      BASE_LOGGER.getAuthenticationSuccessCount(getCaller(), source);
   }

   @LogMessage(id = 601778, value = "User {} is getting authentication success count on target resource: {}", level = LogMessage.Level.INFO)
   void getAuthenticationSuccessCount(String user, Object source);

   static void getAuthenticationFailureCount(Object source) {
      BASE_LOGGER.getAuthenticationFailureCount(getCaller(), source);
   }

   @LogMessage(id = 601779, value = "User {} is getting authentication failure count on target resource: {}", level = LogMessage.Level.INFO)
   void getAuthenticationFailureCount(String user, Object source);

   static void getAuthorizationSuccessCount(Object source) {
      BASE_LOGGER.getAuthorizationSuccessCount(getCaller(), source);
   }

   @LogMessage(id = 601780, value = "User {} is getting authorization success count on target resource: {}", level = LogMessage.Level.INFO)
   void getAuthorizationSuccessCount(String user, Object source);

   static void getAuthorizationFailureCount(Object source) {
      BASE_LOGGER.getAuthorizationFailureCount(getCaller(), source);
   }

   @LogMessage(id = 601781, value = "User {} is getting authorization failure count on target resource: {}", level = LogMessage.Level.INFO)
   void getAuthorizationFailureCount(String user, Object source);

   static void isConnected(Object source) {
      BASE_LOGGER.isConnected(getCaller(), source);
   }

   @LogMessage(id = 601782, value = "User {} is getting connected state on target resource: {}", level = LogMessage.Level.INFO)
   void isConnected(String user, Object source);

   static void getUri(Object source) {
      BASE_LOGGER.getUri(getCaller(), source);
   }

   @LogMessage(id = 601783, value = "User {} is getting URI on target resource: {}", level = LogMessage.Level.INFO)
   void getUri(String user, Object source);

   static void getProtocol(Object source) {
      BASE_LOGGER.getProtocol(getCaller(), source);
   }

   @LogMessage(id = 601784, value = "User {} is getting the protocol on target resource: {}", level = LogMessage.Level.INFO)
   void getProtocol(String user, Object source);

   static void getType(Object source) {
      BASE_LOGGER.getType(getCaller(), source);
   }

   @LogMessage(id = 601785, value = "User {} is getting the type on target resource: {}", level = LogMessage.Level.INFO)
   void getType(String user, Object source);

   static void getRole(Object source) {
      BASE_LOGGER.getRole(getCaller(), source);
   }

   @LogMessage(id = 601786, value = "User {} is getting the role on target resource: {}", level = LogMessage.Level.INFO)
   void getRole(String user, Object source);

   static void getFqqn(Object source) {
      BASE_LOGGER.getFqqn(getCaller(), source);
   }

   @LogMessage(id = 601787, value = "User {} is getting the FQQN on target resource: {}", level = LogMessage.Level.INFO)
   void getFqqn(String user, Object source);

   static void getPriority(Object source) {
      BASE_LOGGER.getPriority(getCaller(), source);
   }

   @LogMessage(id = 601788, value = "User {} is getting the priority on target resource: {}", level = LogMessage.Level.INFO)
   void getPriority(String user, Object source);

   static void getMessagesReceived(Object source) {
      BASE_LOGGER.getMessagesReceived(getCaller(), source);
   }

   @LogMessage(id = 601789, value = "User {} is getting the number of messages received on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesReceived(String user, Object source);

   static void copyMessage(Object source, Object... args) {
      BASE_LOGGER.copyMessage(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601790, value = "User {} is copying a message to another queue on target resource: {} {}", level = LogMessage.Level.INFO)
   void copyMessage(String user, Object source, String args);

   static void getMessagesSent(Object source) {
      BASE_LOGGER.getMessagesSent(getCaller(), source);
   }

   @LogMessage(id = 601791, value = "User {} is getting the number of messages sent on target resource: {}", level = LogMessage.Level.INFO)
   void getMessagesSent(String user, Object source);

   static void getMaxPageReadBytes(Object source) {
      BASE_LOGGER.getMaxPageReadBytes(getCaller(), source);
   }

   @LogMessage(id = 601792, value = "User {} is getting maxPageReadBytes on target resource: {}", level = LogMessage.Level.INFO)
   void getMaxPageReadBytes(String user, Object source);

   static void getMaxPageReadMessages(Object source) {
      BASE_LOGGER.getMaxPageReadMessages(getCaller(), source);
   }

   @LogMessage(id = 601793, value = "User {} is getting maxPageReadMessages on target resource: {}", level = LogMessage.Level.INFO)
   void getMaxPageReadMessages(String user, Object source);

   static void getPrefetchPageMessages(Object source) {
      BASE_LOGGER.getPrefetchPageMessages(getCaller(), source);
   }

   @LogMessage(id = 601794, value = "User {} is getting prefetchPageMessages on target resource: {}", level = LogMessage.Level.INFO)
   void getPrefetchPageMessages(String user, Object source);

   static void getPrefetchPageBytes(Object source) {
      BASE_LOGGER.getPrefetchPageBytes(getCaller(), source);
   }

   @LogMessage(id = 601795, value = "User {} is getting prefetchPageBytes on target resource: {}", level = LogMessage.Level.INFO)
   void getPrefetchPageBytes(String user, Object source);

   static void getSessionCount(Object source, Object... args) {
      BASE_LOGGER.getSessionCount(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601796, value = "User {} is getting session count on target resource: {} {}", level = LogMessage.Level.INFO)
   void getSessionCount(String user, Object source, String args);

   static void getTotalSessionCount(Object source) {
      BASE_LOGGER.getTotalSessionCount(getCaller(), source);
   }

   @LogMessage(id = 601797, value = "User {} is getting total session count on target resource: {}", level = LogMessage.Level.INFO)
   void getTotalSessionCount(String user, Object source);

   static void exportConfigAsProperties(Object source) {
      BASE_LOGGER.exportConfigAsProperties(getCaller(), source);
   }

   @LogMessage(id = 601798, value = "User {} is exporting configuration as properties on target resource: {}", level = LogMessage.Level.INFO)
   void exportConfigAsProperties(String user, Object source);

   static void getPendingMirrorAcks(Object source) {
      BASE_LOGGER.getPendingMirrorAcks(getCaller(), source);
   }

   @LogMessage(id = 601799, value = "User {} is getting PendingMirrorAcks on target resource: {}", level = LogMessage.Level.INFO)
   void getPendingMirrorAcks(String user, Object source);

   static void isPersistedPause(Object source) {
      BASE_LOGGER.isPaused(getCaller(), source);
   }

   @LogMessage(id = 601800, value = "User {} is getting persisted pause property on target resource: {}", level = LogMessage.Level.INFO)
   void isPersistedPause(String user, Object source);

   static void destroyAddress(Object source, Subject user, String remoteAddress, Object... args) {
      BASE_LOGGER.destroyAddress(getCaller(user, remoteAddress), source, parametersList(args));
   }

   @LogMessage(id = 601801, value = "User {} is deleting a address on target resource: {} {}", level = LogMessage.Level.INFO)
   void destroyAddress(String user, Object source, Object... args);

   static void retryMessages(Object source, Object... args) {
      BASE_LOGGER.retryMessages(getCaller(), source, parametersList(args));
   }

   @LogMessage(id = 601802, value = "User {} is retry sending messages on target resource: {} {}", level = LogMessage.Level.INFO)
   void retryMessages(String user, Object source, String args);

   static void isBlockedViaManagement(Object source) {
      BASE_LOGGER.isBlockedViaManagement(getCaller(), source);
   }

   @LogMessage(id = 601803, value = "User {} is getting BlockedViaManagement on target resource: {}", level = LogMessage.Level.INFO)
   void isBlockedViaManagement(String user, Object source);

}
