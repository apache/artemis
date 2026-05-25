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

import javax.management.MBeanOperationInfo;
import javax.management.openmbean.CompositeData;
import java.util.Map;

/**
 * A QueueControl is used to manage a queue.
 */
public interface QueueControl {
   String MESSAGE_COUNT_DESCRIPTION = "number of messages currently in this queue (includes scheduled, paged, and in-delivery messages)";
   String DURABLE_MESSAGE_COUNT_DESCRIPTION = "number of durable messages currently in this queue (includes scheduled, paged, and in-delivery messages)";
   String PERSISTENT_SIZE_DESCRIPTION = "persistent size of all messages (including durable and non-durable) currently in this queue (includes scheduled, paged, and in-delivery messages)";
   String DURABLE_PERSISTENT_SIZE_DESCRIPTION = "persistent size of durable messages currently in this queue (includes scheduled, paged, and in-delivery messages)";

   String SCHEDULED_MESSAGE_COUNT_DESCRIPTION = "number of scheduled messages in this queue";
   String DURABLE_SCHEDULED_MESSAGE_COUNT_DESCRIPTION = "number of durable scheduled messages in this queue";
   String SCHEDULED_SIZE_DESCRIPTION = "persistent size of scheduled messages in this queue";
   String DURABLE_SCHEDULED_SIZE_DESCRIPTION = "persistent size of durable scheduled messages in this queue";

   String DELIVERING_MESSAGE_COUNT_DESCRIPTION = "number of messages that this queue is currently delivering to its consumers";
   String DURABLE_DELIVERING_MESSAGE_COUNT_DESCRIPTION = "number of durable messages that this queue is currently delivering to its consumers";
   String DELIVERING_SIZE_DESCRIPTION = "persistent size of messages that this queue is currently delivering to its consumers";
   String DURABLE_DELIVERING_SIZE_DESCRIPTION = "persistent size of durable messages that this queue is currently delivering to its consumers";

   String CONSUMER_COUNT_DESCRIPTION = "number of consumers consuming messages from this queue";
   String MESSAGES_ADDED_DESCRIPTION = "number of messages added to this queue since it was created";
   String MESSAGES_ACKNOWLEDGED_DESCRIPTION = "number of messages acknowledged from this queue since it was created";
   String MESSAGES_EXPIRED_DESCRIPTION = "number of messages expired from this queue since it was created";
   String MESSAGES_KILLED_DESCRIPTION = "number of messages removed from this queue since it was created due to exceeding the max delivery attempts";

   /**
    * {@return the name of this queue}
    */
   @Attribute(desc = "name of this queue")
   String getName();

   /**
    * {@return the address this queue is bound to}
    */
   @Attribute(desc = "address this queue is bound to")
   String getAddress();

   /**
    * {@return this queue ID}
    */
   @Attribute(desc = "ID of this queue")
   long getID();

   /**
    * {@return whether this queue is temporary}
    */
   @Attribute(desc = "whether this queue is temporary")
   boolean isTemporary();

   /**
    * {@return whether this queue is used for a retroactive address}
    */
   @Attribute(desc = "whether this queue is used for a retroactive address")
   boolean isRetroactiveResource();

   /**
    * {@return whether this queue is durable}
    */
   @Attribute(desc = "whether this queue is durable")
   boolean isDurable();

   /**
    * {@return the user that is associated with creating the queue}
    */
   @Attribute(desc = "the user that created the queue")
   String getUser();

   /**
    * The routing type of this queue.
    */
   @Attribute(desc = "routing type of this queue")
   String getRoutingType();

   /**
    * {@return the filter associated with this queue}
    */
   @Attribute(desc = "filter associated with this queue")
   String getFilter();

   /**
    * {@return the number of messages currently in this queue}
    */
   @Attribute(desc = MESSAGE_COUNT_DESCRIPTION)
   long getMessageCount();

   /**
    * {@return the persistent size of all messages currently in this queue; the persistent size of a message is the
    * amount of space the message would take up on disk which is used to track how much data there is to consume on this
    * queue}
    */
   @Attribute(desc = PERSISTENT_SIZE_DESCRIPTION)
   long getPersistentSize();

