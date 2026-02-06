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

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.DATA_ROW_CONTEXT_MENU;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.smoke.console.pages.AddressesPage;
import org.apache.activemq.artemis.tests.smoke.console.pages.LoginPage;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

@ExtendWith(ParameterizedTestExtension.class)
public class AddressesTest extends ArtemisTest {

   public AddressesTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @TestTemplate
   public void testDefaultAddresses() throws Exception {
      testDefaultAddresses(SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, false, true, true, true);
   }

   @TestTemplate
   public void testDefaultAddressesWithViewUser() throws Exception {
      testDefaultAddresses("addresses", "addresses", true, false, false, false);
   }

   @TestTemplate
   public void testDefaultAddressesWithDeleteUser() throws Exception {
      testDefaultAddresses("deleteAddresses", "deleteAddresses", true, true, false, false);
   }

   private void testDefaultAddresses(String username, String password, boolean isAlertExpected, boolean canDeleteAddress, boolean canSendMessage, boolean canCreateQueue) throws Exception {
      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      org.apache.activemq.artemis.tests.smoke.console.pages.StatusPage statusPage = loginPage.loginValidUser(
         username, password, DEFAULT_TIMEOUT);

      assertEquals(isAlertExpected, statusPage.countAlerts() > 0);
      statusPage.closeAlerts();

      AddressesPage addressesPage = statusPage.getAddressesPage(DEFAULT_TIMEOUT);

      Wait.assertEquals(1, () -> addressesPage.countAddress("DLQ"));
      assertEquals(0, addressesPage.getMessagesCount("DLQ"));

      testAddressContextMenu(addressesPage, "DLQ", canDeleteAddress, canSendMessage, canCreateQueue);

      Wait.assertEquals(1, () -> addressesPage.countAddress("ExpiryQueue"));
      assertEquals(0, addressesPage.getMessagesCount("ExpiryQueue"));

      testAddressContextMenu(addressesPage, "ExpiryQueue", canDeleteAddress, canSendMessage, canCreateQueue);
   }

   private void testAddressContextMenu(AddressesPage addressesPage, String addressName, boolean canDeleteAddress, boolean canSendMessage, boolean canCreateQueue) {
      addressesPage.toggleContextMenu(addressName);
      WebElement addressContextMenu = driver.findElement(DATA_ROW_CONTEXT_MENU);

      assertEquals(1, addressContextMenu.findElements(
         By.xpath("//span[contains(text(),'Show in Artemis JMX')]")).size());
      assertEquals(1, addressContextMenu.findElements(
         By.xpath("//span[contains(text(),'Attributes')]")).size());
      assertEquals(1, addressContextMenu.findElements(
         By.xpath("//span[contains(text(),'Operations')]")).size());
      assertEquals(canDeleteAddress ? 1 : 0, addressContextMenu.findElements(
         By.xpath("//span[contains(text(),'Delete Address')]")).size());
      assertEquals(canSendMessage ? 1 : 0, addressContextMenu.findElements(
         By.xpath("//span[contains(text(),'Send Message')]")).size());
      assertEquals(canCreateQueue ? 1 : 0, addressContextMenu.findElements(
         By.xpath("//span[contains(text(),'Create Queue')]")).size());

      addressesPage.toggleContextMenu(addressName);
   }
}
