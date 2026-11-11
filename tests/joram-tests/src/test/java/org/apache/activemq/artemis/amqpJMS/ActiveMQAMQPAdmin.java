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
package org.apache.activemq.artemis.amqpJMS;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.activemq.artemis.common.AbstractAdmin;
import org.apache.activemq.artemis.common.testjndi.TestContextFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.JmsTopic;

public class ActiveMQAMQPAdmin extends AbstractAdmin {

   Context context;

   {
      // enableJMSFrameTracing();
      try {
         // Use the jetty JNDI context since it's mutable.
         Hashtable<String, String> env = new Hashtable<>();
         env.put("java.naming.factory.initial", TestContextFactory.class.getName());
         env.put("java.naming.provider.url", "tcp://localhost:61616");
         context = new InitialContext(env);
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @SuppressWarnings("resource")
   public static void enableJMSFrameTracing() {
      try {
         String outputStreamName = "amqp-trace.txt";
         final PrintStream out = new PrintStream(new FileOutputStream(new File(outputStreamName)));
         Handler handler = new Handler() {
            @Override
            public void publish(LogRecord r) {
               out.println(String.format("%s:%s", r.getLoggerName(), r.getMessage()));
            }

            @Override
            public void flush() {
               out.flush();
            }

            @Override
            public void close() throws SecurityException {
               out.close();
            }
         };

         Logger log = Logger.getLogger("FRM");
         log.addHandler(handler);
         log.setLevel(Level.FINEST);
      } catch (FileNotFoundException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public String getName() {
      return getClass().getName();
   }

   @Override
   public Context createContext() throws NamingException {
      return context;
   }

   @Override
   public void createQueue(String name) {
      super.createQueue(name);
      try {
         context.bind(name, new JmsQueue(name));
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void createTopic(String name) {
      super.createTopic(name);
      try {
         context.bind(name, new JmsTopic(name));
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void deleteQueue(String name) {
      super.deleteQueue(name);
      try {
         context.unbind(name);
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void deleteTopic(String name) {
      super.deleteTopic(name);
      try {
         context.unbind(name);
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void createConnectionFactory(String name) {
      try {
         final JmsConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");
         context.bind(name, factory);
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void deleteConnectionFactory(String name) {
      try {
         context.unbind(name);
      } catch (NamingException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void createQueueConnectionFactory(String name) {
      createConnectionFactory(name);
   }

   @Override
   public void createTopicConnectionFactory(String name) {
      createConnectionFactory(name);
   }

   @Override
   public void deleteQueueConnectionFactory(String name) {
      deleteConnectionFactory(name);
   }

   @Override
   public void deleteTopicConnectionFactory(String name) {
      deleteConnectionFactory(name);
   }
}
