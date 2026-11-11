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
package org.apache.activemq.artemis.core.security.jaas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.security.auth.Subject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class JAASSecurityManagerClassLoadingTest {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "newLoader=({0})")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {{true}, {false}});
   }

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = PropertiesLoginModuleTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            try {
               path = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8.name());
               System.setProperty("java.security.auth.login.config", path);
            } catch (UnsupportedEncodingException e) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   @Parameter(index = 0)
   public boolean usingNewLoader;

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File tmpDir;

   @TestTemplate
   public void testLoginClassloading() throws Exception {
      ClassLoader existingLoader = Thread.currentThread().getContextClassLoader();
      logger.debug("loader: {}", existingLoader);
      try {
         if (usingNewLoader) {
            URLClassLoader simulatedLoader = new URLClassLoader(new URL[]{tmpDir.toURI().toURL()}, null);
            Thread.currentThread().setContextClassLoader(simulatedLoader);
         }
         ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");

         Subject result = securityManager.authenticate("first", "secret", null, null);

         assertNotNull(result);
         assertEquals("first", securityManager.getUserFromSubject(result));

         Role role = new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false);
         Set<Role> roles = new HashSet<>();
         roles.add(role);
         boolean authorizationResult = securityManager.authorize(result, roles, CheckType.SEND, "someaddress");

         assertTrue(authorizationResult);

      } finally {
         Thread.currentThread().setContextClassLoader(existingLoader);
      }
   }
}
