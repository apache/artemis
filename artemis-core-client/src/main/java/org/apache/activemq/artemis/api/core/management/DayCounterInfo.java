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
package org.apache.activemq.artemis.api.core.management;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.utils.JsonLoader;

/**
 * Helper class to create Java Objects from the JSON serialization returned by
 * {@link QueueControl#listMessageCounterHistory()}.
 */
public final class DayCounterInfo {

   private final String date;

   private final long[] counters;


   public static String toJSON(final DayCounterInfo[] infos) {
      JsonObjectBuilder json = JsonLoader.createObjectBuilder();
      JsonArrayBuilder counters = JsonLoader.createArrayBuilder();
      for (DayCounterInfo info : infos) {
         JsonArrayBuilder counter = JsonLoader.createArrayBuilder();
         for (long c : info.getCounters()) {
            counter.add(c);
         }
         JsonObjectBuilder dci = JsonLoader.createObjectBuilder().add("date", info.getDate()).add("counters", counter);
         counters.add(dci);
      }
      json.add("dayCounters", counters);
      return json.build().toString();
   }

   /**
    * {@return an array of RoleInfo corresponding to the JSON serialization returned by {@link
    * QueueControl#listMessageCounterHistory()}}
    */
   public static DayCounterInfo[] fromJSON(final String jsonString) {
      JsonObject json = JsonUtil.readJsonObject(jsonString);
      JsonArray dayCounters = json.getJsonArray("dayCounters");
      DayCounterInfo[] infos = new DayCounterInfo[dayCounters.size()];
      for (int i = 0; i < dayCounters.size(); i++) {

         JsonObject counter = (JsonObject) dayCounters.get(i);
         JsonArray hour = counter.getJsonArray("counters");
         long[] hourCounters = new long[24];
         for (int j = 0; j < 24; j++) {
            hourCounters[j] = hour.getInt(j);
         }
         DayCounterInfo info = new DayCounterInfo(counter.getString("date"), hourCounters);
         infos[i] = info;
      }
      return infos;
   }


   public DayCounterInfo(final String date, final long[] counters) {
      this.date = date;
      this.counters = counters;
   }

   public String getDate() {
      return date;
   }

   /**
    * {@return a 24-length array corresponding to the number of messages added to the queue
    * for the given hour of the day}
    */
   public long[] getCounters() {
      return counters;
   }
}
