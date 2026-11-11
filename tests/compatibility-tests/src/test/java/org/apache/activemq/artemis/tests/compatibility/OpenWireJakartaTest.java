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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.AMQ_5_11;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.JAKARTAEE;

import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.junit.jupiter.api.Test;

public class OpenWireJakartaTest extends ClasspathBase {

   @Test
   public void testOpenWireJakarta() throws Throwable {
      ClassLoader serverClassloader = getClasspath(JAKARTAEE);
      evaluate(serverClassloader, "openWireJakarta/artemisJakartaServer.groovy", "openWireJakarta");
      evaluate(getClasspath(AMQ_5_11), "openWireJakarta/sendReceiveOW.groovy", "20", "test");
      execute(serverClassloader, "server.stop()");
   }
}