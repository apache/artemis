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

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslHandler;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class CertificateUtil {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SSL_HANDLER_NAME = "ssl";

   public static final String CERT_SUBJECT_DN_UNAVAILABLE = "unavailable";

   public static String getCertSubjectDN(RemotingConnection connection) {
      X509Certificate[] certs = getCertsFromConnection(connection);
      if (certs != null && certs.length > 0 && certs[0] != null) {
         return certs[0].getSubjectDN().getName();
      } else {
         return CERT_SUBJECT_DN_UNAVAILABLE;
      }
   }

   public static X509Certificate[] getCertsFromConnection(RemotingConnection remotingConnection) {
      X509Certificate[] certificates = null;
      if (remotingConnection != null) {
         Connection transportConnection = remotingConnection.getTransportConnection();
         if (transportConnection instanceof NettyServerConnection nettyServerConnection) {
            certificates = nettyServerConnection.getPeerCertificates();
         } else if (transportConnection instanceof NettyConnection nettyConnection) {
            certificates = getCertsFromChannel(nettyConnection.getChannel());
         }
      }
      return certificates;
   }

   public static Principal getPeerPrincipalFromConnection(RemotingConnection remotingConnection) {
      Principal result = null;
      if (remotingConnection != null) {
         Connection transportConnection = remotingConnection.getTransportConnection();
         if (transportConnection instanceof NettyConnection nettyConnection) {
            ChannelHandler channelHandler = nettyConnection.getChannel().pipeline().get(SSL_HANDLER_NAME);
            if (channelHandler != null && channelHandler instanceof SslHandler sslHandler) {
               try {
                  result = sslHandler.engine().getSession().getPeerPrincipal();
               } catch (SSLPeerUnverifiedException ignored) {
               }
            }
         }
      }

      return result;
   }

   public static Principal getLocalPrincipalFromConnection(NettyConnection nettyConnection) {
      Principal result = null;
      ChannelHandler handler = nettyConnection.getChannel().pipeline().get(SSL_HANDLER_NAME);
      if (handler instanceof SslHandler sslHandler) {
         result = sslHandler.engine().getSession().getLocalPrincipal();
      }

      return result;
   }

   public static X509Certificate[] getCertsFromChannel(Channel channel) {
      Certificate[] plainCerts = null;
      ChannelHandler channelHandler = channel.pipeline().get("ssl");
      if (channelHandler != null && channelHandler instanceof SslHandler sslHandler) {
         try {
            plainCerts = sslHandler.engine().getSession().getPeerCertificates();
         } catch (SSLPeerUnverifiedException e) {
            // ignore
         }
      }

      /*
       * When using the OpenSSL provider on the broker the getPeerCertificates() method does *not* return a
       * X509Certificate[] so we need to convert the Certificate[] that is returned. This code is inspired by Tomcat's
       * org.apache.tomcat.util.net.jsse.JSSESupport class.
       */
      X509Certificate[] x509Certs = null;
      if (plainCerts != null && plainCerts.length > 0) {
         x509Certs = new X509Certificate[plainCerts.length];
         for (int i = 0; i < plainCerts.length; i++) {
            if (plainCerts[i] instanceof X509Certificate x509Certificate) {
               x509Certs[i] = x509Certificate;
            } else {
               try {
                  x509Certs[i] = (X509Certificate) CertificateFactory
                     .getInstance("X.509").generateCertificate(new ByteArrayInputStream(plainCerts[i].getEncoded()));
               } catch (Exception ex) {
                  logger.trace("Failed to convert SSL cert", ex);
                  return null;
               }
            }

            if (logger.isTraceEnabled()) {
               logger.trace("Cert #{} = {}", i, x509Certs[i]);
            }
         }
      }

      return x509Certs;
   }
}
