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
package org.apache.activemq.artemis.core.server.impl.jdbc;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.NodeManager.LockListener;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;

/**
 * {@link LeaseLock} holder that allows to schedule a {@link LeaseLock#renew} task with a fixed
 * {@link #renewPeriodMillis()} delay.
 */
interface ScheduledLeaseLock extends ActiveMQComponent {

   @Override
   void start();

   @Override
   void stop();

   LeaseLock lock();

   long renewPeriodMillis();

   String lockName();

   static ScheduledLeaseLock of(ScheduledExecutorService scheduledExecutorService,
                                ArtemisExecutor executor,
                                String lockName,
                                LeaseLock lock,
                                long renewPeriodMillis,
                                LockListener lockListener) {
      return new ActiveMQScheduledLeaseLock(scheduledExecutorService, executor, lockName, lock, renewPeriodMillis, lockListener);
   }

}
