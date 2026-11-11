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
package org.apache.activemq.artemis.tests.integration.critical;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.Test;

public class CriticalCrashTest extends SpawnedTestBase {

   @Test
   public void testCrash() throws Exception {

      // Passing these arguments should change the criticalAnalyzer parameters
      Process process = SpawnedVMSupport.spawnVM(CriticalCrashTest.class.getName(), new String[]{"-Dbrokerconfig.criticalAnalyzer=true", "-Dbrokerconfig.criticalAnalyzerCheckPeriod=100", "-Dbrokerconfig.criticalAnalyzerTimeout=500", "-Dbrokerconfig.criticalAnalyzerPolicy=HALT"}, getTestDir());

      assertEquals(70, process.waitFor());
      deleteDirectory(new File("./target/server"));
   }

   public static void main(String[] arg) {
      try {
         CriticalCrashTest test = new CriticalCrashTest();
         test.setTestDir(arg[0]);

         test.runSimple();
      } catch (Exception e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }

   public void runSimple() throws Exception {
      deleteDirectory(new File("./target/server"));
      ActiveMQServer server = createServer("./target/server");

      try {
         server.start();

         ConnectionFactory factory = new ActiveMQConnectionFactory();
         Connection connection = factory.createConnection();

         Session session = connection.createSession();

         MessageProducer producer = session.createProducer(session.createQueue("queue"));

         for (int i = 0; i < 500; i++) {
            producer.send(session.createTextMessage("text"));
         }


      } finally {
         server.stop();

      }

   }

   ActiveMQServer createServer(String folder) throws Exception {
      final AtomicBoolean blocked = new AtomicBoolean(false);
      Configuration conf = createConfig(folder);
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      conf.setPersistenceEnabled(true);

      ActiveMQServer server = new ActiveMQServerImpl(conf, securityManager) {

         @Override
         protected StorageManager createStorageManager() {

            JournalStorageManager storageManager = new JournalStorageManager(conf, getCriticalAnalyzer(), executorFactory, scheduledPool, ioExecutorFactory, ioCriticalErrorListener) {
               @Override
               public ArtemisCloseable closeableReadLock() {
                  ArtemisCloseable measure = measureCritical(CRITICAL_STORE);
                  storageManagerLock.readLock().lock();

                  if (blocked.get()) {
                     while (true) {
                        try {
                           Thread.sleep(1000);
                        } catch (Throwable ignored) {
                        }
                     }
                  }

                  return () -> {
                     storageManagerLock.readLock().unlock();
                     measure.close();
                  };
               }


               @Override
               public void storeMessage(Message message) throws Exception {
                  super.storeMessage(message);
                  blocked.set(true);
               }
            };

            this.getCriticalAnalyzer().add(storageManager);

            return storageManager;
         }

      };

      return server;
   }

   Configuration createConfig(String folder) throws Exception {

      Configuration configuration = createDefaultConfig(true);
      configuration.setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(getDefaultJournalType()).setJournalDirectory(folder + "/journal").setBindingsDirectory(folder + "/bindings").setPagingDirectory(folder + "/paging").
         setLargeMessagesDirectory(folder + "/largemessage").setJournalCompactMinFiles(0).setJournalCompactPercentage(0).setClusterPassword(CLUSTER_PASSWORD).setJournalDatasync(false);
      configuration.setSecurityEnabled(false);
      configuration.setPersistenceEnabled(true);

      return configuration;
   }

}
