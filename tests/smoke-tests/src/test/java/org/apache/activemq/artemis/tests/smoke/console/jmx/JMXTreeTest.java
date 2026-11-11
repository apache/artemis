/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.smoke.console.jmx;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.smoke.console.pages.jmx.ArtemisTreePage;
import org.apache.activemq.artemis.tests.smoke.console.pages.LoginPage;
import org.apache.activemq.artemis.tests.smoke.console.pages.StatusPage;
import org.apache.activemq.artemis.tests.smoke.console.pages.jmx.AttributesPage;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.DEFAULT_CONSOLE_NAME;
import static org.junit.Assert.assertEquals;

@ExtendWith(ParameterizedTestExtension.class)
public class JMXTreeTest extends ArtemisJMXTest {


   public JMXTreeTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @TestTemplate
   public void testExpandTree() {

      String expectedConsoleName = System.getProperty("artemis.console.name", DEFAULT_CONSOLE_NAME);
      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
            SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);

      loadLandingPage();
      ArtemisTreePage treePage = new ArtemisTreePage(driver);
      treePage.expandTree(DEFAULT_TIMEOUT);

      assertEquals("Select " + expectedConsoleName + " Node", treePage.getNodeTitle());
   }

   @TestTemplate
   public void testSelectBrokerNode() throws Exception {
      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
            SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);

      loadLandingPage();
      ArtemisTreePage treePage = new ArtemisTreePage(driver);
      treePage.expandTree(DEFAULT_TIMEOUT);

      AttributesPage attributesPage = treePage.selectBrokerNode();

      Wait.assertEquals("0.0.0.0", () -> attributesPage.getAttributevalue("Name"));

      attributesPage.selectAttribute("Name");

      Wait.assertTrue(() -> attributesPage.isAttributeDisplayed("Name"));
   }
}
