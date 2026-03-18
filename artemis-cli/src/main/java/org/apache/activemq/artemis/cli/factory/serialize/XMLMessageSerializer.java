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
package org.apache.activemq.artemis.cli.factory.serialize;

import javax.jms.Message;
import javax.jms.Session;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.cli.commands.tools.xml.XMLMessageExporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XMLMessageImporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataConstants;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.utils.XmlProvider;

public class XMLMessageSerializer implements MessageSerializer {

   private XMLMessageExporter writer;

   private XMLMessageImporter reader;

   private ClientSession clientSession;

   private OutputStream out;

   @Override
   public synchronized Message read() throws Exception {
      reader.getRawXMLReader().nextTag();

      // End of document.
      if (reader.getRawXMLReader().getLocalName().equals("messages")) {
         return null;
      }

      XMLMessageImporter.MessageInfo messageInfo = reader.readMessage(true);
      if (messageInfo == null) {
         return null;
      }

      // This is a large message
      ActiveMQMessage jmsMessage = new ActiveMQMessage((ClientMessage) messageInfo.message, clientSession);
      return jmsMessage;
   }

   @Override
   public synchronized void write(Message message) {
      try {
         ICoreMessage core = ((ActiveMQMessage) message).getCoreMessage();
         writer.printSingleMessageAsXML(core, null, true);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void setOutput(OutputStream outputStream) throws Exception {
      this.out = outputStream;
      XMLOutputFactory factory = XMLOutputFactory.newInstance();
      XMLStreamWriter rawXmlWriter = factory.createXMLStreamWriter(outputStream, "UTF-8");
      XmlDataExporter.PrettyPrintHandler handler = new XmlDataExporter.PrettyPrintHandler(rawXmlWriter);
      XMLStreamWriter xmlWriter = (XMLStreamWriter) Proxy.newProxyInstance(XMLStreamWriter.class.getClassLoader(), new Class[]{XMLStreamWriter.class}, handler);
      this.writer = new XMLMessageExporter(xmlWriter);
   }

   @Override
   public void setInput(InputStream inputStream, Session session) throws Exception {
      XMLStreamReader streamReader = XmlProvider.createXMLStreamReader(inputStream);
      this.clientSession = ((ActiveMQSession) session).getCoreSession();
      this.reader = new XMLMessageImporter(streamReader, clientSession);
   }

   @Override
   public synchronized void start() throws Exception {
      if (writer != null) {
         writer.getRawXMLWriter().writeStartDocument(XmlDataConstants.XML_VERSION);
         writer.getRawXMLWriter().writeStartElement(XmlDataConstants.MESSAGES_PARENT);
      }

      if (reader != null) {
         // <messages>
         reader.getRawXMLReader().nextTag();
      }
   }

   @Override
   public synchronized void stop() throws Exception {
      if (writer != null) {
         writer.getRawXMLWriter().writeEndElement();
         writer.getRawXMLWriter().writeEndDocument();
         writer.getRawXMLWriter().flush();
         writer.getRawXMLWriter().close();
         out.flush();
      }
   }
}
