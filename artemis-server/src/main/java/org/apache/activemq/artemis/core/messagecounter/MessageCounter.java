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
package org.apache.activemq.artemis.core.messagecounter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.utils.JsonLoader;

/**
 * This class stores message count informations for a given queue
 * <p>
 * At intervals this class samples the queue for message count data
 * <p>
 * Note that the underlying queue *does not* update statistics every time a message is added since that would reall slow
 * things down, instead we *sample* the queues at regular intervals - this means we are less intrusive on the queue
 */
public class MessageCounter {


   // destination related information
   private final String destName;

   private final String destSubscription;

   private final boolean destTopic;

   private final boolean destDurable;

   private final Queue serverQueue;

   // counter
   private long countTotal;

   private long countTotalLast;

   private long depthLast;

   private long timeLastUpdate;

   private long timeLastAdd;

   private long timeLastAck;

   // per hour day counter history
   private int dayCounterMax;

   private final List<DayCounter> dayCounters;

   private long lastMessagesAdded;

   private long lastMessagesAcked;

   /**
    * Constructor
    *
    * @param name         destination name
    * @param subscription subscription name
    * @param serverQueue  internal queue object
    * @param topic        topic destination flag
    * @param durable      durable subscription flag
    * @param daycountmax  max message history day count
    */
   public MessageCounter(final String name,
                         final String subscription,
                         final Queue serverQueue,
                         final boolean topic,
                         final boolean durable,
                         final int daycountmax) {
      // store destination related information
      destName = name;
      destSubscription = subscription;
      destTopic = topic;
      destDurable = durable;
      this.serverQueue = serverQueue;

      // initialize counter
      resetCounter();

      // initialize message history
      dayCounters = new ArrayList<>();

      setHistoryLimit(daycountmax);
   }

   private final Runnable onTimeExecutor = new Runnable() {
      @Override
      public void run() {
         long latestMessagesAdded = serverQueue.getMessagesAdded();
         long latestMessagesAcked = serverQueue.getMessagesAcknowledged();

         long newMessagesAdded = latestMessagesAdded - lastMessagesAdded;
         long newMessagesAcked = latestMessagesAcked - lastMessagesAcked;

         countTotal += newMessagesAdded;

         lastMessagesAdded = latestMessagesAdded;
         lastMessagesAcked = latestMessagesAcked;

         long timestamp = System.currentTimeMillis();

         if (newMessagesAdded > 0) {
            timeLastAdd = timestamp;
         }

         if (newMessagesAcked > 0) {
            timeLastAck = timestamp;
         }

         // update timestamp
         timeLastUpdate = timestamp;

         // update message history
         updateHistory(newMessagesAdded);

      }
   };


   /**
    * This method is called periodically to update statistics from the queue
    */
   public synchronized void onTimer() {
      // Actor approach here: Instead of having the Counter locking the queue, we will use the Queue's executor
      // instead of possibly making a lock on the queue.
      // This way the scheduled Threads will be free to keep doing their pings in case the server is busy with paging or
      // any other deliveries
      serverQueue.getExecutor().execute(onTimeExecutor);
   }

   public String getDestinationName() {
      return destName;
   }

   public String getDestinationSubscription() {
      return destSubscription;
   }

   public boolean isDestinationTopic() {
      return destTopic;
   }

   public boolean isDestinationDurable() {
      return destDurable;
   }

   /**
    * Gets the total message count since startup or last counter reset
    */
   public long getCount() {
      return countTotal;
   }

   /**
    * Gets the message count delta since last method call
    */
   public long getCountDelta() {
      long delta = countTotal - countTotalLast;

      countTotalLast = countTotal;

      return delta;
   }

   /**
    * Gets the current message count of pending messages within the destination waiting for dispatch
    */
   public long getMessageCount() {
      return serverQueue.getMessageCount();
   }

   /**
    * Gets the message count delta of pending messages since last method call.
    */
   public long getMessageCountDelta() {
      long current = serverQueue.getMessageCount();
      int delta = (int) (current - depthLast);

      depthLast = current;

      return delta;
   }

   public long getLastUpdate() {
      return timeLastUpdate;
   }

   public long getLastAddedMessageTime() {
      return timeLastAdd;
   }

   public long getLastAckedMessageTime() {
      return timeLastAck;
   }

   public void resetCounter() {
      countTotal = 0;
      countTotalLast = 0;
      depthLast = 0;
      timeLastUpdate = 0;
      timeLastAdd = 0;
      timeLastAck = 0;
   }

