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
package org.apache.activemq.artemis.api.core.management;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * Helper class used to build resource names used by management messages.
 * <p>
 * Resource's name is build by appending its <em>name</em> to its corresponding type. For example, the resource name of
 * the "foo" queue is {@code QUEUE + "foo"}.
 */
public final class ResourceNames {
   public static final String BROKER = "broker";

   public static final String MANAGEMENT_SECURITY = "managementsecurity";

   public static final String QUEUE = "queue.";

   public static final String ADDRESS = "address.";

   public static final String BROKER_CONNECTION = "brokerconnection.";

   public static final String REMOTE_BROKER_CONNECTION = "remotebrokerconnection.";

   public static final String BRIDGE = "bridge.";

   public static final String ACCEPTOR = "acceptor.";

   public static final String DIVERT = "divert.";

   public static final String CORE_CLUSTER_CONNECTION = "clusterconnection.";

   public static final String BROADCAST_GROUP = "broadcastgroup.";

   public static final String CONNECTION_ROUTER = "connectionrouter.";

   public static final String RETROACTIVE_SUFFIX = "retro";

   public static SimpleString getRetroactiveResourceQueueName(String prefix, String delimiter, SimpleString address, RoutingType routingType) {
      return getRetroactiveResourceName(prefix, delimiter, address, trimLastCharacter(QUEUE).concat(delimiter).concat(routingType.toString().toLowerCase()));
   }

   public static SimpleString getRetroactiveResourceAddressName(String prefix, String delimiter, SimpleString address) {
      return getRetroactiveResourceName(prefix, delimiter, address, trimLastCharacter(ADDRESS));
   }

   public static SimpleString getRetroactiveResourceDivertName(String prefix, String delimiter, SimpleString address) {
      return getRetroactiveResourceName(prefix, delimiter, address, trimLastCharacter(DIVERT));
   }

   private static SimpleString getRetroactiveResourceName(String prefix, String delimiter, SimpleString address, String resourceType) {
      return SimpleString.of(prefix.concat(address.toString()).concat(delimiter).concat(resourceType).concat(delimiter).concat(RETROACTIVE_SUFFIX));
   }

   public static boolean isRetroactiveResource(String prefix, SimpleString address) {
      return address.toString().startsWith(prefix) && address.toString().endsWith(RETROACTIVE_SUFFIX);
   }

   public static String decomposeRetroactiveResourceAddressName(String prefix, String delimiter, String address) {
      return address.substring(prefix.length(), address.length() - (delimiter.length() + ADDRESS.length() + RETROACTIVE_SUFFIX.length()));
   }

   private static String trimLastCharacter(String toTrim) {
      return toTrim.substring(0, toTrim.length() - 1);
   }
}
