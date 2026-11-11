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
package org.apache.activemq.transport.failover;

import java.io.IOException;
import java.net.URI;

import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore
public class FailoverTransportBackupsTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(FailoverTransportBackupsTest.class);

   protected Transport transport;
   protected FailoverTransport failoverTransport;
   private int transportInterruptions;
   private int transportResumptions;

   EmbeddedJMS[] servers = new EmbeddedJMS[3];

   @Before
   public void setUp() throws Exception {
      setUpClusterServers(servers);

      // Reset stats
      transportInterruptions = 0;
      transportResumptions = 0;
   }

   @After
   public void tearDown() throws Exception {
      if (transport != null) {
         transport.stop();
      }
      shutDownClusterServers(servers);
   }

   @Test
   public void testBackupsAreCreated() throws Exception {
      this.transport = createTransport(2);
      assertNotNull(failoverTransport);
      assertTrue(failoverTransport.isBackup());
      assertEquals(2, failoverTransport.getBackupPoolSize());

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 2;
      }));
   }

   @Test
   public void testFailoverToBackups() throws Exception {
      this.transport = createTransport(2);
      assertNotNull(failoverTransport);
      assertTrue(failoverTransport.isBackup());
      assertEquals(2, failoverTransport.getBackupPoolSize());

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 2;
      }));

      servers[0].stop();

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 1;
      }));

      assertTrue("Incorrect number of Transport interruptions", transportInterruptions >= 1);
      assertTrue("Incorrect number of Transport resumptions", transportResumptions >= 1);

      servers[1].stop();

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 0;
      }));

      assertTrue("Incorrect number of Transport interruptions", transportInterruptions >= 2);
      assertTrue("Incorrect number of Transport resumptions", transportResumptions >= 2);
   }

   @Test
   public void testBackupsRefilled() throws Exception {
      this.transport = createTransport(1);
      assertNotNull(failoverTransport);
      assertTrue(failoverTransport.isBackup());
      assertEquals(1, failoverTransport.getBackupPoolSize());

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 1;
      }));

      servers[0].stop();

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 1;
      }));

      servers[1].stop();

      assertTrue("Timed out waiting for Backups to connect.", Wait.waitFor(() -> {
         LOG.debug("Current Backup Count = " + failoverTransport.getCurrentBackups());
         return failoverTransport.getCurrentBackups() == 0;
      }));
   }

   protected Transport createTransport(int backups) throws Exception {
      String connectionUri = "failover://(" +
         newURI(0) + "," +
         newURI(1) + "," +
         newURI(2) + ")";

      if (backups > 0) {
         connectionUri += "?randomize=false&backup=true&backupPoolSize=" + backups;
      }

      Transport transport = TransportFactory.connect(new URI(connectionUri));
      transport.setTransportListener(new TransportListener() {

         @Override
         public void onCommand(Object command) {
            LOG.debug("Test Transport Listener received Command: " + command);
         }

         @Override
         public void onException(IOException error) {
            LOG.debug("Test Transport Listener received Exception: " + error);
         }

         @Override
         public void transportInterupted() {
            transportInterruptions++;
            LOG.debug("Test Transport Listener records transport Interrupted: " + transportInterruptions);
         }

         @Override
         public void transportResumed() {
            transportResumptions++;
            LOG.debug("Test Transport Listener records transport Resumed: " + transportResumptions);
         }
      });
      transport.start();

      this.failoverTransport = transport.narrow(FailoverTransport.class);

      return transport;
   }
}
