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
package org.apache.activemq.artemis.jms.example;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * A TextReverserService is a MessageListener which consume text messages from a destination
 * and replies with text messages containing the reversed text.
 * It sends replies to the destination specified by the JMS ReplyTo header of the consumed messages.
 */
public class TextReverserService implements MessageListener {


   private final Session session;

   private final Connection connection;

   private static String reverse(final String text) {
      return new StringBuffer(text).reverse().toString();
   }


   public TextReverserService(final ConnectionFactory cf, final Destination destination) throws JMSException {
      // create a JMS connection
      connection = cf.createConnection();
      // create a JMS session
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      // create a JMS MessageConsumer to consume message from the destination
      MessageConsumer consumer = session.createConsumer(destination);
      // let TextReverter implement MessageListener to consume messages
      consumer.setMessageListener(this);

      // start the connection to start consuming messages
      connection.start();
   }

   // MessageListener implementation --------------------------------

   @Override
   public void onMessage(final Message request) {
      TextMessage textMessage = (TextMessage) request;
      try {
         // retrieve the request's text
         String text = textMessage.getText();
         // create a reply containing the reversed text
         TextMessage reply = session.createTextMessage(TextReverserService.reverse(text));

         // retrieve the destination to reply to
         Destination replyTo = request.getJMSReplyTo();
         // create a producer to send the reply
         try (MessageProducer producer = session.createProducer(replyTo)) {
            // send the reply
            producer.send(reply);
         }
      } catch (JMSException e) {
         e.printStackTrace();
      }
   }


   public void close() {
      if (connection != null) {
         try {
            // be sure to close the JMS resources
            connection.close();
         } catch (JMSException e) {
            e.printStackTrace();
         }
      }
   }

}
