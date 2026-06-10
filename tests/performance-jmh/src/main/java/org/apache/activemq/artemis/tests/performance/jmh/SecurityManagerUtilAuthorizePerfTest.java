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

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.utils.SecurityManagerUtil;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 4, time = 1)
@BenchmarkMode(Mode.Throughput)
public class SecurityManagerUtilAuthorizePerfTest {

   /* Total number of roles in the resource ACL */
   @Param({"8", "1024"})
   int roleCount;

   /* Number of roles assigned to each subject */
   @Param({"8", "64"})
   int subjectRoleCount;

   private Set<Role> roles;

   private Subject adminSubject;    // all permissions
   private Subject producerSubject; // send + consume
   private Subject monitorSubject;  // manage + browse
   private Subject guestSubject;    // no permissions

   private Subject[] subjects;

   private static final CheckType SEND = CheckType.SEND;
   private static final CheckType CONSUME = CheckType.CONSUME;
   private static final CheckType MANAGE = CheckType.MANAGE;
   private static final CheckType[] CHECK_TYPES = {SEND, CONSUME, MANAGE};

   @Setup
   public void init() {
      List<Role> roleList = new ArrayList<>(roleCount);

      for (int i = 0; i < roleCount / 4; i++) {
         roleList.add(new Role("role-t0-" + i, true, true, true, true, true, true, true, true, true, true)); // all permissions
         roleList.add(new Role("role-t1-" + i, true, true, false, false, false, false, false, false, false, false)); // send + consume
         roleList.add(new Role("role-t2-" + i, false, false, false, false, false, false, true, true, false, false));  // manage + browse
         roleList.add(new Role("role-t3-" + i, false, false, false, false, false, false, false, false, false, false)); // no permissions
      }

      roles = new HashSet<>(roleList);

      subjects = new Subject[4];
      for (int tier = 0; tier < 4; tier++) {
         Subject s = new Subject();
         for (int i = 0; i < subjectRoleCount; i++) {
            s.getPrincipals().add(new RolePrincipal(roleList.get((tier + i * 4) % roleList.size()).getName()));
         }
         subjects[tier] = s;
      }

      adminSubject = subjects[0];
      producerSubject = subjects[1];
      monitorSubject = subjects[2];
      guestSubject = subjects[3];

   }

   @Benchmark
   public boolean testAdminSend() {
      return SecurityManagerUtil.authorize(adminSubject, roles, SEND, RolePrincipal.class);
   }

   @Benchmark
   public boolean testProducerSend() {
      return SecurityManagerUtil.authorize(producerSubject, roles, SEND, RolePrincipal.class);
   }

   @Benchmark
   public boolean testProducerConsume() {
      return SecurityManagerUtil.authorize(producerSubject, roles, CONSUME, RolePrincipal.class);
   }

   @Benchmark
   public boolean testMonitorManage() {
      return SecurityManagerUtil.authorize(monitorSubject, roles, MANAGE, RolePrincipal.class);
   }

   /*monitor checking SEND — rolesWithPermission is non-empty (tier-0 and tier-1 have send) but none of monitor's principals match, so the inner loop exhausts. */
   @Benchmark
   public boolean testMonitorSendDenied() {
      return SecurityManagerUtil.authorize(monitorSubject, roles, SEND, RolePrincipal.class);
   }

   /** guest checking SEND — same denial path, models high-volume anonymous traffic. */
   @Benchmark
   public boolean testGuestSendDenied() {
      return SecurityManagerUtil.authorize(guestSubject, roles, SEND, RolePrincipal.class);
   }

   @Benchmark
   public boolean testMixedRealisticLoad() {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Subject subject = subjects[rng.nextInt(4)];
      CheckType check = CHECK_TYPES[rng.nextInt(3)];
      return SecurityManagerUtil.authorize(subject, roles, check, RolePrincipal.class);
   }

}
