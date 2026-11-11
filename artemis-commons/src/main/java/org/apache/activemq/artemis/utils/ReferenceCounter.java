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

public interface ReferenceCounter {

   int increment();

   int decrement();

   int getCount();


   void setTask(Runnable task);

   Runnable getTask();

   /**
    * Some asynchronous operations (like ack) may delay certain conditions. After met, during afterCompletion we may
    * need to recheck certain values to make sure we won't get into a situation where the condition was met
    * asynchronously and queues not removed.
    */
   void check();

}
