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
package org.apache.activemq.artemis.api.core.jgroups;

import java.util.ArrayList;
import java.util.List;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class wraps a JChannel with a reference counter. The reference counter controls the life of the JChannel. When
 * reference count is zero, the channel will be disconnected.
 */
public class JChannelWrapper {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean connected = false;
   AtomicInteger refCount = new AtomicInteger(1);
   final JChannel channel;
   final String channelName;
   final List<JGroupsReceiver> receivers = new ArrayList<>();
   private final JChannelManager manager;

   public JChannelWrapper(JChannelManager manager, final String channelName, JChannel channel) throws Exception {
      this.channelName = channelName;
      this.channel = channel;
      this.manager = manager;

      if (logger.isTraceEnabled() && channel.getReceiver() != null) {
         logger.trace("{} The channel already had a receiver previously!!!! == {}", this, channel.getReceiver(), new Exception("trace"));
      }

      //we always add this for the first ref count
      channel.setReceiver(new Receiver() {

         @Override
         public String toString() {
            return "ReceiverAdapter::" + JChannelWrapper.this;
         }

         @Override
         public void receive(org.jgroups.Message msg) {
            if (logger.isTraceEnabled()) {
               logger.trace("{}:: Wrapper received {} on channel {}", this, msg, channelName);
            }
            synchronized (receivers) {
               for (JGroupsReceiver r : receivers) {
                  r.receive(msg);
               }
            }
         }
      });
   }

   public JChannel getChannel() {
      return channel;
   }

   public String getChannelName() {
      return channelName;
   }

   public void close(boolean closeWrappedChannel) {
      manager.closeWrapper(this, closeWrappedChannel);
   }

   public synchronized void closeChannel() {
      connected = false;
      channel.setReceiver(null);
      if (logger.isTraceEnabled()) {
         logger.trace("{}::Closing Channel: {}", this, channelName, new Exception("Trace"));
      }
      channel.close();
   }

   public void removeReceiver(JGroupsReceiver receiver) {
      if (logger.isTraceEnabled()) {
         logger.trace("{}::removeReceiver: {} on {}", this, receiver, channelName, new Exception("Trace"));
      }
      synchronized (receivers) {
         receivers.remove(receiver);
      }
   }

   public synchronized void connect() throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("{}:: Connecting {}", this, channelName, new Exception("Trace"));
      }

      // It is important to check this otherwise we could reconnect an already connected channel
      if (connected) {
         return;
      }

      connected = true;

      if (!channel.isConnected()) {
         channel.connect(channelName);
      }
   }

   public void addReceiver(JGroupsReceiver jGroupsReceiver) {
      synchronized (receivers) {
         if (logger.isTraceEnabled()) {
            logger.trace("{}::Add Receiver: {} on {}", this, jGroupsReceiver, channelName);
         }
         receivers.add(jGroupsReceiver);
      }
   }

   public void send(org.jgroups.Message msg) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("{}::Sending JGroups Message: Open={} on channel {} msg={}", this, channel.isOpen(), channelName, msg);
      }
      if (!manager.isLoopbackMessages()) {
         msg.setFlag(Message.TransientFlag.DONT_LOOPBACK);
      }
      channel.send(msg);
   }

   public JChannelWrapper addRef() {
      final int count = refCount.incrementAndGet();
      if (logger.isTraceEnabled()) {
         logger.trace("{}::RefCount++ = {} on channel {}", this, count, channelName);
      }
      return this;
   }

   public int decRef() {
      final int count = refCount.decrementAndGet();
      if (logger.isTraceEnabled()) {
         logger.trace("{}::RefCount-- {} on channel {}", this, count, channelName, new Exception("Trace"));
      }
      return count;
   }

   @Override
   public String toString() {
      return super.toString() +
         "{refCount=" + refCount.get() +
         ", channel=" + channel +
         ", channelName='" + channelName + '\'' +
         ", connected=" + connected +
         '}';
   }
}
