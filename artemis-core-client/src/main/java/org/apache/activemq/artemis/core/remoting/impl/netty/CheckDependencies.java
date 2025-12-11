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

package org.apache.activemq.artemis.core.remoting.impl.netty;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.uring.IoUring;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.utils.Env;

/**
 * This class will check for Epoll or KQueue is available, and return false in case of NoClassDefFoundError it could be
 * improved to check for other cases eventually.
 */
public class CheckDependencies {

   public static final boolean isEpollAvailable() {
      try {
         return Env.isLinuxOs() && Epoll.isAvailable();
      } catch (NoClassDefFoundError noClassDefFoundError) {
         ActiveMQClientLogger.LOGGER.unableToCheckEpollAvailabilitynoClass();
         return false;
      } catch (Throwable e)  {
         ActiveMQClientLogger.LOGGER.unableToCheckEpollAvailability(e);
         return false;
      }
   }

   public static final boolean isKQueueAvailable() {
      try {
         return Env.isMacOs() && KQueue.isAvailable();
      } catch (NoClassDefFoundError noClassDefFoundError) {
         ActiveMQClientLogger.LOGGER.unableToCheckKQueueAvailabilityNoClass();
         return false;
      } catch (Throwable e) {
         ActiveMQClientLogger.LOGGER.unableToCheckKQueueAvailability(e);
         return false;
      }
   }

   public static final boolean isIoUringAvailable() {
      try {
         return Env.isLinuxOs() && IoUring.isAvailable();
      } catch (NoClassDefFoundError noClassDefFoundError) {
         ActiveMQClientLogger.LOGGER.unableToCheckIoUringAvailabilitynoClass();
         return false;
      } catch (Throwable e)  {
         ActiveMQClientLogger.LOGGER.unableToCheckIoUringAvailability(e);
         return false;
      }
   }

}
