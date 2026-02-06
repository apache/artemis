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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.cli.commands.Create;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

@ExtendWith(ParameterizedTestExtension.class)
public abstract class ConsoleTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final String SERVER_NAME_CONSOLE = "console";
   protected static final String SERVER_NAME_CONSOLE_BROKER_SECURITY = "console-broker-security";

   protected static final String SERVER_ADMIN_USERNAME = "admin";
   protected static final String SERVER_ADMIN_PASSWORD = "admin";

   protected static final String BROWSER_CHROME = "chrome";
   protected static final String BROWSER_FIREFOX = "firefox";

   protected static final int DEFAULT_TIMEOUT = 10000;

   protected String serverName;
   protected String browser;
   protected WebDriver driver;
   protected String webServerUrl;
   private BrowserWebDriverContainer browserWebDriverContainer;

   @BeforeAll
   public static void createServers() throws Exception {
      File consoleServerLocation = getFileServerLocation(SERVER_NAME_CONSOLE);
      deleteDirectory(consoleServerLocation);

      {
         String httpHost = System.getProperty("sts-http-host", "localhost");
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq,connections,sessions,consumers,producers,addresses,queues,deleteAddresses")
            .setUser(SERVER_ADMIN_USERNAME).setPassword(SERVER_ADMIN_PASSWORD)
            .setAllowAnonymous(false).setNoWeb(false).setArtemisInstance(consoleServerLocation)
            .setConfiguration("./src/main/resources/servers/" + SERVER_NAME_CONSOLE)
            .setArgs("--http-host", httpHost, "--http-port", "8161");
         cliCreateServer.createServer();
      }

      File consoleBrokerSecurityServerLocation = getFileServerLocation(SERVER_NAME_CONSOLE_BROKER_SECURITY);
      deleteDirectory(consoleBrokerSecurityServerLocation);

      {
         String httpHost = System.getProperty("sts-http-host", "localhost");
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq,connections,sessions,consumers,producers,addresses,queues,deleteAddresses")
            .setUser(SERVER_ADMIN_USERNAME).setPassword(SERVER_ADMIN_PASSWORD)
            .setAllowAnonymous(false).setNoWeb(false).setArtemisInstance(consoleBrokerSecurityServerLocation)
            .setConfiguration("./src/main/resources/servers/" + SERVER_NAME_CONSOLE_BROKER_SECURITY)
            .setArgs("--http-host", httpHost, "--http-port", "8161", "--java-options",
               "-Djava.rmi.server.hostname=localhost -Djavax.management.builder.initial=org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder");
         cliCreateServer.createServer();
      }
   }

   @Parameters(name = "browser={0}, server={1}")
   public static Collection getParameters() {
      String webdriverBrowsers = System.getProperty("webdriver.browsers");
      if (webdriverBrowsers == null) {
         webdriverBrowsers = BROWSER_CHROME + "," + BROWSER_FIREFOX;
      }
      String[] browsers = webdriverBrowsers.split(",");
      String[] servers = new String[]{SERVER_NAME_CONSOLE, SERVER_NAME_CONSOLE_BROKER_SECURITY};

      return Arrays.stream(browsers)
          .flatMap(browser -> Arrays.stream(servers)
              .map(server -> new Object[]{browser, server}))
          .collect(Collectors.toList());
   }

   public ConsoleTest(String browser, String serverName) {
      this.browser = browser;
      this.serverName = serverName;
      this.webServerUrl = String.format("%s://%s:%d", "http", System.getProperty("sts-http-host", "localhost"), 8161);
   }

   @BeforeEach
   public void before() throws Exception {
      File jolokiaAccessFile = Paths.get(getServerLocation(serverName), "etc", Create.ETC_JOLOKIA_ACCESS_XML).toFile();
      String jolokiaAccessContent = FileUtils.readFileToString(jolokiaAccessFile, "UTF-8");
      if (!jolokiaAccessContent.contains("testcontainers")) {
         jolokiaAccessContent = jolokiaAccessContent.replaceAll("<strict-checking/>",
            "<allow-origin>*://host.testcontainers.internal*</allow-origin><strict-checking/>");
         FileUtils.writeStringToFile(jolokiaAccessFile, jolokiaAccessContent, "UTF-8");
      }

      cleanupData(serverName);
      disableCheckThread();
      startServer(serverName, 0, 0);
      ServerUtil.waitForServerToStart(0, SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, 30000);


      // The ConsoleTest checks the web console using the selenium framework[1].
      // The tests can be executed on the Chrome and Firefox browser by using
      // a remote server, local browsers or testcontainers[2].
      // To configure the browsers on which execute the tests set the `webdriver.browsers` property with
      // the comma-separated list of the browsers, i.e. `-Dwebdriver.browsers=chrome,firefox`.
      // To use a remote server set the `webdriver.remote.server` property with the URL
      // of the server, ie -Dwebdriver.remote.server=http://localhost:4444/wd/hub
      // To use your local Google Chrome browser download the WebDriver for Chrome[3] and set
      // the `webdriver.chrome.driver` property with the WebDriver path, ie
      // -Dwebdriver.chrome.driver=/home/developer/chromedriver_linux64/chromedriver
      // To use your local Firefox browser download the WebDriver for Firefox[4] and set
      // the `webdriver.gecko.driver` property with the WebDriver path, ie
      // -Dwebdriver.gecko.driver=/home/developer/geckodriver-v0.28.0-linux64/geckodriver
      // To use the testcontainers[2] install docker.
      //
      // [1] https://github.com/SeleniumHQ/selenium
      // [2] https://www.testcontainers.org/modules/webdriver_containers
      // [3] https://chromedriver.chromium.org/
      // [4] https://github.com/mozilla/geckodriver/

      try {
         String webdriverName;
         String webdriverLocation;
         String webdriverArguments;
         String webdriverRemoteServer;
         MutableCapabilities browserOptions;
         Function<MutableCapabilities, WebDriver> webDriverConstructor;
         BiConsumer<MutableCapabilities, String[]> webdriverArgumentsSetter;

         if (BROWSER_CHROME.equals(browser)) {
            webdriverName = "chrome";
            browserOptions = new ChromeOptions();
            ((ChromeOptions)browserOptions).setExperimentalOption("prefs", Collections.singletonMap("profile.password_manager_leak_detection", false));
            webDriverConstructor = browserOpts -> new ChromeDriver((ChromeOptions)browserOpts);
            webdriverArgumentsSetter = (browserOpts, arguments) -> ((ChromeOptions) browserOpts).addArguments(arguments);
         } else if (BROWSER_FIREFOX.equals(browser)) {
            webdriverName = "gecko";
            browserOptions = new FirefoxOptions();
            webDriverConstructor = browserOpts -> new FirefoxDriver((FirefoxOptions)browserOpts);
            webdriverArgumentsSetter = (browserOpts, arguments) -> ((FirefoxOptions) browserOpts).addArguments(arguments);
         } else {
            throw new IllegalStateException("Unexpected browser: " + browser);
         }

         webdriverArguments = System.getProperty("webdriver." + webdriverName + ".driver.args");
         if (webdriverArguments != null) {
            webdriverArgumentsSetter.accept(browserOptions, webdriverArguments.split(","));
         }

         webdriverLocation = System.getProperty("webdriver." + webdriverName + ".driver");

         webdriverRemoteServer = System.getProperty("webdriver." + webdriverName + ".remote.server");
         if (webdriverRemoteServer == null) {
            webdriverRemoteServer = System.getProperty("webdriver.remote.server");
         }

         if (webdriverRemoteServer != null) {
            driver = new RemoteWebDriver(new URL(webdriverRemoteServer), browserOptions);
         } else if (webdriverLocation != null) {
            driver = webDriverConstructor.apply(browserOptions);
         } else {
            Testcontainers.exposeHostPorts(8161);
            webServerUrl = webServerUrl.replace("localhost", "host.testcontainers.internal");
            browserWebDriverContainer = new BrowserWebDriverContainer().withCapabilities(browserOptions);
            browserWebDriverContainer.start();
            driver = browserWebDriverContainer.getWebDriver();
         }
      } catch (Exception e) {
         assumeTrue(false, "Error on loading the web driver: " + e.getMessage());
      }

      // Wait for server console
      WebDriverWait loadWebDriverWait = new WebDriverWait(
         driver, Duration.ofMillis(30000));

      logger.info("Loading " + webServerUrl);
      loadWebDriverWait.until((Function<WebDriver, Object>) webDriver -> {
         try {
            webDriver.get(webServerUrl);
            return true;
         } catch (Exception ignore) {
            return false;
         }
      });
   }

   @AfterEach
   public void stopWebDriver() {
      if (browserWebDriverContainer != null) {
         browserWebDriverContainer.stop();
      } else if (driver != null) {
         driver.close();
      }
   }
}
