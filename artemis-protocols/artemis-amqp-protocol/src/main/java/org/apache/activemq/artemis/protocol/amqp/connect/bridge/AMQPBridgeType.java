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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

/**
 * Enumeration that defines the type of AMQP bridging a given policy manager implements
 */
public enum AMQPBridgeType {

   /**
    * Indicates a resource that is handling bridging from a remote address
    */
   BRIDGE_FROM_ADDRESS("bridge-from-address"),

   /**
    * Indicates a resource that is handling bridging to a remote address
    */
   BRIDGE_TO_ADDRESS("bridge-to-address"),

   /**
    * Indicates a resource that is handling bridging from a remote queue
    */
   BRIDGE_FROM_QUEUE("bridge-from-queue"),

   /**
    * Indicates a resource that is handling bridging to a remote queue
    */
   BRIDGE_TO_QUEUE("bridge-to-queue");

   private final String typeName;

   AMQPBridgeType(String typeName) {
      this.typeName = typeName;
   }

   @Override
   public String toString() {
      return typeName;
   }
}
