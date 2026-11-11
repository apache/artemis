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

/**
 * This is not abstract as it could be used through aggregations or extensions. This is only good for cases where you
 * call leave within the same thread as you called enter.
 */
public class CriticalComponentImpl implements CriticalComponent {

   private final CriticalMeasure[] measures;
   private final CriticalAnalyzer analyzer;

   @Override
   public CriticalAnalyzer getCriticalAnalyzer() {
      return analyzer;
   }

   public CriticalComponentImpl(CriticalAnalyzer analyzer, int numberOfPaths) {
      if (analyzer == null) {
         analyzer = EmptyCriticalAnalyzer.getInstance();
      }
      this.analyzer = analyzer;

      if (analyzer.isMeasuring()) {
         measures = new CriticalMeasure[numberOfPaths];
         for (int i = 0; i < numberOfPaths; i++) {
            measures[i] = new CriticalMeasure(this, i);
         }
      } else {
         measures = null;
      }
   }

   @Override
   public CriticalCloseable measureCritical(int path) {
      if (!analyzer.isMeasuring()) {
         return CriticalMeasure.dummyCloseable;
      } else {
         return measures[path].measure();
      }
   }

   protected void enterCritical(int path) {
      if (analyzer.isMeasuring()) {
         measures[path].enterCritical();
      }
   }

   protected void leaveCritical(int path) {
      if (analyzer.isMeasuring()) {
         measures[path].leaveCritical();
      }
   }


   @Override
   public boolean checkExpiration(long timeout, boolean reset) {
      for (int i = 0; i < measures.length; i++) {
         if (measures[i].checkExpiration(timeout, reset)) {
            return true;
         }
      }
      return false;
   }

}
