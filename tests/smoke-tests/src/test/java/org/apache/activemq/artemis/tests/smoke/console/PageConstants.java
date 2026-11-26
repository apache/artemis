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

import org.openqa.selenium.By;

public class PageConstants {
   //String Constants
   public static final String WEB_URL_PATH = "/console";

   public static final String HAWTIO_HEADER_USER_DROPDOWN_TOGGLE = "hawtio-header-user-dropdown-toggle";

   public static final String A_CONTAINS_TEXT_LOG_OUT = "//span[contains(text(),'Log out')]";

   public static final String H2 = "h2";

   public static final String TD = "td";

   public static final String TH = "th";

   public static final String TR = "tr";


   public static final String A = "a";

   public static final String BODY = "body";

   public static final String TABLE = "table";

   public static final String BUTTON = "button";

   //column headers
   public static final String COLUMN_MESSAGE_ID = "Message ID";

   public static final String COLUMN_ORIGINAL_QUEUE = "Original Queue";

   // Tag Locators
   public static final By STATUS_TAB_LOCATOR = By.xpath("//span[contains(text(),'Status')]");

   public static final By H2_TAG_LOCATOR = By.tagName(H2);

   public static final By TH_TAG_LOCATOR = By.tagName(TH);

   public static final By TD_TAG_LOCATOR = By.tagName(TD);

   public static final By TR_TAG_LOCATOR = By.tagName(TR);

   public static final By A_TAG_LOCATOR = By.tagName(A);

   public static final By BODY_TAG_LOCATOR = By.tagName(BODY);

   public static final By TABLE_TAG_LOCATOR = By.tagName(TABLE);

   public static final By BUTTON_LOCATOR = By.tagName(BUTTON);

   //Branding and Image locations
   //this one comes from artemis-console/src/main/webapp/hawtconfig.json
   public static final String DEFAULT_CONSOLE_LOGIN_BRAND_IMAGE = "/img/artemis-logo.svg";
   //this one hides the above and uses a version from branding's artemis.css, so it looks better
   //in dark mode (where the dark background doesn't change)
   public static final String DEFAULT_CONSOLE_LOGIN_BRAND_OVERRIDE_IMAGE = "/img/artemis-logo-white.svg";

   public static final String DEFAULT_CONSOLE_NAME = "Artemis";

   public static final By BRAND_LOCATOR = By.xpath("//img[@class='pf-v6-c-brand']");

   //Component Locators
   public static final By USERNAME_LOCATOR = By.id("pf-login-username-id");

   public static final By PASSWORD_LOCATOR = By.id("pf-login-password-id");

   public static final By LOGOUT_DROPDOWN_LOCATOR = By.id(HAWTIO_HEADER_USER_DROPDOWN_TOGGLE);

   public static final By LOGIN_BUTTON_LOCATOR = By.xpath("//button[@type='submit']");

   public static final By LOGIN_ERROR_LOCATOR = By.cssSelector(".pf-v6-c-helper-text__item.pf-m-error");


   public static final By MESSAGE_TEXT_EDITOR_LOCATOR = By.cssSelector("textarea[id='message-body']");

   public static final By USE_LOGIN_LOCATOR = By.id("uselogon");

   public static final By LOGOUT_MENU_ITEM_LOCATOR = By.xpath(A_CONTAINS_TEXT_LOG_OUT);

   public static final By USER_DROPDOWN_MENU_LOCATOR = By.id("userDropdownMenu");

   public static final By DATA_TABLE = By.id("data-table");

   public static final By DATA_ROW_CONTEXT_MENU = By.cssSelector("div[class='pf-v6-c-menu']");

   public static final By MESSAGE_TABLE = By.id("message-table");

   public static final By TABLE_ROW_LOCATOR = By.cssSelector("tr[data-ouia-component-type='PF6/TableRow']");

   public static final By VIEW_MESSAGE_TITLE_LOCATOR = By.xpath("//h2[contains(text(),'Viewing Message')]");


   public static final By QUEUES_TAB = By.xpath("//button/span[contains(text(),'Queues')]");

   public static final By QUEUES_TAB_SELECTED = By.cssSelector("button[class*=pf-m-current]");

   public static final By ADDRESSES_TAB = By.xpath("//button[contains(@class, 'pf-v6-c-nav__link')]/span[contains(text(),'Addresses')]");

   public static final By SEND_MESSAGE_BUTTON = By.xpath("//span[contains(text(),'Send Message')]");

   public static final By MANAGE_COLUMNS_BUTTON = By.xpath("//button[contains(span/text(),'Manage Columns')]");

   public static final By SAVE_BUTTON = By.xpath("//button[contains(span/text(),'Save')]");

   public static final By SEND_MESSAGE_TITLE = By.xpath("//h1[contains(text(),'Send Message')]");

   public static final By NO_NODE_TITLE = By.xpath("//h1[contains(text(),'Send Message')]");

   public static final By MESSAGE_VIEW_QUEUES_BUTTON = By.xpath("//button[contains(@class, 'pf-m-link')][contains(span/text(), 'Back to Queue list')]");

   public static final By MESSAGE_TABLE_QUEUES_BUTTON = By.id("message-table-queues-button");

   public static final By MESSAGE_VIEW_BODY = By.id("code-content");

   public static final By EXPAND_BUTTON = By.xpath("//button[contains(span/text(), 'Expand all')]");

   public static final By COLLAPSE_BUTTON = By.xpath("//button[contains(span/text(), 'Collapse all')]");

   public static final By NODE_TITLE_LOCATOR = By.cssSelector("h1.pf-v6-c-empty-state__title-text");

   public static final By BROKER_BUTTON_LOCATOR = By.xpath("//button[contains(text(), 'Broker 0.0.0.0')]");

   public static final By ALERT_LOCATOR = By.xpath("//ul/li/div[contains(@class, 'pf-v6-c-alert')]");
}
