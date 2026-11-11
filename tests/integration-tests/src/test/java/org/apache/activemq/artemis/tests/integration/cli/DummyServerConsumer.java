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
package org.apache.activemq.artemis.tests.integration.cli;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.SlowConsumerDetectionListener;
import org.apache.activemq.artemis.core.transaction.Transaction;

public class DummyServerConsumer implements ServerConsumer {

   @Override
   public void setlowConsumerDetection(SlowConsumerDetectionListener listener) {

   }

   @Override
   public void failed(Throwable t) {

   }

   @Override
   public SlowConsumerDetectionListener getSlowConsumerDetecion() {
      return null;
   }

   @Override
   public void fireSlowConsumer() {

   }

   @Override
   public boolean allowReferenceCallback() {
      return false;
   }

   @Override
   public Object getProtocolData() {
      return null;
   }

   @Override
   public void setProtocolData(Object protocolData) {

   }

   @Override
   public void setProtocolContext(Object protocolContext) {

   }

   @Override
   public long sequentialID() {
      return 0;
   }

   @Override
   public Object getProtocolContext() {
      return null;
   }

   @Override
   public long getID() {
      return 0;
   }

   @Override
   public Object getConnectionID() {
      return null;
   }

   @Override
   public void close(boolean failed) throws Exception {

   }

   @Override
   public void removeItself() throws Exception {

   }

   @Override
   public List<MessageReference> cancelRefs(boolean failed,
                                            boolean lastConsumedAsDelivered,
                                            Transaction tx) throws Exception {
      return null;
   }

   @Override
   public void setStarted(boolean started) {

   }

   @Override
   public void receiveCredits(int credits) {

   }

   @Override
   public Queue getQueue() {
      return null;
   }

   @Override
   public MessageReference removeReferenceByID(long messageID) throws Exception {
      return null;
   }

   @Override
   public void backToDelivering(MessageReference reference) {

   }

   @Override
   public List<MessageReference> scanDeliveringReferences(boolean remove,
                                                          Function<MessageReference, Boolean> startFunction,
                                                          Function<MessageReference, Boolean> endFunction) {
      return null;
   }

   @Override
   public void reject(long messageID) throws Exception {

   }

   @Override
   public List<Long> acknowledge(Transaction tx, long messageID) throws Exception {
      return null;
   }

   @Override
   public void individualAcknowledge(Transaction tx, long messageID) throws Exception {

   }

   @Override
   public void individualCancel(long messageID, boolean failed) throws Exception {

   }

   @Override
   public void forceDelivery(long sequence) {

   }

   @Override
   public void setTransferring(boolean transferring) {

   }

   @Override
   public boolean isBrowseOnly() {
      return false;
   }

   @Override
   public long getCreationTime() {
      return 0;
   }

   @Override
   public String getSessionID() {
      return null;
   }

   @Override
   public void metricsAcknowledge(MessageReference ref, Transaction transaction) {

   }

   @Override
   public long getMessagesInTransitSize() {
      return 0;
   }

   @Override
   public long getMessagesDeliveredSize() {
      return 0;
   }

   @Override
   public long getMessagesDelivered() {
      return 0;
   }

   @Override
   public void promptDelivery() {

   }

   @Override
   public HandleStatus handle(MessageReference reference) throws Exception {
      return null;
   }

   @Override
   public void proceedDeliver(MessageReference reference) throws Exception {

   }

   @Override
   public Filter getFilter() {
      return null;
   }

   @Override
   public SimpleString getFilterString() {
      return null;
   }

   @Override
   public List<MessageReference> getDeliveringMessages() {
      return null;
   }

   @Override
   public String debug() {
      return null;
   }

   @Override
   public String toManagementString() {
      return null;
   }

   @Override
   public void disconnect() {

   }

   @Override
   public long getSequentialID() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public SimpleString getQueueName() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public RoutingType getQueueType() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public SimpleString getQueueAddress() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String getSessionName() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String getConnectionClientID() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String getConnectionProtocolName() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String getConnectionLocalAddress() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String getConnectionRemoteAddress() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public int getMessagesInTransit() {
      return 0;
   }

   @Override
   public long getLastDeliveredTime() {
      return 0;
   }

   @Override
   public long getLastAcknowledgedTime() {
      return 0;
   }

   @Override
   public long getMessagesAcknowledged() {
      return 0;
   }

   @Override
   public int getMessagesAcknowledgedAwaitingCommit() {
      return 0;
   }

   @Override
   public void addAttachment(String key, Object attachment) {

   }

   @Override
   public Object getAttachment(String key) {
      return null;
   }

   @Override
   public Map<String, Object> getAttachments() {
      return Collections.emptyMap();
   }

   @Override
   public void removeAttachment(String key) {

   }
}
