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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.utils.XmlProvider;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class XmlExportTest extends AmqpClientTestSupport {

   @Test
   public void testTextMessage() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName());
      AmqpMessage message = new AmqpMessage();
      message.setDurable(true);
      message.setText("TEST");
      sender.send(message);

      sender.close();
      connection.close();

      server.stop();

      ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream();
      XmlDataExporter xmlDataExporter = new XmlDataExporter();
      xmlDataExporter.process(xmlOutputStream, server.getConfiguration().getBindingsDirectory(),
                              server.getConfiguration().getJournalDirectory(),
                              server.getConfiguration().getPagingDirectory(),
                              server.getConfiguration().getLargeMessagesDirectory());

      Document document = XmlProvider.newDocumentBuilder().
         parse(new ByteArrayInputStream(xmlOutputStream.toByteArray()));

      assertNotNull(XPathFactory.newInstance().newXPath().
         compile("//property[@name='" + AMQPMessageSupport.JMS_AMQP_ORIGINAL_ENCODING + "' and @value='" + AMQPMessageSupport.AMQP_VALUE_STRING + "']").
         evaluate(document, XPathConstants.NODE));

      assertNotNull(XPathFactory.newInstance().newXPath().
         compile("//property[@name='" + AMQPMessageSupport.JMS_AMQP_HEADER + "' and @value='true']").
         evaluate(document, XPathConstants.NODE));

      assertNotNull(XPathFactory.newInstance().newXPath().
         compile("//property[@name='" + AMQPMessageSupport.JMS_AMQP_HEADER_DURABLE + "' and @value='true']").
         evaluate(document, XPathConstants.NODE));
   }
}