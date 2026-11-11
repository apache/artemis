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

package org.apache.activemq.artemis.cli.commands.check;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "queue", description = "Check a queue.")
public class QueueCheck extends CheckAbstract {

   @Option(names = "--up", description = "Check that the queue exists and is not paused. This check is executed by default if there are no other checks.")
   private boolean up;

   @Option(names = "--browse", description = "Number of the messages to browse or -1 to browse at most 1 message only if it is immediately available (succeeding also if no messages are available).")
   private Integer browse;

   @Option(names = "--consume", description = "Number of the messages to consume or -1 to consume at most 1 message only if it is immediately available (succeeding also if no messages are available).")
   private Integer consume;

   @Option(names = "--produce", description = "Number of the messages to produce.")
   private Integer produce;

   public boolean isUp() {
      return up;
   }

   public void setUp(boolean up) {
      this.up = up;
   }

   public Integer getBrowse() {
      return browse;
   }

   public void setBrowse(Integer browse) {
      this.browse = browse;
   }

   public Integer getConsume() {
      return consume;
   }

   public void setConsume(Integer consume) {
      this.consume = consume;
   }

   public Integer getProduce() {
      return produce;
   }

   public void setProduce(Integer produce) {
      this.produce = produce;
   }

   @Override
   protected CheckTask[] getCheckTasks() {
      List<CheckTask> checkTasks = new ArrayList<>();

      if (getName() == null) {
         name = input("--name", "Name is a mandatory property for Queue check", null);
      }

      if (getName() == null) {
         throw new IllegalArgumentException("The name of the queue is required");
      }

      if (produce != null) {
         if (produce > 0) {
            checkTasks.add(new CheckTask(String.format("a producer can send %d messages to the queue %s",
                                                       produce, getName()), this::checkQueueProduce));
         } else {
            throw new IllegalArgumentException("Invalid  number of messages to produce: " + produce);
         }
      }

      if (browse != null) {
         if (browse == -1) {
            checkTasks.add(new CheckTask(String.format("a consumer can browse the queue %s",
                                                       getName()), this::checkQueueBrowse));
         } else if (browse > 0) {
            checkTasks.add(new CheckTask(String.format("a consumer can browse %d messages from the queue %s",
                                                       browse, getName()), this::checkQueueBrowse));
         } else {
            throw new IllegalArgumentException("Invalid  number of messages to browse: " + browse);
         }
      }

      if (consume != null) {
         if (consume == -1) {
            checkTasks.add(new CheckTask(String.format("a consumer can consume the queue %s",
                                                       getName()), this::checkQueueConsume));
         } else if (consume > 0) {
            checkTasks.add(new CheckTask(String.format("a consumer can consume %d messages from the queue %s",
                                                       consume, getName()), this::checkQueueConsume));
         } else {
            throw new IllegalArgumentException("Invalid  number of messages to consume: " + consume);
         }
      }

      if (up || checkTasks.isEmpty()) {
         checkTasks.add(0, new CheckTask(String.format("the queue %s exists and is not paused",
                                                       getName()), this::checkQueueUp));
      }

      return checkTasks.toArray(new CheckTask[checkTasks.size()]);
   }

   private void checkQueueUp(final CheckContext context) throws Exception {
      if (context.getManagementProxy().getAttribute(ResourceNames.QUEUE + getName(), "Paused", Boolean.class, 0)) {
         throw new CheckException("The queue is paused.");
      }
   }

   private void checkQueueProduce(final CheckContext context) throws Exception {
      try (Connection connection = context.getFactory().createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
           MessageProducer queueProducer = session.createProducer(session.createQueue(getName()))) {
         connection.start();

         int count = 0;
         while (count < produce) {
            queueProducer.send(session.createTextMessage("CHECK_MESSAGE"));
            count++;
         }
      }
   }

   private void checkQueueBrowse(final CheckContext context) throws Exception {
      try (Connection connection = context.getFactory().createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
           QueueBrowser queueBrowser = session.createBrowser(session.createQueue(getName()))) {
         connection.start();

         Enumeration<Message> queueBrowserEnum = queueBrowser.getEnumeration();

         if (browse == -1) {
            queueBrowserEnum.hasMoreElements();
         } else {
            int count = 0;
            while (count < browse) {
               if (!queueBrowserEnum.hasMoreElements() || queueBrowserEnum.nextElement() == null) {
                  throw new CheckException("Insufficient messages to browse: " + count);
               }
               count++;
            }
         }
      }
   }

   private void checkQueueConsume(final CheckContext context) throws Exception {
      try (Connection connection = context.getFactory().createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
           MessageConsumer queueConsumer = session.createConsumer(session.createQueue(getName()))) {
         connection.start();

         if (consume == -1) {
            queueConsumer.receiveNoWait();
         } else {
            int count = 0;
            while (count < consume) {
               if (queueConsumer.receive() == null) {
                  throw new CheckException("Insufficient messages to consume: " + count);
               }
               count++;
            }
         }
      }
   }
}
