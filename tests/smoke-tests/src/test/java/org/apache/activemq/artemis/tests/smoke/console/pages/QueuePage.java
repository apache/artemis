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

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.A_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.COLUMN_MESSAGE_ID;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.COLUMN_ORIGINAL_QUEUE;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.MESSAGE_TABLE;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TABLE_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TD_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TR_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.VIEW_MESSAGE_TITLE_LOCATOR;

public class QueuePage extends ArtemisPage {

   public QueuePage(WebDriver driver) {
      super(driver);
   }

   public MessagePage getMessagePage(int index, int timeout) {
      int col = getIndexOfColumn(COLUMN_MESSAGE_ID);
      WebElement element = driver.findElement(TABLE_TAG_LOCATOR).findElements(TD_TAG_LOCATOR).get(col).findElement(A_TAG_LOCATOR);
      element.click();
      waitForElementToBeVisible(VIEW_MESSAGE_TITLE_LOCATOR, timeout);

      return new MessagePage(driver);
   }

   public long getMessageId(int index) {
      WebElement messageRowWebElement = driver.findElement(MESSAGE_TABLE).findElements(TR_TAG_LOCATOR).get(index + 1);

      String messageIdText = messageRowWebElement.findElements(TD_TAG_LOCATOR).get(
         getIndexOfColumn(COLUMN_MESSAGE_ID)).findElement(By.tagName("a")).getText();

      return Long.parseLong(messageIdText);
   }

   public String getMessageOriginalQueue(int index) {
      WebElement messageRowWebElement = driver.findElement(MESSAGE_TABLE).findElements(TR_TAG_LOCATOR).get(index + 1);

      int col = getIndexOfColumn(COLUMN_ORIGINAL_QUEUE);
      System.out.println("col = " + col);
      String messageOriginalQueue = messageRowWebElement.findElements(TD_TAG_LOCATOR).get(col).getText();
      return messageOriginalQueue;
   }
}
