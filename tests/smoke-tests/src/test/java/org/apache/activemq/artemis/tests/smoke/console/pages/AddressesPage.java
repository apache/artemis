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
package org.apache.activemq.artemis.tests.smoke.console.pages;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.BUTTON_LOCATOR;

public class AddressesPage extends ArtemisPage {
   private static final String MESSAGE_COUNT_COLUMN_NAME = "Message Count";

   public AddressesPage(org.openqa.selenium.WebDriver driver) {
      super(driver);
   }

   public int countAddress(String name) {
      return driver.findElements(getAddressLocator(name)).size();
   }

   public int getMessagesCount(String name) {
      org.openqa.selenium.WebElement addressRowWebElement = driver.findElement(getAddressLocator(name));

      String messageCountText = addressRowWebElement.
         findElement(org.openqa.selenium.By.xpath("./..")).
         findElements(org.openqa.selenium.By.tagName("td"))
         .get(getIndexOfColumn(MESSAGE_COUNT_COLUMN_NAME)).getText();

      return Integer.parseInt(messageCountText);
   }

   public void toggleContextMenu(String name) {
      org.openqa.selenium.WebElement addressRowWebElement = driver.findElement(getAddressLocator(name));

      java.util.List<org.openqa.selenium.WebElement> tdElements = addressRowWebElement.
         findElement(org.openqa.selenium.By.xpath("./..")).
         findElements(org.openqa.selenium.By.tagName("td"));

      tdElements.get(tdElements.size() - 1).findElement(BUTTON_LOCATOR).click();
   }

   private org.openqa.selenium.By getAddressLocator(String name) {
      return org.openqa.selenium.By.xpath("//tr/td[contains(text(), '" + name + "')]");
   }
}
