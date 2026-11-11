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

package org.apache.activemq.artemis.tests.leak;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.checkleak.core.CheckLeak;
import io.github.checkleak.core.InventoryDataPoint;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerReceiverContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.utils.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryAssertions {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static void basicMemoryAsserts() throws Exception {
      basicMemoryAsserts(true);
   }

   /**
    * most tests should have these as 0 after execution.
    */
   public static void basicMemoryAsserts(boolean validateMessages) throws Exception {
      CheckLeak checkLeak = new CheckLeak();
      assertMemory(checkLeak, 0, OpenWireConnection.class.getName());
      assertMemory(checkLeak, 0, ProtonServerSenderContext.class.getName());
      assertMemory(checkLeak, 0, ProtonServerReceiverContext.class.getName());
      assertMemory(checkLeak, 0, ActiveMQProtonRemotingConnection.class.getName());
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());
      assertMemory(checkLeak, 0, OpenWireConnection.class.getName());
      assertMemory(checkLeak, 0, ActiveMQProtonRemotingConnection.class.getName());
      assertMemory(checkLeak, 0, ServerSessionImpl.class.getName());
      assertMemory(checkLeak, 0, AMQPSessionContext.class.getName());
      assertMemory(checkLeak, 0, ServerConsumerImpl.class.getName());
      assertMemory(checkLeak, 0, RoutingContextImpl.class.getName());
      if (validateMessages) {
         assertMemory(checkLeak, 0, MessageReferenceImpl.class.getName());
      }
   }

   public static void assertMemory(CheckLeak checkLeak, int maxExpected, String clazz) throws Exception {
      assertMemory(checkLeak, maxExpected, 5, 10, clazz);
   }

   public static void assertMemory(CheckLeak checkLeak, int maxExpected, int maxLevel, int maxObjects, String clazz) throws Exception {
      Wait.waitFor(() -> checkLeak.getAllObjects(clazz).length <= maxExpected, 5000, 100);

      Object[] objects = checkLeak.getAllObjects(clazz);
      if (objects.length > maxExpected) {
         for (Object obj : objects) {
            logger.warn("Object {} still in the heap", obj);
         }
         String report = checkLeak.exploreObjectReferences(maxLevel, maxObjects, true, objects);
         logger.info(report);

         fail("Class " + clazz + " has leaked " + objects.length + " objects\n" + report);
      }
   }


   public static void assertNoInnerInstances(Class clazz, CheckLeak checkLeak) throws Exception {
      checkLeak.forceGC();
      List<String> classList = getClassList(clazz);

      Map<Class<?>, InventoryDataPoint> inventoryDataPointMap = checkLeak.produceInventory();

      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      boolean failed = false;

      for (Map.Entry<Class<?>, InventoryDataPoint> entry : inventoryDataPointMap.entrySet()) {
         for (String classElement : classList) {
            if (entry.getKey().getName().startsWith(classElement) && entry.getValue().getInstances() > 0) {
               failed = true;
               printWriter.println(entry.getKey() + " contains " + entry.getValue().getInstances() + " instances");
               logger.warn("references: towards {}: {}", entry.getKey().getName(), checkLeak.exploreObjectReferences(entry.getKey().getName(), 10, 20, true));
            }
         }
      }

      assertFalse(failed, stringWriter.toString());
   }

   private static List<String> getClassList(Class clazz) {
      List<String> classList = new ArrayList<>();
      classList.add(clazz.getName());

      Class<?> superclass = clazz.getSuperclass();
      while (superclass != null) {
         if (superclass != Object.class) {
            classList.add(superclass.getName());
         }
         superclass = superclass.getSuperclass();
      }
      return classList;
   }

}
