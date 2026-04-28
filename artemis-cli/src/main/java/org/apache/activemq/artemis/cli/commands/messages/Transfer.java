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
package org.apache.activemq.artemis.cli.commands.messages;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.InputAbstract;
import org.apache.activemq.artemis.cli.factory.ConnectionFactoryClosable;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "transfer", description = "Move messages from one JMS destination towards another JMS destination.")
public class Transfer extends InputAbstract {

   @Option(names = "--source-url", description = "URL for the source broker. Default: build URL from 'artemis' acceptor defined in the broker.xml or tcp://localhost:61616 if the default cannot be parsed.")
   protected String sourceURL = DEFAULT_BROKER_URL;

   @Option(names = "--source-acceptor", description = "Acceptor from the local broker.xml used to build URL towards the broker. Default: 'artemis'.")
   protected String sourceAcceptor = DEFAULT_BROKER_ACCEPTOR;

   @Option(names = "--source-user", description = "Username for the JMS connection to the source broker.")
   protected String sourceUser;

   @Option(names = "--source-password", description = "Password for the JMS connection to the source broker.")
   protected String sourcePassword;

   @Option(names = "--target-url", description = "URL for the target broker. Default: build URL from 'artemis' acceptor defined in the broker.xml or tcp://localhost:61616 if the default cannot be parsed.")
   protected String targetURL = DEFAULT_BROKER_URL;

   @Option(names = "--target-user", description = "Username for the JMS connection to the target broker.")
   protected String targetUser;

   @Option(names = "--target-password", description = "Password for the JMS connection to the target broker.")
   protected String targetPassword;

   @Option(names = "--receive-timeout", description = "Amount of time (in milliseconds) to wait before giving up the receiving loop; 0 means no wait, -1 means wait forever. Default: 5000.")
   int receiveTimeout = 5000;

   @Option(names = "--source-client-id", description = "JMS client ID to be associated with source connection.")
   String sourceClientID;

   @Option(names = "--source-protocol", description = "Protocol used. Valid values are amqp or core. Default: core.")
   String sourceProtocol = "core";

   @Option(names = "--source-queue", description = "Source JMS queue to transfer messages from. Cannot be used in conjunction with --source-topic.")
   String sourceQueue;

   @Option(names = "--shared-durable-subscription", description = "Name of the JMS shared durable subscription to be used on the source JMS topic.")
   String sharedDurableSubscription;

   @Option(names = "--shared-subscription", description = "Name of the JMS shared non-durable subscription name to be used on the source JMS topic.")
   String sharedSubscription;

   @Option(names = "--durable-consumer", description = "Name of the JMS unshared durable subscription to be used on the source JMS topic.")
   String durableConsumer;

   @Option(names = "--no-Local", description = "Use noLocal when applicable on topic operation")
   boolean noLocal;

   @Option(names = "--source-topic", description = "Source JMS topic to be used. Cannot be used in conjuction with --source-queue.")
   String sourceTopic;

   @Option(names = "--source-filter", description = "Filter to be used with the source consumer.")
   String filter;

   @Option(names = "--target-protocol", description = "Protocol used. Valid values are amqp or core. Default: core.")
   String targetProtocol = "core";

   @Option(names = "--commit-interval", description = "How many messages to transfer before committing the associated transaction. Default: 1000.")
   int commitInterval = 1000;

   @Option(names = "--copy", description = "Copy messages instead of transferring them.")
   boolean copy;

   @Option(names = "--target-queue", description = "Target JMS queue to be used. Cannot be set with --target-topic.")
   String targetQueue;

   @Option(names = "--target-topic", description = "Target JMS topic to be used. Cannot bet set with --target-queue.")
   String targetTopic;

   @Option(names = "--message-count", description = "Number of messages to transfer.")
   int messageCount = Integer.MAX_VALUE;

   public String getSourceURL() {
      return sourceURL;
   }

   public Transfer setSourceURL(String sourceURL) {
      this.sourceURL = sourceURL;
      return this;
   }

   public String getSourceAcceptor() {
      return sourceAcceptor;
   }

   public Transfer setSourceAcceptor(String sourceAcceptor) {
      this.sourceAcceptor = sourceAcceptor;
      return this;
   }

   public String getSourceUser() {
      return sourceUser;
   }