   /**
    * {@return the number of durable messages currently in this queue}
    */
   @Attribute(desc = DURABLE_MESSAGE_COUNT_DESCRIPTION)
   long getDurableMessageCount();

   /**
    * {@return the persistent size of durable messages currently in this queue; the persistent size of a message is the
    * amount of space the message would take up on disk which is used to track how much data there is to consume on this
    * queue}
    */
   @Attribute(desc = DURABLE_PERSISTENT_SIZE_DESCRIPTION)
   long getDurablePersistentSize();

   /**
    * {@return whether this queue was created for the broker's internal use}
    */
   @Attribute(desc = "whether this queue was created for the broker's internal use")
   boolean isInternalQueue();

   /**
    * {@return the number of scheduled messages in this queue}
    */
   @Attribute(desc = SCHEDULED_MESSAGE_COUNT_DESCRIPTION)
   long getScheduledCount();

   /**
    * {@return the size of scheduled messages in this queue}
    */
   @Attribute(desc = SCHEDULED_SIZE_DESCRIPTION)
   long getScheduledSize();

   /**
    * {@return the number of durable scheduled messages in this queue}
    */
   @Attribute(desc = DURABLE_SCHEDULED_MESSAGE_COUNT_DESCRIPTION)
   long getDurableScheduledCount();

   /**
    * {@return the size of durable scheduled messages in this queue}
    */
   @Attribute(desc = DURABLE_SCHEDULED_SIZE_DESCRIPTION)
   long getDurableScheduledSize();

   /**
    * {@return the number of consumers consuming messages from this queue}
    */
   @Attribute(desc = CONSUMER_COUNT_DESCRIPTION)
   int getConsumerCount();

   /**
    * {@return the number of messages that this queue is currently delivering to its consumers}
    */
   @Attribute(desc = DELIVERING_MESSAGE_COUNT_DESCRIPTION)
   int getDeliveringCount();

   /**
    * {@return the persistent size of messages that this queue is currently delivering to its consumers}
    */
   @Attribute(desc = DELIVERING_SIZE_DESCRIPTION)
   long getDeliveringSize();

   /**
    * {@return the number of durable messages that this queue is currently delivering to its consumers}
    */
   @Attribute(desc = DURABLE_DELIVERING_MESSAGE_COUNT_DESCRIPTION)
   int getDurableDeliveringCount();

   /**
    * {@return the size of durable messages that this queue is currently delivering to its consumers}
    */
   @Attribute(desc = DURABLE_DELIVERING_SIZE_DESCRIPTION)
   long getDurableDeliveringSize();

   /**
    * {@return the number of messages added to this queue since it was created}
    */
   @Attribute(desc = MESSAGES_ADDED_DESCRIPTION)
   long getMessagesAdded();

   /**
    * {@return the number of messages added to this queue since it was created}
    */
   @Attribute(desc = MESSAGES_ACKNOWLEDGED_DESCRIPTION)
   long getMessagesAcknowledged();

   /**
    * {@return the number of messages added to this queue since it was created}
    */
   @Attribute(desc = "number of messages acknowledged attempts from this queue since it was created")
   long getAcknowledgeAttempts();


   /**
    * {@return the number of messages expired from this queue since it was created}
    */
   @Attribute(desc = MESSAGES_EXPIRED_DESCRIPTION)
   long getMessagesExpired();

   /**
    * {@return the number of messages removed from this queue since it was created due to exceeding the max delivery
    * attempts}
    */
   @Attribute(desc = MESSAGES_KILLED_DESCRIPTION)
   long getMessagesKilled();

   /**
    * {@return the first message on the queue as JSON}
    */
   @Attribute(desc = "first message on the queue as JSON")
   String getFirstMessageAsJSON() throws Exception;

   /**
    * {@return the timestamp of the first message in milliseconds}
    */
   @Attribute(desc = "timestamp of the first message in milliseconds")
   Long getFirstMessageTimestamp() throws Exception;

   /**
    * {@return the age of the first message in milliseconds}
    */
   @Attribute(desc = "age of the first message in milliseconds")
   Long getFirstMessageAge() throws Exception;

