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

package org.apache.activemq.artemis.tests.e2e.ha.replication.lockmanager;

import org.apache.activemq.artemis.tests.e2e.common.ContainerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class LockManagerTestBase {

   protected static ContainerService service;
   protected static Object network;

   protected static Object zookeeper1;
   protected static Object zookeeper2;
   protected static Object zookeeper3;
   protected static final String ZOOKEEPER_ADMIN_SERVER_START_LOG = ".*Started AdminServer on address.*\\n";

   @BeforeAll
   public static void init() {
      service = ContainerService.getService();
      network = service.newNetwork();

      // Start zookeepers
      String zkServName1 = "zk1";
      String zkServName2 = "zk2";
      String zkServName3 = "zk3";
      String ZOOKEEPER_SERVERS = "server.1=" + zkServName1 + ":2888:3888;2181 " + "server.2=" + zkServName2 + ":2888:3888;2181 " + "server.3=" + zkServName3 + ":2888:3888;2181";
      zookeeper1 = service.newZookeeperImage();
      zookeeper2 = service.newZookeeperImage();
      zookeeper3 = service.newZookeeperImage();
      service.setNetwork(zookeeper1, network);
      service.setNetwork(zookeeper2, network);
      service.setNetwork(zookeeper3, network);
      service.exposeHosts(zookeeper1, "zk1");
      service.exposeHosts(zookeeper2, "zk2");
      service.exposeHosts(zookeeper3, "zk3");
      service.withEnvVar(zookeeper1, "ZOO_MY_ID", "1");
      service.withEnvVar(zookeeper2, "ZOO_MY_ID", "2");
      service.withEnvVar(zookeeper3, "ZOO_MY_ID", "3");
      service.withEnvVar(zookeeper1, "ZOO_STANDALONE_ENABLED", "false");
      service.withEnvVar(zookeeper2, "ZOO_STANDALONE_ENABLED", "false");
      service.withEnvVar(zookeeper3, "ZOO_STANDALONE_ENABLED", "false");
      service.withEnvVar(zookeeper1, "ZOO_SERVERS", ZOOKEEPER_SERVERS);
      service.withEnvVar(zookeeper2, "ZOO_SERVERS", ZOOKEEPER_SERVERS);
      service.withEnvVar(zookeeper3, "ZOO_SERVERS", ZOOKEEPER_SERVERS);

      service.logWait(zookeeper1, ZOOKEEPER_ADMIN_SERVER_START_LOG);
      service.logWait(zookeeper2, ZOOKEEPER_ADMIN_SERVER_START_LOG);
      service.logWait(zookeeper3, ZOOKEEPER_ADMIN_SERVER_START_LOG);

      service.start(zookeeper1);
      service.start(zookeeper2);
      service.start(zookeeper3);
   }

   @AfterAll
   public static void shutdown() {
      service.stop(zookeeper3);
      service.stop(zookeeper2);
      service.stop(zookeeper1);
   }
}
