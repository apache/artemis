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

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.activemq.artemis.tests.unit.util.InVMContext;

/**
 * An in-VM JNDI InitialContextFactory. Lightweight JNDI implementation used for testing.
 */
public class InVMInitialContextFactory implements InitialContextFactory {


   private static final Map<Integer, Context> initialContexts = new HashMap<>();

   public static Hashtable<String, String> getJNDIEnvironment() {
      return InVMInitialContextFactory.getJNDIEnvironment(0);
   }

   /**
    * @return the JNDI environment to use to get this InitialContextFactory
    */
   public static Hashtable<String, String> getJNDIEnvironment(final int serverIndex) {
      Hashtable<String, String> env = new Hashtable<>();
      env.put("java.naming.factory.initial", "org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory");
      env.put(Constants.SERVER_INDEX_PROPERTY_NAME, Integer.toString(serverIndex));
      return env;
   }


   @Override
   public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
      // try first in the environment passed as argument ...
      String s = (String) environment.get(Constants.SERVER_INDEX_PROPERTY_NAME);

      if (s == null) {
         // ... then in the global environment
         s = System.getProperty(Constants.SERVER_INDEX_PROPERTY_NAME);

         if (s == null) {
            // try the thread name
            String tName = Thread.currentThread().getName();
            if (tName.contains("server")) {
               s = tName.substring(6);
            }
            if (s == null) {
               throw new NamingException("Cannot figure out server index!");
            }
         }
      }

      int serverIndex;

      try {
         serverIndex = Integer.parseInt(s);
      } catch (Exception e) {
         throw new NamingException("Failure parsing \"" + Constants.SERVER_INDEX_PROPERTY_NAME +
                                      "\". " +
                                      s +
                                      " is not an integer");
      }

      // Note! This MUST be synchronized
      synchronized (InVMInitialContextFactory.initialContexts) {

         InVMContext ic = (InVMContext) InVMInitialContextFactory.initialContexts.get(serverIndex);

         if (ic == null) {
            ic = new InVMContext(s);
            ic.bind("java:/", new InVMContext(s));
            InVMInitialContextFactory.initialContexts.put(serverIndex, ic);
         }

         return ic;
      }
   }


}
