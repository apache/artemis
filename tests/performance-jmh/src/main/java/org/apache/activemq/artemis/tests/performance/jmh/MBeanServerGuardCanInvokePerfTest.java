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
package org.apache.activemq.artemis.tests.performance.jmh;

import org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerGuard;
import org.apache.activemq.artemis.core.server.management.JMXAccessControlList;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 8, time = 1)
@BenchmarkMode(Mode.Throughput)
public class MBeanServerGuardCanInvokePerfTest {

   @Param({"1000"})
   int objectNameCount;

   @Param({"defaultGuard", "guard"})
   String guardType;

   private ArtemisMBeanServerGuard activeGuard;
   private ArtemisMBeanServerGuard defaultGuard;
   private ArtemisMBeanServerGuard guard;
   private String[] objectNames;
   private String[] operationNames;
   private String[] operationNamesWithParams;
   private String[] queueObjectNames;
   private String allowListedObjectName;
   private Subject subjectWithViewRole;
   private Subject subjectWithAmqRole;
   private Subject subjectWithNoRole;

   private static final int QUEUE_COUNT = 1000;
   private static final int ACL_ROLE_COUNT = 1000;
   private static final int USER_ROLE_COUNT = 10;

   private static final String[] OPERATIONS = {
      "sendMessage(java.lang.String)",
      "sendMessage(java.lang.String,java.lang.String)",
      "removeMessage(long)",
      "listMessages(java.lang.String)",
      "setAttribute(java.lang.String,java.lang.Object)"
   };

   @Setup
   public void init() {
      defaultGuard = new ArtemisMBeanServerGuard();
      guard = new ArtemisMBeanServerGuard();

      // Default ACL: allowlist with hawtio domain, no roles
      JMXAccessControlList defaultAcl = JMXAccessControlList.createDefaultList();
      defaultGuard.setJMXAccessControlList(defaultAcl);

      // Generated ACL: default + 1000 roles, each authorised for all operations on the artemis domain
      JMXAccessControlList acl = JMXAccessControlList.createDefaultList();
      for (int i = 0; i < ACL_ROLE_COUNT; i++) {
         defaultAcl.addToRoleAccess("org.apache.activemq.artemis", null, "*", "role-" + i);
      }
      guard.setJMXAccessControlList(acl);

      // 2. Select which guard to assign to the active target loop
      if ("defaultGuard".equals(guardType)) {
         activeGuard = defaultGuard;
      } else {
         activeGuard = guard;
      }

      // Create test object names from different domains
      objectNames = new String[objectNameCount];
      for (int i = 0; i < objectNameCount; i++) {
         if (i % 3 == 0) {
            objectNames[i] = "org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=\"address." + i + "\"";
         } else if (i % 3 == 1) {
            objectNames[i] = "org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,subcomponent=queues,routing-type=ANYCAST,name=\"queue-" + i + "\"";
         } else {
            objectNames[i] = "java.lang:type=Memory,name=HeapMemoryUsage-" + i;
         }
      }

      // 1000 queue object names, fixed order, never randomized
      queueObjectNames = new String[QUEUE_COUNT];
      for (int i = 0; i < QUEUE_COUNT; i++) {
         queueObjectNames[i] =
            "org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses," +
            "subcomponent=queues,routing-type=ANYCAST,name=\"queue-" + i + "\"";
      }

      // Allowlisted object name (hawtio domain is in the default allowlist)
      allowListedObjectName = "hawtio:type=security,name=RBACRegistry";

      // Common operation names
      operationNames = new String[]{
         "getMessageCount",
         "listMessages",
         "sendMessage",
         "removeMessage",
         "getConsumerCount",
         "listConsumers",
         "getAttribute",
         "setAttribute",
         "invoke"
      };

      // Operation names with parameter lists
      operationNamesWithParams = new String[]{
         "sendMessage(java.lang.String)",
         "sendMessage(java.lang.String,java.lang.String)",
         "removeMessage(long)",
         "listMessages(java.lang.String)",
         "setAttribute(java.lang.String,java.lang.Object)"
      };

      // view: 9 non-matching groups + role-999 last — worst-case disjoint() scan
      subjectWithViewRole = new Subject();
      for (int i = 0; i < USER_ROLE_COUNT - 1; i++) {
         subjectWithViewRole.getPrincipals().add(new RolePrincipal("user-group-" + i));
      }
      subjectWithViewRole.getPrincipals().add(new RolePrincipal("role-999"));

      // amq: role-0 first — best-case match, hits immediately
      subjectWithAmqRole = new Subject();
      subjectWithAmqRole.getPrincipals().add(new RolePrincipal("role-0"));
      for (int i = 1; i < USER_ROLE_COUNT; i++) {
         subjectWithAmqRole.getPrincipals().add(new RolePrincipal("user-group-" + i));
      }

      // none: all 10 principals are outside the ACL — full miss every time
      subjectWithNoRole = new Subject();
      for (int i = 0; i < USER_ROLE_COUNT; i++) {
         subjectWithNoRole.getPrincipals().add(new RolePrincipal("no-match-" + i));
      }

   }

