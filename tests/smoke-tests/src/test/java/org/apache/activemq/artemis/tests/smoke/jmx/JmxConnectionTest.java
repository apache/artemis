/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.smoke.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnection;
import javax.management.remote.rmi.RMIConnector;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteRef;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.jctools.util.UnsafeAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This test checks JMX connection to Artemis with both necessary ports set up so that it's easier to tunnel through
 * firewalls.
 */
public class JmxConnectionTest extends SmokeTestBase {

   // This test will use a smoke created by the pom on this project (smoke-tsts)

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT = 10099;
   private static final int RMI_REGISTRY_PORT = 10098;

   public static final String SERVER_NAME_0 = "jmx";
   private Class<?> proxyRefClass;

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/jmx").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      disableCheckThread();
      startServer(SERVER_NAME_0, 0, 30000);
      try {
         final Class<?> aClass = Class.forName("com.sun.jmx.remote.internal.ProxyRef");
         proxyRefClass = aClass;
      } catch (ClassNotFoundException ex) {
         //try with a shiny new version
         try {
            final Class<?> aClass = Class.forName("com.sun.jmx.remote.internal.rmi.ProxyRef");
            proxyRefClass = aClass;
         } catch (ClassNotFoundException ex2) {
            //no op
         }
      }
   }

   @Test
   public void testJmxConnection() throws Throwable {
      assertNotNull(proxyRefClass);
      try {

         // Without this, the RMI server would bind to the default interface IP (the user's local IP mostly)
         System.setProperty("java.rmi.server.hostname", JMX_SERVER_HOSTNAME);

         // I don't specify both ports here manually on purpose. See actual RMI registry connection port extraction below.
         String urlString = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT + "/jmxrmi";

         JMXServiceURL url = new JMXServiceURL(urlString);
         JMXConnector jmxConnector;

         try {
            jmxConnector = JMXConnectorFactory.connect(url);
            System.out.println("Successfully connected to: " + urlString);
         } catch (Exception e) {
            jmxConnector = null;
            e.printStackTrace();
            fail(e.getMessage());
         }

         try {

         /*
          * Now I need to extract the RMI registry port to make sure it's equal to the configured one. It's gonna be
          * messy because I have to use reflection to reach the information.
          */

            assertInstanceOf(RMIConnector.class, jmxConnector);

            // 1. RMIConnector::connection is expected to be RMIConnectionImpl_Stub
            Field connectionField = RMIConnector.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            RMIConnection rmiConnection = (RMIConnection) connectionField.get(jmxConnector);

            // 2. RMIConnectionImpl_Stub extends RemoteStub which extends RemoteObject
            assertInstanceOf(RemoteObject.class, rmiConnection);
            RemoteObject remoteObject = (RemoteObject) rmiConnection;

            // 3. RemoteObject::getRef is hereby expected to return ProxyRef
            RemoteRef remoteRef = remoteObject.getRef();
            assertTrue(proxyRefClass.isInstance(remoteRef));
            // 4. ProxyRef::ref is expected to contain UnicastRef (UnicastRef2 resp.)
            Field refField = proxyRefClass.getDeclaredField("ref");
            RemoteRef remoteRefField;
            try {
               refField.setAccessible(true);
               remoteRefField = (RemoteRef) refField.get(remoteRef);
            } catch (Throwable error) {
               assumeTrue(PlatformDependent.hasUnsafe(), "Unsafe must be available to continue the test");
               remoteRefField = (RemoteRef) UnsafeAccess.UNSAFE.getObject(remoteRef, UnsafeAccess.UNSAFE.objectFieldOffset(refField));
            }
            assertNotNull(remoteRefField);
            assertEquals("sun.rmi.server.UnicastRef2", remoteRefField.getClass().getTypeName());

            // 5. UnicastRef::getLiveRef returns LiveRef
            Method getLiveRef = remoteRefField.getClass().getMethod("getLiveRef");
            Object liveRef = getLiveRef.invoke(remoteRefField);

            assertEquals(RMI_REGISTRY_PORT, liveRef.getClass().getMethod("getPort").invoke(liveRef));

         } finally {
            jmxConnector.close();
         }
      } catch (Throwable e) {
         e.printStackTrace();
         throw e;
      }
   }

}
