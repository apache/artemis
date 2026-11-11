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
package org.apache.activemq.artemis.core.io;

import java.util.Collection;

import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;

/**
 * The interface used for AIO Callbacks.
 */
public interface IOCallback {

   /**
    * Method for sync notifications. When this callback method is called, there is a guarantee the data is written on
    * the disk.
    * <p>
    * <b>Note:</b><i>Leave this method as soon as possible, or you would be blocking the whole notification thread</i>
    */
   void done();

   /**
    * Method for error notifications. Observation: The whole file will be probably failing if this happens. Like, if you
    * delete the file, you will start to get errors for these operations
    */
   void onError(int errorCode, String errorMessage);

   static void done(Collection<? extends IOCallback> delegates) {
      if (delegates != null) {
         delegates.forEach(callback -> {
            try {
               callback.done();
            } catch (Throwable e) {
               ActiveMQJournalLogger.LOGGER.errorCompletingCallback(e);
            }
         });
      }
   }

   static void onError(Collection<? extends IOCallback> delegates, int errorCode, final String errorMessage) {
      delegates.forEach(callback -> {
         try {
            callback.onError(errorCode, errorMessage);
         } catch (Throwable e) {
            ActiveMQJournalLogger.LOGGER.errorCallingErrorCallback(e);
         }
      });
   }
}
