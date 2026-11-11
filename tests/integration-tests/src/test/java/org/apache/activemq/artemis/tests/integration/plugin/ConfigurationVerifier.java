/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.activemq.artemis.tests.integration.plugin;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;

/**
 * Used in tests to verify configuration passed into plugin correctly.
 */
public class ConfigurationVerifier implements ActiveMQServerPlugin, Serializable {

   public static final String PROPERTY1 = "property1";
   public static final String PROPERTY2 = "property2";
   public static final String PROPERTY3 = "property3";

   public String value1;
   public String value2;
   public String value3;
   public AtomicInteger afterSendCounter = new AtomicInteger();
   public AtomicInteger successRoutedCounter = new AtomicInteger();

   @Override
   public void init(Map<String, String> properties) {
      value1 = properties.get(PROPERTY1);
      value2 = properties.get(PROPERTY2);
      value3 = properties.get(PROPERTY3);
   }

   /**
    * Used to ensure the plugin is being invoked
    */
   @Override
   public void afterSend(ServerSession session,
                         Transaction tx,
                         Message message,
                         boolean direct,
                         boolean noAutoCreateQueue,
                         RoutingStatus result) throws ActiveMQException {
      afterSendCounter.incrementAndGet();
   }

   @Override
   public void afterMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates,
         RoutingStatus result) throws ActiveMQException {
      if (result == RoutingStatus.OK) {
         successRoutedCounter.incrementAndGet();
      }
   }

}