   public Transfer setSourceUser(String sourceUser) {
      this.sourceUser = sourceUser;
      return this;
   }

   public String getSourcePassword() {
      return sourcePassword;
   }

   public Transfer setSourcePassword(String sourcePassword) {
      this.sourcePassword = sourcePassword;
      return this;
   }

   public String getTargetURL() {
      return targetURL;
   }

   public Transfer setTargetURL(String targetURL) {
      this.targetURL = targetURL;
      return this;
   }

   public String getTargetUser() {
      return targetUser;
   }

   public Transfer setTargetUser(String targetUser) {
      this.targetUser = targetUser;
      return this;
   }

   public String getTargetPassword() {
      return targetPassword;
   }

   public Transfer setTargetPassword(String targetPassword) {
      this.targetPassword = targetPassword;
      return this;
   }

   public int getReceiveTimeout() {
      return receiveTimeout;
   }

   public Transfer setReceiveTimeout(int receiveTimeout) {
      this.receiveTimeout = receiveTimeout;
      return this;
   }

   public String getSourceClientID() {
      return sourceClientID;
   }

   public Transfer setSourceClientID(String sourceClientID) {
      this.sourceClientID = sourceClientID;
      return this;
   }

   public String getSourceProtocol() {
      return sourceProtocol;
   }

   public Transfer setSourceProtocol(String sourceProtocol) {
      this.sourceProtocol = sourceProtocol;
      return this;
   }

   public String getSourceQueue() {
      return sourceQueue;
   }

   public Transfer setSourceQueue(String sourceQueue) {
      this.sourceQueue = sourceQueue;
      return this;
   }

   public String getSharedDurableSubscription() {
      return sharedDurableSubscription;
   }

   public Transfer setSharedDurableSubscription(String sharedDurableSubscription) {
      this.sharedDurableSubscription = sharedDurableSubscription;
      return this;
   }

   public String getSharedSubscription() {
      return sharedSubscription;
   }

   public Transfer setSharedSubscription(String sharedSubscription) {
      this.sharedSubscription = sharedSubscription;
      return this;
   }

   public String getDurableConsumer() {
      return durableConsumer;
   }

   public Transfer setDurableConsumer(String durableConsumer) {
      this.durableConsumer = durableConsumer;
      return this;
   }

   public boolean isNoLocal() {
      return noLocal;
   }

   public Transfer setNoLocal(boolean noLocal) {
      this.noLocal = noLocal;
      return this;
   }

   public String getSourceTopic() {
      return sourceTopic;
   }

   public Transfer setSourceTopic(String sourceTopic) {
      this.sourceTopic = sourceTopic;
      return this;
   }

   public String getFilter() {
      return filter;
   }

   public Transfer setFilter(String filter) {
      this.filter = filter;
      return this;
   }

   public String getTargetProtocol() {
      return targetProtocol;
   }

   public Transfer setTargetProtocol(String targetProtocol) {
      this.targetProtocol = targetProtocol;
      return this;
   }

   public int getCommitInterval() {
      return commitInterval;
   }

   public Transfer setCommitInterval(int commitInterval) {
      this.commitInterval = commitInterval;
      return this;
   }

   public boolean isCopy() {
      return copy;
   }

   public Transfer setCopy(boolean copy) {
      this.copy = copy;
      return this;
   }

   public String getTargetQueue() {
      return targetQueue;
   }

   public Transfer setTargetQueue(String targetQueue) {
      this.targetQueue = targetQueue;
      return this;
   }

   public String getTargetTopic() {
      return targetTopic;
   }

   public Transfer setTargetTopic(String targetTopic) {
      this.targetTopic = targetTopic;
      return this;
   }

   public int getMessageCount() {
      return messageCount;
   }

   public Transfer setMessageCount(int messageCount) {
      this.messageCount = messageCount;
      return this;
   }

   @SuppressWarnings("StringEquality")
   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      // it is intentional to make a comparison on the String object here
      // this is to test if the original option was switched or not.
      // we don't care about being .equals at all.
      // as a matter of fact if you pass brokerURL in a way it's equals to DEFAULT_BROKER_URL,
      // we should not the broker URL Instance
      // and still honor the one passed by parameter.
      // SupressWarnings was added to this method to supress the false positive here from error-prone.
      if (sourceURL == DEFAULT_BROKER_URL) {
         String brokerURLInstance = getBrokerURLInstance(sourceAcceptor);

         if (brokerURLInstance != null) {
            sourceURL = brokerURLInstance;
         }
      }

