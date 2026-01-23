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

package org.apache.activemq.artemis.lockmanager;

import java.util.HashMap;
import java.util.ServiceLoader;

public class Registry {

   private final HashMap<String, DistributedLockManagerFactory> factories = new HashMap<>();
   private final HashMap<String, DistributedLockManagerFactory> factoriesWithImpl = new HashMap<>();

   private volatile boolean serviceLoaded = false;

   private static final Registry INSTANCE = new Registry();

   private Registry() {
   }

   public static Registry getInstance() {
      return INSTANCE;
   }

   public synchronized void register(DistributedLockManagerFactory factory) {
      factories.put(factory.getName().toLowerCase(), factory);
      factoriesWithImpl.put(factory.getImplName(), factory);
   }

   public synchronized void unregisterWithType(String type) {
      unregister(factories.get(type.toLowerCase()));
   }

   public synchronized void unregisterWithClassName(String name) {
      unregister(factoriesWithImpl.get(name));
   }

   private void unregister(DistributedLockManagerFactory factory) {
      if (factory != null) {
         factories.remove(factory.getName());
         factoriesWithImpl.remove(factory.getImplName());
      }
   }

   public synchronized DistributedLockManagerFactory getFactoryWithClassName(String className) {
      checkService();
      DistributedLockManagerFactory factory = factoriesWithImpl.get(className);
      if (factory == null) {
         throw new IllegalArgumentException("factory " + className + " not found");
      }
      return factory;
   }

   public synchronized DistributedLockManagerFactory getFactory(String type) {
      checkService();
      return factories.get(type.toLowerCase());
   }

   public synchronized void checkService() {
      if (serviceLoaded) {
         return;
      }
      ServiceLoader<DistributedLockManagerFactory> services = ServiceLoader.load(DistributedLockManagerFactory.class);
      services.forEach(this::register);
      serviceLoaded = true;
   }

}
