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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.smoke.console.pages.LoginPage;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.DEFAULT_CONSOLE_LOGIN_BRAND_IMAGE;
import static org.apache.activemq.artemis.tests.smoke.console.PageConstants.WEB_URL_PATH;
//Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)

public class LoginTest extends ConsoleTest {

   public LoginTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @TestTemplate
   public void testLogin() {
      driver.get(webServerUrl + WEB_URL_PATH);
      LoginPage loginPage = new LoginPage(driver);
      loginPage.loginValidUser(SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);
   }

   @TestTemplate
   public void testLoginBrand() {
      String expectedBrandImage = webServerUrl + WEB_URL_PATH + System.getProperty(
         "artemis.console.login.brand.image", DEFAULT_CONSOLE_LOGIN_BRAND_IMAGE);

      driver.get(webServerUrl + WEB_URL_PATH);
      LoginPage loginPage = new LoginPage(driver);
      assertEquals(expectedBrandImage, loginPage.getBrandImage(DEFAULT_TIMEOUT));
   }
}
