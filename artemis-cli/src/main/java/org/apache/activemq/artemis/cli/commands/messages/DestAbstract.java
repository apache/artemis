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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.factory.serialize.MessageSerializer;
import org.apache.activemq.artemis.cli.factory.serialize.XMLMessageSerializer;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import picocli.CommandLine.Option;

public class DestAbstract extends ConnectionAbstract {

   @Option(names = "--destination", description = "Destination to be used. It can be prefixed with queue:// or topic:// and can be an FQQN in the form of <address>::<queue>. Default: queue://TEST.")
   String destination = "queue://TEST";

   @Option(names = "--message-count", description = "Number of messages to act on. Default: 1000.")
   long messageCount = 1000;

   @Option(names = "--sleep", description = "Time wait between each message.")
   int sleep = 0;

   @Option(names = {"--txt-size"}, description = "Transaction batch size. (deprecated)", hidden = true)
   int oldBatchSize;

   @Option(names = {"--commit-interval"}, description = "Transaction batch size.")
   protected int txBatchSize;

   @Option(names = "--threads", description = "Number of threads to use. Default: 1.")
   int threads = 1;

   @Option(names = "--serializer", description = "The class name of the custom serializer implementation to use intead of the default.")
   String serializer;

   protected MessageSerializer getMessageSerializer() {
      if (serializer != null) {
         try {
            return (MessageSerializer) ClassloadingUtil.getInstanceWithTypeCheck(serializer, MessageSerializer.class, this.getClass().getClassLoader());
         } catch (Exception e) {
            getActionContext().err.println("Error: unable to instantiate serializer class: " + serializer);
            getActionContext().err.println("Defaulting to: " + XMLMessageSerializer.class.getName());
         }
      }

      if (protocol != ConnectionProtocol.CORE) {
         getActionContext().err.println("Default Serializer does not support: " + protocol + " protocol");
         return null;
      }

      return new XMLMessageSerializer();
   }

   protected Destination getDestination(Session session) throws JMSException {
      return getDestination(session, destination);
   }

   public static Destination getDestination(Session session, String destination) throws JMSException {
      if (destination.startsWith(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX)) {
         return session.createTopic(stripPrefix(destination));
      }
      return session.createQueue(stripPrefix(destination));
   }

   public static String stripPrefix(String destination) {
      int index = destination.indexOf("://");
      if (index != -1) {
         return destination.substring(index + 3);
      } else {
         return destination;
      }
   }

   public String getDestination() {
      return destination;
   }

   public DestAbstract setDestination(String destination) {
      this.destination = destination;
      return this;
   }

   public long getMessageCount() {
      return messageCount;
   }

   public DestAbstract setMessageCount(long messageCount) {
      this.messageCount = messageCount;
      return this;
   }

   public int getSleep() {
      return sleep;
   }

   public DestAbstract setSleep(int sleep) {
      this.sleep = sleep;
      return this;
   }

   public int getTxBatchSize() {
      return txBatchSize;
   }

   public DestAbstract setTxBatchSize(int txBatchSize) {
      this.txBatchSize = txBatchSize;
      return this;
   }

   public int getThreads() {
      return threads;
   }

   public DestAbstract setThreads(int threads) {
      this.threads = threads;
      return this;
   }

   public String getSerializer() {
      return serializer;
   }

   public DestAbstract setSerializer(String serializer) {
      this.serializer = serializer;
      return this;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      if (oldBatchSize > 0 && txBatchSize > 0) {
         throw new IllegalArgumentException("Either select --txt-size or --commit-interval. Cannot use both!");
      }

      if (oldBatchSize > 0) {
         context.out.println("--txt-size is deprecated, please use --commit-interval");
         txBatchSize = oldBatchSize;
      }

      return null;
   }
}
