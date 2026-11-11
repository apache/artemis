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

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_1_4_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_4_0;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.tests.compatibility.base.VersionedBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class ConnectionFactoryConfigurationSerializationTest extends VersionedBase {

   @Parameters(name = "producer={0}, consumer={1}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();

      combinations.addAll(combinatory2(SNAPSHOT, new Object[]{ARTEMIS_1_4_0, SNAPSHOT, ARTEMIS_2_4_0}, new Object[]{ARTEMIS_1_4_0, SNAPSHOT, ARTEMIS_2_4_0}));
      return combinations;
   }

   public ConnectionFactoryConfigurationSerializationTest(String sender, String receiver) throws Exception {
      super(sender, receiver);
   }

   @BeforeEach
   public void beforeTest() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
      serverFolder.mkdirs();
      setVariable(senderClassloader, "persistent", false);
   }

   @AfterEach
   public void afterTest() {
   }

   @TestTemplate
   public void testSerializeFactory() throws Throwable {
      File file = File.createTempFile("objects.ser", null, serverFolder);
      evaluate(senderClassloader, "serial/cfserial.groovy", file.getAbsolutePath(), "write", sender);
      evaluate(receiverClassloader, "serial/cfserial.groovy", file.getAbsolutePath(), "read", receiver);
   }

}

