/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.core.server.ServerProducer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class ServerProducerImpl implements ServerProducer {

   private static final AtomicLong PRODUCER_ID_GENERATOR = new AtomicLong();

   private final long ID;
   private final String name;
   private final String protocol;
   private final long creationTime;
   private volatile long messagesSent = 0;
   private volatile long messagesSentSize = 0;

   private static final AtomicLongFieldUpdater<ServerProducerImpl> messagesSentUpdater = AtomicLongFieldUpdater.newUpdater(ServerProducerImpl.class, "messagesSent");

   private static final AtomicLongFieldUpdater<ServerProducerImpl> messagesSentSizeUpdater = AtomicLongFieldUpdater.newUpdater(ServerProducerImpl.class, "messagesSentSize");

   private final String address;

   private volatile Object lastProducedMessageID;

   private String sessionID;

   private String connectionID;

   public ServerProducerImpl(String name, String protocol, String address) {
      this.ID = PRODUCER_ID_GENERATOR.incrementAndGet();
      this.name = name;
      this.protocol = protocol;
      this.address = address;
      this.creationTime = System.currentTimeMillis();
   }

   @Override
   public long getID() {
      return ID;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public String getAddress() {
      return address;
   }

   @Override
   public String getProtocol() {
      return protocol;
   }

   @Override
   public void setSessionID(String sessionID) {
      this.sessionID = sessionID;
   }

   @Override
   public void setConnectionID(String connectionID) {
      this.connectionID = connectionID;
   }

   @Override
   public String getSessionID() {
      return sessionID;
   }

   @Override
   public String getConnectionID() {
      return connectionID;
   }

   @Override
   public long getCreationTime() {
      return creationTime;
   }

   @Override
   public void updateMetrics(Object lastProducedMessageID, int encodeSize) {
      messagesSentUpdater.addAndGet(this, 1);
      messagesSentSizeUpdater.getAndAdd(this, encodeSize);
      this.lastProducedMessageID = lastProducedMessageID;
   }

   @Override
   public Object getLastProducedMessageID() {
      return lastProducedMessageID;
   }

   @Override
   public long getMessagesSent() {
      return messagesSent;
   }

   @Override
   public long getMessagesSentSize() {
      return messagesSentSize;
   }
}
