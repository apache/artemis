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
package org.apache.activemq.artemis.service.extensions;

import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.activemq.artemis.service.extensions.transactions.TransactionManagerLocator;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapperFactory;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapperFactoryImpl;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public class ServiceUtils {

   private static ActiveMQXAResourceWrapperFactory activeMQXAResourceWrapperFactory;

   private static TransactionManager transactionManager;

   private static boolean transactionManagerLoaded = false;

   private static ActiveMQXAResourceWrapperFactory getActiveMQXAResourceWrapperFactory() {
      if (activeMQXAResourceWrapperFactory == null) {
         activeMQXAResourceWrapperFactory = findActiveMQXAResourceWrapperFactory();
      }
      return activeMQXAResourceWrapperFactory;
   }

   public static ActiveMQXAResourceWrapper wrapXAResource(XAResource xaResource, Map<String, Object> properties) {
      return getActiveMQXAResourceWrapperFactory().wrap(xaResource, properties);
   }

   public static synchronized TransactionManager getTransactionManager() {
      if (!transactionManagerLoaded) {
         transactionManager = findTransactionManager();
         if (transactionManager != null) {
            transactionManagerLoaded = true;
         } else {
            ActiveMQServiceExtensionLogger.LOGGER.transactionManagerNotFound();
         }
      }
      return transactionManager;
   }

   public static void setTransactionManager(TransactionManager tm) {
      transactionManager = tm;
      transactionManagerLoaded = (transactionManager != null);
   }

   /**
    * Find the <em>first</em> transaction manager loaded from the {@code TransactionManagerLocator} service or
    * {@code null} if none is loaded.
    */
   private static TransactionManager findTransactionManager() {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<TransactionManager>) () -> {
         Iterator<TransactionManagerLocator> it = ServiceLoader.load(TransactionManagerLocator.class, ServiceUtils.class.getClassLoader()).iterator();
         while (it.hasNext() && transactionManager == null) {
            transactionManager = it.next().getTransactionManager();
         }
         return transactionManager;
      });
   }

   /**
    * Find the <em>first</em> wrapper factory loaded from the {@code ActiveMQXAResourceWrapperFactory} service or use
    * the default {@code ActiveMQXAResourceWrapperFactoryImpl} if none is loaded.
    */
   private static ActiveMQXAResourceWrapperFactory findActiveMQXAResourceWrapperFactory() {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<ActiveMQXAResourceWrapperFactory>) () -> {
         Iterator<ActiveMQXAResourceWrapperFactory> iterator = ServiceLoader.load(ActiveMQXAResourceWrapperFactory.class, ServiceUtils.class.getClassLoader()).iterator();
         if (iterator.hasNext()) {
            return iterator.next();
         } else {
            return new ActiveMQXAResourceWrapperFactoryImpl();
         }
      });
   }
}
