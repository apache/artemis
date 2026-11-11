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
package org.apache.activemq.artemis.utils;

public class SimpleIDGenerator implements IDGenerator {

   private long idSequence;

   private boolean wrapped;

   public SimpleIDGenerator(final long startID) {
      idSequence = startID;
   }

   @Override
   public synchronized long generateID() {
      long id = idSequence++;

      if (idSequence == Long.MIN_VALUE) {
         wrapped = true;
      }

      if (wrapped) {
         // Wrap - Very unlikely to happen
         throw new IllegalStateException("Exhausted ids to use!");
      }

      return id;
   }

   @Override
   public synchronized long getCurrentID() {
      return idSequence;
   }
}
