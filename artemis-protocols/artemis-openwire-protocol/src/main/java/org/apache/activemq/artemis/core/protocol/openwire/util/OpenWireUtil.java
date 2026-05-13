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
package org.apache.activemq.artemis.core.protocol.openwire.util;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.command.XATransactionId;

public class OpenWireUtil {

   public static final WildcardConfiguration OPENWIRE_WILDCARD = new WildcardConfiguration().setDelimiter('.').setAnyWords('>').setSingleWord('*');

   public static final String SELECTOR_AWARE_OPTION = "selectorAware";

   public static String extractFilterStringOrNull(final ConsumerInfo info, final ActiveMQDestination openWireDest) {
      if (openWireDest.isTopic()) {
         return info.getSelector();
      }
      if (info.getSelector() != null) {
         if (openWireDest.getOptions() != null) {
            if (Boolean.valueOf(openWireDest.getOptions().get(SELECTOR_AWARE_OPTION))) {
               return info.getSelector();
            }
         }
      }
      return null;
   }

   /**
    * We convert the core address to an ActiveMQ Destination. We use the actual address on the message rather than the
    * destination set on the consumer because it maybe different and the JMS spec says that it should be what ever was
    * set on publish/send so a divert or wildcard may mean thats its different to the destination subscribed to by the
    * consumer
    */
   public static ActiveMQDestination toAMQAddress(Message message, ActiveMQDestination actualDestination) {
      String address = message.getAddress();

      if (address == null || address.equals(actualDestination.getPhysicalName())) {
         return actualDestination;
      }

      if (actualDestination.isQueue()) {
         return new ActiveMQQueue(address);
      } else {
         return new ActiveMQTopic(address);
      }
   }

   public static XidImpl toXID(TransactionId xaXid) {
      return toXID((XATransactionId) xaXid);
   }

   public static XidImpl toXID(XATransactionId xaXid) {
      return new XidImpl(xaXid.getBranchQualifier(), xaXid.getFormatId(), xaXid.getGlobalTransactionId());
   }
}
