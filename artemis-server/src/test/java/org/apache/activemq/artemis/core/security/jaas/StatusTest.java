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

import static org.apache.activemq.artemis.core.server.impl.ServerStatus.JAAS_COMPONENT;
import static org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoader.LOGIN_CONFIG_SYS_PROP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.security.auth.Subject;
import java.io.File;
import java.util.HashMap;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StatusTest extends ServerTestBase {

   private String existingPath = null;

   @BeforeEach
   public void prepareTest() throws Exception {
      existingPath = System.getProperty(LOGIN_CONFIG_SYS_PROP_NAME);
      ServerStatus.clear();
   }

   @AfterEach
   public void revertExisting() throws Exception {
      setOrClearLoginConfigSystemProperty(existingPath);
   }

   @Test
   public void testStatusOfLoginConfigSystemProperty() throws Exception {

      File parentDir = new File(temporaryFolder, "sub");
      parentDir.mkdirs();

      File fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule = new File(parentDir, "someFileInTempDir.txt");
      fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.createNewFile();

      setOrClearLoginConfigSystemProperty(fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.getAbsolutePath());

      ActiveMQServer server = createServer(false);
      server.getConfiguration().setConfigurationFileRefreshPeriod(1);
      server.start();

      PropertiesLoginModule propertiesLoginModule = new PropertiesLoginModule();
      final HashMap<String, String> options = new HashMap<>();
      options.put("reload", "true");
      options.put(PropertiesLoginModule.USER_FILE_PROP_NAME, fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.getName());
      options.put(PropertiesLoginModule.ROLE_FILE_PROP_NAME, fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.getName());

      propertiesLoginModule.initialize(new Subject(), null, null, options);
      assertTrue(ServerStatus.getInstance().asJson().contains(fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.getName()), "contains");

      // reset current status reloadTime time, to verify reload
      final String UNKNOWN = "UNKNOWN";
      ServerStatus.getInstance().update(ServerStatus.SERVER_COMPONENT, "{\"jaas\":{\"properties\":{\"" + fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.getName() + "\": {\"reloadTime\":\"" + UNKNOWN + "\"}}}}");

      // updating referenced file won't kick in till login
      fileToReferenceViaLoginSystemPropAndFromPropertiesLoginModule.setLastModified(System.currentTimeMillis());

      assertTrue(ServerStatus.getInstance().asJson().contains(UNKNOWN), "contains");

      // mod of login.config dir - trigger a reload
      parentDir.setLastModified(System.currentTimeMillis());

      Wait.assertFalse(() -> ServerStatus.getInstance().asJson().contains(UNKNOWN));
   }

   @Test
   public void testStatusOfServerOrderServerFirst() throws Exception {
      final String EARLY_BIRD = "early";
      final String BIRD = "later";

      ActiveMQServerImpl server = new ActiveMQServerImpl();
      assertEquals(server, ServerStatus.getServer());

      ServerStatus.getInstance().update(JAAS_COMPONENT + "/properties/" + EARLY_BIRD, "{\"reloadTime\":\"2\"}");
      assertTrue(ServerStatus.getInstance().asJson().contains(EARLY_BIRD), "contains");

      ServerStatus.getInstance().update(JAAS_COMPONENT + "/properties/" + BIRD, "{\"reloadTime\":\"2\"}");

      assertTrue(ServerStatus.getInstance().asJson().contains(EARLY_BIRD), "contains");
      assertTrue(ServerStatus.getInstance().asJson().contains(BIRD), "contains");
   }

   @Test
   public void testStatusOfServerOrderServerSecond() throws Exception {
      final String EARLY_BIRD = "early";
      final String BIRD = "later";

      ServerStatus.getInstance().update(JAAS_COMPONENT + "/properties/" + EARLY_BIRD, "{\"reloadTime\":\"2\"}");
      assertTrue(ServerStatus.getInstance().asJson().contains(EARLY_BIRD), "contains");

      ServerStatus.getInstance().update(JAAS_COMPONENT + "/properties/" + BIRD, "{\"reloadTime\":\"2\"}");

      ActiveMQServerImpl server = new ActiveMQServerImpl();
      assertEquals(server, ServerStatus.getServer());

      assertTrue(ServerStatus.getInstance().asJson().contains(EARLY_BIRD), "contains");
      assertTrue(ServerStatus.getInstance().asJson().contains(BIRD), "contains");
      assertTrue(ServerStatus.getInstance().asJson().contains("nodeId"), "contains");
   }

   private static void setOrClearLoginConfigSystemProperty(String path) throws Exception {
      if (path != null) {
         System.setProperty(LOGIN_CONFIG_SYS_PROP_NAME, path);
      } else {
         System.clearProperty(LOGIN_CONFIG_SYS_PROP_NAME);
      }
   }
}