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

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.cli.commands.ActionContext;

public class ConsumerThread extends Thread {

   long messageCount = 1000;
   int receiveTimeOut = 3000;
   Destination destination;
   Session session;
   ActionContext context;
   boolean durable;
   boolean breakOnNull = true;
   int sleep;
   int batchSize;
   boolean verbose;
   boolean browse;

   String filter;

   int received = 0;
   int transactions = 0;
   boolean running = false;
   CountDownLatch finished;
   boolean bytesAsText;
   MessageListener listener;

   public ConsumerThread(Session session, Destination destination, int threadNr, ActionContext context) {
      this(session, destination, "Consumer " + destination.toString() + ", thread=" + threadNr, context);
   }

   public ConsumerThread(Session session, Destination destination, String subscriptionName, ActionContext context) {
      super(subscriptionName);

      this.destination = destination;
      this.session = session;
      this.context = context;
   }

   @Override
   public void run() {
      if (browse) {
         browse();
      } else {
         consume();
      }
   }

   private void handle(Message msg, boolean browse) throws JMSException {
      if (listener != null) {
         listener.onMessage(msg);
      } else {
         if (browse) {
            if (verbose) {
               context.out.println("..." + msg);
            }
            if (bytesAsText && (msg instanceof BytesMessage bytesMessage)) {
               long length = bytesMessage.getBodyLength();
               byte[] bytes = new byte[(int) length];
               bytesMessage.readBytes(bytes);
               context.out.println("Message:" + msg);
            }
         } else {
            if (verbose) {
               context.out.println("JMS Message ID:" + msg.getJMSMessageID());
               if (bytesAsText && (msg instanceof BytesMessage bytesMessage)) {
                  long length = bytesMessage.getBodyLength();
                  byte[] bytes = new byte[(int) length];
                  bytesMessage.readBytes(bytes);
                  context.out.println("Received a message with " + bytes.length);
               }

               if (msg instanceof TextMessage textMessage) {
                  String text = textMessage.getText();
                  context.out.println("Received text sized at " + text.length());
               }

               if (msg instanceof ObjectMessage objectMessage) {
                  Object obj = objectMessage.getObject();
                  context.out.println("Received object " + obj.toString().length());
               }
            }
         }
      }
   }

   public void browse() {
      running = true;
      QueueBrowser consumer = null;
      String threadName = Thread.currentThread().getName();
      context.out.println(threadName + " trying to browse " + messageCount + " messages");
      try {
         if (filter != null) {
            consumer = session.createBrowser((Queue) destination, filter);
         } else {
            consumer = session.createBrowser((Queue) destination);
         }
         Enumeration<Message> enumBrowse = consumer.getEnumeration();

         while (enumBrowse.hasMoreElements()) {
            Message msg = enumBrowse.nextElement();
            if (msg != null) {
               context.out.println(threadName + " browsing " + (msg instanceof TextMessage tm ? tm.getText() : msg.getJMSMessageID()));
               handle(msg, true);
               received++;

               if (received >= messageCount) {
                  break;
               }
            } else {
               break;
            }

            if (sleep > 0) {
               Thread.sleep(sleep);
            }

         }

         consumer.close();
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (finished != null) {
            finished.countDown();
         }
         if (consumer != null) {
            context.out.println(threadName + " browsed: " + this.getReceived() + " messages");
            try {
               consumer.close();
            } catch (JMSException e) {
               e.printStackTrace();
            }
         }
      }

      context.out.println(threadName + " Browser thread finished");
   }

