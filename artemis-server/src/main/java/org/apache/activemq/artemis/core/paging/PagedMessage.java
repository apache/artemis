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
package org.apache.activemq.artemis.core.paging;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.persistence.StorageManager;

/**
 * A Paged message.
 * <p>
 * We can't just record the ServerMessage as we need other information (such as the TransactionID used during paging)
 */
public interface PagedMessage extends EncodingSupport {

   Message getMessage();

   PagePosition newPositionObject();

   /**
    * The queues that were routed during paging
    */
   long[] getQueueIDs();

   void initMessage(StorageManager storageManager);

   long getTransactionID();

   /**
    * This is the size of the message when persisted on disk and is used for metrics tracking If a normal message it
    * will be the encoded message size If a large message it will be encoded message size + large message body size
    */
   long getPersistentSize() throws ActiveMQException;

   /**
    * This returns how much the PagedMessage used, or it's going to use from storage. We can't calculate the encodeSize
    * as some persisters don't guarantee to re-store the data at the same amount of bytes it used. In some cases it may
    * need to add headers in AMQP or extra data that may affect the outcome of getEncodeSize()
    */
   int getStoredSize();

   long getPageNumber();

   PagedMessage setPageNumber(long pageNr);

   int getMessageNumber();

   PagedMessage setMessageNumber(int messageNr);

}
