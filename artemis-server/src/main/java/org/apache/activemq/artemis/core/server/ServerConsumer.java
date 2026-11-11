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
import java.util.Map;
import java.util.function.Function;

import org.apache.activemq.artemis.core.transaction.Transaction;

public interface ServerConsumer extends Consumer, ConsumerInfo {

   void setlowConsumerDetection(SlowConsumerDetectionListener listener);

   SlowConsumerDetectionListener getSlowConsumerDetecion();

   void fireSlowConsumer();

   /**
    * The current queue settings will allow use of the Reference Execution and callback.
    */
   boolean allowReferenceCallback();

   /**
    * this is to be used with anything specific on a protocol head.
    */
   Object getProtocolData();

   /**
    * this is to be used with anything specific on a protocol head.
    */
   void setProtocolData(Object protocolData);

   void setProtocolContext(Object protocolContext);

   /**
    * An object set by the Protocol implementation. it could be anything pre-determined by the implementation
    */
   Object getProtocolContext();

   long getID();

   Object getConnectionID();

   void close(boolean failed) throws Exception;

   /**
    * This method is just to remove itself from Queues. If for any reason during a close an exception occurred, the
    * exception treatment will call removeItself what should take the consumer out of any queues.
    */
   void removeItself() throws Exception;

   List<MessageReference> cancelRefs(boolean failed, boolean lastConsumedAsDelivered, Transaction tx) throws Exception;

   void setStarted(boolean started);

   void receiveCredits(int credits);

   Queue getQueue();

   MessageReference removeReferenceByID(long messageID) throws Exception;

   /**
    * Some protocols may choose to send the message back to delivering instead of redeliver. For example openwire will
    * redeliver through the client, so messages will go back to delivering list after rollback.
    */
   void backToDelivering(MessageReference reference);

   List<MessageReference> scanDeliveringReferences(boolean remove,
                                                   Function<MessageReference, Boolean> startFunction,
                                                   Function<MessageReference, Boolean> endFunction);

   List<Long> acknowledge(Transaction tx, long messageID) throws Exception;

   void individualAcknowledge(Transaction tx, long messageID) throws Exception;

   void reject(long messageID) throws Exception;

   void individualCancel(long messageID, boolean failed) throws Exception;

   void forceDelivery(long sequence);

   void setTransferring(boolean transferring);

   boolean isBrowseOnly();

   long getCreationTime();

   String getSessionID();

   /**
    * This is needed when some protocols (OW) handle the acks themselves and need to update the metrics
    *
    * @param ref         the message reference
    * @param transaction the tx
    */
   void metricsAcknowledge(MessageReference ref, Transaction transaction);

   /**
    * Adds the given attachment to the {@link ServerConsumer} which will overwrite any previously
    * assigned value with the same key.
    *
    * @param key
    *    The key used to identify the attachment.
    * @param attachment
    *    The actual value to store for the assigned key.
    */
   void addAttachment(String key, Object attachment);

   /**
    * Remove the any attachment entry from the {@link ServerConsumer} clearing any previously assigned value
    *
    * @param key
    *    The key used to identify the attachment.
    */
   void removeAttachment(String key);

   /**
    * Gets any attachment that has been assigned to this {@link ServerConsumer} using the provided key.
    * If no value was assigned a null is returned.
    *
    * @param key
    *    The key identifying the target attachment.
    *
    * @return the assigned value associated with the given key or null if nothing assigned.
    */
   Object getAttachment(String key);

   /**
    * Provides access to the full {@link Map} of consumer attachments in an unmodifiable {@link Map} instance.
    * If no attachments are assigned to the consumer an empty {@link Map} instance is returned, never null.
    *
    * @return an unmodifiable {@link Map} that carries all consumer attachments.
    */
   Map<String, Object> getAttachments();

}