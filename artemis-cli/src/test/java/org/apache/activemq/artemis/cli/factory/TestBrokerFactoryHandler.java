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
package org.apache.activemq.artemis.cli.factory;

import org.apache.activemq.artemis.dto.BrokerDTO;

import java.net.URI;

public class TestBrokerFactoryHandler implements BrokerFactoryHandler {

   private static URI brokerURI;
   private static String artemisHome;
   private static String artemisInstance;
   private static URI artemisURIInstance;
   private static BrokerDTO broker;

   public static URI getBrokerURI() {
      return brokerURI;
   }

   public static void setBrokerURI(URI brokerURI) {
      TestBrokerFactoryHandler.brokerURI = brokerURI;
   }

   public static String getArtemisHome() {
      return artemisHome;
   }

   public static void setArtemisHome(String artemisHome) {
      TestBrokerFactoryHandler.artemisHome = artemisHome;
   }

   public static String getArtemisInstance() {
      return artemisInstance;
   }

   public static void setArtemisInstance(String artemisInstance) {
      TestBrokerFactoryHandler.artemisInstance = artemisInstance;
   }

   public static URI getArtemisURIInstance() {
      return artemisURIInstance;
   }

   public static void setArtemisURIInstance(URI artemisURIInstance) {
      TestBrokerFactoryHandler.artemisURIInstance = artemisURIInstance;
   }

   public static BrokerDTO getBroker() {
      return broker;
   }

   public static void setBroker(BrokerDTO broker) {
      TestBrokerFactoryHandler.broker = broker;
   }

   public static void clear() {
      brokerURI = null;
      artemisHome = null;
      artemisInstance = null;
      artemisURIInstance = null;
      broker = null;
   }

   @Override
   public BrokerDTO createBroker(URI brokerURI, String artemisHome, String artemisInstance, URI artemisURIInstance) throws Exception {
      TestBrokerFactoryHandler.brokerURI = brokerURI;
      TestBrokerFactoryHandler.artemisHome = artemisHome;
      TestBrokerFactoryHandler.artemisInstance = artemisInstance;
      TestBrokerFactoryHandler.artemisURIInstance = artemisURIInstance;
      return broker;
   }
}
