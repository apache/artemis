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

package org.apache.activemq.artemis.tests.soak.replicationflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.File;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class SoakReplicatedPagingTest extends SoakTestBase {

   public static int OK = 1;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final int LAG_CONSUMER_TIME = 1000;
   public static final int TIME_RUNNING = 4000;
   public static final int CLIENT_KILLS = 2;

   String protocol;
   String consumerType;
   boolean transaction;
   final String destination;

   public SoakReplicatedPagingTest(String protocol, String consumerType, boolean transaction) {
      this.protocol = protocol;
      this.consumerType = consumerType;
      this.transaction = transaction;

      if (consumerType.equals("queue")) {
         destination = "exampleQueue";
      } else {
         destination = "exampleTopic";
      }
   }

   @Parameters(name = "protocol={0}, type={1}, tx={2}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{"MQTT", "topic", false}, {"AMQP", "shared", false}, {"AMQP", "queue", false}, {"OPENWIRE", "topic", false}, {"OPENWIRE", "queue", false}, {"CORE", "shared", false}, {"CORE", "queue", false},
         {"AMQP", "shared", true}, {"AMQP", "queue", true}, {"OPENWIRE", "topic", true}, {"OPENWIRE", "queue", true}, {"CORE", "shared", true}, {"CORE", "queue", true}});
   }

   public static final String SERVER_NAME_0 = "replicated-static0";
   public static final String SERVER_NAME_1 = "replicated-static1";

   static AtomicInteger produced = new AtomicInteger(0);
   static AtomicInteger consumed = new AtomicInteger(0);
   private static Process server0;

   private static Process server1;

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/replicated-static0");
         cliCreateServer.createServer();
      }
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_1);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/replicated-static1");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      cleanupData(SERVER_NAME_1);

      server0 = startServer(SERVER_NAME_0, 0, 30000);
   }

   static final int CONSUMER_THREADS = 5;
   static final int PRODUCER_THREADS = 5;
   static AtomicInteger producer_count = new AtomicInteger(0);
   static AtomicInteger consumer_count = new AtomicInteger(0);

   private static ConnectionFactory createConnectionFactory(String protocol, String uri) {
      if (protocol.toUpperCase().equals("OPENWIRE")) {
         return new org.apache.activemq.ActiveMQConnectionFactory("failover:(" + uri + ")");
      } else if (protocol.toUpperCase().equals("MQTT")) {
         return new MQTTCF();
      } else if (protocol.toUpperCase().equals("AMQP")) {
         return new JmsConnectionFactory("failover:(amqp://localhost:61616,amqp://localhost:61617)?failover.maxReconnectAttempts=16&jms.prefetchPolicy.all=5&jms.forceSyncSend=true");
      } else if (protocol.toUpperCase().equals("CORE") || protocol.toUpperCase().equals("ARTEMIS")) {
         return new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory(uri);
      } else {
         throw new IllegalStateException("Unknown:" + protocol);
      }
   }

   public static void main(String[] arg) {
      try {

         if (arg.length != 4) {
            System.err.println("You need to pass in protocol, consumerType, Time, transaction");
            exit(2, "invalid arguments");
         }

         String protocol = arg[0];
         String consumerType = arg[1];
         int time = Integer.parseInt(arg[2]);
         boolean tx = Boolean.parseBoolean(arg[3]);
         if (time == 0) {
            time = 15000;
         }

         final String host = "localhost";
         final int port = 61616;

         final ConnectionFactory factory = createConnectionFactory(protocol, "tcp://" + host + ":" + port);

         CountDownLatch producersLatch = new CountDownLatch(PRODUCER_THREADS);
         CountDownLatch consumersLatch = new CountDownLatch(CONSUMER_THREADS);

         for (int i = 0; i < PRODUCER_THREADS; i++) {
            Thread t = new Thread(() -> {
               SoakReplicatedPagingTest app = new SoakReplicatedPagingTest(protocol, consumerType, tx);
               app.produce(factory, producer_count.incrementAndGet(), producersLatch);
            });
            t.start();
         }

         Thread.sleep(1000);

         for (int i = 0; i < CONSUMER_THREADS; i++) {
            Thread t = new Thread(() -> {
               SoakReplicatedPagingTest app = new SoakReplicatedPagingTest(protocol, consumerType, tx);
               app.consume(factory, consumer_count.getAndIncrement(), consumersLatch);
            });
            t.start();
         }

         logger.debug("Awaiting producers...");
         if (!producersLatch.await(60000, TimeUnit.MILLISECONDS)) {
            System.out.println("Awaiting producers timeout");
            exit(3, "awaiting producers timeout");
         }

         logger.debug("Awaiting consumers...");
         if (!consumersLatch.await(60000, TimeUnit.MILLISECONDS)) {
            System.out.println("Awaiting consumers timeout");
            exit(4, "Consumer did not start");
         }

         logger.debug("Awaiting timeout...");
         Thread.sleep(time);

         if (consumed.get() == 0) {
            System.out.println("Retrying to wait consumers...");
            Wait.assertTrue(() -> consumed.get() > 0, 15_000, 100);
         }

         int exitStatus = consumed.get() > 0 ? OK : 5;
         logger.debug("Exiting with the status: {}", exitStatus);

         exit(exitStatus, "Consumed " + consumed.get() + " messages");
      } catch (Throwable t) {
         System.err.println("Exiting with the status 0. Reason: " + t);
         t.printStackTrace();
         exit(6, t.getMessage());
      }
   }

   public static void exit(int code, String message) {
      System.out.println("Exit code:: " + code + "::" + message);
      System.exit(code);
   }

   @TestTemplate
   public void testPagingReplication() throws Throwable {
      server1 = startServer(SERVER_NAME_1, 0, 30000);

      for (int i = 0; i < CLIENT_KILLS; i++) {
         Process process = SpawnedVMSupport.spawnVM(SpawnedVMSupport.getClassPath(),
                                                    null,
                                                    null,
                                                    SoakReplicatedPagingTest.class.getName(),
                                                    null,
                                                    "-Xmx1G",
                                                    null, true, true, -1L, protocol, String.valueOf(consumerType), String.valueOf(TIME_RUNNING), String.valueOf(transaction));


         int result = process.waitFor();
         if (result <= 0) {
            jstack();
         }
         assertEquals(OK, result);
      }
   }

   protected void jstack() throws Exception {
      jstack(server0.pid(), "SERVER 0");
      jstack(server1.pid(), "SERVER 1");
   }

   public void produce(ConnectionFactory factory, int index, CountDownLatch latch) {
      try {
         Connection connection = factory.createConnection("admin", "admin");

         latch.countDown();

         connection.start();
         logger.debug("Producer{} started", index);

         final Session session;

         if (transaction) {
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
         } else {
            session = connection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
         }

         Destination address;

         if (consumerType.equals("queue")) {
            address = session.createQueue(destination);
         } else {
            address = session.createTopic(destination);
         }

         MessageProducer messageProducer = session.createProducer(address);

         int i = 0;
         while (true) {

            Message message;
            if (i % 100 == 0) {
               message = session.createTextMessage(RandomUtil.randomAlphaNumericString(110_000));
            } else {
               message = session.createTextMessage(RandomUtil.randomAlphaNumericString(355));
            }

            messageProducer.send(message);
            produced.incrementAndGet();
            i++;
            if (i % 100 == 0) {
               logger.debug("Producer{} published {} messages", index, i);
               if (transaction) {
                  session.commit();
               }
            }
         }
      } catch (Exception e) {
         System.err.println("Error on Producer" + index + ": " + e.getMessage());
         e.printStackTrace();
      }
   }

   public void consume(ConnectionFactory factory, int index, CountDownLatch latch) {
      try {
         Connection connection = factory.createConnection("admin", "admin");

         final Session session;

         if (transaction) {
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
         } else {
            session = connection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
         }

         Destination address;

         if (consumerType.equals("queue")) {
            address = session.createQueue(destination);
         } else {
            address = session.createTopic(destination);
         }

         String consumerId = "ss" + (index % 5);
         MessageConsumer messageConsumer;

         if (protocol.equals("shared")) {
            messageConsumer = session.createSharedConsumer((Topic)address, consumerId);
         } else {
            messageConsumer = session.createConsumer(address);
         }

         if (LAG_CONSUMER_TIME > 0) {
            Thread.sleep(LAG_CONSUMER_TIME);
         }

         latch.countDown();
         connection.start();
         logger.debug("Consumer{} started", index);

         int i = 0;
         while (true) {
            Message m = messageConsumer.receive(1000);
            consumed.incrementAndGet();
            if (m == null) {
               logger.debug("Consumer{} received null", index);
            }
            i++;
            if (i % 100 == 0) {
               logger.debug("Consumer{} received {} messages", index, i);
               if (transaction) {
                  session.commit();
               }
            }
         }
      } catch (Exception e) {
         System.err.println("Error on Consumer" + index + ": " + e.getMessage());
         e.printStackTrace();
      }
   }
}