   private void setHistoryLimit(final int daycountmax) {
      boolean bInitialize = false;

      // store new maximum day count
      dayCounterMax = daycountmax;

      // update day counter array
      synchronized (dayCounters) {
         if (dayCounterMax > 0) {
            // limit day history to specified day count
            int delta = dayCounters.size() - dayCounterMax;

            for (int i = 0; i < delta; i++) {
               // reduce array size to requested size by dropping
               // oldest day counters
               dayCounters.remove(0);
            }

            // create initial day counter when empty
            bInitialize = dayCounters.isEmpty();
         } else if (dayCounterMax == 0) {
            // disable history
            dayCounters.clear();
         } else {
            // unlimited day history

            // create initial day counter when empty
            bInitialize = dayCounters.isEmpty();
         }

         // optionally initialize first day counter entry
         if (bInitialize) {
            dayCounters.add(new DayCounter(new GregorianCalendar(), true));
         }
      }
   }

   public void resetHistory() {
      int max = dayCounterMax;

      setHistoryLimit(0);
      setHistoryLimit(max);
   }

   public List<DayCounter> getHistory() {
      updateHistory(0);

      return new ArrayList<>(dayCounters);
   }

   /**
    * Get message counter history data as string in format
    * <pre>
    * day count
    * Date 1, hour counter 0, hour counter 1, ..., hour counter 23
    * Date 2, hour counter 0, hour counter 1, ..., hour counter 23
    * .....
    * .....
    * Date n, hour counter 0, hour counter 1, ..., hour counter 23
    * </pre>
    * @return String   message history data string
    */
   public String getHistoryAsString() {
      StringBuilder ret = new StringBuilder();

      // ensure history counters are up to date
      updateHistory(0);

      // compile string
      synchronized (dayCounters) {
         // first line: history day count
         ret.append(dayCounters.size() + "\n");

         // following lines: day counter data
         for (DayCounter counter : dayCounters) {
            ret.append(counter.getDayCounterAsString() + "\n");
         }
      }

      return ret.toString();
   }

   @Override
   public String toString() {
      return "MessageCounter[destName" + destName +
         ", destSubscription=" +
         destSubscription +
         ", destTopic=" +
         destTopic +
         ", destDurable=" +
         destDurable +
         ", serverQueue =" +
         serverQueue +
         "]";
   }

   /**
    * {@return a JSON String serialization of a {@link MessageCounter} object}
    */
   public String toJSon() {
      DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
      String lastAddTimestamp = dateFormat.format(new Date(this.getLastAddedMessageTime()));
      String lastAckTimestamp = dateFormat.format(new Date(this.getLastAckedMessageTime()));
      String updateTimestamp = dateFormat.format(new Date(this.getLastUpdate()));
      return JsonLoader
         .createObjectBuilder()
         .add("destinationName", this.getDestinationName(), JsonValue.NULL)
         .add("destinationSubscription", this.getDestinationSubscription(), JsonValue.NULL)
         .add("destinationDurable", this.isDestinationDurable())
         .add("count", this.getCount())
         .add("countDelta", this.getCountDelta())
         .add("messageCount", this.getMessageCount())
         .add("messageCountDelta", this.getMessageCountDelta())
         .add("lastAddTimestamp", lastAddTimestamp)
         .add("lastAckTimestamp", lastAckTimestamp)
         .add("updateTimestamp", updateTimestamp)
         .build()
         .toString();
   }

