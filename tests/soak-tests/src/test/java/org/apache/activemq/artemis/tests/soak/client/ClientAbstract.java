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
package org.apache.activemq.artemis.tests.soak.client;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * <b>WARNING</b>: This is not a sample on how you should handle XA. You are supposed to use a TransactionManager. This
 * class is doing the job of a TransactionManager that fits for the purpose of this test only, however there are many
 * more pitfalls to deal with Transactions.
 * <p>
 * This is just to stress and soak test Transactions with Apache Artemis.
 * <p>
 * And this is dealing with XA directly for the purpose testing only.
 */
public abstract class ClientAbstract extends Thread {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected ClientSession session;

   protected final ClientSessionFactory sf;

   protected Xid activeXid;

   protected volatile boolean running = true;

   protected int errors = 0;

   /**
    * A commit was called case we don't find the Xid, means it was accepted
    */
   protected volatile boolean pendingCommit = false;

   public ClientAbstract(ClientSessionFactory sf) {
      this.sf = sf;
   }

   public ClientSession getConnection() {
      return session;
   }

   public int getErrorsCount() {
      return errors;
   }

   public final void connect() {
      while (running) {
         try {
            disconnect();

            session = sf.createXASession();

            if (activeXid != null) {
               synchronized (ClientAbstract.class) {
                  Xid[] xids = session.recover(XAResource.TMSTARTRSCAN);
                  boolean found = false;
                  for (Xid recXid : xids) {
                     if (recXid.equals(activeXid)) {
                        // System.out.println("Calling commit after a prepare on " + this);
                        found = true;
                        callCommit();
                     }
                  }

                  if (!found) {
                     if (pendingCommit) {
                        onCommit();
                     } else {
                        onRollback();
                     }

                     activeXid = null;
                     pendingCommit = false;
                  }
               }
            }

            connectClients();

            break;
         } catch (Exception e) {
            ClientAbstract.logger.warn("Can't connect to server, retrying");
            disconnect();
            try {
               Thread.sleep(1000);
            } catch (InterruptedException ignored) {
               // if an interruption was sent, we will respect it and leave the loop
               break;
            }
         }
      }
   }

   @Override
   public void run() {
      connect();
   }

   protected void callCommit() throws Exception {
      pendingCommit = true;
      session.commit(activeXid, false);
      pendingCommit = false;
      activeXid = null;
      onCommit();
   }

   protected void callPrepare() throws Exception {
      session.prepare(activeXid);
   }

   public void beginTX() throws Exception {
      activeXid = newXID();

      session.start(activeXid, XAResource.TMNOFLAGS);
   }

   public void endTX() throws Exception {
      session.end(activeXid, XAResource.TMSUCCESS);
      callPrepare();
      callCommit();
   }

   public void setRunning(final boolean running) {
      this.running = running;
   }

   private XidImpl newXID() {
      return new XidImpl("tst".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
   }

   protected abstract void connectClients() throws Exception;

   protected abstract void onCommit();

   protected abstract void onRollback();

   public void disconnect() {
      try {
         if (session != null) {
            session.close();
         }
      } catch (Exception ignored) {
         ignored.printStackTrace();
      }

      session = null;
   }

}
