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

package org.apache.activemq.artemis.core.protocol.hornetq;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.client.impl.ClientMessageInternal;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.MessagePacketI;
import org.apache.activemq.artemis.core.protocol.hornetq.util.HQPropertiesConverter;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

public class HQPropertiesConversionInterceptor implements Interceptor {

   private final boolean replaceHQ;

   public HQPropertiesConversionInterceptor(final boolean replaceHQ) {
      this.replaceHQ = replaceHQ;
   }

   @Override
   public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {

      if (HQPropertiesConverter.isMessagePacket(packet)) {
         handleReceiveMessage((MessagePacketI) packet);
      }
      return true;
   }

   private void handleReceiveMessage(MessagePacketI messagePacket) {
      Message copy = messagePacket.getMessage();

      // there's no need to copy client messages, only the server ones are problematic
      if (!(copy instanceof ClientMessageInternal)) {
         if (copy instanceof CoreMessage message && message.getBuffer() != null) {
            copy = copy.copy();
         }
         messagePacket.replaceMessage(copy);
      }

      if (replaceHQ) {
         HQPropertiesConverter.replaceHQProperties(copy);
      } else {
         HQPropertiesConverter.replaceAMQProperties(copy);
      }

   }

}
