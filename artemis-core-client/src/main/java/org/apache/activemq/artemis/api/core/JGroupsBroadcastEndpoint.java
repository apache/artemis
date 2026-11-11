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
package org.apache.activemq.artemis.api.core;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.jgroups.JChannelManager;
import org.apache.activemq.artemis.api.core.jgroups.JChannelWrapper;
import org.apache.activemq.artemis.api.core.jgroups.JGroupsReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.jgroups.JChannel;

/**
 * This class is the implementation of Apache Artemis members discovery that will use JGroups.
 */
public abstract class JGroupsBroadcastEndpoint implements BroadcastEndpoint {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String channelName;

   private boolean clientOpened;

   private boolean broadcastOpened;

   private JChannelWrapper channel;

   private JGroupsReceiver receiver;

   private JChannelManager manager;

   public JGroupsBroadcastEndpoint(JChannelManager manager, String channelName) {
      this.manager = manager;
      this.channelName = channelName;
   }

   @Override
   public void broadcast(final byte[] data) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Broadcasting: BroadCastOpened={}, channelOPen={}", broadcastOpened, channel.getChannel().isOpen());
      }

      if (broadcastOpened) {
         org.jgroups.Message msg = new org.jgroups.BytesMessage();

         msg.setArray(data);

         channel.send(msg);
      }
   }

   @Override
   public byte[] receiveBroadcast() throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Receiving Broadcast: clientOpened={}, channelOPen={}", clientOpened, channel.getChannel().isOpen());
      }

      if (clientOpened) {
         return receiver.receiveBroadcast();
      } else {
         return null;
      }
   }

   @Override
   public byte[] receiveBroadcast(long time, TimeUnit unit) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Receiving Broadcast2: clientOpened={}, channelOPen={}", clientOpened, channel.getChannel().isOpen());
      }

      if (clientOpened) {
         return receiver.receiveBroadcast(time, unit);
      } else {
         return null;
      }
   }

   @Override
   public synchronized void openClient() throws Exception {
      if (clientOpened) {
         return;
      }
      if (channel.getChannel() == null || channel.getChannel().isClosed()) {
         initChannel();
      }
      internalOpen();
      receiver = new JGroupsReceiver();
      channel.addReceiver(receiver);
      clientOpened = true;
   }

   @Override
   public synchronized void openBroadcaster() throws Exception {
      if (broadcastOpened) {
         return;
      }
      internalOpen();
      broadcastOpened = true;
   }

   public abstract JChannel createChannel() throws Exception;

   public JGroupsBroadcastEndpoint initChannel() throws Exception {
      this.channel = manager.getJChannel(channelName, this);
      return this;
   }

   protected void internalOpen() throws Exception {
      channel.connect();
   }

   @Override
   public synchronized void close(boolean isBroadcast) throws Exception {
      if (isBroadcast) {
         broadcastOpened = false;
      } else {
         channel.removeReceiver(receiver);
         clientOpened = false;
      }
      internalCloseChannel(channel);
   }

   /**
    * Closes the channel used in this JGroups Broadcast. Can be overridden by implementations that use an externally
    * managed channel.
    */
   protected synchronized void internalCloseChannel(JChannelWrapper channel) {
      channel.close(true);
   }

}
