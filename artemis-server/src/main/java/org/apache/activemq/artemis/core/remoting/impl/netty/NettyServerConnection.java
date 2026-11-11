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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import java.security.cert.X509Certificate;
import java.util.Map;

import io.netty.channel.Channel;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;
import org.apache.activemq.artemis.utils.CertificateUtil;
import org.apache.activemq.artemis.utils.ProxyProtocolUtil;

public class NettyServerConnection extends NettyConnection {

   private String sniHostname;

   private final String router;

   private X509Certificate[] certificates;

   public NettyServerConnection(Map<String, Object> configuration,
                                Channel channel,
                                ServerConnectionLifeCycleListener listener,
                                boolean batchingEnabled,
                                boolean directDeliver,
                                String router) {
      super(configuration, channel, listener, batchingEnabled, directDeliver);

      this.router = router;
   }

   @Override
   public String getSNIHostName() {
      return sniHostname;
   }

   public void setSNIHostname(String sniHostname) {
      this.sniHostname = sniHostname;
   }

   @Override
   public String getRouter() {
      return router;
   }

   public X509Certificate[] getPeerCertificates() {
      if (certificates == null) {
         certificates = CertificateUtil.getCertsFromChannel(channel);
      }
      return certificates;
   }

   /**
    * {@return a string representation of the remote address of this connection; if this connection was made via the
    * proxy protocol then this will be the original address, not the proxy address}
    */
   @Override
   public String getRemoteAddress() {
      return ProxyProtocolUtil.getRemoteAddress(channel);
   }

   /**
    * {@return if this connection is made via the proxy protocol then this will be the address of the proxy; otherwise
    * null}
    */
   public String getProxyAddress() {
      return ProxyProtocolUtil.getProxyAddress(channel);
   }

   /**
    * {@return the version of the proxy protocol used to make the connection or null if not applicable}
    */
   public String getProxyProtocolVersion() {
      return ProxyProtocolUtil.getProxyProtocolVersion(channel);
   }

   public static String getProxyAddress(Connection connection) {
      if (connection instanceof NettyServerConnection nettyServerConnection) {
         return nettyServerConnection.getProxyAddress();
      } else {
         return null;
      }
   }

   public static String getProxyProtocolVersion(Connection connection) {
      if (connection instanceof NettyServerConnection nettyServerConnection) {
         return nettyServerConnection.getProxyProtocolVersion();
      } else {
         return null;
      }
   }
}