class MQTTCF implements ConnectionFactory, Connection, Session, Topic, MessageConsumer, MessageProducer {

   final MQTT mqtt = new MQTT();
   private String topicName;
   private BlockingConnection blockingConnection;
   private boolean consumer;

   MQTTCF() {
      try {
         mqtt.setHost("localhost", 61616);
      } catch (Exception ignored) {
      }
   }

   @Override
   public Connection createConnection() throws JMSException {
      return new MQTTCF();
   }

   @Override
   public Connection createConnection(String userName, String password) throws JMSException {
      MQTTCF result = new MQTTCF();
      result.mqtt.setUserName(userName);
      result.mqtt.setPassword(password);
      return result;
   }

   @Override
   public JMSContext createContext() {
      return null;
   }

   @Override
   public JMSContext createContext(int sessionMode) {
      return null;
   }

   @Override
   public JMSContext createContext(String userName, String password) {
      return null;
   }

   @Override
   public JMSContext createContext(String userName, String password, int sessionMode) {
      return null;
   }

   @Override
   public String getMessageSelector() throws JMSException {
      return null;
   }

   @Override
   public Message receive() throws JMSException {
      return null;
   }

   @Override
   public Message receive(long timeout) throws JMSException {
      final org.fusesource.mqtt.client.Message message;
      try {
         message = blockingConnection.receive(timeout, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
         throw new JMSException(e.getMessage());
      }
      if (message != null) {
         return new TMessage(new String(message.getPayload()));
      }
      return null;
   }

   @Override
   public Message receiveNoWait() throws JMSException {
      return null;
   }

   @Override
   public void setDisableMessageID(boolean value) throws JMSException {

   }

   @Override
   public boolean getDisableMessageID() throws JMSException {
      return false;
   }

   @Override
   public void setDisableMessageTimestamp(boolean value) throws JMSException {

   }

   @Override
   public boolean getDisableMessageTimestamp() throws JMSException {
      return false;
   }

   @Override
   public void setDeliveryMode(int deliveryMode) throws JMSException {

   }

   @Override
   public int getDeliveryMode() throws JMSException {
      return 0;
   }

   @Override
   public void setPriority(int defaultPriority) throws JMSException {

   }

   @Override
   public int getPriority() throws JMSException {
      return 0;
   }

   @Override
   public void setTimeToLive(long timeToLive) throws JMSException {

   }

   @Override
   public long getTimeToLive() throws JMSException {
      return 0;
   }

   @Override
   public Destination getDestination() throws JMSException {
      return null;
   }

   @Override
   public void send(Message message) throws JMSException {
      try {
         blockingConnection.publish(topicName, message.getBody(String.class).getBytes(), QoS.EXACTLY_ONCE, false);
      } catch (Exception e) {
         throw new JMSException(e.getMessage());
      }
   }

   @Override
   public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {

   }

   @Override
   public void send(Destination destination, Message message) throws JMSException {

   }

   @Override
   public void send(Message message, CompletionListener completionListener) throws JMSException {

   }

   @Override
   public void send(Destination destination,
                    Message message,
                    int deliveryMode,
                    int priority,
                    long timeToLive) throws JMSException {

   }

   @Override
   public void send(Destination destination,
                    Message message,
                    CompletionListener completionListener) throws JMSException {

   }

   @Override
   public void send(Message message,
                    int deliveryMode,
                    int priority,
                    long timeToLive,
                    CompletionListener completionListener) throws JMSException {

   }

   @Override
   public void send(Destination destination,
                    Message message,
                    int deliveryMode,
                    int priority,
                    long timeToLive,
                    CompletionListener completionListener) throws JMSException {

   }

   @Override
   public long getDeliveryDelay() throws JMSException {
      return 0;
   }

   @Override
   public void setDeliveryDelay(long deliveryDelay) throws JMSException {

   }

   @Override
   public BytesMessage createBytesMessage() throws JMSException {
      return null;
   }

   @Override
   public MapMessage createMapMessage() throws JMSException {
      return null;
   }

   @Override
   public Message createMessage() throws JMSException {
      return null;
   }

   @Override
   public ObjectMessage createObjectMessage() throws JMSException {
      return null;
   }

   @Override
   public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
      return null;
   }

