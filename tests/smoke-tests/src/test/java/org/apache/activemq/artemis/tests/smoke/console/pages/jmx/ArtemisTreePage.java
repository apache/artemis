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
package org.apache.activemq.artemis.tests.smoke.console.pages.jmx;

import org.apache.activemq.artemis.tests.smoke.console.pages.ConsolePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.BROKER_NODE_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.BROKER_BUTTON_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.COLLAPSE_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.EXPAND_BUTTON;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.NODE_TITLE_LOCATOR;

public class ArtemisTreePage extends ConsolePage {


   public ArtemisTreePage(WebDriver driver) {
      super(driver);
   }

   public void expandTree(int timeout) {
      waitForElementToBeVisible(EXPAND_BUTTON, timeout);
      WebElement element = driver.findElement(EXPAND_BUTTON);
      Actions actions = new Actions(driver);
      actions.moveToElement(element).click().perform();
      waitForElementToBeVisible(COLLAPSE_BUTTON, timeout);
   }


   public void collapseTree(int timeout) {
      waitForElementToBeVisible(COLLAPSE_BUTTON, timeout);
      WebElement element = driver.findElement(COLLAPSE_BUTTON);
      Actions actions = new Actions(driver);
      actions.moveToElement(element).click().perform();
      waitForElementToBeVisible(EXPAND_BUTTON, timeout);
   }

   public String getNodeTitle() {
      return driver.findElement(NODE_TITLE_LOCATOR).getText();
   }

   public AttributesPage selectBrokerNode() {
      WebElement element = driver.findElement(BROKER_NODE_LOCATOR).findElement(BROKER_BUTTON_LOCATOR);
      Actions actions = new Actions(driver);
      actions.moveToElement(element).click().perform();
      return new AttributesPage(driver);
   }
}
