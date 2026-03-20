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
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslHandler;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateUtil {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SSL_HANDLER_NAME = "ssl";

   public static final String UPN_OID = "1.3.6.1.4.1.311.20.2.3";

   private static final byte[] UPN_OID_BYTES = {0x2b, 0x06, 0x01, 0x04, 0x01, (byte) 0x82, 0x37, 0x14, 0x02, 0x03};

   public static final String CERT_INFO_UNAVAILABLE = "unavailable";

   /**
    * Inspects the input {@code RemotingConnection} and extracts the Distinguished Name (DN) from the associated SSL
    * certificate. If this name cannot be retrieved then it returns the value of {@link #CERT_INFO_UNAVAILABLE}.
    * This method is suitable when printing the DN to the logs, adding it to a notification message, etc. It will never
    * return {@code null}.
    *
    * @return the Distinguished Name (DN) of the SSL certificate associated with the {@code RemotingConnection} or
    * {@link #CERT_INFO_UNAVAILABLE} otherwise
    */
   public static String getDistinguishedNameForPrint(RemotingConnection connection) {
      return Objects.requireNonNullElse(getDistinguishedName(getCertsFromConnection(connection)), CERT_INFO_UNAVAILABLE);
   }

   /**
    * {@return the Distinguished Name (DN) of the SSL certificate associated with the {@code RemotingConnection}
    * otherwise {@code null}}
    */
   public static String getDistinguishedName(RemotingConnection connection) {
      return getDistinguishedName(getCertsFromConnection(connection));
   }

   /**
    * {@return the Distinguished Name (DN) from the first SSL certificate in the array otherwise null}
    */
   public static String getDistinguishedName(X509Certificate[] certs) {
      if (certs != null && certs.length > 0 && certs[0] != null) {
         return certs[0].getSubjectDN().getName();
      } else {
         return null;
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
      ChannelHandler channelHandler = channel.pipeline().get(SSL_HANDLER_NAME);
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

   /**
    * Extracts the User Principal Name (UPN) from the Subject Alternative Names (SANs) of the first SSL certificate in
    * the array. If this name cannot be retrieved then it returns the value of {@link #CERT_INFO_UNAVAILABLE}.
    * This method is suitable when printing the UPN to the logs, adding it to a notification message, etc. It will never
    * return {@code null}.
    *
    * @return the User Principal Name (UPN) of the SSL certificate associated with the {@code RemotingConnection} or
    * {@link #CERT_INFO_UNAVAILABLE} otherwise
    */
   public static String getUserPrincipalNameForPrint(RemotingConnection connection) {
      return Objects.requireNonNullElse(getUserPrincipalName(getCertsFromConnection(connection)), CERT_INFO_UNAVAILABLE);
   }

   /**
    * {@return the User Principal Name (UPN) of the SSL certificate associated with the {@code RemotingConnection}
    * otherwise {@code null}}
    */
   public static String getUserPrincipalName(RemotingConnection connection) {
      return getUserPrincipalName(getCertsFromConnection(connection));
   }

   /**
    * Extracts the User Principal Name (UPN) from the Subject Alternative Names (SANs) of the first SSL certificate in
    * the array.
    *
    * @param certs an array of X.509 certificates, where the first certificate is inspected for the UPN. If the array is
    *              null, empty, or the first certificate is null, the method returns null.
    * @return the extracted UPN as a string, or null if the UPN is not found or if the SANs are null for the given
    * certificate.
    */
   public static String getUserPrincipalName(X509Certificate[] certs) {
      if (certs == null || certs.length == 0 || certs[0] == null) {
         return null;
      }
      Collection<List<?>> sans;
      try {
         sans = certs[0].getSubjectAlternativeNames();
      } catch (CertificateParsingException e) {
         ActiveMQServerLogger.LOGGER.failedToParseCertificate(certs[0].toString(), e);
         return null;
      }
      if (sans == null) {
         logger.debug("No SANs found in certificate");
         return null;
      }

      for (List<?> san : sans) {
         if (san.size() == 4 && san.get(0) instanceof Integer generalName && generalName == 0 && san.get(2) instanceof String oid && oid.equals(UPN_OID)) {
            // This works on Java 21+
            return (String) san.get(3);
         } else if (san.size() == 2 && san.get(0) instanceof Integer generalName && generalName == 0) {
            // Manual parsing is still required before Java 21
            return parseOtherNameForUpn((byte[]) san.get(1));
         }
      }
      return null;
   }

   /**
    * Parses a DER-encoded Subject Alternative Name {@code otherName} value and tries to extract a UPN string.
    * <p>
    * The method walks the nested tag-length-value ASN.1/DER structure. It expects an outer context-specific wrapper,
    * verifies the embedded UPN OID, then reads the inner wrapped string value (which may be double-wrapped). It accepts
    * either UTF8String or IA5String encodings.
    *
    * @param der the buffer containing the DER bytes to inspect
    * @return the decoded UPN string, or {@code null} if the structure does not match the expected layout
    */
   protected static String parseOtherNameForUpn(byte[] der) {
      ByteBuf buf = Unpooled.wrappedBuffer(der);
      try {
         // read outer sequence
         short outerSequenceTag = buf.readUnsignedByte();
         if (outerSequenceTag != 0x30) {
            logger.debug("Unexpected outer sequence tag 0x{}; expected 0x30", String.format("%02X", outerSequenceTag));
            return null;
         }
         readDerLength(buf);

         // read & validate OID
         short oidTag = buf.readUnsignedByte();
         if (oidTag != 0x06) {
            logger.debug("Unexpected oid tag 0x{}; expected 0x06", String.format("%02X", oidTag));
            return null;
         }
         int oidLen = readDerLength(buf);
         byte[] oidBytes = new byte[oidLen];
         buf.readBytes(oidBytes);
         if (!Arrays.equals(oidBytes, UPN_OID_BYTES)) {
            logger.debug("OID mismatch");
            return null;
         }

         // read context tag
         short upnContextTag = buf.readUnsignedByte();
         if (upnContextTag != 0xA0) {
            logger.debug("Unexpected context tag for UPN 0x{}; expected 0xA0", String.format("%02X", upnContextTag));
            return null;
         }
         readDerLength(buf);

         // handle potential "double wrap"
         short nextTag = buf.getUnsignedByte(buf.readerIndex());
         if (nextTag == 0xA0) {
            buf.readUnsignedByte();
            readDerLength(buf);
            nextTag = buf.getByte(buf.readerIndex());
         }

         if (nextTag != 0x0C && nextTag != 0x16) {
            logger.debug("Unexpected string tag 0x{}; expected UTF8String (0x0C) or IA5String (0x16)", String.format("%02X", nextTag));
            return null;
         }
         buf.readUnsignedByte();

         // read the string
         int upnLen = readDerLength(buf);
         byte[] upnBytes = new byte[upnLen];
         buf.readBytes(upnBytes);
         return new String(upnBytes, StandardCharsets.UTF_8);
      } finally {
         buf.release();
      }
   }

   /**
    * In DER length encoding:
    * <ul>
    * <li>if the first length byte has top bit 0</li>
    * <ul>
    * <li>it is a short-form length</li>
    * <li>the length is stored right there in that byte</li>
    * </ul>
    * <li>if the top bit is 1</li>
    * <ul>
    * <li>it is a long-form length</li>
    * <li>the lower 7 bits tell you how many additional bytes encode the length</li>
    * </ul>
    * </ul>
    *
    * @param buf the {@code ByteBuf} to read the length value from. It must contain enough bytes to decode the length
    *            fully according to the encoded format.
    * @return the decoded length as an integer.
    */
   private static int readDerLength(ByteBuf buf) {
      int first = buf.readUnsignedByte();
      if ((first & 0x80) == 0) {
         return first;
      }

      int numBytes = first & 0x7F;
      int len = 0;
      for (int i = 0; i < numBytes; i++) {
         len = (len << 8) | buf.readUnsignedByte();
      }
      return len;
   }
}
