package multiVersionReplica
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

import org.apache.activemq.artemis.tests.compatibility.GroovyRun


for (int i = 0; i < 100 && !theMainServer.getActiveMQServer().isReplicaSync(); i++) {
    Thread.sleep(100);
}
System.out.println("*******************************************************************************************************************************");
System.out.println("replica sync sync = " + theMainServer.getActiveMQServer().isReplicaSync())
System.out.println("*******************************************************************************************************************************");

GroovyRun.assertTrue(theMainServer.getActiveMQServer().isReplicaSync());
