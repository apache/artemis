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

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;

import java.io.File;

import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.junit.jupiter.api.Test;

public class AckRetryCompatibilityTest extends ClasspathBase {

   @Test
   public void testAckRetryEncoding_2_44_0_versus_Snapshot() throws Exception {
      ClassLoader two_44_classloader = getClasspath(ARTEMIS_2_44_0);
      ClassLoader snapshot = getClasspath(SNAPSHOT);
      testAckRetryEncodeDecode(two_44_classloader, snapshot);
   }

   @Test
   public void testAckRetryEncodingSnapshot() throws Exception {
      ClassLoader snapshot = getClasspath(SNAPSHOT);
      testAckRetryEncodeDecode(snapshot, snapshot);
   }

   private void testAckRetryEncodeDecode(ClassLoader senderLoader, ClassLoader receiverLoader) throws Exception {
      File file = File.createTempFile("ackRetry", ".bin", serverFolder);
      evaluate(senderLoader, "ackManager/ackRetryEncoding.groovy", file.getAbsolutePath(), "write");
      evaluate(receiverLoader, "ackManager/ackRetryEncoding.groovy", file.getAbsolutePath(), "read");
   }
}
