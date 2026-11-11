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
package org.apache.activemq.transport.tcp;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.TransportLoggerSupport;
import org.apache.activemq.artemiswrapper.ArtemisBrokerHelper;
import org.apache.activemq.broker.BrokerRegistry;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.transport.InactivityMonitor;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.TransportServer;
import org.apache.activemq.transport.WireFormatNegotiator;
import org.apache.activemq.util.IOExceptionSupport;
import org.apache.activemq.util.IntrospectionSupport;
import org.apache.activemq.util.URISupport;
import org.apache.activemq.wireformat.WireFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpTransportFactory extends TransportFactory {

   private static final Logger LOG = LoggerFactory.getLogger(TcpTransportFactory.class);

   private static volatile InternalServiceInfo brokerService = null;

   @Override
   public Transport doConnect(URI location) throws Exception {
      //here check broker, if no broker, we start one
      Map<String, String> params = URISupport.parseParameters(location);
      String brokerId = params.remove("invmBrokerId");
      boolean autoCreate = true;
      String create = params.remove("create");
      if (create != null) {
         autoCreate = "true".equals(create);
      }

      URI location1 = URISupport.createRemainingURI(location, Collections.emptyMap());

      LOG.info("deciding whether starting an internal broker: " + brokerService + " flag: " + BrokerService.disableWrapper);
      if (autoCreate && brokerService == null && !BrokerService.disableWrapper && BrokerService.checkPort(location1.getPort())) {

         LOG.info("starting internal broker: " + location1);
         ArtemisBrokerHelper.startArtemisBroker(location1);
         brokerService = new InternalServiceInfo(location.toString());

         if (brokerId != null) {
            BrokerRegistry.getInstance().bind(brokerId, ArtemisBrokerHelper.getBroker());
            LOG.info("bound: " + brokerId);
         }
      }
      //remove unused invm parameters
      params.remove("broker.persistent");
      params.remove("broker.useJmx");
      params.remove("marshal");
      params.remove("create");
      params.remove("asyncQueueDepth");
      URI location2 = URISupport.createRemainingURI(location, params);
      return super.doConnect(location2);
   }

   @Override
   public TransportServer doBind(final URI location) throws IOException {
      try {
         Map<String, String> options = new HashMap<>(URISupport.parseParameters(location));

         ServerSocketFactory serverSocketFactory = createServerSocketFactory();
         TcpTransportServer server = createTcpTransportServer(location, serverSocketFactory);
         server.setWireFormatFactory(createWireFormatFactory(options));
         IntrospectionSupport.setProperties(server, options);
         Map<String, Object> transportOptions = IntrospectionSupport.extractProperties(options, "transport.");
         server.setTransportOption(transportOptions);
         server.bind();

         return server;
      } catch (URISyntaxException e) {
         throw IOExceptionSupport.create(e);
      }
   }

   protected TcpTransportServer createTcpTransportServer(final URI location,
                                                         ServerSocketFactory serverSocketFactory) throws IOException, URISyntaxException {
      return new TcpTransportServer(this, location, serverSocketFactory);
   }

   @Override
   public Transport compositeConfigure(Transport transport, WireFormat format, Map options) {

      TcpTransport tcpTransport = transport.narrow(TcpTransport.class);
      IntrospectionSupport.setProperties(tcpTransport, options);

      Map<String, Object> socketOptions = IntrospectionSupport.extractProperties(options, "socket.");
      tcpTransport.setSocketOptions(socketOptions);

      if (tcpTransport.isTrace()) {
         try {
            transport = TransportLoggerSupport.createTransportLogger(transport, tcpTransport.getLogWriterName(), tcpTransport.isDynamicManagement(), tcpTransport.isStartLogging(), tcpTransport.getJmxPort());
         } catch (Throwable e) {
            LOG.error("Could not create TransportLogger object for: " + tcpTransport.getLogWriterName() + ", reason: " + e, e);
         }
      }

      boolean useInactivityMonitor = "true".equals(getOption(options, "useInactivityMonitor", "true"));
      if (useInactivityMonitor && isUseInactivityMonitor(transport)) {
         transport = createInactivityMonitor(transport, format);
         IntrospectionSupport.setProperties(transport, options);
      }

      // Only need the WireFormatNegotiator if using openwire
      if (format instanceof OpenWireFormat) {
         transport = new WireFormatNegotiator(transport, (OpenWireFormat) format, tcpTransport.getMinmumWireFormatVersion());
      }

      return super.compositeConfigure(transport, format, options);
   }

   protected boolean isUseInactivityMonitor(Transport transport) {
      return true;
   }

   @Override
   protected Transport createTransport(URI location, WireFormat wf) throws UnknownHostException, IOException {
      URI localLocation = null;
      String path = location.getPath();
      // see if the path is a local URI location
      if (path != null && path.length() > 0) {
         int localPortIndex = path.indexOf(':');
         try {
            Integer.parseInt(path.substring(localPortIndex + 1, path.length()));
            String localString = location.getScheme() + ":/" + path;
            localLocation = new URI(localString);
         } catch (Exception e) {
            LOG.warn("path isn't a valid local location for TcpTransport to use", e.getMessage());
            if (LOG.isDebugEnabled()) {
               LOG.debug("Failure detail", e);
            }
         }
      }
      SocketFactory socketFactory = createSocketFactory();
      return createTcpTransport(wf, socketFactory, location, localLocation);
   }

   protected TcpTransport createTcpTransport(WireFormat wf,
                                             SocketFactory socketFactory,
                                             URI location,
                                             URI localLocation) throws UnknownHostException, IOException {
      return new TcpTransport(wf, socketFactory, location, localLocation);
   }

   protected ServerSocketFactory createServerSocketFactory() throws IOException {
      return ServerSocketFactory.getDefault();
   }

   protected SocketFactory createSocketFactory() throws IOException {
      return SocketFactory.getDefault();
   }

   protected Transport createInactivityMonitor(Transport transport, WireFormat format) {
      return new InactivityMonitor(transport, format);
   }

   //remember call this if the test is using the internal broker.
   public static void clearService() {
      LOG.info("#### clearing internal service " + brokerService);
      if (brokerService != null) {
         try {
            ArtemisBrokerHelper.stopArtemisBroker();
         } catch (Exception e) {
            e.printStackTrace();
         } finally {
            brokerService = null;
         }
      }
   }

   //added createTime for debugging
   private static class InternalServiceInfo {

      private String internalService;
      private long createTime;

      public InternalServiceInfo(String brokerService) {
         this.internalService = brokerService;
         this.createTime = System.currentTimeMillis();
         LOG.info("just created " + this);
      }

      @Override
      public String toString() {
         return internalService + "@" + createTime;
      }
   }
}
