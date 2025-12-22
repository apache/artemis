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
package org.apache.activemq.artemis.cli.commands.tools.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;
import javax.xml.validation.Validator;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.PrivilegedAction;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionConfigurationAbtract;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.ListUtil;
import org.apache.activemq.artemis.utils.XmlProvider;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Read XML output from {@code org.apache.activemq.artemis.core.persistence.impl.journal.XmlDataExporter}, create a core
 * session, and send the messages to a running instance of Apache Artemis.  It uses the StAX
 * {@code javax.xml.stream.XMLStreamReader} for speed and simplicity.
 */
@Command(name = "imp", description = "Import all message-data using an XML that could be interpreted by any system.")
public final class XmlDataImporter extends ConnectionConfigurationAbtract {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private XMLStreamReader reader;

   private XMLMessageImporter messageReader;

   // this session is really only needed if the "session" variable does not auto-commit sends
   ClientSession managementSession;

   boolean localSession = false;

   final Map<String, String> addressMap = new HashMap<>();

   final Map<String, Long> queueIDs = new HashMap<>();

   Map<String, String> oldPrefixTranslation = new HashMap<>();

   private ClientSession session;
   private ClientProducer producer;

   @Deprecated(forRemoval = true)
   @Option(names = "--host", description = "The host used to import the data. Default: null.", hidden = true)
   public String host = null;

   @Deprecated(forRemoval = true)
   @Option(names = "--port", description = "The port used to import the data. Default: 61616.", hidden = true)
   public int port = 61616;

   @Option(names = "--transaction", description = "Import every message using a single transction. If anything goes wrong during the process the entire import will be aborted. Default: false.", hidden = true)
   public boolean transactional;

   @Option(names = "--commit-interval", description = "How often to commit.", hidden = true)
   public int commitInterval = 1000;

   @Option(names = "--input", description = "The input file name. Default: exp.dmp.", required = true)
   public String input = "exp.dmp";

   @Option(names = "--sort", description = "Sort the messages from the input (used for older versions that won't sort messages).")
   public boolean sort = false;

   @Option(names = "--legacy-prefixes", description = "Do not remove prefixes from legacy imports.")
   public boolean legacyPrefixes = false;

   TreeSet<XMLMessageImporter.MessageInfo> messages;

   @Override
   public Object execute(ActionContext context) throws Exception {
      process(input, host, port);
      return null;
   }

   public void process(String inputFileName, String host, int port) throws Exception {
      try (FileInputStream inputFile = new FileInputStream(inputFileName)) {
         this.process(inputFile, host, port);
      }
   }

   /**
    * This is the normal constructor for programmatic access to the
    * {@code org.apache.activemq.artemis.core.persistence.impl.journal.XmlDataImporter} if the session passed in uses
    * auto-commit for sends.
    * <p>
    * If the session needs to be transactional then use the constructor which takes 2 sessions.
    *
    * @param inputStream the stream from which to read the XML for import
    * @param session     used for sending messages, must use auto-commit for sends
    */
   public void process(InputStream inputStream, ClientSession session) throws Exception {
      this.process(inputStream, session, null);
   }

   /**
    * This is the constructor to use if you wish to import all messages transactionally.
    * <p>
    * Pass in a session which doesn't use auto-commit for sends, and one that does (for management operations necessary
    * during import).
    *
    * @param inputStream       the stream from which to read the XML for import
    * @param session           used for sending messages, doesn't need to auto-commit sends
    * @param managementSession used for management queries, must use auto-commit for sends
    */
   public void process(InputStream inputStream,
                       ClientSession session,
                       ClientSession managementSession) throws Exception {
      Objects.requireNonNull(inputStream);
      reader = XmlProvider.createXMLStreamReader(inputStream);
      messageReader = new XMLMessageImporter(reader, session);
      messageReader.setOldPrefixTranslation(oldPrefixTranslation);

      this.session = session;
      this.producer = session.createProducer();
      if (managementSession != null) {
         this.managementSession = managementSession;
      } else {
         this.managementSession = session;
      }

      processXml();
   }

