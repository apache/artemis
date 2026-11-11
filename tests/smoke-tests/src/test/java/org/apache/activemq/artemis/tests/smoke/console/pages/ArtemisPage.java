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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.utils.StringEscapeUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.ADDRESSES_TAB;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.ALERT_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.BUTTON_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.DATA_TABLE;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.LOGOUT_DROPDOWN_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.LOGOUT_MENU_ITEM_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.MANAGE_COLUMNS_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.MESSAGE_TABLE_QUEUES_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.MESSAGE_VIEW_QUEUES_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.QUEUES_TAB;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.QUEUES_TAB_SELECTED;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.SAVE_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.SEND_MESSAGE_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.SEND_MESSAGE_TITLE;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TABLE_ROW_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TD_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TH_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.USER_DROPDOWN_MENU_LOCATOR;


public abstract class ArtemisPage extends ConsolePage {
   public ArtemisPage(WebDriver driver) {
      super(driver);
   }

   public String getUser() {
      WebElement logoutWebElement = driver.findElement(LOGOUT_DROPDOWN_LOCATOR);
      WebElement userDropdownMenuWebElement = driver.findElement(USER_DROPDOWN_MENU_LOCATOR);

      if (!logoutWebElement.isDisplayed()) {
         userDropdownMenuWebElement.click();
      }

      String logoutText = logoutWebElement.getText();
      Pattern pattern = Pattern.compile("Logout \\(([^\\)]+)\\)");
      Matcher matcher = pattern.matcher(logoutText);

      userDropdownMenuWebElement.click();

      if (matcher.find()) {
         return matcher.group(1);
      }

      return null;
   }

   public AddressesPage getAddressesPage(int timeout) {
      WebElement queuesMenuItem = driver.findElement(ADDRESSES_TAB);

      queuesMenuItem.click();

      return new AddressesPage(driver);
   }

   public SendMessagePage getAddressSendMessagePage(String address, int timeout) {
      refresh(timeout);
      WebElement element = driver.findElement(ADDRESSES_TAB);
      element.click();
      List<WebElement> tdElements = driver.findElement(DATA_TABLE).findElement(By.xpath("//tr/td[contains(text(), '" + address + "')]")).findElement(By.xpath("./..")).findElements(TD_TAG_LOCATOR);

      tdElements.get(tdElements.size() - 1).findElement(BUTTON_LOCATOR).click();

      tdElements.get(tdElements.size() - 1).findElement(SEND_MESSAGE_BUTTON).click();

      waitForElementToBeVisible(SEND_MESSAGE_TITLE, timeout);

      return new SendMessagePage(driver);
   }


   public SendMessagePage getQueueSendMessagePage(String queue, int timeout) {
      refresh(timeout);
      WebElement element = driver.findElement(QUEUES_TAB);
      element.click();
      List<WebElement> tdElements = driver.findElement(DATA_TABLE).findElement(By.xpath("//tr/td/a[contains(text(), '" + queue + "')]")).findElement(By.xpath("./../..")).findElements(By.tagName("td"));

      tdElements.get(tdElements.size() - 1).findElement(BUTTON_LOCATOR).click();

      tdElements.get(tdElements.size() - 1).findElement(SEND_MESSAGE_BUTTON).click();

      waitForElementToBeVisible(SEND_MESSAGE_TITLE, timeout);

      return new SendMessagePage(driver);
   }

   public QueuesPage getQueuesPage(int timeout) {
      WebElement queuesMenuItem = driver.findElement(QUEUES_TAB);

      queuesMenuItem.click();

      waitForElementToBeVisible(QUEUES_TAB_SELECTED, timeout);

      return new QueuesPage(driver);
   }

   public int getIndexOfColumn(String name) {
      WebElement headerRowWebElement = driver.findElement(TABLE_ROW_LOCATOR);

      List<WebElement> columnWebElements = headerRowWebElement.findElements(TH_TAG_LOCATOR);
      for (int i = 0; i < columnWebElements.size(); i++) {
         if (name.equals(columnWebElements.get(i).getText())) {
            return i;
         }
      }

      return -1;
   }


   public QueuesPage getQueuesPageFromMessageView(int timeout) {
      WebElement queuesMenuItem = driver.findElement(MESSAGE_VIEW_QUEUES_BUTTON);

      queuesMenuItem.click();

      waitForElementToBeVisible(QUEUES_TAB_SELECTED, timeout);

      return new QueuesPage(driver);
   }

   public QueuesPage getQueuesPageFromMessagesView(int timeout) {
      WebElement queuesMenuItem = driver.findElement(MESSAGE_TABLE_QUEUES_BUTTON);

      queuesMenuItem.click();

      waitForElementToBeVisible(QUEUES_TAB_SELECTED, timeout);

      return new QueuesPage(driver);
   }

   public LoginPage logout(int timeout) {
      WebElement logoutWebElement = driver.findElement(LOGOUT_DROPDOWN_LOCATOR);

      logoutWebElement.click();
      WebElement userDropdownMenuWebElement = logoutWebElement.findElement(LOGOUT_MENU_ITEM_LOCATOR);

      userDropdownMenuWebElement.click();

      return new LoginPage(driver);
   }

   public void enableColumn(String columnId) {
      WebElement element = driver.findElement(MANAGE_COLUMNS_BUTTON);
      element.click();

      element = driver.findElement(By.id("check-" + columnId));

      element.click();
      element = driver.findElement(SAVE_BUTTON);
      element.click();
   }

   public Object postJolokiaExecRequest(String mbean, String operation, String arguments) {
      Object response = ((JavascriptExecutor) driver).executeAsyncScript(
         "var callback = arguments[arguments.length - 1];" +
            "var xhr = new XMLHttpRequest();" +
            "xhr.open('POST', '/console/jolokia', true);" +
            "xhr.onreadystatechange = function() {" +
            "  if (xhr.readyState == 4) {" +
            "    callback(xhr.responseText);" +
            "  }" +
            "};" +
            "xhr.send('{\"type\":\"exec\",\"mbean\":\"" + StringEscapeUtils.escapeString(StringEscapeUtils.escapeString(mbean)) +
            "\",\"operation\":\"" + StringEscapeUtils.escapeString(StringEscapeUtils.escapeString(operation)) +
            "\",\"arguments\":[" + StringEscapeUtils.escapeString(arguments) + "]}');");

      return response;
   }

   public int countAlerts() {
      return driver.findElements(ALERT_LOCATOR).size();
   }

   public void closeAlerts() {
      List<WebElement> alerts = driver.findElements(ALERT_LOCATOR);

      for (WebElement alert : alerts) {
         List<WebElement> alertButtons = alert.findElements(By.tagName("button"));
         for (WebElement alertButton : alertButtons) {
            alertButton.click();
         }
      }
   }
}
