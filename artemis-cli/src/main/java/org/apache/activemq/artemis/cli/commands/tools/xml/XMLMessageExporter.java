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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageImpl;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.reader.TextMessageUtil;


/**
 * This is an Utility class that will import the outputs in XML format.
 */
public class XMLMessageExporter {

   private static final int LARGE_MESSAGE_CHUNK_SIZE = 1000;

   private XMLStreamWriter xmlWriter;

   public XMLMessageExporter(XMLStreamWriter xmlWriter) {
      this.xmlWriter = xmlWriter;
   }

   public XMLStreamWriter getRawXMLWriter() {
      return xmlWriter;
   }

   public void printSingleMessageAsXML(ICoreMessage message, List<String> queues, boolean encodeTextUTF8) throws Exception {
      xmlWriter.writeStartElement(XmlDataConstants.MESSAGES_CHILD);
      printMessageAttributes(message);
      printMessageProperties(message);
      printMessageQueues(queues);
      printMessageBody(message.toCore(), encodeTextUTF8);
      xmlWriter.writeEndElement(); // end MESSAGES_CHILD
   }

   public void printMessageBody(Message message, boolean encodeTextMessageUTF8) throws Exception {
      xmlWriter.writeStartElement(XmlDataConstants.MESSAGE_BODY);

      if (message.isLargeMessage()) {
         printLargeMessageBody(message);
      } else {
         if (encodeTextMessageUTF8 && message.toCore().getType() == Message.TEXT_TYPE) {
            xmlWriter.writeCData(TextMessageUtil.readBodyText(message.toCore().getReadOnlyBodyBuffer()).toString());
         } else {
            xmlWriter.writeCData(XmlDataExporterUtil.encodeMessageBodyBase64(message));
         }
      }
      xmlWriter.writeEndElement(); // end MESSAGE_BODY
   }

   private static ByteBuffer acquireHeapBodyBuffer(ByteBuffer chunkBytes, int requiredCapacity) {
      if (chunkBytes == null || chunkBytes.capacity() != requiredCapacity) {
         chunkBytes = ByteBuffer.allocate(requiredCapacity);
      } else {
         chunkBytes.clear();
      }
      return chunkBytes;
   }

   public void printLargeMessageBody(Message message) throws XMLStreamException {
      xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_IS_LARGE, Boolean.TRUE.toString());
      LargeBodyReader encoder = null;

      try {
         if (message instanceof LargeServerMessage largeServerMessage) {
            encoder = largeServerMessage.toMessage().toCore().getLargeBodyReader();
         } else if (message instanceof ClientLargeMessageImpl clientLargeMessage) {
            encoder = clientLargeMessage.getLargeBodyReader();
         } else {
            throw new RuntimeException("Unrecognized message implementation: " + message.getClass().getName());
         }
         encoder.open();
         long totalBytesWritten = 0;
         int bufferSize;
         long bodySize = encoder.getSize();
         ByteBuffer buffer = null;
         for (long i = 0; i < bodySize; i += LARGE_MESSAGE_CHUNK_SIZE) {
            long remainder = bodySize - totalBytesWritten;
            if (remainder >= LARGE_MESSAGE_CHUNK_SIZE) {
               bufferSize = LARGE_MESSAGE_CHUNK_SIZE;
            } else {
               bufferSize = (int) remainder;
            }
            buffer = acquireHeapBodyBuffer(buffer, bufferSize);
            encoder.readInto(buffer);
            xmlWriter.writeCData(XmlDataExporterUtil.encode(buffer.array()));
            totalBytesWritten += bufferSize;
         }
         encoder.close();
      } catch (ActiveMQException e) {
         e.printStackTrace();
      } finally {
         if (encoder != null) {
            try {
               encoder.close();
            } catch (ActiveMQException e) {
               e.printStackTrace();
            }
         }
      }
   }

   public void printMessageQueues(List<String> queues) throws XMLStreamException {
      if (queues != null) {
         xmlWriter.writeStartElement(XmlDataConstants.QUEUES_PARENT);
         for (String queueName : queues) {
            xmlWriter.writeEmptyElement(XmlDataConstants.QUEUES_CHILD);
            xmlWriter.writeAttribute(XmlDataConstants.QUEUE_NAME, queueName);
         }
         xmlWriter.writeEndElement(); // end QUEUES_PARENT
      }
   }

   public void printMessageProperties(Message message) throws XMLStreamException {
      xmlWriter.writeStartElement(XmlDataConstants.PROPERTIES_PARENT);
      for (SimpleString key : message.getPropertyNames()) {
         Object value = message.getObjectProperty(key);
         xmlWriter.writeEmptyElement(XmlDataConstants.PROPERTIES_CHILD);
         xmlWriter.writeAttribute(XmlDataConstants.PROPERTY_NAME, key.toString());
         xmlWriter.writeAttribute(XmlDataConstants.PROPERTY_VALUE, XmlDataExporterUtil.convertProperty(value));

         // Write the property type as an attribute
         String propertyType = XmlDataExporterUtil.getPropertyType(value);
         if (propertyType != null) {
            xmlWriter.writeAttribute(XmlDataConstants.PROPERTY_TYPE, propertyType);
         }
      }
      xmlWriter.writeEndElement(); // end PROPERTIES_PARENT
   }

   public void printMessageAttributes(ICoreMessage message) throws XMLStreamException {
      xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_ID, Long.toString(message.getMessageID()));
      xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_PRIORITY, Byte.toString(message.getPriority()));
      xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_EXPIRATION, Long.toString(message.getExpiration()));
      xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_TIMESTAMP, Long.toString(message.getTimestamp()));
      String prettyType = XmlDataExporterUtil.getMessagePrettyType(message.getType());
      xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_TYPE, prettyType);
      if (message.getUserID() != null) {
         xmlWriter.writeAttribute(XmlDataConstants.MESSAGE_USER_ID, message.getUserID().toString());
      }
   }
}
