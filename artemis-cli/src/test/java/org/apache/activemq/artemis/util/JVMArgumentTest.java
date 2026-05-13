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

package org.apache.activemq.artemis.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JVMArgumentTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testArgumentsWindows() {
      doArgumentsWindowsTestImpl(true);
   }

   @Test
   public void testArgumentsWindowsWithArgRenameRequired() {
      doArgumentsWindowsTestImpl(false);
   }

   private void doArgumentsWindowsTestImpl(boolean useNewArgPropName) {
      final String arguments;
      if (useNewArgPropName) {
         // Uses new args prop -Dhawtio.roles=
         arguments = "IF \"%JAVA_ARGS%\"==\"\" (set JAVA_ARGS= -must-go -XX:AutoBoxCacheMax=20000 -XX:+PrintClassHistogram  -XX:+UseG1GC -XX:+UseStringDeduplication -Xms333M -Xmx77G -Dhawtio.disableProxy=true -Dhawtio.offline=true -Dhawtio.realm=activemq -Dhawtio.roles=amq -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal -Dhawtio.http.strictTransportSecurity=max-age=31536000;includeSubDomains;preload -Djolokia.policyLocation=classpath:jolokia-access.xml --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dlog4j2.disableJmx=true)";
      } else {
         // Uses old args prop -Dhawtio.role=
         arguments = "IF \"%JAVA_ARGS%\"==\"\" (set JAVA_ARGS= -must-go -XX:AutoBoxCacheMax=20000 -XX:+PrintClassHistogram  -XX:+UseG1GC -XX:+UseStringDeduplication -Xms333M -Xmx77G -Dhawtio.disableProxy=true -Dhawtio.offline=true -Dhawtio.realm=activemq -Dhawtio.role=amq -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal -Dhawtio.http.strictTransportSecurity=max-age=31536000;includeSubDomains;preload -Djolokia.policyLocation=classpath:jolokia-access.xml --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dlog4j2.disableJmx=true)";
      }

      String prefix = "IF \"%JAVA_ARGS%\"==\"\" (set JAVA_ARGS= ";

      String[] fixedArguments = new String[]{"-Xmx", "-Xms", "-Dhawtio.roles="};
      Map<String, String> keepArgumentsAlternates = Map.of("-Dhawtio.roles=", "-Dhawtio.role=");

      Map<String, String> usedArgs = new HashMap<>();
      JVMArgumentParser.parseOriginalArgs(prefix, "\"", arguments, keepArgumentsAlternates, fixedArguments, usedArgs);
      assertEquals(3, usedArgs.size());
      assertEquals("-Xmx77G", usedArgs.get("-Xmx"));
      assertEquals("-Xms333M", usedArgs.get("-Xms"));
      assertEquals("-Dhawtio.roles=amq", usedArgs.get("-Dhawtio.roles="));

      String newLine = "IF \"%JAVA_ARGS%\"==\"\" (set JAVA_ARGS= -XX:AutoBoxCacheMax=20000 -XX:+PrintClassHistogram  -XX:+UseG1GC -XX:+UseStringDeduplication -Xms512M -Xmx1G -Dhawtio.disableProxy=true -Dhawtio.offline=true -Dhawtio.realm=activemq -Dhawtio.roles=replaceThisRole -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal -Djolokia.policyLocation=classpath:jolokia-access.xml)";

      String resultLine = JVMArgumentParser.parseNewLine(prefix, "\"", newLine, fixedArguments, usedArgs);

      logger.info("output:: {}", resultLine);

      assertFalse(resultLine.contains("-must-go"));
      assertTrue(resultLine.contains("-Xmx77G"));
      assertTrue(resultLine.contains("-Xms333M"));
      assertTrue(resultLine.contains("-Dhawtio.roles=amq"));
      assertFalse(resultLine.contains("-Xmx1G"));
      assertFalse(resultLine.contains("-Xmx512M"));
      assertFalse(resultLine.contains("replaceThisRole"));
      assertFalse(resultLine.contains("-Dhawtio.role="));
   }


   @Test
   public void testArgumentsLinux() {
      String arguments = "    JAVA_ARGS=\"-must-go -XX:AutoBoxCacheMax=20000 -XX:+PrintClassHistogram -XX:+UseG1GC -XX:+UseStringDeduplication -Xms333M -Xmx77G -Dhawtio.disableProxy=true -Dhawtio.realm=activemq -Dhawtio.offline=true -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal -Dhawtio.http.strictTransportSecurity=max-age=31536000;includeSubDomains;preload -Djolokia.policyLocation=classpath:jolokia-access.xml -Dlog4j2.disableJmx=true --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \"";

      String prefix = "JAVA_ARGS=";

      String[] fixedArguments = new String[]{"-Xmx", "-Xms"};

      Map<String, String> usedArgs = new HashMap<>();
      JVMArgumentParser.parseOriginalArgs(prefix, "\"", arguments, Collections.emptyMap(), fixedArguments, usedArgs);
      assertEquals(2, usedArgs.size());
      assertEquals("-Xmx77G", usedArgs.get("-Xmx"));
      assertEquals("-Xms333M", usedArgs.get("-Xms"));

      String newLine = "    JAVA_ARGS=\"-XX:AutoBoxCacheMax=20000 -XX:+PrintClassHistogram -XX:+UseG1GC -XX:+UseStringDeduplication -Xms512M -Xmx1G -Dhawtio.disableProxy=true -Dhawtio.realm=activemq -Dhawtio.offline=true -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal -Dhawtio.http.strictTransportSecurity=max-age=31536000;includeSubDomains;preload -Djolokia.policyLocation=classpath:jolokia-access.xml -Dlog4j2.disableJmx=true --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \"";

      String resultLine = JVMArgumentParser.parseNewLine(prefix, "\"", newLine, fixedArguments, usedArgs);

      System.out.println("output::" + resultLine);

      assertFalse(resultLine.contains("-must-go"));
      assertTrue(resultLine.contains("-Xmx77G"));
      assertTrue(resultLine.contains("-Xms333M"));
      assertFalse(resultLine.contains("-Xmx1G"));
      assertFalse(resultLine.contains("-Xmx512M"));

      assertTrue(resultLine.startsWith("    "));
   }

}