   @Benchmark
   public boolean testCanInvokeAllowListed() {
      return activeGuard.canInvoke(allowListedObjectName, "anyOperation");
   }

   @Benchmark
   public boolean testCanInvokeWithViewRole() {
      return Subject.doAs(subjectWithViewRole, (PrivilegedAction<Boolean>) () -> {
         int idx = ThreadLocalRandom.current().nextInt(objectNames.length);
         int opIdx = ThreadLocalRandom.current().nextInt(operationNames.length);
         return activeGuard.canInvoke(objectNames[idx], operationNames[opIdx]);
      });
   }

   @Benchmark
   public boolean testCanInvokeWithAmqRole() {
      return Subject.doAs(subjectWithAmqRole, (PrivilegedAction<Boolean>) () -> {
         int idx = ThreadLocalRandom.current().nextInt(objectNames.length);
         int opIdx = ThreadLocalRandom.current().nextInt(operationNames.length);
         return activeGuard.canInvoke(objectNames[idx], operationNames[opIdx]);
      });
   }

   @Benchmark
   public boolean testCanInvokeWithNoRole() {
      return Subject.doAs(subjectWithNoRole, (PrivilegedAction<Boolean>) () -> {
         int idx = ThreadLocalRandom.current().nextInt(objectNames.length);
         int opIdx = ThreadLocalRandom.current().nextInt(operationNames.length);
         return activeGuard.canInvoke(objectNames[idx], operationNames[opIdx]);
      });
   }

   @Benchmark
   public boolean testCanInvokeWithParameterStripping() {
      return Subject.doAs(subjectWithViewRole, (PrivilegedAction<Boolean>) () -> {
         int idx = ThreadLocalRandom.current().nextInt(objectNames.length);
         int opIdx = ThreadLocalRandom.current().nextInt(operationNamesWithParams.length);
         return activeGuard.canInvoke(objectNames[idx], operationNamesWithParams[opIdx]);
      });
   }

   @Benchmark
   public boolean testCanInvokeNullOperation() {
      return activeGuard.canInvoke(objectNames[0], null);
   }

   @Benchmark
   public boolean testCanInvokeInvalidObjectName() {
      return activeGuard.canInvoke("invalid:object:name:with:too:many:colons", "operation");
   }

   @Benchmark
   public boolean testCanInvokeMixedScenarios() {
      return Subject.doAs(subjectWithViewRole, (PrivilegedAction<Boolean>) () -> {
         int scenario = ThreadLocalRandom.current().nextInt(4);
         switch (scenario) {
            case 0:
               return activeGuard.canInvoke(allowListedObjectName, "operation");
            case 1:
               int idx = ThreadLocalRandom.current().nextInt(objectNames.length);
               int opIdx = ThreadLocalRandom.current().nextInt(operationNames.length);
               return activeGuard.canInvoke(objectNames[idx], operationNames[opIdx]);
            case 2:
               idx = ThreadLocalRandom.current().nextInt(objectNames.length);
               opIdx = ThreadLocalRandom.current().nextInt(operationNamesWithParams.length);
               return activeGuard.canInvoke(objectNames[idx], operationNamesWithParams[opIdx]);
            default:
               return activeGuard.canInvoke(objectNames[0], null);
         }
      });
   }

   @Benchmark
   public void testConsolePageLoadViewRole() {
      Subject.doAs(subjectWithViewRole, (PrivilegedAction<Void>) () -> {
         for (int i = 0; i < QUEUE_COUNT; i++) {
            for (String op : OPERATIONS) {
               activeGuard.canInvoke(queueObjectNames[i], op);
            }
         }
         return null;
      });
   }

   @Benchmark
   public void testConsolePageLoadAmqRole() {
      Subject.doAs(subjectWithAmqRole, (PrivilegedAction<Void>) () -> {
         for (int i = 0; i < QUEUE_COUNT; i++) {
            for (String op : OPERATIONS) {
               activeGuard.canInvoke(queueObjectNames[i], op);
            }
         }
         return null;
      });
   }

   @Benchmark
   public void testConsolePageLoadNoRole() {
      Subject.doAs(subjectWithNoRole, (PrivilegedAction<Void>) () -> {
         for (int i = 0; i < QUEUE_COUNT; i++) {
            for (String op : OPERATIONS) {
               activeGuard.canInvoke(queueObjectNames[i], op);
            }
         }
         return null;
      });
   }

}