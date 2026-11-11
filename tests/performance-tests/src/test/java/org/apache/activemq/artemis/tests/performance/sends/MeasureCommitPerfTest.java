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
package org.apache.activemq.artemis.tests.performance.sends;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

import org.junit.jupiter.api.Test;

public class MeasureCommitPerfTest extends AbstractSendReceivePerfTest {

   @Override
   protected void consumeMessages(Connection c, String qName) throws Exception {
   }

   // This will by default send non persistent messages
   @Override
   protected void sendMessages(Connection c, String qName) throws JMSException {
      Session s = c.createSession(true, Session.SESSION_TRANSACTED);

      long timeout = System.currentTimeMillis() + 30 * 1000;

      long startMeasure = System.currentTimeMillis() + 5000;
      long start = 0;
      long commits = 0;
      while (timeout > System.currentTimeMillis()) {

         if (start == 0 && System.currentTimeMillis() > startMeasure) {
            System.out.println("heat up");
            start = System.currentTimeMillis();
            commits = 0;
         }

         s.commit();
         commits++;
         if (start > 0 && commits % 1000 == 0) {
            printCommitsSecond(start, commits);
         }
      }
      printCommitsSecond(start, commits);

      s.close();
   }

   protected void printCommitsSecond(final long start, final double commits) {

      long end = System.currentTimeMillis();
      double elapsed = ((double) end - (double) start) / 1000f;

      double commitsPerSecond = commits / elapsed;

      System.out.println("end = " + end + ", start=" + start + ", numberOfMessages=" + commits + ", elapsed=" + elapsed + " msgs/sec= " + commitsPerSecond);

   }

   @Test
   public void testSendReceive() throws Exception {
      super.doSendReceiveTestImpl();
   }
}
