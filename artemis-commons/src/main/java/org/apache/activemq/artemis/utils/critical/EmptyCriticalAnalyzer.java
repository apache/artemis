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

public class EmptyCriticalAnalyzer implements CriticalAnalyzer {

   private static final EmptyCriticalAnalyzer instance = new EmptyCriticalAnalyzer();

   public static EmptyCriticalAnalyzer getInstance() {
      return instance;
   }

   private EmptyCriticalAnalyzer() {
   }

   @Override
   public void add(CriticalComponent component) {

   }

   @Override
   public void remove(CriticalComponent component) {

   }

   @Override
   public boolean isMeasuring() {
      return false;
   }

   @Override
   public void start() throws Exception {

   }

   @Override
   public void stop() throws Exception {

   }

   @Override
   public long getTimeoutNanoSeconds() {
      return 0;
   }

   @Override
   public boolean isStarted() {
      return false;
   }

   @Override
   public CriticalAnalyzer setCheckTime(long timeout, TimeUnit unit) {
      return this;
   }

   @Override
   public long getCheckTimeNanoSeconds() {
      return 0;
   }

   @Override
   public CriticalAnalyzer setTimeout(long timeout, TimeUnit unit) {
      return this;
   }

   @Override
   public long getTimeout(TimeUnit unit) {
      return 0;
   }

   @Override
   public CriticalAnalyzer addAction(CriticalAction action) {
      return this;
   }

   @Override
   public void check() {

   }
}