   @Override
   public StreamMessage createStreamMessage() throws JMSException {
      return null;
   }

   @Override
   public TextMessage createTextMessage() throws JMSException {
      return null;
   }

   @Override
   public TextMessage createTextMessage(String text) throws JMSException {
      return new TMessage(text);
   }

   @Override
   public boolean getTransacted() throws JMSException {
      return false;
   }

   @Override
   public int getAcknowledgeMode() throws JMSException {
      return 0;
   }

   @Override
   public void commit() throws JMSException {

   }

   @Override
   public void rollback() throws JMSException {

   }

   @Override
   public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
      return this;
   }

   @Override
   public Session createSession(int sessionMode) throws JMSException {
      return null;
   }

   @Override
   public Session createSession() throws JMSException {
      return null;
   }

   @Override
   public String getClientID() throws JMSException {
      return null;
   }

   @Override
   public void setClientID(String clientID) throws JMSException {

   }

   @Override
   public ConnectionMetaData getMetaData() throws JMSException {
      return null;
   }

   @Override
   public ExceptionListener getExceptionListener() throws JMSException {
      return null;
   }

   @Override
   public void setExceptionListener(ExceptionListener listener) throws JMSException {

   }

   @Override
   public void start() throws JMSException {
      blockingConnection = mqtt.blockingConnection();
      try {
         blockingConnection.connect();

         if (consumer) {
            blockingConnection.subscribe(new org.fusesource.mqtt.client.Topic[]{new org.fusesource.mqtt.client.Topic(topicName, QoS.EXACTLY_ONCE)});
         }

      } catch (Exception e) {
         throw new JMSException(e.getMessage());
      }
   }

   @Override
   public void stop() throws JMSException {

   }

   @Override
   public void close() throws JMSException {

   }

   @Override
   public ConnectionConsumer createConnectionConsumer(Destination destination,
                                                      String messageSelector,
                                                      ServerSessionPool sessionPool,
                                                      int maxMessages) throws JMSException {
      return null;
   }

   @Override
   public ConnectionConsumer createDurableConnectionConsumer(Topic topic,
                                                             String subscriptionName,
                                                             String messageSelector,
                                                             ServerSessionPool sessionPool,
                                                             int maxMessages) throws JMSException {
      return null;
   }

   @Override
   public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic,
                                                                   String subscriptionName,
                                                                   String messageSelector,
                                                                   ServerSessionPool sessionPool,
                                                                   int maxMessages) throws JMSException {
      return null;
   }

   @Override
   public ConnectionConsumer createSharedConnectionConsumer(Topic topic,
                                                            String subscriptionName,
                                                            String messageSelector,
                                                            ServerSessionPool sessionPool,
                                                            int maxMessages) throws JMSException {
      return null;
   }

   @Override
   public void recover() throws JMSException {

   }

   @Override
   public MessageListener getMessageListener() throws JMSException {
      return null;
   }

   @Override
   public void setMessageListener(MessageListener listener) throws JMSException {

   }

   @Override
   public void run() {

   }

   @Override
   public MessageProducer createProducer(Destination destination) throws JMSException {
      return this;
   }

   @Override
   public MessageConsumer createConsumer(Destination destination) throws JMSException {
      consumer = true;
      return this;
   }

   @Override
   public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
      return null;
   }

   @Override
   public MessageConsumer createConsumer(Destination destination,
                                         String messageSelector,
                                         boolean NoLocal) throws JMSException {
      return null;
   }

   @Override
   public Queue createQueue(String queueName) throws JMSException {
      return null;
   }

   @Override
   public Topic createTopic(String topicName) throws JMSException {
      this.topicName = topicName;
      return this;
   }

   @Override
   public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
      return null;
   }

   @Override
   public TopicSubscriber createDurableSubscriber(Topic topic,
                                                  String name,
                                                  String messageSelector,
                                                  boolean noLocal) throws JMSException {
      return null;
   }

   @Override
   public QueueBrowser createBrowser(Queue queue) throws JMSException {
      return null;
   }

   @Override
   public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
      return null;
   }

   @Override
   public TemporaryQueue createTemporaryQueue() throws JMSException {
      return null;
   }

   @Override
   public TemporaryTopic createTemporaryTopic() throws JMSException {
      return null;
   }

   @Override
   public void unsubscribe(String name) throws JMSException {

   }

   @Override
   public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
      return null;
   }

   @Override
   public MessageConsumer createSharedConsumer(Topic topic,
                                               String sharedSubscriptionName,
                                               String messageSelector) throws JMSException {
      return null;
   }

   @Override
   public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
      return null;
   }

   @Override
   public MessageConsumer createDurableConsumer(Topic topic,
                                                String name,
                                                String messageSelector,
                                                boolean noLocal) throws JMSException {
      return null;
   }

   @Override
   public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
      return null;
   }

   @Override
   public MessageConsumer createSharedDurableConsumer(Topic topic,
                                                      String name,
                                                      String messageSelector) throws JMSException {
      return null;
   }

   @Override
   public String getTopicName() throws JMSException {
      return topicName;
   }

   private class TMessage implements Message, TextMessage {

      final String s;

      TMessage(String s) {
         this.s = s;
      }

      @Override
      public String getJMSMessageID() throws JMSException {
         return null;
      }

      @Override
      public void setJMSMessageID(String id) throws JMSException {

      }

      @Override
      public long getJMSTimestamp() throws JMSException {
         return 0;
      }

      @Override
      public void setJMSTimestamp(long timestamp) throws JMSException {

      }

      @Override
      public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
         return new byte[0];
      }

      @Override
      public void setJMSCorrelationIDAsBytes(byte[] correlationID) throws JMSException {

      }

      @Override
      public void setJMSCorrelationID(String correlationID) throws JMSException {

      }

      @Override
      public String getJMSCorrelationID() throws JMSException {
         return null;
      }

      @Override
      public Destination getJMSReplyTo() throws JMSException {
         return null;
      }

      @Override
      public void setJMSReplyTo(Destination replyTo) throws JMSException {

      }

      @Override
      public Destination getJMSDestination() throws JMSException {
         return null;
      }

      @Override
      public void setJMSDestination(Destination destination) throws JMSException {

      }

      @Override
      public int getJMSDeliveryMode() throws JMSException {
         return 0;
      }

      @Override
      public void setJMSDeliveryMode(int deliveryMode) throws JMSException {

      }

      @Override
      public boolean getJMSRedelivered() throws JMSException {
         return false;
      }

      @Override
      public void setJMSRedelivered(boolean redelivered) throws JMSException {

      }

      @Override
      public String getJMSType() throws JMSException {
         return null;
      }

      @Override
      public void setJMSType(String type) throws JMSException {

      }

      @Override
      public long getJMSExpiration() throws JMSException {
         return 0;
      }

      @Override
      public void setJMSExpiration(long expiration) throws JMSException {

      }

      @Override
      public int getJMSPriority() throws JMSException {
         return 0;
      }

      @Override
      public void setJMSPriority(int priority) throws JMSException {

      }

      @Override
      public void clearProperties() throws JMSException {

      }

      @Override
      public boolean propertyExists(String name) throws JMSException {
         return false;
      }

      @Override
      public boolean getBooleanProperty(String name) throws JMSException {
         return false;
      }

      @Override
      public byte getByteProperty(String name) throws JMSException {
         return 0;
      }

      @Override
      public short getShortProperty(String name) throws JMSException {
         return 0;
      }

      @Override
      public int getIntProperty(String name) throws JMSException {
         return 0;
      }

      @Override
      public long getLongProperty(String name) throws JMSException {
         return 0;
      }

      @Override
      public float getFloatProperty(String name) throws JMSException {
         return 0;
      }

      @Override
      public double getDoubleProperty(String name) throws JMSException {
         return 0;
      }

      @Override
      public String getStringProperty(String name) throws JMSException {
         return null;
      }

      @Override
      public Object getObjectProperty(String name) throws JMSException {
         return null;
      }

      @Override
      public Enumeration getPropertyNames() throws JMSException {
         return null;
      }

      @Override
      public void setBooleanProperty(String name, boolean value) throws JMSException {

      }

      @Override
      public void setByteProperty(String name, byte value) throws JMSException {

      }

      @Override
      public void setShortProperty(String name, short value) throws JMSException {

      }

      @Override
      public void setIntProperty(String name, int value) throws JMSException {

      }

      @Override
      public void setLongProperty(String name, long value) throws JMSException {

      }

      @Override
      public void setFloatProperty(String name, float value) throws JMSException {

      }

      @Override
      public void setDoubleProperty(String name, double value) throws JMSException {

      }

      @Override
      public void setStringProperty(String name, String value) throws JMSException {

      }

      @Override
      public void setObjectProperty(String name, Object value) throws JMSException {

      }

      @Override
      public void acknowledge() throws JMSException {

      }

      @Override
      public void clearBody() throws JMSException {

      }

      @Override
      public long getJMSDeliveryTime() throws JMSException {
         return 0;
      }

      @Override
      public void setJMSDeliveryTime(long deliveryTime) throws JMSException {

      }

      @Override
      public <T> T getBody(Class<T> c) throws JMSException {
         return (T) s;
      }

      @Override
      public boolean isBodyAssignableTo(Class c) throws JMSException {
         return false;
      }

      @Override
      public void setText(String string) throws JMSException {
      }

      @Override
      public String getText() throws JMSException {
         return s;
      }
   }
}
