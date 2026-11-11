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
package org.apache.activemq.artemis.tests.util;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;


public class Jmx {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @FunctionalInterface
   public interface ThrowableFunction<T, R> {

      R apply(T t) throws Throwable;
   }

   private static <C, T> Optional<T> queryControl(JMXServiceURL serviceURI,
                                                  ObjectName objectName,
                                                  ThrowableFunction<C, T> queryControl,
                                                  Class<C> controlClass,
                                                  Function<Throwable, T> onThrowable) {
      try {
         try (JMXConnector jmx = JMXConnectorFactory.connect(serviceURI)) {
            final C control = MBeanServerInvocationHandler.newProxyInstance(jmx.getMBeanServerConnection(), objectName, controlClass, false);
            return Optional.ofNullable(queryControl.apply(control));
         }
      } catch (Throwable t) {
         return Optional.ofNullable(onThrowable.apply(t));
      }
   }

   public static Optional<Boolean> isReplicaSync(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::isReplicaSync, ActiveMQServerControl.class, throwable -> null);
   }

   public static Optional<Boolean> isBackup(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::isBackup, ActiveMQServerControl.class, throwable -> null);
   }

   public static Optional<String> getNodeID(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::getNodeID, ActiveMQServerControl.class, throwable -> null);
   }

   public static Optional<Long> getActivationSequence(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::getActivationSequence, ActiveMQServerControl.class, throwable -> null);

   }

   public static Optional<Boolean> isActive(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::isActive, ActiveMQServerControl.class, throwable -> null);

   }

   public static Optional<String> listNetworkTopology(JMXServiceURL serviceURI,
                                                       ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::listNetworkTopology, ActiveMQServerControl.class, throwable -> null);
   }

   public static Map<String, Pair<String, String>> decodeNetworkTopologyJson(String networkTopologyJson) {
      if (networkTopologyJson == null || networkTopologyJson.isEmpty()) {
         return Collections.emptyMap();
      }
      final JsonArray nodeIDs = JsonLoader.readArray(new StringReader(networkTopologyJson));
      final int nodeCount = nodeIDs.size();
      Map<String, Pair<String, String>> networkTopology = new HashMap<>(nodeCount);
      for (int i = 0; i < nodeCount; i++) {
         final JsonObject nodePair = nodeIDs.getJsonObject(i);
         try {
            final String nodeID = nodePair.getString("nodeID");
            final String primary = Objects.requireNonNullElse(nodePair.getString("primary"), nodePair.getString("live"));
            final String backup = nodePair.getString("backup", null);
            networkTopology.put(nodeID, new Pair<>(primary, backup));
         } catch (Exception e) {
            logger.warn("Error on {}", nodePair, e);
         }
      }
      return networkTopology;
   }

   private static long countMembers(Map<String, Pair<String, String>> networkTopology) {
      final long count = networkTopology.values().stream()
         .map(Pair::getA).filter(primary -> primary != null && !primary.isEmpty())
         .count();
      return count;
   }

   private static long countNodes(Map<String, Pair<String, String>> networkTopology) {
      final long count = networkTopology.values().stream()
         .flatMap(pair -> Stream.of(pair.getA(), pair.getB()))
         .filter(primaryOrBackup -> primaryOrBackup != null && !primaryOrBackup.isEmpty())
         .count();
      return count;
   }

   public static boolean validateNetworkTopology(String networkTopologyJson,
                                                  Predicate<Map<String, Pair<String, String>>> checkTopology) {
      final Map<String, Pair<String, String>> networkTopology = decodeNetworkTopologyJson(networkTopologyJson);
      return checkTopology.test(networkTopology);
   }

   public static String backupOf(String nodeID, Map<String, Pair<String, String>> networkTopology) {
      return networkTopology.get(nodeID).getB();
   }

   public static String primaryOf(String nodeID, Map<String, Pair<String, String>> networkTopology) {
      return networkTopology.get(nodeID).getA();
   }

   public static Predicate<Map<String, Pair<String, String>>> containsExactNodeIds(String... nodeID) {
      Objects.requireNonNull(nodeID);
      return topology -> topology.size() == nodeID.length && Stream.of(nodeID).allMatch(topology::containsKey);
   }

   public static Predicate<Map<String, Pair<String, String>>> withMembers(int count) {
      return topology -> countMembers(topology) == count;
   }

   public static Predicate<Map<String, Pair<String, String>>> withNodes(int count) {
      return topology -> countNodes(topology) == count;
   }

   public static Predicate<Map<String, Pair<String, String>>> withBackup(String nodeId, Predicate<String> compare) {
      return topology -> compare.test(backupOf(nodeId, topology));
   }

   public static Predicate<Map<String, Pair<String, String>>> withPrimary(String nodeId, Predicate<String> compare) {
      return topology -> compare.test(primaryOf(nodeId, topology));
   }
}
