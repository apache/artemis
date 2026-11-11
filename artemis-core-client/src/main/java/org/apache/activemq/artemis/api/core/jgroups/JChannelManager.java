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

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.JGroupsBroadcastEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This class maintain a global Map of JChannels wrapped in JChannelWrapper for the purpose of reference counting.
 * <p>
 * Wherever a JChannel is needed it should only get it by calling the getChannel() method of this class. The real
 * disconnect of channels are also done here only.
 */
public class JChannelManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final JChannelManager theInstance = new JChannelManager();

   public static JChannelManager getInstance() {
      return theInstance;
   }

   private JChannelManager() {
   }

   public synchronized JChannelManager clear() {
      for (JChannelWrapper wrapper : channels.values()) {
         wrapper.closeChannel();
      }
      channels.clear();
      setLoopbackMessages(false);
      return this;
   }

   // if true, messages will be loopbacked
   // this is useful for testcases using a single channel.
   private boolean loopbackMessages = false;

   private static final Map<String, JChannelWrapper> channels = new HashMap<>();

   public boolean isLoopbackMessages() {
      return loopbackMessages;
   }

   public JChannelManager setLoopbackMessages(boolean loopbackMessages) {
      this.loopbackMessages = loopbackMessages;
      return this;
   }

   public synchronized JChannelWrapper getJChannel(String channelName,
                                                   JGroupsBroadcastEndpoint endpoint) throws Exception {
      JChannelWrapper wrapper = channels.get(channelName);
      if (wrapper == null) {
         wrapper = new JChannelWrapper(this, channelName, endpoint.createChannel());
         channels.put(channelName, wrapper);
         logger.trace("Put Channel {}", channelName);
         return wrapper;
      }
      logger.trace("Add Ref Count {}", channelName);

      return wrapper.addRef();
   }

   public synchronized void removeChannel(String channelName) {
      channels.remove(channelName);
   }

   public synchronized void closeWrapper(JChannelWrapper wrapper, boolean closeWrappedChannel) {
      if (wrapper.decRef() == 0) {
         if (closeWrappedChannel) {
            wrapper.closeChannel();
         }
         removeChannel(wrapper.getChannelName());
      }
   }
}
