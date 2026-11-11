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
package org.apache.activemq.artemis.core.server.management;

public interface NotificationService {

   /**
    * the message corresponding to a notification will always contain the properties:
    * <ul>
    * <li>{@code ManagementHelper.HDR_NOTIFICATION_TYPE} - the type of notification (SimpleString)</li>
    * <li>{@code ManagementHelper.HDR_NOTIFICATION_MESSAGE} - a message contextual to the notification (SimpleString)</li>
    * <li>{@code ManagementHelper.HDR_NOTIFICATION_TIMESTAMP} - the timestamp when the notification occurred (long)</li>
    * </ul>
    * in addition to the properties defined in {@code props}
    *
    * @see org.apache.activemq.artemis.api.core.management.ManagementHelper
    */
   void sendNotification(Notification notification) throws Exception;

   void enableNotifications(boolean enable);

   void addNotificationListener(NotificationListener listener);

   void removeNotificationListener(NotificationListener listener);

}
