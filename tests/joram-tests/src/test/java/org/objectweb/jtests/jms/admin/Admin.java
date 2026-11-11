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
package org.objectweb.jtests.jms.admin;

import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Simple Administration interface.
 * <p>
 * JMS Provider has to implement this simple interface to be able to use the test suite.
 */
public interface Admin {

   /**
    * {@return name of the JMS Provider}
    */
   String getName();

   /**
    * {@return an {@code Context} for the JMS Provider}
    */
   Context createContext() throws NamingException;

   /**
    * Creates a {@code ConnectionFactory} and makes it available from JNDI with name {@code name}.
    *
    * @param name JNDI name of the {@code ConnectionFactory}
    * @since JMS 1.1
    */
   void createConnectionFactory(String name);

   /**
    * Creates a {@code QueueConnectionFactory} and makes it available from JNDI with name {@code name}.
    *
    * @param name JNDI name of the {@code QueueConnectionFactory}
    */
   void createQueueConnectionFactory(String name);

   /**
    * Creates a {@code TopicConnectionFactory} and makes it available from JNDI with name {@code name}.
    *
    * @param name JNDI name of the {@code TopicConnectionFactory}
    */
   void createTopicConnectionFactory(String name);

   /**
    * Creates a {@code Queue} and makes it available from JNDI with name {@code name}.
    *
    * @param name JNDI name of the {@code Queue}
    */
   void createQueue(String name);

   /**
    * Creates a {@code Topic} and makes it available from JNDI with name {@code name}.
    *
    * @param name JNDI name of the {@code Topic}
    */
   void createTopic(String name);

   /**
    * Removes the {@code Queue} of name {@code name} from JNDI and deletes it
    *
    * @param name JNDI name of the {@code Queue}
    */
   void deleteQueue(String name);

   /**
    * Removes the {@code Topic} of name {@code name} from JNDI and deletes it
    *
    * @param name JNDI name of the {@code Topic}
    */
   void deleteTopic(String name);

   /**
    * Removes the {@code ConnectionFactory} of name {@code name} from JNDI and deletes it
    *
    * @param name JNDI name of the {@code ConnectionFactory}
    * @since JMS 1.1
    */
   void deleteConnectionFactory(String name);

   /**
    * Removes the {@code QueueConnectionFactory} of name {@code name} from JNDI and deletes it
    *
    * @param name JNDI name of the {@code QueueConnectionFactory}
    */
   void deleteQueueConnectionFactory(String name);

   /**
    * Removes the {@code TopicConnectionFactory} of name {@code name} from JNDI and deletes it
    *
    * @param name JNDI name of the {@code TopicConnectionFactory}
    */
   void deleteTopicConnectionFactory(String name);

   /**
    * Optional method to start the server embedded (instead of running an external server)
    */
   void startServer() throws Exception;

   /**
    * Optional method to stop the server embedded (instead of running an external server)
    */
   void stopServer() throws Exception;

   /**
    * Optional method for processing to be made after the Admin is instantiated and before it is used to create the
    * administrated objects
    */
   void start() throws Exception;

   /**
    * Optional method for processing to be made after the administrated objects have been cleaned up
    */
   void stop() throws Exception;

}