   /**
    * Update message counter history
    *
    * @param newMessages number of new messages to add to the latest day counter
    */
   private void updateHistory(final long newMessages) {
      // check history activation
      if (dayCounters.isEmpty()) {
         return;
      }

      // calculate day difference between current date and date of last day counter entry
      synchronized (dayCounters) {
         DayCounter counterLast = dayCounters.get(dayCounters.size() - 1);

         GregorianCalendar calNow = new GregorianCalendar();
         GregorianCalendar calLast = counterLast.getDate();

         // clip day time part for day delta calulation
         calNow.clear(Calendar.AM_PM);
         calNow.clear(Calendar.HOUR);
         calNow.clear(Calendar.HOUR_OF_DAY);
         calNow.clear(Calendar.MINUTE);
         calNow.clear(Calendar.SECOND);
         calNow.clear(Calendar.MILLISECOND);

         calLast.clear(Calendar.AM_PM);
         calLast.clear(Calendar.HOUR);
         calLast.clear(Calendar.HOUR_OF_DAY);
         calLast.clear(Calendar.MINUTE);
         calLast.clear(Calendar.SECOND);
         calLast.clear(Calendar.MILLISECOND);

         long millisPerDay = 86400000; // 24 * 60 * 60 * 1000
         long millisDelta = calNow.getTime().getTime() - calLast.getTime().getTime();

         int dayDelta = (int) (millisDelta / millisPerDay);

         if (dayDelta > 0) {
            // finalize last day counter
            counterLast.finalizeDayCounter();

            // add new intermediate empty day counter entries
            DayCounter counterNew;

            for (int i = 1; i < dayDelta; i++) {
               // increment date
               calLast.add(Calendar.DAY_OF_YEAR, 1);

               counterNew = new DayCounter(calLast, false);
               counterNew.finalizeDayCounter();

               dayCounters.add(counterNew);
            }

            // add new day counter entry for current day
            counterNew = new DayCounter(calNow, false);

            dayCounters.add(counterNew);

            // ensure history day count limit
            setHistoryLimit(dayCounterMax);
         }

         // update last day counter entry
         counterLast = dayCounters.get(dayCounters.size() - 1);
         counterLast.updateDayCounter(newMessages);
      }
   }


   /**
    * Internal day counter class for one day hour based counter history
    */
   public static final class DayCounter {

      static final int HOURS = 24;

      GregorianCalendar date = null;

      long[] counters = new long[DayCounter.HOURS];

      /**
       * Constructor
       *
       * @param date       day counter date
       * @param isStartDay true first day counter false follow up day counter
       */
      DayCounter(final GregorianCalendar date, final boolean isStartDay) {
         // store internal copy of creation date
         this.date = (GregorianCalendar) date.clone();

         // initialize the array with '0'- values to current hour (if it is not the
         // first monitored day) and the rest with default values ('-1')
         int hour = date.get(Calendar.HOUR_OF_DAY);

         for (int i = 0; i < DayCounter.HOURS; i++) {
            if (i < hour) {
               if (isStartDay) {
                  counters[i] = -1L;
               } else {
                  counters[i] = 0L;
               }
            } else {
               counters[i] = -1L;
            }
         }

         // set the array element of the current hour to '0'
         counters[hour] = 0L;
      }

      /**
       * Gets copy of day counter date
       *
       * @return GregorianCalendar        day counter date
       */
      public GregorianCalendar getDate() {
         return (GregorianCalendar) date.clone();
      }

      public long[] getCounters() {
         return counters;
      }

      /**
       * Update day counter hour array elements
       *
       * @param newMessages number of new messages since the counter was last updated.
       */
      void updateDayCounter(final long newMessages) {
         // get the current hour of the day
         GregorianCalendar cal = new GregorianCalendar();

         int currentIndex = cal.get(Calendar.HOUR_OF_DAY);

         // check if the last array update is more than 1 hour ago, if so fill all
         // array elements between the last index and the current index with '0' values
         boolean bUpdate = false;

         for (int i = 0; i <= currentIndex; i++) {
            if (counters[i] > -1) {
               // found first initialized hour counter
               // -> set all following uninitialized
               // counter values to 0
               bUpdate = true;
            }

            if (bUpdate == true) {
               if (counters[i] == -1) {
                  counters[i] = 0;
               }
            }
         }

         // increment current counter with the new messages
         counters[currentIndex] += newMessages;
      }

      /**
       * Finalize day counter hour array elements
       */
      private void finalizeDayCounter() {
         // a new day has began, so fill all array elements from index to end with
         // '0' values
         boolean bFinalize = false;

         for (int i = 0; i < DayCounter.HOURS; i++) {
            if (counters[i] > -1) {
               // found first initialized hour counter
               // -> finalize all following uninitialized
               // counter values
               bFinalize = true;
            }

            if (bFinalize) {
               if (counters[i] == -1) {
                  counters[i] = 0;
               }
            }
         }
      }

      /**
       * Return day counter data as string with format
       * <pre>
       * Date, hour counter 0, hour counter 1, ..., hour counter 23
       * </pre>
       *
       * @return String day counter data
       */
      private String getDayCounterAsString() {
         // first element day counter date
         DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);

         StringBuilder strData = new StringBuilder(dateFormat.format(date.getTime()));

         // append 24 comma separated hour counter values
         for (int i = 0; i < DayCounter.HOURS; i++) {
            strData.append("," + counters[i]);
         }

         return strData.toString();
      }
   }
}