   /**
    * {@return the expiry address associated with this queue}
    */
   @Attribute(desc = "expiry address associated with this queue")
   String getExpiryAddress();

   /**
    * {@return the dead-letter address associated with this queue}
    */
   @Attribute(desc = "dead-letter address associated with this queue")
   String getDeadLetterAddress();

   @Attribute(desc = "maximum number of consumers allowed on this queue at any one time")
   int getMaxConsumers();

   @Attribute(desc = "purge this queue when the last consumer disconnects")
   boolean isPurgeOnNoConsumers();

   @Attribute(desc = "if the queue is enabled, default it is enabled, when disabled messages will not be routed to the queue")
   boolean isEnabled();

   /**
    * Enables the queue. Messages are now routed to this queue.
    */
   @Operation(desc = "Enables routing of messages to the Queue", impact = MBeanOperationInfo.ACTION)
   void enable() throws Exception;

   /**
    * Enables the queue. Messages are not routed to this queue.
    */
   @Operation(desc = "Disables routing of messages to the Queue", impact = MBeanOperationInfo.ACTION)
   void disable() throws Exception;

   @Attribute(desc = "is this queue managed by configuration (broker.xml)")
   boolean isConfigurationManaged();

   @Attribute(desc = "If the queue should route exclusively to one consumer")
   boolean isExclusive();

   @Attribute(desc = "is this queue a last value queue")
   boolean isLastValue();

   /**
    * {@return key used for the last value queues}
    */
   @Attribute(desc = "last value key")
   String getLastValueKey();

   /**
    * {@return the Consumers Before Dispatch}
    */
   @Attribute(desc = "Return the Consumers Before Dispatch")
   int getConsumersBeforeDispatch();

   /**
    * {@return the Delay Before Dispatch}
    */
   @Attribute(desc = "Return the Delay Before Dispatch")
   long getDelayBeforeDispatch();

   // Operations ----------------------------------------------------

   /**
    * Lists all the messages scheduled for delivery for this queue.
    * <p>
    * 1 Map represents 1 message, keys are the message's properties and headers, values are the corresponding values.
    */
   @Operation(desc = "List the messages scheduled for delivery", impact = MBeanOperationInfo.INFO)
   Map<String, Object>[] listScheduledMessages() throws Exception;

   /**
    * Lists all the messages scheduled for delivery for this queue using JSON serialization.
    */
   @Operation(desc = "List the messages scheduled for delivery and returns them using JSON", impact = MBeanOperationInfo.INFO)
   String listScheduledMessagesAsJSON() throws Exception;

   /**
    * Lists all the messages being deliver per consumer.
    * <p>
    * The Map's key is a toString representation for the consumer. Each consumer will then return a
    * {@code Map<String,Object>[]} same way is returned by {@link #listScheduledMessages()}
    */
   @Operation(desc = "List all messages being delivered per consumer")
   Map<String, Map<String, Object>[]> listDeliveringMessages() throws Exception;

   /**
    * Executes a conversion of {@link #listDeliveringMessages()} to JSON
    */
   @Operation(desc = "list all messages being delivered per consumer using JSON form")
   String listDeliveringMessagesAsJSON() throws Exception;

