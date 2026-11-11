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
package org.apache.activemq.artemis.core.messagecounter.impl;

import java.text.DateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.activemq.artemis.api.core.management.DayCounterInfo;
import org.apache.activemq.artemis.core.messagecounter.MessageCounter;
import org.apache.activemq.artemis.core.messagecounter.MessageCounter.DayCounter;

public class MessageCounterHelper {


   public static String listMessageCounterHistory(final MessageCounter counter) throws Exception {
      List<DayCounter> history = counter.getHistory();
      DayCounterInfo[] infos = new DayCounterInfo[history.size()];
      for (int i = 0; i < infos.length; i++) {
         DayCounter dayCounter = history.get(i);
         long[] counters = dayCounter.getCounters();
         GregorianCalendar date = dayCounter.getDate();

         DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
         String strData = dateFormat.format(date.getTime());
         infos[i] = new DayCounterInfo(strData, counters);
      }
      return DayCounterInfo.toJSON(infos);
   }

   @Deprecated
   public static String listMessageCounterAsHTML(final MessageCounter[] counters) {
      if (counters == null) {
         return null;
      }

      String ret0 = "<table class=\"activemq-message-counter\">\n" + "<tr>" + "<th>Type</th>" + "<th>Name</th>" + "<th>Subscription</th>" + "<th>Durable</th>" + "<th>Count</th>" + "<th>CountDelta</th>" + "<th>Depth</th>" + "<th>DepthDelta</th>" + "<th>Last Add</th>" + "<th>Last Ack</th>" + "<th>Last Update</th>" + "</tr>\n";
      StringBuilder ret = new StringBuilder(ret0);
      for (int i = 0; i < counters.length; i++) {
         MessageCounter counter = counters[i];
         String type = counter.isDestinationTopic() ? "Topic" : "Queue";
         String subscription = counter.getDestinationSubscription();
         if (subscription == null) {
            subscription = "-";
         }
         String durableStr = "-"; // makes no sense for a queue
         if (counter.isDestinationTopic()) {
            durableStr = Boolean.toString(counter.isDestinationDurable());
         }
         ret.append("<tr bgcolor=\"#" + (i % 2 == 0 ? "FFFFFF" : "F0F0F0") + "\">");

         ret.append("<td>" + type + "</td>");
         ret.append("<td>" + counter.getDestinationName() + "</td>");
         ret.append("<td>" + subscription + "</td>");
         ret.append("<td>" + durableStr + "</td>");
         ret.append("<td>" + counter.getCount() + "</td>");
         ret.append("<td>" + MessageCounterHelper.prettify(counter.getCountDelta()) + "</td>");
         ret.append("<td>" + MessageCounterHelper.prettify(counter.getMessageCount()) + "</td>");
         ret.append("<td>" + MessageCounterHelper.prettify(counter.getMessageCountDelta()) + "</td>");
         ret.append("<td>" + MessageCounterHelper.asDate(counter.getLastAddedMessageTime()) + "</td>");
         ret.append("<td>" + MessageCounterHelper.asDate(counter.getLastAckedMessageTime()) + "</td>");
         ret.append("<td>" + MessageCounterHelper.asDate(counter.getLastUpdate()) + "</td>");

         ret.append("</tr>\n");
      }

      ret.append("</table>\n");

      return ret.toString();
   }

   @Deprecated
   public static String listMessageCounterHistoryAsHTML(final MessageCounter[] counters) {
      if (counters == null) {
         return null;
      }

      StringBuilder ret = new StringBuilder("<ul>\n");

      for (MessageCounter counter : counters) {
         ret.append("<li>\n");
         ret.append("  <ul>\n");

         ret.append("    <li>");
         // destination name
         ret.append((counter.isDestinationTopic() ? "Topic '" : "Queue '") + counter.getDestinationName() + "'");
         ret.append("</li>\n");

         if (counter.getDestinationSubscription() != null) {
            ret.append("    <li>");
            ret.append("Subscription '" + counter.getDestinationSubscription() + "'");
            ret.append("</li>\n");
         }

         ret.append("    <li>");
         // table header
         ret.append("<table class=\"activemq-message-counter-history\">\n");
         ret.append("<tr><th>Date</th>");

         for (int j = 0; j < 24; j++) {
            ret.append("<th>" + j + "</th>");
         }

         ret.append("<th>Total</th></tr>\n");

         // get history data as CSV string
         StringTokenizer tokens = new StringTokenizer(counter.getHistoryAsString(), ",\n");

         // get history day count
         int days = Integer.parseInt(tokens.nextToken());

         for (int j = 0; j < days; j++) {
            // next day counter row
            ret.append("<tr bgcolor=\"#" + (j % 2 == 0 ? "FFFFFF" : "F0F0F0") + "\">");

            // date
            ret.append("<td>" + tokens.nextToken() + "</td>");

            // 24 hour counters
            int total = 0;

            for (int k = 0; k < 24; k++) {
               int value = Integer.parseInt(tokens.nextToken().trim());

               if (value == -1) {
                  ret.append("<td></td>");
               } else {
                  ret.append("<td>" + value + "</td>");

                  total += value;
               }
            }

            ret.append("<td>" + total + "</td></tr>\n");
         }

         ret.append("</table></li>\n");
         ret.append("  </ul>\n");
         ret.append("</li>\n");
      }

      ret.append("</ul>\n");

      return ret.toString();
   }

   private static String prettify(final long value) {
      if (value == 0) {
         return "-";
      }
      return Long.toString(value);
   }

   private static String asDate(final long time) {
      if (time > 0) {
         return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(time));
      } else {
         return "-";
      }
   }



}
