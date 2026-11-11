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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.BRAND_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.LOGIN_BUTTON_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.LOGOUT_DROPDOWN_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.USERNAME_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.PASSWORD_LOCATOR;

public class LoginPage extends ConsolePage {
   public LoginPage(WebDriver driver) {
      super(driver);
   }

   public String getBrandImage(int timeout) {
      waitForElementToBeVisible(BRAND_LOCATOR, timeout);

      return driver.findElement(BRAND_LOCATOR).getAttribute("src");
   }

   public StatusPage loginValidUser(String username, String password, int timeout) {
      waitForElementToBeVisible(USERNAME_LOCATOR, timeout);
      waitForElementToBeVisible(PASSWORD_LOCATOR, timeout);
      waitForElementToBeClickable(LOGIN_BUTTON_LOCATOR, timeout);

      driver.findElement(USERNAME_LOCATOR).sendKeys(username);
      driver.findElement(PASSWORD_LOCATOR).sendKeys(password);
      WebElement element = driver.findElement(LOGIN_BUTTON_LOCATOR);
      element.click();
      waitForElementToBeVisible(LOGOUT_DROPDOWN_LOCATOR, timeout);
      waitForElementToBeVisible(By.xpath("//button/span[contains(text(),'Status')]"), timeout);

      return new StatusPage(driver);
   }
}
