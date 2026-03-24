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
package org.apache.activemq.artemis.jms;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

import org.apache.activemq.artemis.common.AbstractAdmin;
import org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory;

public class ActiveMQCoreAdmin extends AbstractAdmin {

   private Context context;

   Hashtable<String, String> jndiProps = new Hashtable<>();

   public ActiveMQCoreAdmin() {
      super();
      jndiProps.put(Context.INITIAL_CONTEXT_FACTORY, ActiveMQInitialContextFactory.class.getCanonicalName());
      try {
         Hashtable<String, String> env = new Hashtable<>();
         env.put("java.naming.factory.initial", "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
         env.put("java.naming.provider.url", "tcp://localhost:61616");
         context = new InitialContext(env);
      } catch (NamingException e) {
         e.printStackTrace();
      }
   }

   @Override
   public void start() throws Exception {
      super.start();

   }

   @Override
   public void stop() throws Exception {
      super.stop();
   }

   @Override
   public void createConnectionFactory(final String name) {
      jndiProps.put("connectionFactory." + name, "tcp://127.0.0.1:61616?type=CF");

   }

   @Override
   public Context createContext() throws NamingException {
      return new InitialContext(jndiProps);
   }

   @Override
   public void createQueue(final String name) {
      super.createQueue(name);
      jndiProps.put("queue." + name, name);
   }

   @Override
   public void createQueueConnectionFactory(final String name) {
      jndiProps.put("connectionFactory." + name, "tcp://127.0.0.1:61616?type=QUEUE_CF");
   }

   @Override
   public void createTopic(final String name) {
      super.createTopic(name);
      jndiProps.put("topic." + name, name);
   }

   @Override
   public void createTopicConnectionFactory(final String name) {
      jndiProps.put("connectionFactory." + name, "tcp://127.0.0.1:61616?type=TOPIC_CF");
   }

   @Override
   public void deleteConnectionFactory(final String name) {
      jndiProps.remove("connectionFactory." + name);
   }

   @Override
   public void deleteQueue(final String name) {
      super.deleteQueue(name);
      jndiProps.remove("queue." + name);
   }

   @Override
   public void deleteQueueConnectionFactory(final String name) {
      deleteConnectionFactory(name);
      jndiProps.remove("connectionFactory." + name);
   }

   @Override
   public void deleteTopic(final String name) {
      super.deleteTopic(name);
      jndiProps.remove("topic." + name);
   }

   @Override
   public void deleteTopicConnectionFactory(final String name) {
      deleteConnectionFactory(name);
      jndiProps.remove("connectionFactory." + name);

   }

   @Override
   public String getName() {
      return this.getClass().getName();
   }


}
