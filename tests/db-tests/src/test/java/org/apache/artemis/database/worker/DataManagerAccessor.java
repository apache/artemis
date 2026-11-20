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

package org.apache.artemis.database.worker;

import java.util.ArrayList;
import java.util.List;

import org.apache.artemis.database.data.DBData;
import org.apache.artemis.database.data.DeleteMessageData;

public class DataManagerAccessor {

   public static ArrayList<DBData> getPendingData(DataManager dataManager) {
      return dataManager.pendingData;
   }

   public static List<DBData> extractTaskList(DataManager dataManager) {
      return dataManager.extractTaskList();
   }

   public static void setCredits(DataManager dataManager, int credits) {
      dataManager.acquireLock();
      try {
         dataManager.setCredits(credits);
         dataManager.checkReleaseFlowControl();
      } finally {
         dataManager.releaseLock();
      }
   }

   public static int getCredits(DataManager dataManager) {
      return dataManager.getCredits();
   }

   public static int getOnReleaseSize(DataManager dataManager) {
      return dataManager.onRelease.size();
   }

   public static void addMockPendingData(DataManager dataManager, int count) {
      for (int i = 0; i < count; i++) {
         dataManager.pendingData.add(new DeleteMessageData(i, null));
         dataManager.incrementCredit();
      }
   }
}