   public void consume() {
      running = true;
      MessageConsumer consumer = null;
      String threadName = Thread.currentThread().getName();
      context.out.println(threadName + " wait " + (receiveTimeOut == -1 ? "forever" : receiveTimeOut + "ms") + " until " + messageCount + " messages are consumed");
      try {
         if (durable && destination instanceof Topic topic) {
            if (filter != null) {
               consumer = session.createDurableSubscriber(topic, getName(), filter, false);
            } else {
               consumer = session.createDurableSubscriber(topic, getName());
            }
         } else {
            if (filter != null) {
               consumer = session.createConsumer(destination, filter);
            } else {
               consumer = session.createConsumer(destination);
            }
         }
         long tStart = System.currentTimeMillis();
         int count = 0;
         while (running && received < messageCount) {
            Message msg;
            if (receiveTimeOut == -1) {
               msg = consumer.receive();
            } else {
               msg = consumer.receive(receiveTimeOut);
            }
            if (msg != null) {
               if (verbose) {
                  context.out.println(threadName + " Received " + (msg instanceof TextMessage tm ? tm.getText() : msg.getJMSMessageID()));
               } else {
                  if (++count % 1000 == 0) {
                     context.out.println("Received " + count);
                  }
               }
               handle(msg, false);
               received++;
            } else {
               if (breakOnNull) {
                  break;
               }
            }

            if (session.getTransacted()) {
               if (batchSize > 0 && received > 0 && received % batchSize == 0) {
                  context.out.println(threadName + " Committing transaction: " + transactions++);
                  session.commit();
               }
            } else if (session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE && msg != null) {
               if (batchSize > 0 && received > 0 && received % batchSize == 0) {
                  context.out.println("Acknowledging last " + batchSize + " messages; messages so far = " + received);
                  msg.acknowledge();
               }
            }
            if (sleep > 0) {
               Thread.sleep(sleep);
            }

         }

         try {
            session.commit();
         } catch (Throwable ignored) {
         }


         context.out.println(threadName + " Consumed: " + this.getMessageCount() + " messages");
         long tEnd = System.currentTimeMillis();
         long elapsed = (tEnd - tStart) / 1000;
         context.out.println(threadName + " Elapsed time in second : " + elapsed + " s");
         context.out.println(threadName + " Elapsed time in milli second : " + (tEnd - tStart) + " milli seconds");

      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (finished != null) {
            finished.countDown();
         }
         if (consumer != null) {
            context.out.println(threadName + " Consumed: " + this.getReceived() + " messages");
            try {
               consumer.close();
            } catch (JMSException e) {
               e.printStackTrace();
            }
         }
      }

      context.out.println(threadName + " Consumer thread finished");
   }

   public int getReceived() {
      return received;
   }

   public boolean isDurable() {
      return durable;
   }

   public ConsumerThread setDurable(boolean durable) {
      this.durable = durable;
      return this;
   }

   public ConsumerThread setMessageCount(long messageCount) {
      this.messageCount = messageCount;
      return this;
   }

   public ConsumerThread setBreakOnNull(boolean breakOnNull) {
      this.breakOnNull = breakOnNull;
      return this;
   }

   public int getBatchSize() {
      return batchSize;
   }

   public ConsumerThread setBatchSize(int batchSize) {
      this.batchSize = batchSize;
      return this;
   }

   public long getMessageCount() {
      return messageCount;
   }

   public boolean isBreakOnNull() {
      return breakOnNull;
   }

   public int getReceiveTimeOut() {
      return receiveTimeOut;
   }

   public ConsumerThread setReceiveTimeOut(int receiveTimeOut) {
      this.receiveTimeOut = receiveTimeOut;
      return this;
   }

   public boolean isRunning() {
      return running;
   }

   public ConsumerThread setRunning(boolean running) {
      this.running = running;
      return this;
   }

   public int getSleep() {
      return sleep;
   }

   public ConsumerThread setSleep(int sleep) {
      this.sleep = sleep;
      return this;
   }

   public CountDownLatch getFinished() {
      return finished;
   }

   public ConsumerThread setFinished(CountDownLatch finished) {
      this.finished = finished;
      return this;
   }

   public boolean isBytesAsText() {
      return bytesAsText;
   }

   public boolean isVerbose() {
      return verbose;
   }

   public ConsumerThread setVerbose(boolean verbose) {
      this.verbose = verbose;
      return this;
   }

   public ConsumerThread setBytesAsText(boolean bytesAsText) {
      this.bytesAsText = bytesAsText;
      return this;
   }

   public String getFilter() {
      return filter;
   }

   public ConsumerThread setFilter(String filter) {
      this.filter = filter;
      return this;
   }

   public boolean isBrowse() {
      return browse;
   }

   public ConsumerThread setBrowse(boolean browse) {
      this.browse = browse;
      return this;
   }

   public void setListener(MessageListener listener) {
      this.listener = listener;
   }
}
