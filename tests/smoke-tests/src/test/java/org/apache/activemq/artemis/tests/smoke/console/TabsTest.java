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

import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.smoke.console.pages.LoginPage;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;

//Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class TabsTest extends ArtemisTest {

   public TabsTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @TestTemplate
   public void testConnectionsTab() {
      testTab("connections", "Connections");
   }

   @TestTemplate
   public void testSessionsTab() {
      testTab("sessions", "Sessions");
   }

   @TestTemplate
   public void testConsumersTab() {
      testTab("consumers", "Consumers");
   }

   @TestTemplate
   public void testProducersTab() {
      testTab("producers", "Producers");
   }

   @TestTemplate
   public void testAddressesTab() {
      testTab("addresses", "Addresses");
   }

   @TestTemplate
   public void testQueuesTab() {
      testTab("queues", "Queues");
   }

   private void testTab(String userpass, String tab) {
      loadLandingPage();
      new LoginPage(driver).loginValidUser(userpass, userpass, DEFAULT_TIMEOUT);
      driver.findElement(By.xpath("//button/span[contains(text(),'" + tab + "')]"));
   }

   @TestTemplate
   public void testConnectionsTabNegative() {
      // use credentials for a valid user who cannot see the tab
      testTabNegative("queues", "Connections");
   }

   @TestTemplate
   public void testSessionsTabNegative() {
      // use credentials for a valid user who cannot see the tab
      testTabNegative("connections", "Sessions");
   }

   @TestTemplate
   public void testConsumersTabNegative() {
      // use credentials for a valid user who cannot see the tab
      testTabNegative("connections", "Consumers");
   }

   @TestTemplate
   public void testProducersTabNegative() {
      // use credentials for a valid user who cannot see the tab
      testTabNegative("connections", "roducers");
   }

   @TestTemplate
   public void testAddressesTabNegative() {
      // use credentials for a valid user who cannot see the tab
      testTabNegative("connections", "Addresses");
   }

   @TestTemplate
   public void testQueuesTabNegative() {
      // use credentials for a valid user who cannot see the tab
      testTabNegative("connections", "Queues");
   }

   private void testTabNegative(String userpass, String tab) {
      loadLandingPage();
      new LoginPage(driver).loginValidUser(userpass, userpass, DEFAULT_TIMEOUT);
      try {
         driver.findElement(By.xpath("//a[contains(text(),'" + tab + "')]"));
         fail("User " + userpass + " should not have been able to see the " + tab + " tab.");
      } catch (NoSuchElementException e) {
         // expected
      }
   }
}
