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
package org.apache.activemq.artemis.core.journal;

import java.util.Objects;

/**
 * This is a POJO containing information about the journal during load time.
 */
public class JournalLoadInformation {

   private int numberOfRecords = 0;

   private long maxID = -1;

   public JournalLoadInformation() {
      super();
   }

   public JournalLoadInformation(final int numberOfRecords, final long maxID) {
      super();
      this.numberOfRecords = numberOfRecords;
      this.maxID = maxID;
   }

   public int getNumberOfRecords() {
      return numberOfRecords;
   }

   public void setNumberOfRecords(final int numberOfRecords) {
      this.numberOfRecords = numberOfRecords;
   }

   public long getMaxID() {
      return maxID;
   }

   public void setMaxID(final long maxID) {
      this.maxID = maxID;
   }

   @Override
   public int hashCode() {
      return Objects.hash(maxID, numberOfRecords);
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof JournalLoadInformation other)) {
         return false;
      }

      return maxID == other.maxID &&
             numberOfRecords == other.numberOfRecords;
   }

   @Override
   public String toString() {
      return "JournalLoadInformation [maxID=" + maxID + ", numberOfRecords=" + numberOfRecords + "]";
   }
}
