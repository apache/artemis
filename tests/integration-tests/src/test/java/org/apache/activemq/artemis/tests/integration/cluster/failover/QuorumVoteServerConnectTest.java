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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVoteServerConnect;
import org.apache.activemq.artemis.core.server.cluster.quorum.ServerConnectVote;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class QuorumVoteServerConnectTest extends ActiveMQTestBase {

   private final int size;
   private final int trueVotes;

   @Parameters(name = "size={0} trueVotes={1}")
   public static Collection primeNumbers() {
      return Arrays.asList(new Object[][]{{1, 0}, {2, 1}, {3, 2}, {4, 3}, {5, 3}, {6, 4}, {7, 4}, {8, 5}, {9, 5}, {10, 6}});
   }

   public QuorumVoteServerConnectTest(int size, int trueVotes) {
      this.size = size;
      this.trueVotes = trueVotes;
   }

   @TestTemplate
   public void testVoteOnRequestToStay() {
      assumeTrue(trueVotes > 0);
      assumeTrue(size > trueVotes);
      final String connector = "primary";
      final String backupConnector = "backup";
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo", true, connector);
      quorum.vote(new ServerConnectVote("foo", true, backupConnector));
      assertFalse(quorum.getDecision());
      for (int i = 0; i < trueVotes - 1; i++) {
         quorum.vote(new ServerConnectVote("foo", true, connector));
         assertFalse(quorum.getDecision());
      }
      quorum.vote(new ServerConnectVote("foo", true, connector));
      assertTrue(quorum.getDecision());
   }

   @TestTemplate
   public void testAllVoteCastFreezeNotRequestToStayDecision() {
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo");
      assertFalse(quorum.isRequestToStayActive());
      final boolean decisionBeforeVoteCompleted = quorum.getDecision();
      quorum.allVotesCast(null);
      for (int i = 0; i < trueVotes; i++) {
         quorum.vote(new ServerConnectVote("foo", true, null));
      }
      assertEquals(decisionBeforeVoteCompleted, quorum.getDecision());
   }

   @TestTemplate
   public void testAllVoteCastFreezeRequestToStayDecision() {
      final String connector = "primary";
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo", true, connector);
      assertTrue(quorum.isRequestToStayActive());
      final boolean decisionBeforeVoteCompleted = quorum.getDecision();
      quorum.allVotesCast(null);
      for (int i = 0; i < trueVotes; i++) {
         quorum.vote(new ServerConnectVote("foo", true, connector));
      }
      assertEquals(decisionBeforeVoteCompleted, quorum.getDecision());
   }

   @TestTemplate
   public void testAllVoteCastUnblockAwait() throws InterruptedException {
      assumeTrue(trueVotes > 0);
      assumeTrue(size > trueVotes);
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo");
      assertFalse(quorum.getDecision());
      CountDownLatch taskStarted = new CountDownLatch(1);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      runAfter(executor::shutdownNow);
      final Future<InterruptedException> waitingTaskResult = executor.submit(() -> {
         taskStarted.countDown();
         try {
            quorum.await(1, TimeUnit.MINUTES);
            return null;
         } catch (InterruptedException e) {
            // preserve interrupt state
            Thread.currentThread().interrupt();
            return e;
         }
      });
      // realistic expectation of the max time to start a Thread
      assertTrue(taskStarted.await(10, TimeUnit.SECONDS));
      assertFalse(waitingTaskResult.isDone());
      quorum.allVotesCast(null);
      try {
         assertNull(waitingTaskResult.get(5, TimeUnit.SECONDS));
      } catch (TimeoutException ex) {
         fail("allVoteCast hasn't unblocked the waiting task");
      } catch (ExecutionException ex) {
         fail("This shouldn't really happen: the wait task shouldn't throw any exception: " + ex);
      }
      assertTrue(waitingTaskResult.isDone());
      assertFalse(quorum.getDecision());
   }

   @TestTemplate
   public void testRequestToStayQuorumUnblockAwait() throws InterruptedException {
      assumeTrue(trueVotes > 0);
      assumeTrue(size > trueVotes);
      final String connector = "primary";
      final String backupConnector = "backup";
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo", true, connector);
      assertFalse(quorum.getDecision());
      CountDownLatch taskStarted = new CountDownLatch(1);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      runAfter(executor::shutdownNow);
      final Future<InterruptedException> waitingTaskResult = executor.submit(() -> {
         taskStarted.countDown();
         try {
            quorum.await(1, TimeUnit.MINUTES);
            return null;
         } catch (InterruptedException e) {
            // preserve interrupt state
            Thread.currentThread().interrupt();
            return e;
         }
      });
      // realistic expectation of the max time to start a Thread
      assertTrue(taskStarted.await(10, TimeUnit.SECONDS));
      quorum.vote(new ServerConnectVote("foo", true, backupConnector));
      assertFalse(waitingTaskResult.isDone());
      assertFalse(quorum.getDecision());
      for (int i = 0; i < trueVotes - 1; i++) {
         quorum.vote(new ServerConnectVote("foo", true, connector));
         assertFalse(waitingTaskResult.isDone());
         assertFalse(quorum.getDecision());
      }
      quorum.vote(new ServerConnectVote("foo", true, connector));
      assertTrue(quorum.getDecision());
      try {
         assertNull(waitingTaskResult.get(5, TimeUnit.SECONDS));
      } catch (TimeoutException ex) {
         fail("allVoteCast hasn't unblocked the waiting task");
      } catch (ExecutionException ex) {
         fail("This shouldn't really happen: the wait task shouldn't throw any exception: " + ex);
      }
      assertTrue(waitingTaskResult.isDone());
      assertTrue(quorum.getDecision());
   }

   @TestTemplate
   public void testNotRequestToStayQuorumUnblockAwait() throws InterruptedException {
      assumeTrue(trueVotes > 0);
      assumeTrue(size > trueVotes);
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo");
      assertFalse(quorum.getDecision());
      CountDownLatch taskStarted = new CountDownLatch(1);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      runAfter(executor::shutdownNow);
      final Future<InterruptedException> waitingTaskResult = executor.submit(() -> {
         taskStarted.countDown();
         try {
            quorum.await(1, TimeUnit.MINUTES);
            return null;
         } catch (InterruptedException e) {
            // preserve interrupt state
            Thread.currentThread().interrupt();
            return e;
         }
      });
      // realistic expectation of the max time to start a Thread
      assertTrue(taskStarted.await(10, TimeUnit.SECONDS));
      quorum.vote(new ServerConnectVote("foo", false, null));
      assertFalse(waitingTaskResult.isDone());
      assertFalse(quorum.getDecision());
      for (int i = 0; i < trueVotes - 1; i++) {
         quorum.vote(new ServerConnectVote("foo", true, null));
         assertFalse(waitingTaskResult.isDone());
         assertFalse(quorum.getDecision());
      }
      quorum.vote(new ServerConnectVote("foo", true, null));
      assertTrue(quorum.getDecision());
      try {
         assertNull(waitingTaskResult.get(5, TimeUnit.SECONDS));
      } catch (TimeoutException ex) {
         fail("allVoteCast hasn't unblocked the waiting task");
      } catch (ExecutionException ex) {
         fail("This shouldn't really happen: the wait task shouldn't throw any exception: " + ex);
      }
      assertTrue(waitingTaskResult.isDone());
      assertTrue(quorum.getDecision());
   }

   @TestTemplate
   public void testSuccessfulVote() {
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo");
      for (int i = 0; i < trueVotes - 1; i++) {
         quorum.vote(new ServerConnectVote("foo", true, null));
      }

      if (size > 1) {
         assertFalse(quorum.getDecision());
      }
      quorum = new QuorumVoteServerConnect(size, "foo");
      for (int i = 0; i < trueVotes; i++) {
         quorum.vote(new ServerConnectVote("foo", true, null));
      }
      assertTrue(quorum.getDecision());
   }

   @TestTemplate
   public void testUnSuccessfulVote() {
      QuorumVoteServerConnect quorum = new QuorumVoteServerConnect(size, "foo");
      for (int i = 0; i < trueVotes - 1; i++) {
         quorum.vote(new ServerConnectVote("foo", true, null));
      }

      if (size > 1) {
         assertFalse(quorum.getDecision());
      }
      quorum = new QuorumVoteServerConnect(size, "foo");
      for (int i = 0; i < trueVotes - 1; i++) {
         quorum.vote(new ServerConnectVote("foo", true, null));
      }
      if (size == 1) {
         assertTrue(quorum.getDecision());
      } else {
         assertFalse(quorum.getDecision());
      }
   }
}
