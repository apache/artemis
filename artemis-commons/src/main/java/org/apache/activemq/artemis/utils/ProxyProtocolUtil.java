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
package org.apache.activemq.artemis.utils;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class ProxyProtocolUtil {
   public static final AttributeKey<String> PROXY_PROTOCOL_SOURCE_ADDRESS = AttributeKey.valueOf("proxyProtocolSourceAddress");
   public static final AttributeKey<String> PROXY_PROTOCOL_DESTINATION_ADDRESS = AttributeKey.valueOf("proxyProtocolDestinationAddress");
   public static final AttributeKey<String> PROXY_PROTOCOL_VERSION = AttributeKey.valueOf("proxyProtocolVersion");

   /**
    * {@return a string representation of the remote address of this Channel taking into account whether the PROXY
    * protocol was used by the remote client}
    */
   public static String getRemoteAddress(Channel channel) {
      String addressFromAttribute = getAddressFromAttribute(PROXY_PROTOCOL_SOURCE_ADDRESS, channel);
      if (addressFromAttribute != null) {
         return addressFromAttribute;
      } else {
         return SocketAddressUtil.toString(channel.remoteAddress());
      }
   }

   /**
    * {@return a string representation of the proxy address of this Channel if and only if the PROXY protocol was used
    * by the remote client; otherwise null}
    */
   public static String getProxyAddress(Channel channel) {
      return getAddressFromAttribute(PROXY_PROTOCOL_DESTINATION_ADDRESS, channel);
   }

   private static String getAddressFromAttribute(AttributeKey<String> addressKey, Channel channel) {
      String address = channel.attr(addressKey).get();
      if (address != null && !address.isEmpty()) {
         return address;
      } else {
         return null;
      }
   }

   public static String getProxyProtocolVersion(Channel channel) {
      return channel.attr(PROXY_PROTOCOL_VERSION).get() == null ? null : channel.attr(PROXY_PROTOCOL_VERSION).get().toString();
   }
}
