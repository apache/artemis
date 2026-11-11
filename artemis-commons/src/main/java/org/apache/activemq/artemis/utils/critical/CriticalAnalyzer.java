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
package org.apache.activemq.artemis.utils.critical;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQComponent;

public interface CriticalAnalyzer extends ActiveMQComponent {

   default void clear() {
   }

   default int getNumberOfComponents() {
      return 0;
   }

   boolean isMeasuring();

   void add(CriticalComponent component);

   void remove(CriticalComponent component);

   CriticalAnalyzer setCheckTime(long timeout, TimeUnit unit);

   long getCheckTimeNanoSeconds();

   CriticalAnalyzer setTimeout(long timeout, TimeUnit unit);

   long getTimeout(TimeUnit unit);

   long getTimeoutNanoSeconds();

   CriticalAnalyzer addAction(CriticalAction action);

   void check();
}