   /**
    * Lists all the messages in this queue matching the specified filter.
    * <p>
    * 1 Map represents 1 message, keys are the message's properties and headers, values are the corresponding values.
    * <p>
    * Using {@code null} or an empty filter will list <em>all</em> messages from this queue.
    */
   @Operation(desc = "List all the messages in the queue matching the given filter", impact = MBeanOperationInfo.INFO)
   Map<String, Object>[] listMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   /**
    * Lists all the messages in this queue matching the specified filter using JSON serialization.
    * <p>
    * Using {@code null} or an empty filter will list <em>all</em> messages from this queue.
    */
   @Operation(desc = "List all the messages in the queue matching the given filter and returns them using JSON", impact = MBeanOperationInfo.INFO)
   String listMessagesAsJSON(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   /**
    * Counts the number of messages in this queue matching the specified filter.
    * <p>
    * Using {@code null} or an empty filter will count <em>all</em> messages from this queue.
    */
   @Operation(desc = "Returns the number of the messages in the queue matching the given filter", impact = MBeanOperationInfo.INFO)
   long countMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   @Operation(desc = "Returns the number of the messages in the queue", impact = MBeanOperationInfo.INFO)
   long countMessages() throws Exception;

   /**
    * Counts the number of messages in this queue matching the specified filter, grouped by the given property field.
    * In case of null property will be grouped in "null"
    * <p>
    * Using {@code null} or an empty filter will count <em>all</em> messages from this queue.
    */
   @Operation(desc = "Returns the number of the messages in the queue matching the given filter, grouped by the given property field", impact = MBeanOperationInfo.INFO)
   String countMessages(@Parameter(name = "filter", desc = "This filter separate account messages") String filter, @Parameter(name = "groupByProperty", desc = "This property to group by") String groupByProperty) throws Exception;

   /**
    * Counts the number of delivering messages in this queue matching the specified filter.
    * <p>
    * Using {@code null} or an empty filter will count <em>all</em> messages from this queue.
    */
   @Operation(desc = "Returns the number of the messages in the queue matching the given filter")
   long countDeliveringMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   /**
    * Counts the number of delivering messages in this queue matching the specified filter, grouped by the given
    * property field. In case of null property will be grouped in "null"
    * <p>
    * Using {@code null} or an empty filter will count <em>all</em> messages from this queue.
    */
   @Operation(desc = "Returns the number of the messages in the queue matching the given filter, grouped by the given property field")
   String countDeliveringMessages(@Parameter(name = "filter", desc = "This filter separate account messages") String filter, @Parameter(name = "groupByProperty", desc = "This property to group by") String groupByProperty) throws Exception;

   /**
    * Removes the message corresponding to the specified message ID.
    *
    * @return {@code true} if the message was removed, {@code false} else
    */
   @Operation(desc = "Remove the message corresponding to the given messageID", impact = MBeanOperationInfo.ACTION)
   boolean removeMessage(@Parameter(name = "messageID", desc = "A message ID") long messageID) throws Exception;

   /**
    * Removes all the message corresponding to the specified filter.
    * <p>
    * Using {@code null} or an empty filter will remove <em>all</em> messages from this queue.
    *
    * @return the number of removed messages
    */
   @Operation(desc = "Remove the messages corresponding to the given filter (and returns the number of removed messages)", impact = MBeanOperationInfo.ACTION)
   int removeMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   /**
    * Removes all the message corresponding to the specified filter.
    * <p>
    * Using {@code null} or an empty filter will remove <em>all</em> messages from this queue.
    *
    * @return the number of removed messages
    */
   @Operation(desc = "Remove the messages corresponding to the given filter (and returns the number of removed messages)", impact = MBeanOperationInfo.ACTION)
   int removeMessages(@Parameter(name = "flushLimit", desc = "Limit to flush transactions during the operation to avoid OutOfMemory") int flushLimit,
                      @Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   /**
    * Removes all the message from the queue.
    *
    * @return the number of removed messages
    */
   @Operation(desc = "Remove all the messages from the Queue (and returns the number of removed messages)", impact = MBeanOperationInfo.ACTION)
   int removeAllMessages() throws Exception;

   /**
    * Expires all the message corresponding to the specified filter.
    * <p>
    * Using {@code null} or an empty filter will expire <em>all</em> messages from this queue.
    *
    * @return the number of expired messages
    */
   @Operation(desc = "Expire the messages corresponding to the given filter (and returns the number of expired messages)", impact = MBeanOperationInfo.ACTION)
   int expireMessages(@Parameter(name = "filter", desc = "A message filter") String filter) throws Exception;

   /**
    * Expires the message corresponding to the specified message ID.
    *
    * @return {@code true} if the message was expired, {@code false} else
    */
   @Operation(desc = "Remove the message corresponding to the given messageID", impact = MBeanOperationInfo.ACTION)
   boolean expireMessage(@Parameter(name = "messageID", desc = "A message ID") long messageID) throws Exception;

   /**
    * Retries the message corresponding to the given messageID to the original queue. This is appropriate on dead
    * messages on Dead letter queues only.
    *
    * @return {@code true} if the message was retried, {@code false} else
    */
   @Operation(desc = "Retry the message corresponding to the given messageID to the original queue", impact = MBeanOperationInfo.ACTION)
   boolean retryMessage(@Parameter(name = "messageID", desc = "A message ID") long messageID) throws Exception;

   /**
    * Retries all messages on a DLQ to their respective original queues.
    * This is appropriate on dead messages on Dead letter queues only.
    *
    * @return the number of retried messages
    */
   @Operation(desc = "Retry all messages on a DLQ to their respective original queues", impact = MBeanOperationInfo.ACTION)
   int retryMessages() throws Exception;

   /**
    * Retries all messages that match the given filter on a DLQ to their respective original queues.
    * This operation is appropriate for Dead letter queues only.
    *
    * @return the number of retried messages.
    */
   @Operation(desc = "Retry all messages matching the given filter on a DLQ to their respective original queues", impact = MBeanOperationInfo.ACTION)
   int retryMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   /**
    * Moves the message corresponding to the specified message ID to the specified other queue.
    *
    * @return {@code true} if the message was moved, {@code false} else
    */
   @Operation(desc = "Move the message corresponding to the given messageID to another queue. rejectDuplicate=false on this case", impact = MBeanOperationInfo.ACTION)
   boolean moveMessage(@Parameter(name = "messageID", desc = "A message ID") long messageID,
                       @Parameter(name = "otherQueueName", desc = "The name of the queue to move the message to") String otherQueueName) throws Exception;

   /**
    * Moves the message corresponding to the specified message ID to the specified other queue.
    *
    * @return {@code true} if the message was moved, {@code false} else
    */
   @Operation(desc = "Move the message corresponding to the given messageID to another queue", impact = MBeanOperationInfo.ACTION)
   boolean moveMessage(@Parameter(name = "messageID", desc = "A message ID") long messageID,
                       @Parameter(name = "otherQueueName", desc = "The name of the queue to move the message to") String otherQueueName,
                       @Parameter(name = "rejectDuplicates", desc = "Reject messages identified as duplicate by the duplicate message") boolean rejectDuplicates) throws Exception;

   /**
    * Moves all the message corresponding to the specified filter  to the specified other queue.
    * RejectDuplicates = false on this case
    * <p>
    * Using {@code null} or an empty filter will move <em>all</em> messages from this queue.
    *
    * @return the number of moved messages
    */
   @Operation(desc = "Move the messages corresponding to the given filter (and returns the number of moved messages). RejectDuplicates=false on this case.", impact = MBeanOperationInfo.ACTION)
   int moveMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter,
                    @Parameter(name = "otherQueueName", desc = "The name of the queue to move the messages to") String otherQueueName) throws Exception;

   /**
    * Moves all the message corresponding to the specified filter  to the specified other queue.
    * <p>
    * Using {@code null} or an empty filter will move <em>all</em> messages from this queue.
    *
    * @return the number of moved messages
    */
   @Operation(desc = "Move the messages corresponding to the given filter (and returns the number of moved messages)", impact = MBeanOperationInfo.ACTION)
   int moveMessages(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter,
                    @Parameter(name = "otherQueueName", desc = "The name of the queue to move the messages to") String otherQueueName,
                    @Parameter(name = "rejectDuplicates", desc = "Reject messages identified as duplicate by the duplicate message") boolean rejectDuplicates) throws Exception;

   @Operation(desc = "Move the messages corresponding to the given filter (and returns the number of moved messages)", impact = MBeanOperationInfo.ACTION)
   int moveMessages(@Parameter(name = "flushLimit", desc = "Limit to flush transactions during the operation to avoid OutOfMemory") int flushLimit,
                    @Parameter(name = "filter", desc = "A message filter (can be empty)") String filter,
                    @Parameter(name = "otherQueueName", desc = "The name of the queue to move the messages to") String otherQueueName,
                    @Parameter(name = "rejectDuplicates", desc = "Reject messages identified as duplicate by the duplicate message") boolean rejectDuplicates) throws Exception;

   @Operation(desc = "Move the messages corresponding to the given filter (and returns the number of moved messages)", impact = MBeanOperationInfo.ACTION)
   int moveMessages(@Parameter(name = "flushLimit", desc = "Limit to flush transactions during the operation to avoid OutOfMemory") int flushLimit,
                    @Parameter(name = "filter", desc = "A message filter (can be empty)") String filter,
                    @Parameter(name = "otherQueueName", desc = "The name of the queue to move the messages to") String otherQueueName,
                    @Parameter(name = "rejectDuplicates", desc = "Reject messages identified as duplicate by the duplicate message") boolean rejectDuplicates,
                    @Parameter(name = "messageCount", desc = "Number of messages to move.") int messageCount) throws Exception;

   @Operation(desc = "Send a copy of the message with given messageID to another queue)", impact = MBeanOperationInfo.ACTION)
   boolean copyMessage(@Parameter(name = "messageID", desc = "A message ID") long messageID,
                       @Parameter(name = "targetQueue", desc = "The name of the queue to copy the messages to") String targetQueue) throws Exception;

   /**
    * Sends the message corresponding to the specified message ID to this queue's dead letter address.
    *
    * @return {@code true} if the message was sent to the dead letter address, {@code false} else
    */
   @Operation(desc = "Send the message corresponding to the given messageID to this queue's Dead Letter Address", impact = MBeanOperationInfo.ACTION)
   boolean sendMessageToDeadLetterAddress(@Parameter(name = "messageID", desc = "A message ID") long messageID) throws Exception;

   /**
    * Sends all the message corresponding to the specified filter to this queue's dead letter address.
    * <p>
    * Using {@code null} or an empty filter will send <em>all</em> messages from this queue.
    *
    * @return the number of sent messages
    */
   @Operation(desc = "Send the messages corresponding to the given filter to this queue's Dead Letter Address", impact = MBeanOperationInfo.ACTION)
   int sendMessagesToDeadLetterAddress(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filterStr) throws Exception;

   /**
    * Sends a TextMessage to a password-protected destination.
    *
    * @param headers the message headers and properties to set. Can only container Strings maped to primitive types.
    * @param body    the text to send
    */
   @Operation(desc = "Sends a TextMessage to a password-protected destination.", impact = MBeanOperationInfo.ACTION)
   String sendMessage(@Parameter(name = "headers", desc = "The headers to add to the message") Map<String, String> headers,
                      @Parameter(name = "type", desc = "A type for the message") int type,
                      @Parameter(name = "body", desc = "The body (byte[]) of the message encoded as a string using Base64") String body,
                      @Parameter(name = "durable", desc = "Whether the message is durable") boolean durable,
                      @Parameter(name = "user", desc = "The user to authenticate with") String user,
                      @Parameter(name = "password", desc = "The users password to authenticate with") String password) throws Exception;

   /**
    * Sends a TextMessage to a password-protected destination.
    *
    * @param headers         the message headers and properties to set. Can only container Strings maped to primitive
    *                        types.
    * @param body            the text to send
    * @param createMessageId whether to auto generate a Message ID
    */
   @Operation(desc = "Sends a TextMessage to a password-protected destination.", impact = MBeanOperationInfo.ACTION)
   String sendMessage(@Parameter(name = "headers", desc = "The headers to add to the message") Map<String, String> headers,
                      @Parameter(name = "type", desc = "A type for the message") int type,
                      @Parameter(name = "body", desc = "The body (byte[]) of the message encoded as a string using Base64") String body,
                      @Parameter(name = "durable", desc = "Whether the message is durable") boolean durable,
                      @Parameter(name = "user", desc = "The user to authenticate with") String user,
                      @Parameter(name = "password", desc = "The users password to authenticate with") String password,
                      @Parameter(name = "createMessageId", desc = "whether or not to auto generate a Message ID") boolean createMessageId) throws Exception;

   /**
    * Changes the message's priority corresponding to the specified message ID to the specified priority.
    *
    * @param newPriority between 0 and 9 inclusive.
    * @return {@code true} if the message priority was changed
    */
   @Operation(desc = "Change the priority of the message corresponding to the given messageID", impact = MBeanOperationInfo.ACTION)
   boolean changeMessagePriority(@Parameter(name = "messageID", desc = "A message ID") long messageID,
                                 @Parameter(name = "newPriority", desc = "the new priority (between 0 and 9)") int newPriority) throws Exception;

   /**
    * Changes the priority for all the message corresponding to the specified filter to the specified priority.
    * <p>
    * Using {@code null} or an empty filter will change <em>all</em> messages from this queue.
    *
    * @return the number of changed messages
    */
   @Operation(desc = "Change the priority of the messages corresponding to the given filter", impact = MBeanOperationInfo.ACTION)
   int changeMessagesPriority(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter,
                              @Parameter(name = "newPriority", desc = "the new priority (between 0 and 9)") int newPriority) throws Exception;

   /**
    * Lists the message counter for this queue.
    */
   @Operation(desc = "List the message counters", impact = MBeanOperationInfo.INFO)
   String listMessageCounter() throws Exception;

   /**
    * Resets the message counter for this queue.
    */
   @Operation(desc = "Reset the message counters", impact = MBeanOperationInfo.INFO)
   void resetMessageCounter() throws Exception;

   /**
    * Lists the message counter for this queue as a HTML table.
    */
   @Operation(desc = "List the message counters as HTML", impact = MBeanOperationInfo.INFO)
   String listMessageCounterAsHTML() throws Exception;

   /**
    * Lists the message counter history for this queue.
    */
   @Operation(desc = "List the message counters history", impact = MBeanOperationInfo.INFO)
   String listMessageCounterHistory() throws Exception;

   /**
    * Lists the message counter history for this queue as a HTML table.
    */
   @Deprecated
   @Operation(desc = "List the message counters history HTML", impact = MBeanOperationInfo.INFO)
   String listMessageCounterHistoryAsHTML() throws Exception;

   /**
    * Pauses the queue. Messages are no longer delivered to its consumers.
    */
   @Operation(desc = "Pauses the Queue", impact = MBeanOperationInfo.ACTION)
   void pause() throws Exception;

   /**
    * Pauses the queue. Messages are no longer delivered to its consumers.
    */
   @Operation(desc = "Pauses the Queue", impact = MBeanOperationInfo.ACTION)
   void pause(@Parameter(name = "persist", desc = "if true, the pause state will be persisted.") boolean persist) throws Exception;

   /**
    * Resumes the queue. Messages are again delivered to its consumers.
    */
   @Operation(desc = "Resumes delivery of queued messages and gets the queue out of paused state. It will also affected the state of a persisted pause.", impact = MBeanOperationInfo.ACTION)
   void resume() throws Exception;


   @Operation(desc = "List all the existent consumers on the Queue")
   String listConsumersAsJSON() throws Exception;

   /**
    * {@return whether the queue is paused}
    */
   @Attribute(desc = "whether the queue is paused")
   boolean isPaused();

   /**
    * {@return whether the queue's paused status is persisted}
    */
   @Attribute(desc = "whether the queue's paused status is persisted")
   boolean isPersistedPause();

   @Operation(desc = "Browse Messages", impact = MBeanOperationInfo.ACTION)
   CompositeData[] browse() throws Exception;

   @Operation(desc = "Browse Messages", impact = MBeanOperationInfo.ACTION)
   CompositeData[] browse(@Parameter(name = "filter", desc = "A message filter (can be empty)") String filter) throws Exception;

   @Operation(desc = "Browse Messages", impact = MBeanOperationInfo.ACTION)
   CompositeData[] browse(@Parameter(name = "page", desc = "Current page") int page,
                          @Parameter(name = "pageSize", desc = "Page size") int pageSize) throws Exception;

   @Operation(desc = "Browse Messages", impact = MBeanOperationInfo.ACTION)
   CompositeData[] browse(@Parameter(name = "page", desc = "Current page") int page,
                          @Parameter(name = "pageSize", desc = "Page size") int pageSize,
                          @Parameter(name = "filter", desc = "filter") String filter) throws Exception;

   /**
    * Resets the MessagesAdded property
    */
   @Operation(desc = "Resets the MessagesAdded property", impact = MBeanOperationInfo.ACTION)
   void resetMessagesAdded() throws Exception;

   /**
    * Resets the MessagesAdded property
    */
   @Operation(desc = "Resets the MessagesAcknowledged property", impact = MBeanOperationInfo.ACTION)
   void resetMessagesAcknowledged() throws Exception;

   /**
    * Resets the MessagesExpired property
    */
   @Operation(desc = "Resets the MessagesExpired property", impact = MBeanOperationInfo.ACTION)
   void resetMessagesExpired() throws Exception;

   /**
    * Resets the MessagesExpired property
    */
   @Operation(desc = "Resets the MessagesKilled property", impact = MBeanOperationInfo.ACTION)
   void resetMessagesKilled() throws Exception;

   /**
    * it will flush one cycle on internal executors, so you would be sure that any pending tasks are done before you
    * call any other measure. It is useful if you need the exact number of counts on a message
    */
   @Operation(desc = "Flush internal executors", impact = MBeanOperationInfo.ACTION)
   void flushExecutor();

   /**
    * Will reset the all the groups. This is useful if you want a complete rebalance of the groups to consumers
    */
   @Operation(desc = "Resets all groups", impact = MBeanOperationInfo.ACTION)
   void resetAllGroups();

   /**
    * Will reset the group matching the given groupID. This is useful if you want the given group to be rebalanced to
    * the consumers
    */
   @Operation(desc = "Reset the specified group", impact = MBeanOperationInfo.ACTION)
   void resetGroup(@Parameter(name = "groupID", desc = "ID of group to reset") String groupID);

   /**
    * Will return the current number of active groups.
    */
   @Attribute(desc = "Get the current number of active groups")
   int getGroupCount();


   @Operation(desc = "List all the existent group to consumers mappings on the Queue")
   String listGroupsAsJSON() throws Exception;

   /**
    * Will return the ring size.
    */
   @Attribute(desc = "Get the ring size")
   long getRingSize();

   /**
    * {@return whether the groups of this queue are automatically rebalanced}
    */
   @Attribute(desc = "whether the groups of this queue are automatically rebalanced")
   boolean isGroupRebalance();

   /**
    * {@return whether the dispatch is paused when groups of this queue are automatically rebalanced}
    */
   @Attribute(desc = "whether the dispatch is paused when groups of this queue are automatically rebalanced")
   boolean isGroupRebalancePauseDispatch();

   /**
    * Will return the group buckets.
    */
   @Attribute(desc = "Get the group buckets")
   int getGroupBuckets();

   /**
    * Will return the header key to notify a consumer of a group change.
    */
   @Attribute(desc = "Get the header key to notify a consumer of a group change")
   String getGroupFirstKey();

   /**
    * Will return the number of messages stuck in prepared transactions
    */
   @Attribute(desc = "return how many messages are stuck in prepared transactions")
   int getPreparedTransactionMessageCount();

   /**
    * Deliver the scheduled messages which match the filter
    */
   @Operation(desc = "Immediately deliver the scheduled messages which match the filter", impact = MBeanOperationInfo.ACTION)
   void deliverScheduledMessages(@Parameter(name = "filter", desc = "filter to match messages to deliver") String filter) throws Exception;

   /**
    * Deliver the scheduled message with the specified message ID
    */
   @Operation(desc = "Immediately deliver the scheduled message with the specified message ID", impact = MBeanOperationInfo.ACTION)
   void deliverScheduledMessage(@Parameter(name = "messageID", desc = "ID of the message to deliver") long messageId) throws Exception;

   /**
    * {@return whether this queue is available for auto deletion}
    */
   @Attribute(desc = "whether this queue is available for auto deletion")
   boolean isAutoDelete();

   /**
    * {@return the first message on the queue as JSON}
    */
   @Operation(desc = "Returns first message on the queue as JSON", impact = MBeanOperationInfo.INFO)
   String peekFirstMessageAsJSON() throws Exception;

   /**
    * {@return the first scheduled message on the queue as JSON}
    */
   @Operation(desc = "Returns first scheduled message on the queue as JSON", impact = MBeanOperationInfo.INFO)
   String peekFirstScheduledMessageAsJSON() throws Exception;

}
