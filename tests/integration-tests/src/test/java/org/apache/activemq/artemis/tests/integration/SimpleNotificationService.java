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
package org.apache.activemq.artemis.tests.integration;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.management.NotificationType;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationListener;
import org.apache.activemq.artemis.core.server.management.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleNotificationService implements NotificationService {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final List<NotificationListener> listeners = new ArrayList<>();


   // NotificationService implementation ----------------------------

   @Override
   public void addNotificationListener(final NotificationListener listener) {
      listeners.add(listener);
   }

   @Override
   public void enableNotifications(final boolean enable) {
   }

   @Override
   public void removeNotificationListener(final NotificationListener listener) {
      listeners.remove(listener);
   }

   @Override
   public void sendNotification(final Notification notification) throws Exception {
      for (NotificationListener listener : listeners) {
         listener.onNotification(notification);
      }
   }


   public static class Listener implements NotificationListener {

      public synchronized int count(NotificationType... interestingTypes) {
         if (logger.isDebugEnabled()) {
            logger.debug("count for {}", stringOf(interestingTypes));
         }
         return (int) notifications.stream().filter(n -> matchTypes(n, interestingTypes)).count();
      }

      private static String stringOf(NotificationType[] types) {
         StringBuilder sb = new StringBuilder();
         sb.append("types[" + types.length + "] = {");
         for (int i = 0; i < types.length; i++) {
            sb.append(types[i]);
            if (i + 1 < types.length) {
               sb.append(",");
            }
         }
         sb.append("}");
         return sb.toString();
      }

      public synchronized int size() {
         return notifications.size();
      }

      private boolean matchTypes(Notification notification, NotificationType... interestingTypes) {
         logger.debug("matching {}", notification);
         for (NotificationType t : interestingTypes) {
            logger.debug("looking to match {} with type parameter {}", notification, t);
            if (notification.getType() == t) {
               return true;
            }
         }
         return false;
      }

      public synchronized Notification findAny(NotificationType notificationType) {
         return notifications.stream().filter(n -> n.getType() == notificationType).findAny().get();
      }

      ////////////////////////////////////////////////////////////////////////////////////
      // Note: Do not expose this collection directly.
      // Instead, filter notifications by the types you are interested in.
      // Previous tests validated whether this collection was empty, but later, new notifications were added.
      // These tests became flaky as they received irrelevant notifications.
      private final List<Notification> notifications = new ArrayList<>();

      @Override
      public synchronized void onNotification(final Notification notification) {
         notifications.add(notification);
      }

   }
}
