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
package org.apache.activemq.artemis.tests.smoke.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.smoke.console.pages.LoginPage;
import org.apache.activemq.artemis.tests.smoke.console.pages.StatusPage;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;

import java.util.Collections;
import java.util.Set;

//Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class TabsTest extends ArtemisTest {

   private final String TAB_CONNECTIONS = "Connections";

   private final String TAB_SESSIONS = "Sessions";

   private final String TAB_CONSUMERS = "Consumers";

   private final String TAB_PRODUCERS = "Producers";

   private final String TAB_ADDRESSES = "Addresses";

   private final String TAB_QUEUES = "Queues";

   private final String[] TABS = new String[]{TAB_CONNECTIONS, TAB_SESSIONS, TAB_CONSUMERS, TAB_PRODUCERS, TAB_ADDRESSES, TAB_QUEUES};

   public TabsTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @TestTemplate
   public void testVisibleTabs() {
      testTabs(SERVER_ADMIN_USERNAME, false, Set.of(TABS));
      testTabs("connections", true, Collections.singleton(TAB_CONNECTIONS));
      testTabs("sessions", true, Collections.singleton(TAB_SESSIONS));
      testTabs("consumers", true, Collections.singleton(TAB_CONSUMERS));
      testTabs("producers", true, Collections.singleton(TAB_PRODUCERS));
      testTabs("addresses", true, Collections.singleton(TAB_ADDRESSES));
      testTabs("queues", true, Collections.singleton(TAB_QUEUES));
   }

   private void testTabs(String userpass, boolean isAlertExpected, Set<String> visibleTabs) {
      loadLandingPage();

      StatusPage statusPage = new LoginPage(driver).loginValidUser(userpass, userpass, DEFAULT_TIMEOUT);

      assertEquals(isAlertExpected, statusPage.countAlerts() > 0);
      statusPage.closeAlerts();

      for (String tab : TABS) {
         By tabLocator = By.xpath("//button/span[contains(text(),'" + tab + "')]");
         if (visibleTabs.contains(tab)) {
            driver.findElement(tabLocator);
         } else {
            try {
               driver.findElement(tabLocator);
               fail("User " + userpass + " should not have been able to see the " + tab + " tab.");
            } catch (Exception e) {
               assertEquals(NoSuchElementException.class, e.getClass());
            }
         }
      }

      statusPage.logout(DEFAULT_TIMEOUT);
   }
}
