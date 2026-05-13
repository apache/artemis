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

package org.apache.artemis.nativo.jlibaio.util;

import org.apache.artemis.nativo.jlibaio.SubmitInfo;

/**
 * this is an utility class where you can reuse Callback objects for your LibaioContext usage.
 */
public class CallbackCache<Callback extends SubmitInfo> {

   private final SubmitInfo[] pool;

   private int put = 0;
   private int get = 0;
   private int available = 0;
   private final int size;

   private final Object lock = new Object();

   public CallbackCache(int size) {
      this.pool = new SubmitInfo[size];
      this.size = size;
   }

   public Callback get() {
      synchronized (lock) {
         if (available <= 0) {
            return null;
         } else {
            Callback retValue = (Callback) pool[get];
            pool[get] = null;
            if (retValue == null) {
               throw new NullPointerException("You should initialize the pool before using it");
            }
            available--;
            get++;
            if (get >= size) {
               get = 0;
            }
            return retValue;
         }
      }
   }

   public CallbackCache put(Callback callback) {
      if (callback == null) {
         return null;
      }
      synchronized (lock) {
         if (available < size) {
            available++;
            pool[put++] = callback;
            if (put >= size) {
               put = 0;
            }
         }
      }
      return this;
   }
}
