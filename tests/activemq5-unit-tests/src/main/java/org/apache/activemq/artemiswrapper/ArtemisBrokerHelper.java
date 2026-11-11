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
package org.apache.activemq.artemiswrapper;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtemisBrokerHelper {

   private static final Logger LOG = LoggerFactory.getLogger(ArtemisBrokerHelper.class);

   private static volatile Object service = null;
   private static Class<?> serviceClass;

   static {
      try {
         serviceClass = Class.forName("org.apache.activemq.broker.BrokerService");
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }

   }

   // start a tcp transport artemis broker, the broker need to
   // be invm with client.
   public static void startArtemisBroker(URI location) throws IOException {
      LOG.info("---starting broker, service is there? " + service);
      if (service != null) {
         return;
      }
      try {
         service = serviceClass.getDeclaredConstructor().newInstance();
         Method startMethod = serviceClass.getMethod("start");
         startMethod.invoke(service, (Object[]) null);
         LOG.info("started a service instance: " + service);
      } catch (InstantiationException e) {
         throw new IOException("Inst exception", e);
      } catch (IllegalAccessException e) {
         throw new IOException("IllegalAccess exception ", e);
      } catch (NoSuchMethodException e) {
         throw new IOException("Nosuchmethod", e);
      } catch (SecurityException e) {
         throw new IOException("Security exception", e);
      } catch (IllegalArgumentException e) {
         throw new IOException("IllegalArgumentException exception", e);
      } catch (InvocationTargetException e) {
         throw new IOException("InvocationTargetException exception", e);
      }
   }

   public static void makeSureDestinationExists(ActiveMQDestination activemqDestination) throws Exception {
      Method startMethod = serviceClass.getMethod("makeSureDestinationExists", ActiveMQDestination.class);
      startMethod.invoke(service, activemqDestination);
   }

   public static BrokerService getBroker() {
      return (BrokerService) service;
   }

   public static void stopArtemisBroker() {
      try {
         if (service != null) {
            Method startMethod = serviceClass.getMethod("stop");
            startMethod.invoke(service, (Object[]) null);
            System.out.println("stopped the service instance: " + service);
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         service = null;
      }
   }
}
