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
package org.apache.activemq.artemis.jms.tests.tools.container;

import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;
import java.util.Hashtable;

import org.apache.activemq.artemis.jms.tests.JmsTestLogger;

public class InVMInitialContextFactoryBuilder implements InitialContextFactoryBuilder {


   private static final JmsTestLogger log = JmsTestLogger.LOGGER;



   public InVMInitialContextFactoryBuilder() {
   }

   @Override
   public InitialContextFactory createInitialContextFactory(final Hashtable<?, ?> environment) throws NamingException {

      InitialContextFactory icf = null;

      if (environment != null) {
         String icfName = (String) environment.get("java.naming.factory.initial");

         if (icfName != null) {
            Class c = null;

            try {
               c = Class.forName(icfName);
            } catch (ClassNotFoundException e) {
               InVMInitialContextFactoryBuilder.log.error("\"" + icfName + "\" cannot be loaded", e);
               throw new NamingException("\"" + icfName + "\" cannot be loaded");
            }

            try {
               icf = (InitialContextFactory) c.newInstance();
            } catch (InstantiationException e) {
               InVMInitialContextFactoryBuilder.log.error(c.getName() + " cannot be instantiated", e);
               throw new NamingException(c.getName() + " cannot be instantiated");
            } catch (IllegalAccessException e) {
               InVMInitialContextFactoryBuilder.log.error(c.getName() + " instantiation generated an IllegalAccessException", e);
               throw new NamingException(c.getName() + " instantiation generated an IllegalAccessException");
            }
         }
      }

      if (icf == null) {
         icf = new InVMInitialContextFactory();
      }

      return icf;
   }
}