      context.out.println("Connection brokerURL = " + sourceURL);

      return doTransfer(context);
   }

   private int doTransfer(ActionContext context) throws Exception {
      try (ConnectionFactoryClosable sourceConnectionFactory = createConnectionFactory("source", sourceProtocol, sourceURL, sourceUser, sourcePassword, sourceClientID);
           Connection sourceConnection = sourceConnectionFactory.createConnection();
           Session sourceSession = sourceConnection.createSession(Session.SESSION_TRANSACTED)) {

         Destination sourceDestination = createDestination("source", sourceSession, sourceQueue, sourceTopic);
         MessageConsumer consumer = null;
         if (sourceDestination instanceof Queue) {
            if (filter != null) {
               consumer = sourceSession.createConsumer(sourceDestination, filter);
            } else {
               consumer = sourceSession.createConsumer(sourceDestination);
            }
         } else if (sourceDestination instanceof Topic topic) {

            if (durableConsumer != null) {
               if (filter != null) {
                  consumer = sourceSession.createDurableConsumer(topic, durableConsumer);
               } else {
                  consumer = sourceSession.createDurableConsumer(topic, durableConsumer, filter, noLocal);
               }
            } else if (sharedDurableSubscription != null) {
               if (filter != null) {
                  consumer = sourceSession.createSharedDurableConsumer(topic, sharedDurableSubscription, filter);
               } else {
                  consumer = sourceSession.createSharedDurableConsumer(topic, sharedDurableSubscription);
               }
            } else if (sharedSubscription != null) {
               if (filter != null) {
                  consumer = sourceSession.createSharedConsumer(topic, sharedSubscription, filter);
               } else {
                  consumer = sourceSession.createSharedConsumer(topic, sharedSubscription);
               }
            } else {
               throw new IllegalArgumentException("you must specify either --durable-consumer, --shared-durable-subscription or --shared-subscription with a JMS topic");
            }
         }

         try (MessageConsumer sourceConsumer = consumer;
              ConnectionFactoryClosable targetConnectionFactory = createConnectionFactory("target", targetProtocol, targetURL, targetUser, targetPassword, null);
              Connection targetConnection = targetConnectionFactory.createConnection();
              Session targetSession = targetConnection.createSession(Session.SESSION_TRANSACTED)) {

            Destination targetDestination = createDestination("target", targetSession, targetQueue, targetTopic);

            try (MessageProducer producer = targetSession.createProducer(targetDestination)) {

               if (sourceURL.equals(targetURL) && sourceDestination.equals(targetDestination)) {
                  context.out.println("You cannot transfer between " + sourceURL + "/" + sourceDestination + " and " + targetURL + "/" + targetDestination + ".\n" + "That would create an infinite recursion.");
                  throw new IllegalArgumentException("cannot use " + sourceDestination + " == " + targetDestination);
               }

               sourceConnection.start();
               int pending = 0, total = 0;
               while (total < messageCount) {

                  Message receivedMessage;
                  if (receiveTimeout < 0) {
                     receivedMessage = sourceConsumer.receive();
                  } else if (receiveTimeout == 0) {
                     receivedMessage = sourceConsumer.receiveNoWait();
                  } else {
                     receivedMessage = sourceConsumer.receive(receiveTimeout);
                  }

                  if (receivedMessage == null) {
                     if (isVerbose()) {
                        context.out.println("could not receive any more messages");
                     }
                     break;
                  }
                  producer.send(receivedMessage);
                  pending++;
                  total++;

                  if (isVerbose()) {
                     context.out.println("Received message " + total + " with " + pending + " messages pending to be commited");
                  }
                  if (pending > commitInterval) {
                     context.out.println("Transferred " + pending + " messages of " + total);
                     pending = 0;
                     targetSession.commit();
                     if (!isCopy()) {
                        sourceSession.commit();
                     }
                  }
               }

               context.out.println("Transferred a total of " + total + " messages");

               if (pending != 0) {
                  targetSession.commit();
                  if (isCopy()) {
                     sourceSession.rollback();
                  } else {
                     sourceSession.commit();
                  }
               }

               return total;
            }
         }
      }
   }

   Destination createDestination(String role, Session session, String queue, String topic) throws Exception {
      if (queue != null && topic != null) {
         throw new IllegalArgumentException("Cannot have both topic and queue passed as " + role);
      }

      if (queue != null) {
         return session.createQueue(queue);
      }

      if (topic != null) {
         return session.createTopic(topic);
      }

      throw new IllegalArgumentException("You need to pass either a topic or a queue as " + role);
   }

   protected ConnectionFactoryClosable createConnectionFactory(String role,
                                                       String protocol,
                                                       String brokerURL,
                                                       String user,
                                                       String password,
                                                       String clientID) throws Exception {
      if (protocol.equals("core")) {
         if (isVerbose()) {
            getActionContext().out.println("Creating " + role + " CORE Connection towards " + brokerURL);
         }
         return new ConnectionFactoryClosable(createCoreConnectionFactory(brokerURL, user, password, clientID));
      } else if (protocol.equals("amqp")) {
         if (isVerbose()) {
            getActionContext().out.println("Creating " + role + " AMQP Connection towards " + brokerURL);
         }
         return new ConnectionFactoryClosable(createAMQPConnectionFactory(brokerURL, user, password, clientID));
      } else {
         throw new IllegalStateException("protocol " + protocol + " not supported");
      }
   }

   private ConnectionFactory createAMQPConnectionFactory(String brokerURL,
                                                         String user,
                                                         String password,
                                                         String clientID) {
      if (brokerURL.startsWith("tcp://")) {
         // replacing tcp:// by amqp://
         brokerURL = "amqp" + brokerURL.substring(3);
      }
      JmsConnectionFactory cf = new JmsConnectionFactory(user, password, brokerURL);
      if (clientID != null) {
         cf.setClientID(clientID);
      }

      try {
         Connection connection = cf.createConnection();
         connection.close();
         return cf;
      } catch (JMSSecurityException e) {
         // if a security exception will get the user and password through an input
         getActionContext().err.println("Connection failed::" + e.getMessage());
         userPassword(brokerURL);
         cf = new JmsConnectionFactory(user, password, brokerURL);
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         return cf;
      } catch (JMSException e) {
         // if a connection exception will ask for the URL, user and password
         getActionContext().err.println("Connection failed::" + e.getMessage());
         brokerURL = input("--url", "Type in the broker URL for a retry (e.g. tcp://localhost:61616)", brokerURL);
         userPassword(brokerURL);
         cf = new JmsConnectionFactory(user, password, brokerURL);
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         return cf;
      }
   }

   protected ActiveMQConnectionFactory createCoreConnectionFactory(String brokerURL,
                                                                   String user,
                                                                   String password,
                                                                   String clientID) {
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(brokerURL, user, password);

      if (clientID != null) {
         getActionContext().out.println("Consumer:: clientID = " + clientID);
         cf.setClientID(clientID);
      }
      try {
         Connection connection = cf.createConnection();
         connection.close();
         return cf;
      } catch (JMSSecurityException e) {
         cf.close();
         // if a security exception will get the user and password through an input
         getActionContext().err.println("Connection failed::" + e.getMessage());
         Pair<String, String> userPair = userPassword(brokerURL);
         cf = new ActiveMQConnectionFactory(brokerURL, userPair.getA(), userPair.getB());
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         return cf;
      } catch (JMSException e) {
         cf.close();
         // if a connection exception will ask for the URL, user and password
         getActionContext().err.println("Connection failed::" + e.getMessage());
         brokerURL = input("--url", "Type in the broker URL for a retry (e.g. tcp://localhost:61616)", brokerURL);
         Pair<String, String> userPair = userPassword(brokerURL);
         cf = new ActiveMQConnectionFactory(brokerURL, userPair.getA(), userPair.getB());
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         return cf;
      }
   }

   Pair<String, String> userPassword(String uri) {
      getActionContext().out.println("Type in user/password towards " + uri);
      String user, password;
      user = input("--user", "Type the username for a retry", null);
      password = inputPassword("--password", "Type the password for a retry", null);
      return new Pair<>(user, password);
   }

}
