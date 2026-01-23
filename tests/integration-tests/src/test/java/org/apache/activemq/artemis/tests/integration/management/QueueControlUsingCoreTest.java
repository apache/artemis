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
package org.apache.activemq.artemis.tests.integration.management;

import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.CompositeData;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.junit.jupiter.api.extension.ExtendWith;

// Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class QueueControlUsingCoreTest extends QueueControlTest {

   public QueueControlUsingCoreTest(boolean durable) {
      super(durable);
   }

   @Override
   protected QueueControl createManagementControl(final SimpleString address,
                                                  final SimpleString queue) throws Exception {
      return new QueueControl() {
         private final CoreMessagingProxy proxy = new CoreMessagingProxy(addServerLocator(createInVMNonHALocator()), ResourceNames.QUEUE + queue);

         @Override
         public void flushExecutor() {
            try {
               proxy.invokeOperation("flushExecutor");
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public int getPreparedTransactionMessageCount() {
            try {
               return (Integer) proxy.invokeOperation(Integer.class, "getPreparedTransactionMessageCount");
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public void deliverScheduledMessages(String filter) throws Exception {
            try {
               proxy.invokeOperation("deliverScheduledMessages", filter);
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public void deliverScheduledMessage(long messageId) throws Exception {
            try {
               proxy.invokeOperation("deliverScheduledMessage", messageId);
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public boolean isAutoDelete() {
            return (Boolean) proxy.retrieveAttributeValue("autoDelete");
         }

         @Override
         public void resetAllGroups() {
            try {
               proxy.invokeOperation("resetAllGroups");
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public void resetGroup(String groupID) {
            try {
               proxy.invokeOperation("resetGroup", groupID);
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public int getGroupCount() {
            try {
               return (Integer) proxy.invokeOperation(Integer.TYPE, "getGroupCount");
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public String listGroupsAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listGroupsAsJSON");
         }

         @Override
         public long getRingSize() {
            try {
               return (Long) proxy.invokeOperation(Integer.TYPE, "getRingSize");
            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         }

         @Override
         public boolean isGroupRebalance() {
            return (Boolean) proxy.retrieveAttributeValue("groupRebalance");
         }

         @Override
         public boolean isGroupRebalancePauseDispatch() {
            return (Boolean) proxy.retrieveAttributeValue("groupRebalancePauseDispatch");
         }

         @Override
         public int getGroupBuckets() {
            return (Integer) proxy.retrieveAttributeValue("groupBuckets", Integer.class);
         }

         @Override
         public String getGroupFirstKey() {
            return (String) proxy.retrieveAttributeValue("groupFirstKey");
         }

         @Override
         public boolean changeMessagePriority(final long messageID, final int newPriority) throws Exception {
            return (Boolean) proxy.invokeOperation("changeMessagePriority", messageID, newPriority);
         }

         @Override
         public int changeMessagesPriority(final String filter, final int newPriority) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "changeMessagesPriority", filter, newPriority);
         }

         @Override
         public long countMessages(final String filter) throws Exception {
            return (Long) proxy.invokeOperation(Long.class, "countMessages", filter);
         }

         @Override
         public long countMessages() throws Exception {
            return (Long) proxy.invokeOperation(Long.class, "countMessages");
         }

         @Override
         public String countMessages(final String filter, final String groupByFilter) throws Exception {
            return (String) proxy.invokeOperation(String.class, "countMessages", filter, groupByFilter);
         }

         @Override
         public long countDeliveringMessages(final String filter) throws Exception {
            return (Long) proxy.invokeOperation(Long.class, "countDeliveringMessages", filter);
         }

         @Override
         public String countDeliveringMessages(final String filter, final String groupByFilter) throws Exception {
            return (String) proxy.invokeOperation(String.class, "countDeliveringMessages", filter, groupByFilter);
         }

         @Override
         public boolean expireMessage(final long messageID) throws Exception {
            return (Boolean) proxy.invokeOperation("expireMessage", messageID);
         }

         @Override
         public int expireMessages(final String filter) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "expireMessages", filter);
         }

         @Override
         public String getAddress() {
            return (String) proxy.retrieveAttributeValue("address");
         }

         @Override
         public String getUser() {
            return (String) proxy.retrieveAttributeValue("user");
         }

         @Override
         public int getConsumerCount() {
            return (Integer) proxy.retrieveAttributeValue("consumerCount", Integer.class);
         }

         @Override
         public String getDeadLetterAddress() {
            return (String) proxy.retrieveAttributeValue("deadLetterAddress");
         }

         @Override
         public int getMaxConsumers() {
            return (Integer) proxy.retrieveAttributeValue("maxConsumers");
         }

         @Override
         public boolean isPurgeOnNoConsumers() {
            return (Boolean) proxy.retrieveAttributeValue("purgeOnNoConsumers");
         }

         @Override
         public boolean isEnabled() {
            return  (Boolean) proxy.retrieveAttributeValue("isEnabled");
         }

         @Override
         public void enable() throws Exception {
            proxy.invokeOperation("enable");
         }

         @Override
         public void disable() throws Exception {
            proxy.invokeOperation("disable");
         }

         @Override
         public boolean isConfigurationManaged() {
            return (Boolean) proxy.retrieveAttributeValue("configurationManaged");
         }

         @Override
         public boolean isExclusive() {
            return (Boolean) proxy.retrieveAttributeValue("exclusive");
         }

         @Override
         public boolean isLastValue() {
            return (Boolean) proxy.retrieveAttributeValue("lastValue");
         }

         @Override
         public String getLastValueKey() {
            return (String) proxy.retrieveAttributeValue("lastValueKey");
         }

         @Override
         public boolean isInternalQueue() {
            return (boolean) proxy.retrieveAttributeValue("internalQueue");
         }

         @Override
         public int getConsumersBeforeDispatch() {
            return (Integer) proxy.retrieveAttributeValue("consumersBeforeDispatch");
         }

         @Override
         public long getDelayBeforeDispatch() {
            return (Long) proxy.retrieveAttributeValue("delayBeforeDispatch");
         }

         @Override
         public int getDeliveringCount() {
            return (Integer) proxy.retrieveAttributeValue("deliveringCount", Integer.class);
         }

         @Override
         public long getDeliveringSize() {
            return (Long) proxy.retrieveAttributeValue("deliveringSize", Long.class);
         }

         @Override
         public int getDurableDeliveringCount() {
            return (Integer) proxy.retrieveAttributeValue("durableDeliveringCount", Integer.class);
         }

         @Override
         public long getDurableDeliveringSize() {
            return (Long) proxy.retrieveAttributeValue("durableDeliveringSize", Long.class);
         }

         @Override
         public String getExpiryAddress() {
            return (String) proxy.retrieveAttributeValue("expiryAddress");
         }

         @Override
         public String getFilter() {
            return (String) proxy.retrieveAttributeValue("filter");
         }

         @Override
         public long getMessageCount() {
            return (Long) proxy.retrieveAttributeValue("messageCount", Long.class);
         }

         @Override
         public long getMessagesAdded() {
            return (Integer) proxy.retrieveAttributeValue("messagesAdded", Integer.class);
         }

         @Override
         public long getMessagesAcknowledged() {
            return (Integer) proxy.retrieveAttributeValue("messagesAcknowledged", Integer.class);
         }

         @Override
         public long getAcknowledgeAttempts() {
            return (Integer) proxy.retrieveAttributeValue("acknowledgeAttempts", Integer.class);
         }

         @Override
         public long getMessagesExpired() {
            return (Long) proxy.retrieveAttributeValue("messagesExpired", Long.class);
         }

         @Override
         public long getMessagesKilled() {
            return ((Number) proxy.retrieveAttributeValue("messagesKilled")).longValue();
         }

         @Override
         public void resetMessagesAdded() throws Exception {
            proxy.invokeOperation("resetMessagesAdded");
         }

         @Override
         public void resetMessagesAcknowledged() throws Exception {
            proxy.invokeOperation("resetMessagesAcknowledged");
         }

         @Override
         public void resetMessagesExpired() throws Exception {
            proxy.invokeOperation("resetMessagesExpired");
         }

         @Override
         public void resetMessagesKilled() throws Exception {
            proxy.invokeOperation("resetMessagesKilled");
         }

         @Override
         public String getName() {
            return (String) proxy.retrieveAttributeValue("name");
         }

         @Override
         public long getID() {
            return (Long) proxy.retrieveAttributeValue("ID");
         }

         @Override
         public long getScheduledCount() {
            return (Long) proxy.retrieveAttributeValue("scheduledCount", Long.class);
         }

         @Override
         public long getScheduledSize() {
            return (Long) proxy.retrieveAttributeValue("scheduledSize", Long.class);
         }

         @Override
         public long getDurableScheduledCount() {
            return (Long) proxy.retrieveAttributeValue("durableScheduledCount", Long.class);
         }

         @Override
         public long getDurableScheduledSize() {
            return (Long) proxy.retrieveAttributeValue("durableScheduledSize", Long.class);
         }

         @Override
         public boolean isDurable() {
            return (Boolean) proxy.retrieveAttributeValue("durable");
         }

         @Override
         public String getRoutingType() {
            return (String) proxy.retrieveAttributeValue("routingType");
         }

         @Override
         public boolean isTemporary() {
            return (Boolean) proxy.retrieveAttributeValue("temporary");
         }

         @Override
         public boolean isRetroactiveResource() {
            return (Boolean) proxy.retrieveAttributeValue("retroactiveResource");
         }

         @Override
         public String listMessageCounter() throws Exception {
            return (String) proxy.invokeOperation("listMessageCounter");
         }

         @Override
         public String listMessageCounterAsHTML() throws Exception {
            return (String) proxy.invokeOperation("listMessageCounterAsHTML");
         }

         @Override
         public String listMessageCounterHistory() throws Exception {
            return (String) proxy.invokeOperation("listMessageCounterHistory");
         }

         @Override
         public String getFirstMessageAsJSON() throws Exception {
            return (String) proxy.invokeOperation("getFirstMessageAsJSON");
         }

         @Override
         public String peekFirstMessageAsJSON() throws Exception {
            return (String) proxy.invokeOperation("peekFirstMessageAsJSON");
         }

         @Override
         public String peekFirstScheduledMessageAsJSON() throws Exception {
            return (String) proxy.invokeOperation("peekFirstScheduledMessageAsJSON");
         }

         @Override
         public Long getFirstMessageTimestamp() throws Exception {
            return (Long) proxy.invokeOperation("getFirstMessageTimestamp");
         }

         @Override
         public Long getFirstMessageAge() throws Exception {
            return (Long) proxy.invokeOperation(Long.class, "getFirstMessageAge");
         }

         @Override
         public String listMessageCounterHistoryAsHTML() throws Exception {
            return (String) proxy.invokeOperation("listMessageCounterHistoryAsHTML");
         }

         @Override
         public Map<String, Object>[] listMessages(final String filter) throws Exception {
            Object[] res = (Object[]) proxy.invokeOperation("listMessages", filter);
            Map<String, Object>[] results = new Map[res.length];
            for (int i = 0; i < res.length; i++) {
               results[i] = (Map<String, Object>) res[i];
            }
            return results;
         }

         @Override
         public String listMessagesAsJSON(final String filter) throws Exception {
            return (String) proxy.invokeOperation("listMessagesAsJSON", filter);
         }

         @Override
         public Map<String, Object>[] listScheduledMessages() throws Exception {
            Object[] res = (Object[]) proxy.invokeOperation("listScheduledMessages");
            Map<String, Object>[] results = new Map[res.length];
            for (int i = 0; i < res.length; i++) {
               results[i] = (Map<String, Object>) res[i];
            }
            return results;
         }

         @Override
         public String listScheduledMessagesAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listScheduledMessagesAsJSON");
         }

         @Override
         public int moveMessages(final String filter, final String otherQueueName) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "moveMessages", filter, otherQueueName);
         }

         @Override
         public int moveMessages(int flushLimit,
                                 String filter,
                                 String otherQueueName,
                                 boolean rejectDuplicates) throws Exception {
            return (Integer) proxy.invokeOperation("moveMessages", flushLimit, filter, otherQueueName, rejectDuplicates);
         }

         @Override
         public int moveMessages(int flushLimit, String filter, String otherQueueName, boolean rejectDuplicates, int messageCount) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "moveMessages", flushLimit, filter, otherQueueName, rejectDuplicates, messageCount);
         }

         @Override
         public boolean copyMessage(long messageID, String targetQueue) throws Exception {
            return (Boolean) proxy.invokeOperation("copyMessage", messageID, targetQueue);
         }

         @Override
         public int moveMessages(final String filter,
                                 final String otherQueueName,
                                 final boolean rejectDuplicates) throws Exception {
            return (Integer) proxy.invokeOperation("moveMessages", filter, otherQueueName, rejectDuplicates);
         }

         @Override
         public boolean moveMessage(final long messageID, final String otherQueueName) throws Exception {
            return (Boolean) proxy.invokeOperation("moveMessage", messageID, otherQueueName);
         }

         @Override
         public boolean moveMessage(final long messageID,
                                    final String otherQueueName,
                                    final boolean rejectDuplicates) throws Exception {
            return (Boolean) proxy.invokeOperation("moveMessage", messageID, otherQueueName, rejectDuplicates);
         }

         @Override
         public boolean retryMessage(final long messageID) throws Exception {
            return (Boolean) proxy.invokeOperation("retryMessage", messageID);
         }

         @Override
         public int retryMessages() throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "retryMessages");
         }

         @Override
         public int retryMessages(String filter) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "retryMessages", filter);
         }

         @Override
         public int removeMessages(final String filter) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "removeMessages", filter);
         }

         @Override
         public int removeMessages(final int limit, final String filter) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "removeMessages", limit, filter);
         }

         @Override
         public int removeAllMessages() throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "removeAllMessages");
         }

         @Override
         public boolean removeMessage(final long messageID) throws Exception {
            return (Boolean) proxy.invokeOperation("removeMessage", messageID);
         }

         @Override
         public void resetMessageCounter() throws Exception {
            proxy.invokeOperation("resetMessageCounter");
         }

         @Override
         public boolean sendMessageToDeadLetterAddress(final long messageID) throws Exception {
            return (Boolean) proxy.invokeOperation("sendMessageToDeadLetterAddress", messageID);
         }

         @Override
         public int sendMessagesToDeadLetterAddress(final String filterStr) throws Exception {
            return (Integer) proxy.invokeOperation(Integer.class, "sendMessagesToDeadLetterAddress", filterStr);
         }

         @Override
         public String sendMessage(Map<String, String> headers,
                                   int type,
                                   String body,
                                   boolean durable,
                                   String user,
                                   String password) throws Exception {
            return (String) proxy.invokeOperation("sendMessage", headers, type, body, durable, user, password);
         }

         @Override
         public String sendMessage(Map<String, String> headers,
                                   int type,
                                   String body,
                                   boolean durable,
                                   String user,
                                   String password,
                                   boolean createMessageId) throws Exception {
            return (String) proxy.invokeOperation("sendMessage", headers, type, body, durable, user, password, createMessageId);
         }

         public void setDeadLetterAddress(final String deadLetterAddress) throws Exception {
            proxy.invokeOperation("setDeadLetterAddress", deadLetterAddress);
         }

         public void setExpiryAddress(final String expiryAddress) throws Exception {
            proxy.invokeOperation("setExpiryAddress", expiryAddress);
         }

         @Override
         public void pause() throws Exception {
            proxy.invokeOperation("pause");
         }

         @Override
         public void pause(boolean persist) throws Exception {
            proxy.invokeOperation("pause", persist);
         }

         @Override
         public void resume() throws Exception {
            proxy.invokeOperation("resume");
         }

         @Override
         public boolean isPaused() {
            return (Boolean) proxy.retrieveAttributeValue("paused");
         }

         @Override
         public boolean isPersistedPause() {
            return (Boolean) proxy.retrieveAttributeValue("persistedPause");
         }

         @Override
         public CompositeData[] browse() throws Exception {
            Map map = (Map) proxy.invokeOperation("browse");
            CompositeData[] compositeDatas = (CompositeData[]) map.get(CompositeData.class.getName());
            if (compositeDatas == null) {
               compositeDatas = new CompositeData[0];
            }
            return compositeDatas;
         }

         @Override
         public CompositeData[] browse(int page, int pageSize) throws Exception {
            Map map = (Map) proxy.invokeOperation("browse", page, pageSize);
            CompositeData[] compositeDatas = (CompositeData[]) map.get(CompositeData.class.getName());
            if (compositeDatas == null) {
               compositeDatas = new CompositeData[0];
            }
            return compositeDatas;
         }

         @Override
         public CompositeData[] browse(int page, int pageSize, String filter) throws Exception {
            Map map = (Map) proxy.invokeOperation("browse", page, pageSize, filter);
            CompositeData[] compositeDatas = (CompositeData[]) map.get(CompositeData.class.getName());
            if (compositeDatas == null) {
               compositeDatas = new CompositeData[0];
            }
            return compositeDatas;
         }

         @Override
         public CompositeData[] browse(String filter) throws Exception {
            Map map = (Map) proxy.invokeOperation("browse", filter);
            CompositeData[] compositeDatas = (CompositeData[]) map.get(CompositeData.class.getName());
            if (compositeDatas == null) {
               compositeDatas = new CompositeData[0];
            }
            return compositeDatas;
         }

         @Override
         public String listConsumersAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listConsumersAsJSON");
         }

         @Override
         public Map<String, Map<String, Object>[]> listDeliveringMessages() throws Exception {
            // This map code could be done better,
            // however that's just to convert stuff for the test class, so I
            // am not going to spend a lot more time on it (Clebert)
            @SuppressWarnings("rawtypes")
            Map res = (Map) proxy.invokeOperation("listDeliveringMessages");

            @SuppressWarnings("rawtypes")
            Map response = new HashMap();

            for (Object key : res.keySet()) {
               Object[] value = (Object[]) res.get(key);

               Map<String, Object>[] results = new Map[value.length];
               for (int i = 0; i < value.length; i++) {
                  results[i] = (Map<String, Object>) value[i];
               }

               response.put(key, results);
            }

            return response;
         }

         @Override
         public String listDeliveringMessagesAsJSON() throws Exception {
            return (String) proxy.invokeOperation("listDeliveringMessagesAsJSON");
         }

         @Override
         public long getPersistentSize() {
            return (Long) proxy.retrieveAttributeValue("persistentSize", Long.class);
         }

         @Override
         public long getDurableMessageCount() {
            return (Long) proxy.retrieveAttributeValue("durableMessageCount", Long.class);
         }

         @Override
         public long getDurablePersistentSize() {
            return (Long) proxy.retrieveAttributeValue("durablePersistentSize", Long.class);
         }
      };
   }
}
