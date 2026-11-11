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

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.MESSAGE_TEXT_EDITOR_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.USE_LOGIN_LOCATOR;

public class SendMessagePage extends ArtemisPage {

   public SendMessagePage(WebDriver driver) {
      super(driver);
   }

   public void clearMessageText() {
      //the "1" here is what HawtIO shows when clear as the 1st line number
      while (!"1".equals(getMessageText())) {
         Actions actions = new Actions(driver);
         actions.click(driver.findElement(MESSAGE_TEXT_EDITOR_LOCATOR));
         actions.sendKeys(Keys.BACK_SPACE);
         actions.sendKeys(Keys.DELETE);
         actions.perform();
      }
   }

   public boolean isUseCurrentLogonUserSelected() {
      return driver.findElement(USE_LOGIN_LOCATOR).isSelected();
   }


   public void selectUseCurrentLogonUser() {
      if (!isUseCurrentLogonUserSelected()) {
         WebElement element = driver.findElement(USE_LOGIN_LOCATOR);
         element.click();
      }
   }

   public void unselectUseCurrentLogonUser() {
      if (isUseCurrentLogonUserSelected()) {
         WebElement element = driver.findElement(USE_LOGIN_LOCATOR);
         element.click();
      }
   }

   public void appendMessageText(String text) {
      Actions actions = new Actions(driver);
      actions.click(driver.findElement(MESSAGE_TEXT_EDITOR_LOCATOR));
      actions.sendKeys(text);
      actions.perform();
   }

   public String getMessageText() {
      return driver.findElement(MESSAGE_TEXT_EDITOR_LOCATOR).getText();
   }

   public void sendMessage() {
      WebElement element = driver.findElement(By.xpath("//button[contains(text(),'Send')]"));
      element.click();
      element = driver.findElement(By.xpath("//button[contains(text(),'Cancel')]"));
      element.click();
   }
}
