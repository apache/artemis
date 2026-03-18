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

package org.apache.activemq.artemis.cli.commands.messages;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.factory.serialize.MessageSerializer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "consumer", description = "Consume messages from a queue.")
public class Consumer extends DestAbstract {

   @Option(names = "--durable", description = "Whether the consumer's subscription will be durable.")
   boolean durable = false;

   @Option(names = "--break-on-null", description = "Stop consuming when a null message is received.")
   boolean breakOnNull = false;

   @Option(names = "--receive-timeout", description = "Timeout for receiving messages (in milliseconds). Specify -1 to wait forever.")
   int receiveTimeout = 3000;

   @Option(names = "--filter", description = "The message filter.")
   String filter;

   @Option(names = "--data", description = "Serialize the messages to the specified file as they are consumed.")
   String file;

   @Option(names = "--subscriptionName", description = "The subscription name to use for a durable consumer.")
   String subscriptionName;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      context.out.println("Consumer:: filter = " + filter);

      OutputStream outputStream = null;

      SerialiserMessageListener listener = null;
      MessageSerializer serializer = null;
      if (file != null) {
         serializer = getMessageSerializer();
         if (serializer == null) {
            context.err.println("Error. Unable to instantiate serializer class: " + this.serializer);
            return null;
         }

         try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
         } catch (Exception e) {
            context.err.println("Error: Unable to open file for writing\n" + e.getMessage());
            return null;
         }

         listener = new SerialiserMessageListener(serializer, outputStream);
         serializer.start();
      }

      if (subscriptionName != null) {
         if (threads != 1) {
            context.err.println("Error: Cannot assign a subscription name when multiple threads are also configured.");
            return null;
         } else {
            context.out.println("Consumer:: subscription name = " + subscriptionName);
         }
      }

      ConnectionFactory factory = createConnectionFactory();

      try (Connection connection = factory.createConnection()) {
         // We read messages in a single thread when persisting to file.
         ConsumerThread[] threadsArray = new ConsumerThread[threads];
         for (int i = 0; i < threads; i++) {
            Session session;
            if (txBatchSize > 0) {
               session = connection.createSession(true, Session.SESSION_TRANSACTED);
            } else {
               session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }

            Destination dest = getDestination(session);
            if (subscriptionName == null) {
               threadsArray[i] = new ConsumerThread(session, dest, i, context);
            } else {
               threadsArray[i] = new ConsumerThread(session, dest, subscriptionName, context);
            }

            threadsArray[i]
               .setVerbose(verbose)
               .setSleep(sleep)
               .setDurable(durable)
               .setBatchSize(txBatchSize)
               .setBreakOnNull(breakOnNull)
               .setMessageCount(messageCount)
               .setReceiveTimeOut(receiveTimeout)
               .setFilter(filter)
               .setBrowse(false)
               .setListener(listener);
         }

         for (ConsumerThread thread : threadsArray) {
            thread.start();
         }

         connection.start();

         long received = 0;

         try {
            for (ConsumerThread thread : threadsArray) {
               thread.join();
               received += thread.getReceived();
            }
         } catch (InterruptedException e) {
            // Interrupt any sub threads if an interrupt is captured
            for (Thread t : threadsArray) {
               t.interrupt();
            }
            throw e;
         }

         return received;
      } finally {
         try {
            if (serializer != null) {
               serializer.stop();
            }

            if (outputStream != null) {
               outputStream.close();
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private class SerialiserMessageListener implements MessageListener {

      private MessageSerializer messageSerializer;

      SerialiserMessageListener(MessageSerializer messageSerializer, OutputStream outputStream) throws Exception {
         this.messageSerializer = messageSerializer;
         this.messageSerializer.setOutput(outputStream);
      }

      @Override
      public void onMessage(Message message) {
         messageSerializer.write(message);
      }
   }

   public boolean isDurable() {
      return durable;
   }

   public Consumer setDurable(boolean durable) {
      this.durable = durable;
      return this;
   }

   public boolean isBreakOnNull() {
      return breakOnNull;
   }

   public Consumer setBreakOnNull(boolean breakOnNull) {
      this.breakOnNull = breakOnNull;
      return this;
   }

   public int getReceiveTimeout() {
      return receiveTimeout;
   }

   public Consumer setReceiveTimeout(int receiveTimeout) {
      this.receiveTimeout = receiveTimeout;
      return this;
   }

   public String getFilter() {
      return filter;
   }

   public Consumer setFilter(String filter) {
      this.filter = filter;
      return this;
   }

   public String getFile() {
      return file;
   }

   public Consumer setFile(String file) {
      this.file = file;
      return this;
   }

   public String getSubscriptionName() {
      return subscriptionName;
   }

   public Consumer setSubscriptionName(String subscriptionName) {
      this.subscriptionName = subscriptionName;
      return this;
   }
}
