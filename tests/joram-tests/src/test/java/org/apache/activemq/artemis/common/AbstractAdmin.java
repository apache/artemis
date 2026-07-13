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
package org.apache.activemq.artemis.common;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.objectweb.jtests.jms.admin.Admin;

public class AbstractAdmin implements Admin {

   protected ClientSession clientSession;

   protected ClientRequestor requestor;

   protected boolean serverLifeCycleActive;

   protected Process serverProcess;

   protected ServerLocator serverLocator;

   protected ClientSessionFactory sf;

   // this is a constant to control if we should use a separate VM for the server.
   public static final boolean spawnServer = false;

   /**
    * Determines whether to act or 'no-op' on serverStart() and serverStop(). This is used when testing combinations of
    * client and servers with different versions.
    */
   private static final String SERVER_LIVE_CYCLE_PROPERTY = "org.apache.activemq.artemis.jms.ActiveMQAMQPAdmin.serverLifeCycle";

   public AbstractAdmin() {
      serverLifeCycleActive = Boolean.valueOf(System.getProperty(SERVER_LIVE_CYCLE_PROPERTY, "true"));

   }

   @Override
   public String getName() {
      return getClass().getName();
   }

   @Override
   public void start() throws Exception {
      serverLocator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName()));
      serverLocator.setConnectionCredentials(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword());
      sf = serverLocator.createSessionFactory();
      clientSession = sf.createSession(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword(), false, true, true, false, 1);
      requestor = new ClientRequestor(clientSession, ActiveMQDefaultConfiguration.getDefaultManagementAddress());
      clientSession.start();

   }

   @Override
   public void stop() throws Exception {
      requestor.close();

      if (sf != null) {
         sf.close();
      }

      if (serverLocator != null) {
         serverLocator.close();
      }

      sf = null;
      serverLocator = null;
   }

   @Override
   public Context createContext() throws NamingException {
      return new InitialContext();
   }

   @Override
   public void createConnectionFactory(final String name) {
      throw new RuntimeException("FIXME NYI createConnectionFactory");
   }

   @Override
   public void deleteConnectionFactory(final String name) {
      throw new RuntimeException("FIXME NYI deleteConnectionFactory");
   }

   @Override
   public void createQueue(final String name) {
      try {
         invokeSyncOperation(ResourceNames.BROKER, "createQueue", name, RoutingType.ANYCAST.toString(), name, null, true, ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), true);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void deleteQueue(final String name) {
      try {
         invokeSyncOperation(ResourceNames.BROKER, "destroyQueue", name, true);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void createQueueConnectionFactory(final String name) {
      createConnectionFactory(name);
   }

   @Override
   public void deleteQueueConnectionFactory(final String name) {
      deleteConnectionFactory(name);
   }

   @Override
   public void createTopic(final String name) {
      try {
         invokeSyncOperation(ResourceNames.BROKER, "createAddress", name, "MULTICAST");
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void deleteTopic(final String name) {
      try {
         invokeSyncOperation(ResourceNames.BROKER, "deleteAddress", name, Boolean.TRUE);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void createTopicConnectionFactory(final String name) {
      createConnectionFactory(name);
   }

   @Override
   public void deleteTopicConnectionFactory(final String name) {
      deleteConnectionFactory(name);
   }

   @Override
   public void startServer() throws Exception {
      if (!serverLifeCycleActive) {
         return;
      }

      if (spawnServer) {
         String[] vmArgs = new String[]{};
         serverProcess = SpawnedVMSupport.spawnVM(SpawnedJMSServer.class.getName(), vmArgs, false);
         InputStreamReader isr = new InputStreamReader(serverProcess.getInputStream());

         final BufferedReader br = new BufferedReader(isr);
         String line = null;
         while ((line = br.readLine()) != null) {
            System.out.println("SERVER: " + line);
            if ("OK".equals(line.trim())) {
               new Thread(() -> {
                  try {
                     String line1 = null;
                     while ((line1 = br.readLine()) != null) {
                        System.out.println("SERVER: " + line1);
                     }
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }).start();
               return;
            } else if ("KO".equals(line.trim())) {
               // something went wrong with the server, destroy it:
               serverProcess.destroy();
               throw new IllegalStateException("Unable to start the spawned server :" + line);
            }
         }
      } else {
         SpawnedJMSServer.startServer();
      }
   }

   @Override
   public void stopServer() throws Exception {
      if (!serverLifeCycleActive) {
         return;
      }
      if (spawnServer) {
         OutputStreamWriter osw = new OutputStreamWriter(serverProcess.getOutputStream());
         osw.write("STOP\n");
         osw.flush();
         int exitValue = serverProcess.waitFor();
         if (exitValue != 0) {
            serverProcess.destroy();
         }
      } else {
         SpawnedJMSServer.stopServer();
      }
   }

   protected Object invokeSyncOperation(final String resourceName,
                                        final String operationName,
                                        final Object... parameters) throws Exception {
      ClientMessage message = clientSession.createMessage(false);
      ManagementHelper.putOperationInvocation(message, resourceName, operationName, parameters);
      ClientMessage reply;
      try {
         reply = requestor.request(message, 3000);
      } catch (Exception e) {
         throw new IllegalStateException("Exception while invoking " + operationName + " on " + resourceName, e);
      }
      if (reply == null) {
         throw new IllegalStateException("no reply received when invoking " + operationName + " on " + resourceName);
      }
      if (!ManagementHelper.hasOperationSucceeded(reply)) {
         throw new IllegalStateException("operation failed when invoking " + operationName +
                                            " on " +
                                            resourceName +
                                            ": " +
                                            ManagementHelper.getResult(reply));
      }
      return ManagementHelper.getResult(reply);
   }

}