   public void process(InputStream inputStream, String host, int port) throws Exception {
      ServerLocator serverLocator;
      if (host != null) {
         Map<String, Object> connectionParams = Map.of(
            TransportConstants.HOST_PROP_NAME, host,
            TransportConstants.PORT_PROP_NAME, Integer.toString(port)
         );
         serverLocator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName(), connectionParams));
      } else {
         serverLocator = ActiveMQClient.createServerLocator(brokerURL);
      }
      ClientSessionFactory sf = serverLocator.createSessionFactory();

      ClientSession session = null;
      ClientSession managementSession = null;

      try {

         if (user != null || password != null) {
            session = sf.createSession(user, password, false, false, true, false, 0);
            managementSession = sf.createSession(user, password, false, true, true, false, 0);
         } else {
            session = sf.createSession(false, false, true);
            managementSession = sf.createSession(false, true, true);
         }
         localSession = true;

         producer = session.createProducer();

         process(inputStream, session, managementSession);
      } finally {
         try {
            session.commit();
            session.close();
         } catch (Throwable ignored) {
         }
         try {
            managementSession.commit();
            managementSession.close();
         } catch (Throwable ignored) {
         }


      }
   }

   public void validate(String fileName) throws Exception {
      try (FileInputStream file = new FileInputStream(fileName)) {
         validate(file);
      }
   }

   public void validate(InputStream inputStream) throws Exception {
      XMLStreamReader reader = XmlProvider.createXMLStreamReader(inputStream);
      Validator validator = XmlProvider.newValidator(XmlDataImporter.findResource("schema/artemis-import-export.xsd"));
      validator.validate(new StAXSource(reader));
      reader.close();
   }

   private static URL findResource(final String resourceName) {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<URL>) () -> ClassloadingUtil.findResource(resourceName));
   }

   private void processXml() throws Exception {
      if (sort) {
         messages = new TreeSet<XMLMessageImporter.MessageInfo>(Comparator.comparingLong(o -> o.id));
      }
      while (reader.hasNext()) {
         if (logger.isDebugEnabled()) {
            logger.debug("EVENT:[{}][{}] ", reader.getLocation().getLineNumber(), reader.getLocation().getColumnNumber());
         }

         if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
            if (XmlDataConstants.OLD_BINDING.equals(reader.getLocalName())) {
               oldBinding(); // export from 1.x
            } else if (XmlDataConstants.QUEUE_BINDINGS_CHILD.equals(reader.getLocalName())) {
               bindQueue();
            } else if (XmlDataConstants.ADDRESS_BINDINGS_CHILD.equals(reader.getLocalName())) {
               bindAddress();
            } else if (XmlDataConstants.MESSAGES_CHILD.equals(reader.getLocalName())) {
               processMessage();
            }
         }
         reader.next();
      }

      if (sort) {
         long messageNr = 0;
         for (XMLMessageImporter.MessageInfo msgtmp : messages) {
            sendMessage(msgtmp.queues, msgtmp.message);
            messageNr++;
            if (messageNr % commitInterval == 0) {
               session.commit();
            }
         }
      }

      session.commit();
   }

   long messageNr = 0;

   private void processMessage() throws Exception {
      messageNr++;

      if (messageNr % commitInterval == 0) {
         getActionContext().err.println("Processed " + messageNr + " messages");
         session.commit();
      }
      XMLMessageImporter.MessageInfo info = messageReader.readMessage(false);
      if (sort) {
         messages.add(info);
      } else {
         sendMessage(info.queues, info.message);
      }
   }


   private void createUndefinedQueue(String name, RoutingType routingType) throws Exception {
      ClientSession.QueueQuery queueQuery = managementSession.queueQuery(SimpleString.of(name));
      if (!queueQuery.isExists()) {
         managementSession.createQueue(QueueConfiguration.of(name).setRoutingType(routingType).setDurable(true).setAutoCreateAddress(true));
      }
   }


   private void sendMessage(List<String> queues, Message message) throws Exception {
      String destination = addressMap.get(queues.get(0));
      if (destination == null) {
         createUndefinedQueue(queues.get(0), message.getRoutingType());
         destination = queues.get(0);
         addressMap.put(queues.get(0), queues.get(0));
      }

      final ByteBuffer buffer = ByteBuffer.allocate(queues.size() * 8);

      final boolean debugLog = logger.isDebugEnabled();
      final StringBuilder debugLogMessage = debugLog ? new StringBuilder() : null;
      if (debugLog) {
         debugLogMessage.append("Sending ").append(message).append(" to address: ").append(destination).append("; routed to queues: ");
      }

      for (String queue : queues) {
         long queueID = -1;

         if (queueIDs.containsKey(queue)) {
            queueID = queueIDs.get(queue);
         } else {
            // Get the ID of the queues involved so the message can be routed properly.  This is done because we cannot
            // send directly to a queue, we have to send to an address instead but not all the queues related to the
            // address may need the message
            try (ClientRequestor requestor = new ClientRequestor(managementSession, "activemq.management")) {
               ClientMessage managementMessage = managementSession.createMessage(false);
               ManagementHelper.putAttribute(managementMessage, ResourceNames.QUEUE + queue, "ID");
               managementSession.start();
               if (debugLog) {
                  logger.debug("Requesting ID for: {}", queue);
               }
               ClientMessage reply = requestor.request(managementMessage);
               if (ManagementHelper.hasOperationSucceeded(reply)) {
                  Number idObject = (Number) ManagementHelper.getResult(reply);
                  queueID = idObject.longValue();
               } else {
                  if (debugLog) {
                     logger.debug("Failed to get ID for {}, reply: {}", queue, ManagementHelper.getResult(reply, String.class));
                  }
               }
            }

            if (debugLog) {
               logger.debug("ID for {} is: {}", queue, queueID);
            }
            if (queueID != -1) {
               queueIDs.put(queue, queueID);  // store it so we don't have to look it up every time
            }
         }

         if (queueID != -1) {
            buffer.putLong(queueID);
         }
         if (debugLog) {
            debugLogMessage.append(queue).append(", ");
         }
      }

      if (debugLog) {
         debugLogMessage.delete(debugLogMessage.length() - 2, debugLogMessage.length()); // take off the trailing comma+space
         logger.debug(debugLogMessage.toString());
      }

      message.putBytesProperty(Message.HDR_ROUTE_TO_IDS, buffer.array());
      producer.send(SimpleString.of(destination), message);
   }

   private void oldBinding() throws Exception {
      String queueName = "";
      String address = "";
      String filter = "";

      for (int i = 0; i < reader.getAttributeCount(); i++) {
         String attributeName = reader.getAttributeLocalName(i);
         switch (attributeName) {
            case XmlDataConstants.OLD_ADDRESS:
               address = reader.getAttributeValue(i);
               break;
            case XmlDataConstants.OLD_QUEUE:
               queueName = reader.getAttributeValue(i);
               break;
            case XmlDataConstants.OLD_FILTER:
               filter = reader.getAttributeValue(i);
               break;
         }
      }

      if (queueName == null || address == null || filter == null) {
         // not expected to happen unless someone manually changed the format
         throw new IllegalStateException("invalid format, missing queue, address or filter");
      }

      RoutingType routingType = RoutingType.MULTICAST;

      if (address.startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString())) {
         routingType = RoutingType.ANYCAST;
         if (!legacyPrefixes) {
            String newaddress = address.substring(PacketImpl.OLD_QUEUE_PREFIX.length());
            address = newaddress;
         }
      } else if (address.startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString())) {
         routingType = RoutingType.MULTICAST;
         if (!legacyPrefixes) {
            String newaddress = address.substring(PacketImpl.OLD_TOPIC_PREFIX.length());
            address = newaddress;
         }
      }

      if (queueName.startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString())) {
         if (!legacyPrefixes) {
            String newQueueName = queueName.substring(PacketImpl.OLD_QUEUE_PREFIX.length());
            oldPrefixTranslation.put(queueName, newQueueName);
            queueName = newQueueName;
         }
      } else if (queueName.startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString())) {
         if (!legacyPrefixes) {
            String newQueueName = queueName.substring(PacketImpl.OLD_TOPIC_PREFIX.length());
            oldPrefixTranslation.put(queueName, newQueueName);
            queueName = newQueueName;
         }
      }


      ClientSession.AddressQuery addressQuery = session.addressQuery(SimpleString.of(address));

      if (!addressQuery.isExists()) {
         session.createAddress(SimpleString.of(address), routingType, true);
      }

      if (!filter.equals(FilterImpl.GENERIC_IGNORED_FILTER)) {
         ClientSession.QueueQuery queueQuery = session.queueQuery(SimpleString.of(queueName));

         if (!queueQuery.isExists()) {
            session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(routingType).setFilterString(filter));
            if (logger.isDebugEnabled()) {
               logger.debug("Binding queue(name={}, address={}, filter={})", queueName, address, filter);
            }
         } else {
            logger.debug("Binding {} already exists so won't re-bind.", queueName);
         }
      }

      addressMap.put(queueName, address);
   }


   private void bindQueue() throws Exception {
      String queueName = "";
      String address = "";
      String filter = "";
      String routingType = "";

      for (int i = 0; i < reader.getAttributeCount(); i++) {
         String attributeName = reader.getAttributeLocalName(i);
         switch (attributeName) {
            case XmlDataConstants.QUEUE_BINDING_ADDRESS:
               address = reader.getAttributeValue(i);
               break;
            case XmlDataConstants.QUEUE_BINDING_NAME:
               queueName = reader.getAttributeValue(i);
               break;
            case XmlDataConstants.QUEUE_BINDING_FILTER_STRING:
               filter = reader.getAttributeValue(i);
               break;
            case XmlDataConstants.QUEUE_BINDING_ROUTING_TYPE:
               routingType = reader.getAttributeValue(i);
               break;
         }
      }

      ClientSession.QueueQuery queueQuery = session.queueQuery(SimpleString.of(queueName));

      if (!queueQuery.isExists()) {
         session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setRoutingType(RoutingType.valueOf(routingType)).setFilterString(filter));
         if (logger.isDebugEnabled()) {
            logger.debug("Binding queue(name={}, address={}, filter={})", queueName, address, filter);
         }
      } else {
         logger.debug("Binding {} already exists so won't re-bind.", queueName);
      }

      addressMap.put(queueName, address);
   }

   private void bindAddress() throws Exception {
      String addressName = "";
      String routingTypes = "";

      for (int i = 0; i < reader.getAttributeCount(); i++) {
         String attributeName = reader.getAttributeLocalName(i);
         switch (attributeName) {
            case XmlDataConstants.ADDRESS_BINDING_NAME:
               addressName = reader.getAttributeValue(i);
               break;
            case XmlDataConstants.ADDRESS_BINDING_ROUTING_TYPE:
               routingTypes = reader.getAttributeValue(i);
               break;
         }
      }

      ClientSession.AddressQuery addressQuery = session.addressQuery(SimpleString.of(addressName));

      if (!addressQuery.isExists()) {
         EnumSet<RoutingType> set = EnumSet.noneOf(RoutingType.class);
         for (String routingType : ListUtil.toList(routingTypes)) {
            set.add(RoutingType.valueOf(routingType));
         }
         session.createAddress(SimpleString.of(addressName), set, false);
         logger.debug("Binding address(name={}, routingTypes={})", addressName, routingTypes);
      } else {
         logger.debug("Binding {} already exists so won't re-bind.", addressName);
      }
   }
}
