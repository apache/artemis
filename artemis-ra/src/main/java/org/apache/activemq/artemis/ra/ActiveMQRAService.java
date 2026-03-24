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
package org.apache.activemq.artemis.ra;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.util.Set;

/**
 * An ActiveMQRAService ensures that ActiveMQ Artemis Resource Adapter will be stopped *before* the ActiveMQ Artemis server.
 * https://jira.jboss.org/browse/HORNETQ-339
 */
public class ActiveMQRAService {

   private final MBeanServer mBeanServer;

   private final String resourceAdapterObjectName;



   public ActiveMQRAService(final MBeanServer mBeanServer, final String resourceAdapterObjectName) {
      this.mBeanServer = mBeanServer;
      this.resourceAdapterObjectName = resourceAdapterObjectName;
   }


   public void stop() {
      try {
         ObjectName objectName = new ObjectName(resourceAdapterObjectName);
         Set<ObjectInstance> mbeanSet = mBeanServer.queryMBeans(objectName, null);

         for (ObjectInstance mbean : mbeanSet) {
            String stateString = (String) mBeanServer.getAttribute(mbean.getObjectName(), "StateString");

            if ("Started".equalsIgnoreCase(stateString) || "Starting".equalsIgnoreCase(stateString)) {
               mBeanServer.invoke(mbean.getObjectName(), "stop", new Object[0], new String[0]);
            }
         }
      } catch (Exception e) {
         ActiveMQRALogger.LOGGER.errorStoppingRA(e);
      }
   }

}
