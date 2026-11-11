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
package org.apache.activemq.artemis.core.server;

import java.util.List;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.transaction.Transaction;

public interface ScheduledDeliveryHandler {

   boolean checkAndSchedule(MessageReference ref, boolean tail);

   int getScheduledCount();

   int getNonPagedScheduledCount();

   long getScheduledSize();

   long getNonPagedScheduledSize();

   int getDurableScheduledCount();

   int getNonPagedDurableScheduledCount();

   long getDurableScheduledSize();

   long getNonPagedDurableScheduledSize();

   MessageReference peekFirstScheduledMessage();

   List<MessageReference> getScheduledReferences();

   List<MessageReference> cancel(Predicate<MessageReference> predicate) throws ActiveMQException;

   MessageReference removeReferenceWithID(long id) throws Exception;

   MessageReference removeReferenceWithID(long id, Transaction tx) throws Exception;
}
