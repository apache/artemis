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

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.Iterator;
import java.util.List;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.H2_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TABLE_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TD_TAG_LOCATOR;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.TR_TAG_LOCATOR;

public class AttributesPage extends ArtemisTreePage {
   public AttributesPage(WebDriver driver) {
      super(driver);
   }


   public void selectAttribute(String name) {
      List<WebElement> rows = driver.findElement(TABLE_TAG_LOCATOR).findElements(TR_TAG_LOCATOR);
      for (Iterator<WebElement> iterator = rows.iterator(); iterator.hasNext(); ) {
         WebElement row = iterator.next();
         List<WebElement> cols = row.findElements(TD_TAG_LOCATOR);
         if (cols.size() == 2 && name.equals(cols.get(0).getText())) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView();", cols.get(0));
            Actions actions = new Actions(driver);
            actions.moveToElement(cols.get(0)).click().perform();
            break;
         }
      }
   }

   public String getAttributevalue(String name) {
      String value = "";
      try {
         List<WebElement> rows = driver.findElement(TABLE_TAG_LOCATOR).findElements(TR_TAG_LOCATOR);
         for (Iterator<WebElement> iterator = rows.iterator(); iterator.hasNext(); ) {
            WebElement row = iterator.next();
            List<WebElement> cols = row.findElements(TD_TAG_LOCATOR);
            if (cols.size() == 2 && name.equals(cols.get(0).getText())) {
               value = cols.get(1).getText();
               break;
            }
         }
      } catch (Exception e) {
         return value;
      }
      return value;
   }

   public boolean isAttributeDisplayed(String attributeName) {
      try {
         return ("Attribute: " + attributeName).equals(driver.findElement(H2_TAG_LOCATOR).getText());
      } catch (Exception e) {
         return false;
      }
   }
}
