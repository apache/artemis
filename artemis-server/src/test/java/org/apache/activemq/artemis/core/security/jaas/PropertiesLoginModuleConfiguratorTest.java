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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModuleConfigurator;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoader;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@link PropertiesLoginModuleConfigurator} resolves property-file paths
 * using the same {@code baseDir} priority as {@link PropertiesLoader}:
 * <ol>
 *   <li>Explicit {@code baseDir} option in the JAAS login-module options</li>
 *   <li>Parent directory of the {@code java.security.auth.login.config} system property</li>
 *   <li>Broker {@code etc} directory as last resort</li>
 * </ol>
 */
public class PropertiesLoginModuleConfiguratorTest extends ArtemisTestCase {

   private static final String ENTRY_NAME = "testRealm";
   private static final String USER_FILE = "users.properties";
   private static final String ROLE_FILE = "roles.properties";

   @TempDir(factory = TargetTempDirFactory.class)
   public File tempDir;

   private String savedLoginConfig;

   @BeforeEach
   public void saveLoginConfig() {
      savedLoginConfig = System.getProperty(PropertiesLoader.LOGIN_CONFIG_SYS_PROP_NAME);
   }

   @AfterEach
   public void restoreLoginConfig() {
      if (savedLoginConfig == null) {
         System.clearProperty(PropertiesLoader.LOGIN_CONFIG_SYS_PROP_NAME);
      } else {
         System.setProperty(PropertiesLoader.LOGIN_CONFIG_SYS_PROP_NAME, savedLoginConfig);
      }
      Configuration.setConfiguration(null);
      PropertiesLoader.resetUsersAndGroupsCache();
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private void writePropertyFiles(File dir, String username, String role) throws Exception {
      dir.mkdirs();
      try (FileWriter fw = new FileWriter(new File(dir, USER_FILE))) {
         fw.write(username + "=password\n");
      }
      try (FileWriter fw = new FileWriter(new File(dir, ROLE_FILE))) {
         fw.write(role + "=" + username + "\n");
      }
   }

   /**
    * Installs a programmatic JAAS {@link Configuration} for {@value #ENTRY_NAME}
    * with the standard file-name keys plus any {@code extraOptions}.
    */
   private void installJaasConfig(Map<String, String> extraOptions) {
      Map<String, String> options = new HashMap<>();
      options.put(PropertiesLoginModule.USER_FILE_PROP_NAME, USER_FILE);
      options.put(PropertiesLoginModule.ROLE_FILE_PROP_NAME, ROLE_FILE);
      options.putAll(extraOptions);

      AppConfigurationEntry entry = new AppConfigurationEntry(
         PropertiesLoginModule.class.getName(),
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
         options);
      Configuration.setConfiguration(new Configuration() {
         @Override
         public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            if (ENTRY_NAME.equals(name)) {
               return new AppConfigurationEntry[]{entry};
            }
            return null;
         }
      });
   }

   /**
    * Constructs a {@link PropertiesLoginModuleConfigurator} and returns the result of
    * {@code listUser(null)} so individual tests only need to assert on the map.
    */
   private Map<String, Set<String>> listUsers(File brokerEtcDir) throws Exception {
      return new PropertiesLoginModuleConfigurator(ENTRY_NAME, brokerEtcDir.getAbsolutePath())
         .listUser(null);
   }

   // ── tests ──────────────────────────────────────────────────────────────────

   /**
    * When the JAAS config contains an explicit {@code baseDir} option the property
    * files must be resolved from that directory, not from the {@code brokerEtc} directory.
    */
   @Test
   public void testBaseDirOptionTakesPrecedenceOverBrokerEtc() throws Exception {
      File baseDirDir = new File(tempDir, "custom-base");
      File brokerEtcDir = new File(tempDir, "broker-etc");
      brokerEtcDir.mkdirs();

      writePropertyFiles(baseDirDir, "alice", "admin");

      installJaasConfig(Map.of("baseDir", baseDirDir.getAbsolutePath()));

      Map<String, Set<String>> users = listUsers(brokerEtcDir);

      assertTrue(users.containsKey("alice"), "user 'alice' should be listed");
      assertTrue(users.get("alice").contains("admin"), "role 'admin' should be listed for alice");
   }

   /**
    * When no {@code baseDir} option is set but {@code java.security.auth.login.config}
    * is defined, files are resolved relative to the parent directory of that path —
    * matching the behaviour of {@link PropertiesLoader}.
    */
   @Test
   public void testLoginConfigSystemPropertyUsedWhenNoBaseDirOption() throws Exception {
      File loginConfigDir = new File(tempDir, "etc");
      loginConfigDir.mkdirs();

      // Write a dummy login.config so we can point the system property at it
      File loginConfigFile = new File(loginConfigDir, "login.config");
      loginConfigFile.createNewFile();
      writePropertyFiles(loginConfigDir, "bob", "users");

      System.setProperty(PropertiesLoader.LOGIN_CONFIG_SYS_PROP_NAME, loginConfigFile.getAbsolutePath());

      installJaasConfig(Map.of()); // no baseDir — should fall back to login.config parent dir

      // brokerEtc points somewhere else (no property files there)
      File brokerEtcDir = new File(tempDir, "broker-etc-empty");
      brokerEtcDir.mkdirs();

      Map<String, Set<String>> users = listUsers(brokerEtcDir);

      assertTrue(users.containsKey("bob"), "user 'bob' should be listed");
      assertTrue(users.get("bob").contains("users"), "role 'users' should be listed for bob");
   }

   /**
    * When neither {@code baseDir} option nor the login-config system property is set,
    * the {@code brokerEtc} path is the fallback — preserving backwards-compatible behaviour.
    */
   @Test
   public void testBrokerEtcFallbackWhenNoBaseDirAndNoSystemProperty() throws Exception {
      System.clearProperty(PropertiesLoader.LOGIN_CONFIG_SYS_PROP_NAME);

      File brokerEtcDir = new File(tempDir, "broker-etc");
      writePropertyFiles(brokerEtcDir, "charlie", "operators");

      installJaasConfig(Map.of()); // no baseDir, no system property

      Map<String, Set<String>> users = listUsers(brokerEtcDir);

      assertTrue(users.containsKey("charlie"), "user 'charlie' should be listed");
      assertFalse(users.get("charlie").isEmpty(), "charlie should have at least one role");
   }
}
