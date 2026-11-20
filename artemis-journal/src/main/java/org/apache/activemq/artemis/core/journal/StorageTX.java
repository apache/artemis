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

/**
 * This interface was created specifically to allow the JDBC storage manager
 * to hold data until commit is called.
 * In case the storage manager decide to hold all the Transaction date until a commit happens,
 * the storage may decide to add any special data for that purpose on implementations of this interface.
 *
 * This interface is pretty much a tagging interface, and all the implementation will endup being specific to the storage itself. */
public interface StorageTX {

   long getId();

   void setContext(IOCompletion context);

   IOCompletion getContext();

   // TODO-important: remove this
   boolean isEmpty();

   void beforeIO();

   void afterIO();

}
