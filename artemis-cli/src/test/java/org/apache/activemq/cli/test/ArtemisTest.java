/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.cli.test;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.CLIException;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.Create;
import org.apache.activemq.artemis.cli.commands.Mask;
import org.apache.activemq.artemis.cli.commands.Run;
import org.apache.activemq.artemis.cli.commands.PrintVersion;
import org.apache.activemq.artemis.cli.commands.queue.StatQueue;
import org.apache.activemq.artemis.cli.commands.user.AddUser;
import org.apache.activemq.artemis.cli.commands.user.ListUser;
import org.apache.activemq.artemis.cli.commands.user.RemoveUser;
import org.apache.activemq.artemis.cli.commands.user.ResetUser;
import org.apache.activemq.artemis.cli.commands.util.SyncCalculation;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.impl.SecurityStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.management.ManagementContext;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.HashProcessor;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.apache.activemq.artemis.utils.StringUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test to validate that the CLI doesn't throw improper exceptions when invoked.
 */
public class ArtemisTest extends CliTestBase {
   private static final Logger log = Logger.getLogger(ArtemisTest.class);

   // some tests will set this, as some security methods will need to know the server the CLI started
   private ActiveMQServer server;

   @Before
   @Override
   public void setup() throws Exception {
      setupAuth();
      super.setup();
   }

   long timeBefore;

   @Before
   public void setupScanTimeout() throws Exception {
      timeBefore = ActiveMQDefaultConfiguration.getDefaultAddressQueueScanPeriod();
      org.apache.activemq.artemis.api.config.ActiveMQDefaultConfigurationTestAccessor.setDefaultAddressQueueScanPeriod(100);
   }

   @After
   public void resetScanTimeout() throws Exception {
      org.apache.activemq.artemis.api.config.ActiveMQDefaultConfigurationTestAccessor.setDefaultAddressQueueScanPeriod(timeBefore);
   }

   @Test
   public void invalidCliDoesntThrowException() {
      testCli("--silent", "create");
   }

   @Test
   public void invalidPathDoesntThrowException() {
      if (isWindows()) {
         testCli("create", "zzzzz:/rawr", "--silent");
      } else {
         testCli("create", "/rawr", "--silent");
      }
   }

   @Test
   public void testSupportsLibaio() throws Exception {
      Create x = new Create();
      x.setInstance(new File("/tmp/foo"));
      x.supportsLibaio();
   }

   @Test
   public void testSync() throws Exception {
      int writes = 2;
      int tries = 5;
      long totalAvg = SyncCalculation.syncTest(temporaryFolder.getRoot(), 4096, writes, tries, true, true, true, "file.tmp", 1, JournalType.NIO);
      log.debug("TotalAvg = " + totalAvg);
      long nanoTime = SyncCalculation.toNanos(totalAvg, writes, false);
      log.debug("nanoTime avg = " + nanoTime);
      assertEquals(0, LibaioContext.getTotalMaxIO());

   }

   @Test
   public void testSimpleCreate() throws Exception {
      //instance1: default using http
      File instance1 = new File(temporaryFolder.getRoot(), "instance1");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune");
   }


   @Test
   public void testCreateDB() throws Exception {
      File instance1 = new File(temporaryFolder.getRoot(), "instance1");
      Artemis.internalExecute("create", instance1.getAbsolutePath(), "--silent", "--jdbc");
   }


   @Test
   public void testSimpleCreateMapped() throws Throwable {
      try {
         //instance1: default using http
         File instance1 = new File(temporaryFolder.getRoot(), "instance1");
         Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--mapped", "--no-autotune");
      } catch (Throwable e) {
         e.printStackTrace();
         throw e;
      }
   }

   @Test
   public void testOpenwireSupportAdvisoryDisabledByDefault() throws Exception {
      FileConfiguration configuration = createFileConfiguration("supportAdvisory",
                                                                "--force", "--silent", "--no-web", "--no-autotune");
      Map<String, Object> params = configuration.getAcceptorConfigurations()
         .stream().filter(tc -> tc.getName().equals("artemis")).findFirst().get().getExtraParams();
      Assert.assertFalse(Boolean.parseBoolean(params.get("supportAdvisory").toString()));
      Assert.assertFalse(Boolean.parseBoolean(params.get("suppressInternalManagementObjects").toString()));
   }

   @Test
   public void testOpenwireEnabledSupportAdvisory() throws Exception {
      FileConfiguration configuration = createFileConfiguration("supportAdvisory",
                                                                "--force", "--silent", "--no-web", "--no-autotune",
                                                                "--support-advisory", "--suppress-internal-management-objects");
      Map<String, Object> params = configuration.getAcceptorConfigurations()
         .stream().filter(tc -> tc.getName().equals("artemis")).findFirst().get().getExtraParams();
      Assert.assertTrue(Boolean.parseBoolean(params.get("supportAdvisory").toString()));
      Assert.assertTrue(Boolean.parseBoolean(params.get("suppressInternalManagementObjects").toString()));
   }


   private FileConfiguration createFileConfiguration(String folder, String... createAdditionalArg) throws Exception {
      File instanceFolder = temporaryFolder.newFolder(folder);

      setupAuth(instanceFolder);

      // This is usually set when run from the command line via artemis.profile
      Run.setEmbedded(true);
      final String[] createArgs = new String[2 + (createAdditionalArg == null ? 0 : createAdditionalArg.length)];
      createArgs[0] = "create";
      createArgs[1] = instanceFolder.getAbsolutePath();
      if (createAdditionalArg != null) {
         System.arraycopy(createAdditionalArg, 0, createArgs, 2, createAdditionalArg.length);
      }
      Artemis.main(createArgs);
      System.setProperty("artemis.instance", instanceFolder.getAbsolutePath());

      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(new File(instanceFolder, "./etc/broker.xml").toURI().toString());
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      return fc;
   }

   @Test
   public void testWebConfig() throws Exception {
      setupAuth();
      Run.setEmbedded(true);
      //instance1: default using http
      File instance1 = new File(temporaryFolder.getRoot(), "instance1");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune");
      File bootstrapFile = new File(new File(instance1, "etc"), "bootstrap.xml");
      assertTrue(bootstrapFile.exists());
      Document config = parseXml(bootstrapFile);
      Element webElem = (Element) config.getElementsByTagName("web").item(0);

      String bindAttr = webElem.getAttribute("bind");
      String bindStr = "http://" + Create.HTTP_HOST + ":" + Create.HTTP_PORT;

      assertEquals(bindAttr, bindStr);
      //no any of those
      assertFalse(webElem.hasAttribute("keyStorePath"));
      assertFalse(webElem.hasAttribute("keyStorePassword"));
      assertFalse(webElem.hasAttribute("clientAuth"));
      assertFalse(webElem.hasAttribute("trustStorePath"));
      assertFalse(webElem.hasAttribute("trustStorePassword"));

      //instance2: https
      File instance2 = new File(temporaryFolder.getRoot(), "instance2");
      Artemis.main("create", instance2.getAbsolutePath(), "--silent", "--ssl-key", "etc/keystore", "--ssl-key-password", "password1", "--no-fsync", "--no-autotune");
      bootstrapFile = new File(new File(instance2, "etc"), "bootstrap.xml");
      assertTrue(bootstrapFile.exists());
      config = parseXml(bootstrapFile);
      webElem = (Element) config.getElementsByTagName("web").item(0);

      bindAttr = webElem.getAttribute("bind");
      bindStr = "https://localhost:" + Create.HTTP_PORT;
      assertEquals(bindAttr, bindStr);

      String keyStr = webElem.getAttribute("keyStorePath");
      assertEquals("etc/keystore", keyStr);
      String keyPass = webElem.getAttribute("keyStorePassword");
      assertEquals("password1", keyPass);

      assertFalse(webElem.hasAttribute("clientAuth"));
      assertFalse(webElem.hasAttribute("trustStorePath"));
      assertFalse(webElem.hasAttribute("trustStorePassword"));

      //instance3: https with clientAuth
      File instance3 = new File(temporaryFolder.getRoot(), "instance3");
      Artemis.main("create", instance3.getAbsolutePath(), "--silent", "--ssl-key", "etc/keystore", "--ssl-key-password", "password1", "--use-client-auth", "--ssl-trust", "etc/truststore", "--ssl-trust-password", "password2", "--no-fsync", "--no-autotune");
      bootstrapFile = new File(new File(instance3, "etc"), "bootstrap.xml");
      assertTrue(bootstrapFile.exists());

      byte[] contents = Files.readAllBytes(bootstrapFile.toPath());
      String cfgText = new String(contents);
      log.debug("confg: " + cfgText);

      config = parseXml(bootstrapFile);
      webElem = (Element) config.getElementsByTagName("web").item(0);

      bindAttr = webElem.getAttribute("bind");
      bindStr = "https://localhost:" + Create.HTTP_PORT;
      assertEquals(bindAttr, bindStr);

      keyStr = webElem.getAttribute("keyStorePath");
      assertEquals("etc/keystore", keyStr);
      keyPass = webElem.getAttribute("keyStorePassword");
      assertEquals("password1", keyPass);

      String clientAuthAttr = webElem.getAttribute("clientAuth");
      assertEquals("true", clientAuthAttr);
      String trustPathAttr = webElem.getAttribute("trustStorePath");
      assertEquals("etc/truststore", trustPathAttr);
      String trustPass = webElem.getAttribute("trustStorePassword");
      assertEquals("password2", trustPass);
   }

   @Test
   public void testSecurityManagerConfiguration() throws Exception {
      setupAuth();
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance1");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune", "--no-web", "--no-stomp-acceptor", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-hornetq-acceptor");
      File originalBootstrapFile = new File(new File(instance1, "etc"), "bootstrap.xml");
      assertTrue(originalBootstrapFile.exists());

      // modify the XML programmatically since we don't support setting this stuff via the CLI
      Document config = parseXml(originalBootstrapFile);
      Node broker = config.getChildNodes().item(1);
      NodeList list = broker.getChildNodes();
      Node server = null;

      for (int i = 0; i < list.getLength(); i++) {
         Node node = list.item(i);
         if ("jaas-security".equals(node.getNodeName())) {
            // get rid of the default jaas-security config
            broker.removeChild(node);
         }
         if ("server".equals(node.getNodeName())) {
            server = node;
         }
      }

      // add the new security-plugin config
      Element securityPluginElement = config.createElement("security-manager");
      securityPluginElement.setAttribute("class-name", TestSecurityManager.class.getName());
      Element property1 = config.createElement("property");
      property1.setAttribute("key", "myKey1");
      property1.setAttribute("value", "myValue1");
      securityPluginElement.appendChild(property1);
      Element property2 = config.createElement("property");
      property2.setAttribute("key", "myKey2");
      property2.setAttribute("value", "myValue2");
      securityPluginElement.appendChild(property2);
      broker.insertBefore(securityPluginElement, server);

      originalBootstrapFile.delete();

      // write the modified config into the bootstrap.xml file
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      DOMSource source = new DOMSource(config);
      StreamResult streamResult = new StreamResult(originalBootstrapFile);
      transformer.transform(source, streamResult);

      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>)result).getB();

      // trigger security
      ServerLocator locator = ActiveMQClient.createServerLocator("tcp://127.0.0.1:61616");
      ClientSessionFactory sessionFactory = locator.createSessionFactory();
      ClientSession session = sessionFactory.createSession("myUser", "myPass", false, true, true, false, 0);
      ClientProducer producer = session.createProducer("foo");
      producer.send(session.createMessage(true));

      // verify results
      assertTrue(activeMQServer.getSecurityManager() instanceof TestSecurityManager);
      TestSecurityManager securityPlugin = (TestSecurityManager) activeMQServer.getSecurityManager();
      assertTrue(securityPlugin.properties.containsKey("myKey1"));
      assertEquals("myValue1", securityPlugin.properties.get("myKey1"));
      assertTrue(securityPlugin.properties.containsKey("myKey2"));
      assertEquals("myValue2", securityPlugin.properties.get("myKey2"));
      assertTrue(securityPlugin.validateUser);
      assertEquals("myUser", securityPlugin.validateUserName);
      assertEquals("myPass", securityPlugin.validateUserPass);
      assertTrue(securityPlugin.validateUserAndRole);
      assertEquals("myUser", securityPlugin.validateUserAndRoleName);
      assertEquals("myPass", securityPlugin.validateUserAndRolePass);
      assertEquals(CheckType.SEND, securityPlugin.checkType);

      activeMQServer.stop();
      stopServer();
   }

   @Test
   public void testStopManagementContext() throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      ManagementContext managementContext = ((Pair<ManagementContext, ActiveMQServer>)result).getA();
      ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>)result).getB();
      activeMQServer.stop();
      assertTrue(Wait.waitFor(() -> managementContext.isStarted() == false, 5000, 200));
      stopServer();
   }

   @Test
   public void testUserCommandJAAS() throws Exception {
      testUserCommand(false);
   }

   @Test
   public void testUserCommandBasic() throws Exception {
      testUserCommand(true);
   }

   private void testUserCommand(boolean basic) throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--require-login", "--security-manager", basic ? "basic" : "jaas");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object runResult = Artemis.internalExecute("run");
      server = ((Pair<ManagementContext, ActiveMQServer>)runResult).getB();

      try {
         File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
         File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");

         ListUser listCmd = new ListUser();
         TestActionContext context = new TestActionContext();
         listCmd.setUser("admin");
         listCmd.setPassword("admin");
         listCmd.execute(context);

         String result = context.getStdout();
         log.debug("output1:\n" + result);

         //default only one user admin with role amq
         assertTrue(result.contains("\"admin\"(amq)"));
         checkRole("admin", roleFile, basic, "amq");

         //add a simple user
         AddUser addCmd = new AddUser();
         addCmd.setUserCommandUser("guest");
         addCmd.setUserCommandPassword("guest123");
         addCmd.setRole("admin");
         addCmd.setUser("admin");
         addCmd.setPassword("admin");
         addCmd.execute(new TestActionContext());

         //verify use list cmd
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output2:\n" + result);

         assertTrue(result.contains("\"admin\"(amq)"));
         assertTrue(result.contains("\"guest\"(admin)"));

         checkRole("guest", roleFile, basic, "admin");
         assertTrue(checkPassword("guest", "guest123", userFile, basic));

         //add a user with 2 roles
         addCmd = new AddUser();
         addCmd.setUserCommandUser("scott");
         addCmd.setUserCommandPassword("tiger");
         addCmd.setRole("admin,operator");
         addCmd.setUser("admin");
         addCmd.setPassword("admin");
         addCmd.execute(ActionContext.system());

         //verify
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output3:\n" + result);

         assertTrue(result.contains("\"admin\"(amq)"));
         assertTrue(result.contains("\"guest\"(admin)"));
         assertTrue(result.contains("\"scott\"(admin,operator)"));

         checkRole("scott", roleFile, basic, "admin", "operator");
         assertTrue(checkPassword("scott", "tiger", userFile, basic));

         //add an existing user
         addCmd = new AddUser();
         addCmd.setUserCommandUser("scott");
         addCmd.setUserCommandPassword("password");
         addCmd.setRole("visitor");
         addCmd.setUser("admin");
         addCmd.setPassword("admin");
         context = new TestActionContext();
         addCmd.execute(context);
         result = context.getStderr();
         assertTrue(result.contains("Failed to add user scott. Reason: AMQ229223: User scott already exists"));

         //check existing users are intact
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output4:\n" + result);

         assertTrue(result.contains("\"admin\"(amq)"));
         assertTrue(result.contains("\"guest\"(admin)"));
         assertTrue(result.contains("\"scott\"(admin,operator)"));

         //remove a user
         RemoveUser rmCmd = new RemoveUser();
         rmCmd.setUserCommandUser("guest");
         rmCmd.setUser("admin");
         rmCmd.setPassword("admin");
         rmCmd.execute(ActionContext.system());

         //check
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output5:\n" + result);

         assertTrue(result.contains("\"admin\"(amq)"));
         assertFalse(result.contains("\"guest\"(admin)"));
         assertTrue(result.contains("\"scott\"(admin,operator)") || result.contains("\"scott\"(operator,admin)"));
         assertTrue(result.contains("Total: 2"));

         //remove another
         rmCmd = new RemoveUser();
         rmCmd.setUserCommandUser("scott");
         rmCmd.setUser("admin");
         rmCmd.setPassword("admin");
         rmCmd.execute(ActionContext.system());

         //check
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output6:\n" + result);

         assertTrue(result.contains("\"admin\"(amq)"));
         assertFalse(result.contains("\"guest\"(admin)"));
         assertFalse(result.contains("\"scott\"(admin,operator)") || result.contains("\"scott\"(operator,admin)"));
         assertTrue(result.contains("Total: 1"));

         //remove non-exist
         rmCmd = new RemoveUser();
         rmCmd.setUserCommandUser("alien");
         rmCmd.setUser("admin");
         rmCmd.setPassword("admin");
         context = new TestActionContext();
         rmCmd.execute(context);
         result = context.getStderr();
         assertTrue(result.contains("Failed to remove user alien. Reason: AMQ229224: User alien does not exist"));

         //check
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output7:\n" + result);
         assertTrue(result.contains("\"admin\"(amq)"));
         assertTrue(result.contains("Total: 1"));

         //now remove last
         rmCmd = new RemoveUser();
         rmCmd.setUserCommandUser("admin");
         rmCmd.setUser("admin");
         rmCmd.setPassword("admin");
         rmCmd.execute(ActionContext.system());

         //check
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output8:\n" + result);

         assertTrue(result.contains("Total: 0"));
      } finally {
         stopServer();
      }
   }

   @Test
   public void testUserCommandViaManagementPlaintextJAAS() throws Exception {
      internalTestUserCommandViaManagement(true, false);
   }

   @Test
   public void testUserCommandViaManagementHashedJAAS() throws Exception {
      internalTestUserCommandViaManagement(false, false);
   }

   @Test
   public void testUserCommandViaManagementPlaintextBasic() throws Exception {
      internalTestUserCommandViaManagement(true, true);
   }

   @Test
   public void testUserCommandViaManagementHashedBasic() throws Exception {
      internalTestUserCommandViaManagement(false, true);
   }

   private void internalTestUserCommandViaManagement(boolean plaintext, boolean basic) throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor", "--security-manager", basic ? "basic" : "jaas");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      server = ((Pair<ManagementContext, ActiveMQServer>)result).getB();
      ActiveMQServerControl activeMQServerControl = server.getActiveMQServerControl();

      File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
      File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");

      //default only one user admin with role amq
      String jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");
      checkRole("admin", roleFile, basic, "amq");

      //add a simple user
      activeMQServerControl.addUser("guest", "guest123", "admin", plaintext);

      //verify add
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin");
      checkRole("guest", roleFile, basic, "admin");
      assertTrue(checkPassword("guest", "guest123", userFile, basic));
      assertEquals(plaintext, !PasswordMaskingUtil.isEncMasked(getStoredPassword("guest", userFile, basic)));

      //add a user with 2 roles
      activeMQServerControl.addUser("scott", "tiger", "admin,operator", plaintext);

      //verify add
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "operator");
      checkRole("scott", roleFile, basic, "admin", "operator");
      assertTrue(checkPassword("scott", "tiger", userFile, basic));
      assertEquals(plaintext, !PasswordMaskingUtil.isEncMasked(getStoredPassword("scott", userFile, basic)));

      try {
         activeMQServerControl.addUser("scott", "password", "visitor", plaintext);
         fail("should throw an exception if adding a existing user");
      } catch (IllegalArgumentException expected) {
      }

      //check existing users are intact
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "operator");

      //check listing with just one user
      jsonResult = activeMQServerControl.listUser("admin");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin", false);
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "admin", false);
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "operator", false);

      //check listing with another single user
      jsonResult = activeMQServerControl.listUser("guest");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq", false);
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "admin", false);
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "operator", false);

      //remove a user
      activeMQServerControl.removeUser("guest");
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin", false);
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "operator");

      //remove another
      activeMQServerControl.removeUser("scott");
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin", false);
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "admin", false);
      contains(JsonUtil.readJsonArray(jsonResult), "scott", "operator", false);

      //remove non-exist
      try {
         activeMQServerControl.removeUser("alien");
         fail("should throw exception when removing a non-existing user");
      } catch (IllegalArgumentException expected) {
      }

      //check
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");

      //now remove last
      activeMQServerControl.removeUser("admin");
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq", false);

      stopServer();
   }

   @Test
   public void testListUserWithMultipleRolesWithSpaces() throws Exception {
      try {
         Run.setEmbedded(true);
         File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
         System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
         Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor", "--security-manager", "jaas");
         System.setProperty("artemis.instance", instance1.getAbsolutePath());
         Object result = Artemis.internalExecute("run");
         server = ((Pair<ManagementContext, ActiveMQServer>) result).getB();
         ActiveMQServerControl activeMQServerControl = server.getActiveMQServerControl();

         File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
         BufferedWriter writer = Files.newBufferedWriter(Paths.get(userFile.getPath()));
         writer.write("");
         writer.write("user1 = pass1");
         writer.newLine();
         writer.write("user2 = pass2");
         writer.flush();
         writer.close();
         File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");
         writer = Files.newBufferedWriter(Paths.get(roleFile.getPath()));
         writer.write("");
         writer.write("role1 = user1, user2"); // the space here is what breaks the parsing
         writer.newLine();
         writer.write("role2 = user2");
         writer.flush();
         writer.close();

         String jsonResult = activeMQServerControl.listUser("");
         contains(JsonUtil.readJsonArray(jsonResult), "user2", "role1");
         contains(JsonUtil.readJsonArray(jsonResult), "user2", "role2");
         checkRole("user2", roleFile, false, "role1", "role2");
         assertTrue(checkPassword("user1", "pass1", userFile, false));
         assertTrue(checkPassword("user2", "pass2", userFile, false));
      } finally {
         stopServer();
      }
   }

   @Test
   public void testProperReloadWhenAddingUserViaManagementJAAS() throws Exception {
      testProperReloadWhenAddingUserViaManagement(false);
   }

   @Test
   public void testProperReloadWhenAddingUserViaManagementBasic() throws Exception {
      testProperReloadWhenAddingUserViaManagement(true);
   }

   private void testProperReloadWhenAddingUserViaManagement(boolean basic) throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor", "--require-login", "--security-manager", basic ? "basic" : "jaas");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>)result).getB();
      ActiveMQServerControl activeMQServerControl = activeMQServer.getActiveMQServerControl();

      ServerLocator serverLocator = ActiveMQClient.createServerLocator("tcp://localhost:61616");
      ClientSessionFactory sessionFactory = serverLocator.createSessionFactory();

      try {
         // this will force a properties "reload" event (i.e. initial loading)
         sessionFactory.createSession("foo", "bar", false, false, false, false, 0);
         fail("Should have failed to create session here due to security");
      } catch (Exception e) {
         // ignore
      }

      try {
         activeMQServerControl.createAddress("myAddress", RoutingType.ANYCAST.toString());
         activeMQServerControl.addSecuritySettings("myAddress", "myRole", "myRole", "myRole", "myRole", "myRole", "myRole", "myRole", "myRole", "myRole", "myRole");
         // change properties files which should cause another "reload" event
         activeMQServerControl.addUser("foo", "bar", "myRole", true);
         ((SecurityStoreImpl)activeMQServer.getSecurityStore()).invalidateAuthenticationCache();
         ClientSession session = sessionFactory.createSession("foo", "bar", false, false, false, false, 0);
         session.createQueue("myAddress", RoutingType.ANYCAST, "myQueue", true);
         ClientProducer producer = session.createProducer("myAddress");
         producer.send(session.createMessage(true));
         session.close();
      } finally {
         sessionFactory.close();
         serverLocator.close();
         stopServer();
      }
   }

   @Test
   public void testMissingUserFileViaManagement() throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>)result).getB();
      ActiveMQServerControl activeMQServerControl = activeMQServer.getActiveMQServerControl();

      File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
      userFile.delete();

      try {
         activeMQServerControl.listUser("");
         fail();
      } catch (ActiveMQIllegalStateException expected) {
      }

      stopServer();
   }

   @Test
   public void testMissingRoleFileViaManagement() throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>)result).getB();
      ActiveMQServerControl activeMQServerControl = activeMQServer.getActiveMQServerControl();

      File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");
      roleFile.delete();

      try {
         activeMQServerControl.listUser("");
         fail();
      } catch (ActiveMQIllegalStateException expected) {
      }

      stopServer();
   }

   @Test
   public void testUserCommandResetJAAS() throws Exception {
      testUserCommandReset(false);
   }

   @Test
   public void testUserCommandResetBasic() throws Exception {
      testUserCommandReset(true);
   }

   private void testUserCommandReset(boolean basic) throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--require-login", "--security-manager", basic ? "basic" : "jaas");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object runResult = Artemis.internalExecute("run");
      server = ((Pair<ManagementContext, ActiveMQServer>)runResult).getB();

      try {
         File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
         File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");

         ListUser listCmd = new ListUser();
         listCmd.setUser("admin");
         listCmd.setPassword("admin");
         TestActionContext context = new TestActionContext();
         listCmd.execute(context);

         String result = context.getStdout();
         log.debug("output1:\n" + result);

         //default only one user admin with role amq
         assertTrue(result.contains("\"admin\"(amq)"));

         //remove a user
         RemoveUser rmCmd = new RemoveUser();
         rmCmd.setUserCommandUser("admin");
         rmCmd.setUser("admin");
         rmCmd.setPassword("admin");
         rmCmd.execute(ActionContext.system());

         //check
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output8:\n" + result);

         assertTrue(result.contains("Total: 0"));

         //add some users
         AddUser addCmd = new AddUser();
         addCmd.setUserCommandUser("guest");
         addCmd.setUserCommandPassword("guest123");
         addCmd.setRole("admin");
         addCmd.setUser("admin");
         addCmd.setPassword("admin");
         addCmd.execute(new TestActionContext());

         addCmd.setUserCommandUser("user1");
         addCmd.setUserCommandPassword("password1");
         addCmd.setRole("admin,manager");
         addCmd.execute(new TestActionContext());
         assertTrue(checkPassword("user1", "password1", userFile, basic));

         addCmd.setUserCommandUser("user2");
         addCmd.setUserCommandPassword("password2");
         addCmd.setRole("admin,manager,master");
         addCmd.execute(new TestActionContext());

         addCmd.setUserCommandUser("user3");
         addCmd.setUserCommandPassword("password3");
         addCmd.setRole("system,master");
         addCmd.execute(new TestActionContext());

         //verify use list cmd
         context = new TestActionContext();
         listCmd.execute(context);
         result = context.getStdout();
         log.debug("output2:\n" + result);

         assertTrue(result.contains("Total: 4"));
         assertTrue(result.contains("\"guest\"(admin)"));
         assertTrue(Pattern
                       .compile("\"user1\"\\((admin|manager),(admin|manager)\\)")
                       .matcher(result)
                       .find());
         assertTrue(Pattern
                       .compile("\"user2\"\\((admin|manager|master),(admin|manager|master),(admin|manager|master)\\)")
                       .matcher(result)
                       .find());
         assertTrue(Pattern
                       .compile("\"user3\"\\((master|system),(master|system)\\)")
                       .matcher(result)
                       .find());

         checkRole("user1", roleFile, basic, "admin", "manager");

         //reset password
         context = new TestActionContext();
         ResetUser resetCommand = new ResetUser();
         resetCommand.setUserCommandUser("user1");
         resetCommand.setUserCommandPassword("newpassword1");
         resetCommand.setUser("admin");
         resetCommand.setPassword("admin");
         resetCommand.execute(context);

         checkRole("user1", roleFile, basic, "admin", "manager");
         assertFalse(checkPassword("user1", "password1", userFile, basic));
         assertTrue(checkPassword("user1", "newpassword1", userFile, basic));

         //reset role
         resetCommand.setUserCommandUser("user2");
         resetCommand.setRole("manager,master,operator");
         resetCommand.execute(new TestActionContext());

         checkRole("user2", roleFile, basic, "manager", "master", "operator");

         //reset both
         resetCommand.setUserCommandUser("user3");
         resetCommand.setUserCommandPassword("newpassword3");
         resetCommand.setRole("admin,system");
         resetCommand.execute(new ActionContext());

         checkRole("user3", roleFile, basic, "admin", "system");
         assertTrue(checkPassword("user3", "newpassword3", userFile, basic));
      } finally {
         stopServer();
      }
   }

   @Test
   public void testConcurrentUserAdministrationJAAS() throws Exception {
      testConcurrentUserAdministration(false);
   }

   @Test
   public void testConcurrentUserAdministrationBasic() throws Exception {
      testConcurrentUserAdministration(true);
   }

   private void testConcurrentUserAdministration(boolean basic) throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--require-login", "--security-manager", basic ? "basic" : "jaas");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Artemis.internalExecute("run");

      try {
         File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
         File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");

         TestActionContext context = new TestActionContext();
         ListUser listCmd = new ListUser();
         listCmd.setUser("admin");
         listCmd.setPassword("admin");
         listCmd.execute(context);

         String result = context.getStdout();
         log.debug("output1:\n" + result);

         assertTrue(result.contains("Total: 1"));
         assertTrue(result.contains("\"admin\"(amq)"));

         int nThreads = 4;

         UserAdmin[] userAdminThreads = new UserAdmin[nThreads];
         for (int j = 0; j < 25; j++) {

            for (int i = 0; i < nThreads; i++) {
               userAdminThreads[i] = new UserAdmin();
            }

            for (int i = 0; i < nThreads; i++) {
               userAdminThreads[i].start();
            }

            for (UserAdmin userAdmin : userAdminThreads) {
               userAdmin.join();
            }
         }

         context = new TestActionContext();
         listCmd = new ListUser();
         listCmd.setUser("admin");
         listCmd.setPassword("admin");
         listCmd.execute(context);

         result = context.getStdout();
         log.debug("output2:\n" + result);

         // make sure the admin user is still in tact (i.e. that the file wasn't corrupted via concurrent access)
         assertTrue(result.contains("\"admin\"(amq)"));
         checkRole("admin", roleFile, "amq");
         checkPassword("admin", "admin", userFile);
      } finally {
         stopServer();
      }
   }

   private class UserAdmin extends Thread {
      @Override
      public void run() {
         //remove "myuser""
         RemoveUser rmCmd = new RemoveUser();
         rmCmd.setUserCommandUser("myuser");
         rmCmd.setUser("admin");
         rmCmd.setPassword("admin");
         try {
            rmCmd.execute(new TestActionContext());
         } catch (Exception e) {
            // this could fail if the user doesn't exist
         }

         //create user 'myuser' with password 'mypassword'
         AddUser addCmd = new AddUser();
         addCmd.setUserCommandUser("myuser");
         addCmd.setUserCommandPassword("mypassword");
         addCmd.setRole("foo");
         addCmd.setUser("admin");
         addCmd.setPassword("admin");
         try {
            addCmd.execute(new TestActionContext());
         } catch (Exception e) {
            // this could fail if the user already exists
         }

         //reset 'myuser' with role 'myrole'
         ResetUser resetCmd = new ResetUser();
         resetCmd.setUserCommandUser("myuser");
         resetCmd.setUserCommandPassword("mypassword");
         resetCmd.setRole("myrole");
         resetCmd.setUser("admin");
         resetCmd.setPassword("admin");
         try {
            resetCmd.execute(new TestActionContext());
         } catch (Exception e) {
            // this could fail if the user doesn't exist
         }
      }
   }

   @Test
   public void testRoleWithSpaces() throws Exception {
      String roleWithSpaces = "amq with spaces";
      Run.setEmbedded(true);
      File instanceRole = new File(temporaryFolder.getRoot(), "instance_role");
      System.setProperty("java.security.auth.login.config", instanceRole.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instanceRole.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--require-login", "--role", roleWithSpaces);
      System.setProperty("artemis.instance", instanceRole.getAbsolutePath());
      Artemis.internalExecute("run");

      try {
         File roleFile = new File(instanceRole.getAbsolutePath() + "/etc/artemis-roles.properties");

         ListUser listCmd = new ListUser();
         listCmd.setUser("admin");
         listCmd.setPassword("admin");
         TestActionContext context = new TestActionContext();
         listCmd.execute(context);

         String result = context.getStdout();
         log.debug("output1:\n" + result);

         assertTrue(result.contains("\"admin\"(" + roleWithSpaces + ")"));

         checkRole("admin", roleFile, roleWithSpaces);
      } finally {
         stopServer();
      }
   }

   @Test
   public void testUserCommandResetViaManagementPlaintext() throws Exception {
      internalTestUserCommandResetViaManagement(true);
   }

   @Test
   public void testUserCommandResetViaManagementHashed() throws Exception {
      internalTestUserCommandResetViaManagement(false);
   }

   private void internalTestUserCommandResetViaManagement(boolean plaintext) throws Exception {
      Run.setEmbedded(true);
      File instance1 = new File(temporaryFolder.getRoot(), "instance_user");
      System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
      Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor");
      System.setProperty("artemis.instance", instance1.getAbsolutePath());
      Object result = Artemis.internalExecute("run");
      ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>)result).getB();
      ActiveMQServerControl activeMQServerControl = activeMQServer.getActiveMQServerControl();

      File userFile = new File(instance1.getAbsolutePath() + "/etc/artemis-users.properties");
      File roleFile = new File(instance1.getAbsolutePath() + "/etc/artemis-roles.properties");

      //default only one user admin with role amq
      String jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq");
      checkRole("admin", roleFile, "amq");

      //remove a user
      activeMQServerControl.removeUser("admin");
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "admin", "amq", false);

      //add some users
      activeMQServerControl.addUser("guest", "guest123", "admin", plaintext);
      activeMQServerControl.addUser("user1", "password1", "admin,manager", plaintext);
      assertTrue(checkPassword("user1", "password1", userFile));
      assertEquals(plaintext, !PasswordMaskingUtil.isEncMasked(getStoredPassword("user1", userFile)));
      activeMQServerControl.addUser("user2", "password2", "admin,manager,master", plaintext);
      activeMQServerControl.addUser("user3", "password3", "system,master", plaintext);


      //verify use list cmd
      jsonResult = activeMQServerControl.listUser("");
      contains(JsonUtil.readJsonArray(jsonResult), "guest", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "user1", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "user1", "manager");
      contains(JsonUtil.readJsonArray(jsonResult), "user2", "admin");
      contains(JsonUtil.readJsonArray(jsonResult), "user2", "manager");
      contains(JsonUtil.readJsonArray(jsonResult), "user2", "master");
      contains(JsonUtil.readJsonArray(jsonResult), "user3", "master");
      contains(JsonUtil.readJsonArray(jsonResult), "user3", "system");

      checkRole("user1", roleFile, "admin", "manager");

      //reset password
      activeMQServerControl.resetUser("user1", "newpassword1", null, plaintext);

      checkRole("user1", roleFile, "admin", "manager");
      assertFalse(checkPassword("user1", "password1", userFile));
      assertTrue(checkPassword("user1", "newpassword1", userFile));
      assertEquals(plaintext, !PasswordMaskingUtil.isEncMasked(getStoredPassword("user1", userFile)));

      //reset role
      activeMQServerControl.resetUser("user2", null, "manager,master,operator", plaintext);

      checkRole("user2", roleFile, "manager", "master", "operator");
      assertTrue(checkPassword("user2", "password2", userFile));
      assertEquals(plaintext, !PasswordMaskingUtil.isEncMasked(getStoredPassword("user2", userFile)));

      //reset both
      activeMQServerControl.resetUser("user3", "newpassword3", "admin,system", plaintext);

      checkRole("user3", roleFile, "admin", "system");
      assertTrue(checkPassword("user3", "newpassword3", userFile));
      assertEquals(plaintext, !PasswordMaskingUtil.isEncMasked(getStoredPassword("user3", userFile)));
      stopServer();
   }

   @Test
   public void testMaskCommand() throws Exception {

      String password1 = "password";
      String encrypt1 = "3a34fd21b82bf2a822fa49a8d8fa115d";
      String newKey = "artemisfun";
      String encrypt2 = "-2b8e3b47950b9b481a6f3100968e42e9";


      TestActionContext context = new TestActionContext();
      Mask mask = new Mask();
      mask.setPassword(password1);

      String result = (String) mask.execute(context);
      log.debug(context.getStdout());
      assertEquals(encrypt1, result);

      context = new TestActionContext();
      mask = new Mask();
      mask.setPassword(password1);
      mask.setHash(true);
      result = (String) mask.execute(context);
      log.debug(context.getStdout());
      SensitiveDataCodec<String> codec = mask.getCodec();
      Assert.assertEquals(DefaultSensitiveStringCodec.class, codec.getClass());
      Assert.assertTrue(((DefaultSensitiveStringCodec)codec).verify(password1.toCharArray(), result));

      context = new TestActionContext();
      mask = new Mask();
      mask.setPassword(password1);
      mask.setKey(newKey);
      result = (String) mask.execute(context);
      log.debug(context.getStdout());
      assertEquals(encrypt2, result);
   }

   @Test
   public void testMaskCommandWithPasswordCodec() throws Exception {
      File instanceWithPasswordCodec = new File(temporaryFolder.getRoot(), "instance_with_password_codec");
      Files.createDirectories(Paths.get(instanceWithPasswordCodec.getAbsolutePath(), "etc"));
      Files.copy(Paths.get(ArtemisTest.class.getClassLoader().getResource("broker-with-password-codec.xml").toURI()),
                 Paths.get(instanceWithPasswordCodec.getAbsolutePath(), "etc", "broker.xml"));
      System.setProperty("artemis.instance", instanceWithPasswordCodec.getAbsolutePath());

      String password = "password";
      String encrypt = "3a34fd21b82bf2a822fa49a8d8fa115d";

      TestActionContext context = new TestActionContext();
      Mask mask = new Mask();
      mask.setPassword(password);
      String result = (String) mask.execute(context);
      assertEquals(DefaultSensitiveStringCodec.class, mask.getCodec().getClass());
      assertEquals(encrypt, result);

      context = new TestActionContext();
      mask = new Mask();
      mask.setPassword(password);
      mask.setPasswordCodec(true);
      result = (String) mask.execute(context);
      assertEquals(TestPasswordCodec.class, mask.getCodec().getClass());
      assertEquals(result, result);
   }

   @Test
   public void testSimpleRun() throws Exception {
      testSimpleRun("server");
   }

   @Test
   public void testWeirdCharacter() throws Exception {
      testSimpleRun("test%26%26x86_6");
   }


   @Test
   public void testSpaces() throws Exception {
      testSimpleRun("with space");
   }


   @Test
   public void testCustomPort() throws Exception {
      testSimpleRun("server", 61696);
   }

   @Test
   public void testPerfJournal() throws Exception {
      File instanceFolder = temporaryFolder.newFolder("server1");
      setupAuth(instanceFolder);

      Run.setEmbedded(true);
      Artemis.main("create", instanceFolder.getAbsolutePath(), "--force", "--silent", "--no-web", "--no-autotune", "--require-login");
      System.setProperty("artemis.instance", instanceFolder.getAbsolutePath());

      Artemis.main("perf-journal", "--journal-type", "NIO", "--writes", "5", "--tries", "1");

   }


   public void testSimpleRun(String folderName) throws Exception {
      testSimpleRun(folderName, 61616);
   }

   public void testSimpleRun(String folderName, int acceptorPort) throws Exception {
      File instanceFolder = temporaryFolder.newFolder(folderName);

      setupAuth(instanceFolder);
      String queues = "q1,q2:multicast";
      String addresses = "a1,a2";


      // This is usually set when run from the command line via artemis.profile
      Run.setEmbedded(true);
      Artemis.main("create", instanceFolder.getAbsolutePath(), "--force", "--silent", "--no-web", "--queues", queues, "--addresses", addresses, "--no-autotune", "--require-login", "--default-port", Integer.toString(acceptorPort));
      System.setProperty("artemis.instance", instanceFolder.getAbsolutePath());


      try {
         // Some exceptions may happen on the initialization, but they should be ok on start the basic core protocol
         Artemis.internalExecute("run");

         try (ServerLocator locator = ServerLocatorImpl.newLocator("tcp://localhost:" + acceptorPort);
              ClientSessionFactory factory = locator.createSessionFactory();
              ClientSession coreSession = factory.createSession("admin", "admin", false, true, true, false, 0)) {
            for (String str : queues.split(",")) {
               String[] seg = str.split(":");
               RoutingType routingType = RoutingType.valueOf((seg.length == 2 ? seg[1] : "anycast").toUpperCase());
               ClientSession.QueueQuery queryResult = coreSession.queueQuery(SimpleString.toSimpleString(seg[0]));
               assertTrue("Couldn't find queue " + seg[0], queryResult.isExists());
               assertEquals(routingType, queryResult.getRoutingType());
            }
            for (String str : addresses.split(",")) {
               ClientSession.AddressQuery queryResult = coreSession.addressQuery(SimpleString.toSimpleString(str));
               assertTrue("Couldn't find address " + str, queryResult.isExists());
            }
         }

         try {
            Artemis.internalExecute("data", "print");
            Assert.fail("Exception expected");
         } catch (CLIException expected) {
         }
         Artemis.internalExecute("data", "print", "--f");

         assertEquals(Integer.valueOf(100), Artemis.internalExecute("producer", "--destination", "queue://q1", "--message-count", "100", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(100), Artemis.internalExecute("consumer", "--destination", "queue://q1", "--break-on-null", "--receive-timeout", "100", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("producer", "--destination", "queue://q1", "--text-size", "500", "--message-count", "10", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("consumer", "--destination", "queue://q1", "--break-on-null", "--receive-timeout", "100", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("producer", "--destination", "queue://q1", "--message-size", "500", "--message-count", "10", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("consumer", "--destination", "queue://q1", "--break-on-null", "--receive-timeout", "100", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("producer", "--destination", "queue://q1", "--message", "message", "--message-count", "10", "--user", "admin", "--password", "admin"));
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("consumer", "--destination", "queue://q1", "--break-on-null", "--receive-timeout", "100", "--user", "admin", "--password", "admin"));

         ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:" + acceptorPort);
         Connection connection = cf.createConnection("admin", "admin");
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(ActiveMQDestination.createDestination("queue://q1", ActiveMQDestination.TYPE.QUEUE));

         TextMessage message = session.createTextMessage("Banana");
         message.setStringProperty("fruit", "banana");
         producer.send(message);

         for (int i = 0; i < 100; i++) {
            message = session.createTextMessage("orange");
            message.setStringProperty("fruit", "orange");
            producer.send(message);
         }
         session.commit();

         connection.close();
         cf.close();

         assertEquals(Integer.valueOf(1), Artemis.internalExecute("browser", "--destination", "queue://q1", "--txt-size", "50", "--filter", "fruit='banana'", "--user", "admin", "--password", "admin"));

         assertEquals(Integer.valueOf(100), Artemis.internalExecute("browser", "--destination", "queue://q1", "--txt-size", "50", "--filter", "fruit='orange'", "--user", "admin", "--password", "admin"));

         assertEquals(Integer.valueOf(101), Artemis.internalExecute("browser", "--destination", "queue://q1", "--txt-size", "50", "--user", "admin", "--password", "admin"));

         // should only receive 10 messages on browse as I'm setting messageCount=10
         assertEquals(Integer.valueOf(10), Artemis.internalExecute("browser", "--destination", "queue://q1", "--txt-size", "50", "--message-count", "10", "--user", "admin", "--password", "admin"));

         // Nothing was consumed until here as it was only browsing, check it's receiving again
         assertEquals(Integer.valueOf(1), Artemis.internalExecute("consumer", "--destination", "queue://q1", "--txt-size", "50", "--break-on-null", "--receive-timeout", "100", "--filter", "fruit='banana'", "--user", "admin", "--password", "admin"));

         // Checking it was acked before
         assertEquals(Integer.valueOf(100), Artemis.internalExecute("consumer", "--destination", "queue://q1", "--txt-size", "50", "--break-on-null", "--receive-timeout", "100", "--user", "admin", "--password", "admin"));
      } finally {
         stopServer();
      }
   }

   @Test
   public void testAutoDeleteTrue() throws Exception {
      testAutoDelete(true);
   }

   @Test
   public void testAutoDeleteFalse() throws Exception {
      testAutoDelete(false);
   }

   private void testAutoDelete(boolean autoDelete) throws Exception {

      File instanceFolder = temporaryFolder.newFolder("autocreate" + autoDelete);
      setupAuth(instanceFolder);

      // This is usually set when run from the command line via artemis.profile
      Run.setEmbedded(true);
      if (autoDelete) {
         Artemis.main("create", instanceFolder.getAbsolutePath(), "--force", "--silent",  "--no-web", "--no-autotune",  "--require-login", "--autodelete");
      } else {
         Artemis.main("create", instanceFolder.getAbsolutePath(), "--force", "--silent", "--no-web",  "--require-login", "--no-autotune");
      }
      System.setProperty("artemis.instance", instanceFolder.getAbsolutePath());


      try {
         // Some exceptions may happen on the initialization, but they should be ok on start the basic core protocol
         Pair<ManagementContext, ActiveMQServer> run = (Pair<ManagementContext, ActiveMQServer>) Artemis.internalExecute("run");

         String queueName = "testAutoDelete" + RandomUtil.randomPositiveInt();

         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
         try (Connection connection = factory.createConnection("admin", "admin")) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("hi"));

            Wait.assertTrue(() -> run.getB().locateQueue(queueName) != null);
            connection.start();
            MessageConsumer consumer = session.createConsumer(queue);
            Assert.assertNotNull(consumer.receive(5000));
         }

         if (autoDelete) {
            Wait.assertTrue(() -> run.getB().locateQueue(queueName) == null);
         } else {
            // Things are async, allowing some time to make sure it would eventually fail
            Thread.sleep(500);
            Assert.assertNotNull(run.getB().locateQueue(queueName));
         }
      } finally {
         stopServer();
      }
   }



   @Test
   public void testPing() throws Exception {
      File instanceFolder = temporaryFolder.newFolder("pingTest");

      setupAuth(instanceFolder);
      String queues = "q1,t2";
      String topics = "t1,t2";

      // This is usually set when run from the command line via artemis.profile
      Run.setEmbedded(true);
      Artemis.main("create", instanceFolder.getAbsolutePath(), "--force", "--silent", "--no-web", "--queues", queues, "--addresses", topics, "--no-autotune", "--require-login", "--ping", "127.0.0.1", "--no-autotune");
      System.setProperty("artemis.instance", instanceFolder.getAbsolutePath());

      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(new File(instanceFolder, "./etc/broker.xml").toURI().toString());
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      Assert.assertEquals("127.0.0.1", fc.getNetworkCheckList());

   }

   @Test
   public void testAutoTune() throws Exception {
      File instanceFolder = temporaryFolder.newFolder("autoTuneTest");

      setupAuth(instanceFolder);

      // This is usually set when run from the command line via artemis.profile
      Run.setEmbedded(true);
      Artemis.main("create", instanceFolder.getAbsolutePath(), "--force", "--silent", "--no-web", "--require-login");
      System.setProperty("artemis.instance", instanceFolder.getAbsolutePath());

      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(new File(instanceFolder, "./etc/broker.xml").toURI().toString());
      deploymentManager.addDeployable(fc);

      fc.setPageSyncTimeout(-1);
      deploymentManager.readConfiguration();

      Assert.assertNotEquals(-1, fc.getPageSyncTimeout());
   }

   @Test
   public void testQstat() throws Exception {

      File instanceQstat = new File(temporaryFolder.getRoot(), "instanceQStat");
      setupAuth(instanceQstat);
      Run.setEmbedded(true);
      Artemis.main("create", instanceQstat.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune", "--no-web", "--require-login");
      System.setProperty("artemis.instance", instanceQstat.getAbsolutePath());
      Artemis.internalExecute("run");

      try (ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616"); Connection connection = cf.createConnection("admin", "admin");) {

         //set up some queues with messages and consumers
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.start();
         sendMessages(session, "Test1", 15);
         sendMessages(session, "Test11", 1);
         sendMessages(session, "Test12", 1);
         sendMessages(session, "Test20", 20);
         MessageConsumer consumer = session.createConsumer(ActiveMQDestination.createDestination("queue://Test1", ActiveMQDestination.TYPE.QUEUE));
         MessageConsumer consumer2 = session.createConsumer(ActiveMQDestination.createDestination("queue://Test1", ActiveMQDestination.TYPE.QUEUE));

         for (int i = 0; i < 5; i++) {
            Message message = consumer.receive(100);
         }

         //check all queues containing "Test1" are displayed
         TestActionContext context = new TestActionContext();
         StatQueue statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("Test1");
         statQueue.execute(context);
         ArrayList<String> lines = getOutputLines(context, false);
         // Header line + 3 queues
         Assert.assertEquals("rows returned using queueName=Test1", 4, lines.size());

         //check all queues are displayed when no Filter set
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 4 queues (at least - possibly other infra queues as well)
         Assert.assertTrue("rows returned filtering no name ", 5 <= lines.size());

         //check all queues containing "Test1" are displayed using Filter field NAME
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("NAME");
         statQueue.setOperationName("CONTAINS");
         statQueue.setValue("Test1");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 3 queues
         Assert.assertEquals("rows returned filtering by NAME ", 4, lines.size());

         //check all queues NOT containing "management" are displayed using Filter field NAME
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("NAME");
         statQueue.setOperationName("NOT_CONTAINS");
         statQueue.setValue("management");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 6 queues (Test1/11/12/20+DLQ+ExpiryQueue, but not activemq.management.d6dbba78-d76f-43d6-a2c9-fc0575ed6f5d)
         Assert.assertEquals("rows returned filtering by NAME operation NOT_CONTAINS", 7, lines.size());

         //check only queue named "Test1" is displayed using Filter field NAME and operation EQUALS
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("NAME");
         statQueue.setOperationName("EQUALS");
         statQueue.setValue("Test1");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         //Header line + 1 queue only
         Assert.assertEquals("rows returned filtering by NAME operation EQUALS", 2, lines.size());
         //verify contents of queue stat line is correct
         String queueTest1 = lines.get(1);
         String[] parts = queueTest1.split("\\|");
         Assert.assertEquals("queue name", "Test1", parts[1].trim());
         Assert.assertEquals("address name", "Test1", parts[2].trim());
         Assert.assertEquals("Consumer count", "2", parts[3].trim());
         Assert.assertEquals("Message count", "10", parts[4].trim());
         Assert.assertEquals("Added count", "15", parts[5].trim());
         Assert.assertEquals("Delivering count", "10", parts[6].trim());
         Assert.assertEquals("Acked count", "5", parts[7].trim());
         Assert.assertEquals("Scheduled count", "0", parts[8].trim());
         Assert.assertEquals("Routing type", "ANYCAST", parts[9].trim());

         //check all queues containing address "Test1" are displayed using Filter field ADDRESS
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("ADDRESS");
         statQueue.setOperationName("CONTAINS");
         statQueue.setValue("Test1");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 3 queues
         Assert.assertEquals("rows returned filtering by ADDRESS", 4, lines.size());

         //check all queues containing address "Test1" are displayed using Filter field MESSAGE_COUNT
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGE_COUNT");
         statQueue.setOperationName("CONTAINS");
         statQueue.setValue("10");
         statQueue.execute(context);
         lines = getOutputLines(context, false);

         // Header line + 0 queues
         Assert.assertEquals("rows returned filtering by MESSAGE_COUNT", 1, lines.size());

         //check all queues containing address "Test1" are displayed using Filter field MESSAGE_ADDED
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGES_ADDED");
         statQueue.setOperationName("CONTAINS");
         statQueue.setValue("20");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 0 queues
         Assert.assertEquals("rows returned filtering by MESSAGES_ADDED", 1, lines.size());

         //check  queues with greater_than 19 MESSAGE_ADDED  displayed
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGES_ADDED");
         statQueue.setOperationName("GREATER_THAN");
         statQueue.setValue("19");
         statQueue.execute(context);
         lines = getOutputLines(context, false);

         // Header line + 1 queues
         Assert.assertEquals("rows returned filtering by MESSAGES_ADDED", 2, lines.size());
         String[] columns = lines.get(1).split("\\|");
         Assert.assertEquals("queue name filtered by MESSAGES_ADDED GREATER_THAN ", "Test20", columns[2].trim());

         //check queues with less_than 2 MESSAGE_ADDED displayed
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGES_ADDED");
         statQueue.setOperationName("LESS_THAN");
         statQueue.setValue("2");
         statQueue.execute(context);
         lines = getOutputLines(context, false);

         // Header line + "at least" 2 queues
         Assert.assertTrue("rows returned filtering by MESSAGES_ADDED LESS_THAN", 2 <= lines.size());

         //walk the result returned and the specific destinations are not part of the output
         for (String line : lines) {
            columns = line.split("\\|");
            Assert.assertNotEquals("ensure Test20 is not part of returned result", "Test20", columns[2].trim());
            Assert.assertNotEquals("ensure Test1 is not part of returned result", "Test1", columns[2].trim());
         }

         //check all queues containing address "Test1" are displayed using Filter field DELIVERING_COUNT
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("DELIVERING_COUNT");
         statQueue.setOperationName("EQUALS");
         statQueue.setValue("10");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         columns = lines.get(1).split("\\|");
         // Header line + 1 queues
         Assert.assertEquals("rows returned filtering by DELIVERING_COUNT", 2, lines.size());
         Assert.assertEquals("queue name filtered by DELIVERING_COUNT ", "Test1", columns[2].trim());

         //check all queues containing address "Test1" are displayed using Filter field CONSUMER_COUNT
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("CONSUMER_COUNT");
         statQueue.setOperationName("EQUALS");
         statQueue.setValue("2");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         columns = lines.get(1).split("\\|");
         // Header line + 1 queues
         Assert.assertEquals("rows returned filtering by CONSUMER_COUNT ", 2, lines.size());
         Assert.assertEquals("queue name filtered by CONSUMER_COUNT ", "Test1", columns[2].trim());

         //check all queues containing address "Test1" are displayed using Filter field MESSAGE_ACKED
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGES_ACKED");
         statQueue.setOperationName("EQUALS");
         statQueue.setValue("5");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         columns = lines.get(1).split("\\|");
         // Header line + 1 queues
         Assert.assertEquals("rows returned filtering by MESSAGE_ACKED ", 2, lines.size());
         Assert.assertEquals("queue name filtered by MESSAGE_ACKED", "Test1", columns[2].trim());

         //check no queues  are displayed when name does not match
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("no_queue_name");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 0 queues
         Assert.assertEquals("rows returned by queueName for no Matching queue ", 1, lines.size());

         //check maxrows is taking effect"
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("Test1");
         statQueue.setMaxRows(1);
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 1 queue only + warning line
         Assert.assertEquals("rows returned by maxRows=1", 3, lines.size());

      } finally {
         stopServer();
      }

   }

   @Test
   public void testQstatErrors() throws Exception {

      File instanceQstat = new File(temporaryFolder.getRoot(), "instanceQStatErrors");
      setupAuth(instanceQstat);
      Run.setEmbedded(true);
      Artemis.main("create", instanceQstat.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune", "--no-web", "--require-login");
      System.setProperty("artemis.instance", instanceQstat.getAbsolutePath());
      Artemis.internalExecute("run");
      try {

         //check err when FIELD wrong"
         TestActionContext context = new TestActionContext();
         StatQueue statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("WRONG_FILE");
         statQueue.setOperationName("EQUALS");
         statQueue.setValue("5");
         statQueue.execute(context);
         ArrayList<String> lines = getOutputLines(context, false);
         // Header line + 0 queue
         Assert.assertEquals("No stdout for wrong FIELD", 0, lines.size());

         lines = getOutputLines(context, true);
         // 1 error line
         Assert.assertEquals("stderr for wrong FIELD", 1, lines.size());
         Assert.assertTrue("'FIELD incorrect' error messsage", lines.get(0).contains("'--field' must be set to one of the following"));

         //Test err when OPERATION wrong
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGE_COUNT");
         statQueue.setOperationName("WRONG_OPERATION");
         statQueue.setValue("5");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 0 queue
         Assert.assertEquals("No stdout for wrong OPERATION", 0, lines.size());
         lines = getOutputLines(context, true);
         // 1 error line
         Assert.assertEquals("stderr for wrong OPERATION", 1, lines.size());
         Assert.assertTrue("'OPERATION incorrect' error message", lines.get(0).contains("'--operation' must be set to one of the following"));

         //Test err when queueName and field set together
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("DLQ");
         statQueue.setFieldName("MESSAGE_COUNT");
         statQueue.setOperationName("CONTAINS");
         statQueue.setValue("5");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 0 queue
         Assert.assertEquals("No stdout for --field and --queueName both set", 0, lines.size());

         lines = getOutputLines(context, true);
         // 1 error line
         Assert.assertEquals("stderr for  --field and --queueName both set", 1, lines.size());
         Assert.assertTrue("field and queueName error message", lines.get(0).contains("'--field' and '--queueName' cannot be specified together"));

         //Test err when field set but no value
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGE_COUNT");
         statQueue.setOperationName("CONTAINS");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 0 queue
         Assert.assertEquals("No stdout for --field set BUT no --value", 0, lines.size());
         lines = getOutputLines(context, true);
         // 1 error line
         Assert.assertEquals("stderr for --field set BUT no --value", 1, lines.size());
         Assert.assertTrue("NO VALUE error message", lines.get(0).contains("'--value' needs to be set when '--field' is specified"));

         //Test err when field set but no operation
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setFieldName("MESSAGE_COUNT");
         statQueue.setValue("5");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + 0 queue
         Assert.assertEquals("No stdout for --field set BUT no --operation", 0, lines.size());
         lines = getOutputLines(context, true);
         // 1 error line
         Assert.assertEquals("stderr for --field set BUT no --operation", 1, lines.size());
         Assert.assertTrue("OPERATION incorrect error message", lines.get(0).contains("'--operation' must be set when '--field' is specified "));

      } finally {
         stopServer();
      }

   }

   @Test
   public void testQstatWarnings() throws Exception {

      File instanceQstat = new File(temporaryFolder.getRoot(), "instanceQStat");
      setupAuth(instanceQstat);
      Run.setEmbedded(true);
      Artemis.main("create", instanceQstat.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune", "--no-web", "--require-login");
      System.setProperty("artemis.instance", instanceQstat.getAbsolutePath());
      Artemis.internalExecute("run");

      try (ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616"); Connection connection = cf.createConnection("admin", "admin");) {

         TestActionContext context;
         StatQueue statQueue;
         ArrayList<String> lines;

         //set up some queues with messages and consumers
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.start();
         for (int i = 0; i < StatQueue.DEFAULT_MAX_ROWS; i++) {
            sendMessages(session, "Test" + i, 1);
         }

         //check all queues containing "Test" are displayed
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("Test");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + DEFAULT_MAX_ROWS queues + warning line
         Assert.assertEquals("rows returned using queueName=Test", 1 + StatQueue.DEFAULT_MAX_ROWS, lines.size());
         Assert.assertFalse(lines.get(lines.size() - 1).startsWith("WARNING"));

         //check all queues containing "Test" are displayed
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("Test");
         statQueue.setMaxRows(StatQueue.DEFAULT_MAX_ROWS);
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + DEFAULT_MAX_ROWS queues
         Assert.assertEquals("rows returned using queueName=Test", 1 + StatQueue.DEFAULT_MAX_ROWS, lines.size());
         Assert.assertFalse(lines.get(lines.size() - 1).startsWith("WARNING"));

         sendMessages(session, "Test" + StatQueue.DEFAULT_MAX_ROWS, 1);

         //check all queues containing "Test" are displayed
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("Test");
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + DEFAULT_MAX_ROWS queues + warning line
         Assert.assertEquals("rows returned using queueName=Test", 1 + StatQueue.DEFAULT_MAX_ROWS + 1, lines.size());
         Assert.assertTrue(lines.get(lines.size() - 1).startsWith("WARNING"));

         //check all queues containing "Test" are displayed
         context = new TestActionContext();
         statQueue = new StatQueue();
         statQueue.setUser("admin");
         statQueue.setPassword("admin");
         statQueue.setQueueName("Test");
         statQueue.setMaxRows(StatQueue.DEFAULT_MAX_ROWS);
         statQueue.execute(context);
         lines = getOutputLines(context, false);
         // Header line + DEFAULT_MAX_ROWS queues + warning line
         Assert.assertEquals("rows returned using queueName=Test", 1 + StatQueue.DEFAULT_MAX_ROWS + 1, lines.size());
         Assert.assertTrue(lines.get(lines.size() - 1).startsWith("WARNING"));

      } finally {
         stopServer();
      }

   }

   @Test
   public void testVersionCommand() throws Exception {
      TestActionContext context = new TestActionContext();
      PrintVersion printVersion = new PrintVersion();
      Version result = (Version) printVersion.execute(context);
      log.debug(context.getStdout());
   }

   //read individual lines from byteStream
   public static ArrayList<String> getOutputLines(TestActionContext context, boolean errorOutput) throws IOException {
      byte[] bytes;

      if (errorOutput) {
         bytes = context.getStdErrBytes();
      } else {
         bytes = context.getStdoutBytes();
      }
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
      ArrayList<String> lines = new ArrayList<>();

      String currentLine = bufferedReader.readLine();
      while (currentLine != null) {
         lines.add(currentLine);
         currentLine = bufferedReader.readLine();
      }

      return lines;
   }

   private void sendMessages(Session session, String queueName, int messageCount) throws JMSException {
      MessageProducer producer = session.createProducer(ActiveMQDestination.createDestination("queue://" + queueName, ActiveMQDestination.TYPE.QUEUE));

      TextMessage message = session.createTextMessage("sample message");
      for (int i = 0; i < messageCount; i++) {
         producer.send(message);
      }
   }

   private void testCli(String... args) {
      try {
         Artemis.main(args);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception caught " + e.getMessage());
      }
   }

   public boolean isWindows() {
      return System.getProperty("os.name", "null").toLowerCase().indexOf("win") >= 0;
   }

   private static Document parseXml(File xmlFile) throws ParserConfigurationException, IOException, SAXException {
      DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
      return domBuilder.parse(xmlFile);
   }

   private void checkRole(String user, File roleFile, String... roles) throws Exception {
      checkRole(user, roleFile, false, roles);
   }

   private void checkRole(String user, File roleFile, boolean basicSecurityManager, String... roles) throws Exception {
      if (basicSecurityManager) {
         for (String r : roles) {
            assertTrue(server.getStorageManager().getPersistedRoles().get(user).getRoles().contains(r));
         }
      } else {
         Configurations configs = new Configurations();
         FileBasedConfigurationBuilder<PropertiesConfiguration> roleBuilder = configs.propertiesBuilder(roleFile);
         PropertiesConfiguration roleConfig = roleBuilder.getConfiguration();

         for (String r : roles) {
            String storedUsers = (String) roleConfig.getProperty(r);

            log.debug("users in role: " + r + " ; " + storedUsers);
            List<String> userList = StringUtil.splitStringList(storedUsers, ",");
            assertTrue(userList.contains(user));
         }
      }
   }

   private String getStoredPassword(String user, File userFile) throws Exception {
      return getStoredPassword(user, userFile, false);
   }

   private String getStoredPassword(String user, File userFile, boolean basicSecurityManager) throws Exception {
      String result;
      if (basicSecurityManager) {
         result = server.getStorageManager().getPersistedUsers().get(user).getPassword();
      } else {
         Configurations configs = new Configurations();
         FileBasedConfigurationBuilder<PropertiesConfiguration> userBuilder = configs.propertiesBuilder(userFile);
         PropertiesConfiguration userConfig = userBuilder.getConfiguration();
         result = (String) userConfig.getProperty(user);
      }
      return result;
   }

   private boolean checkPassword(String user, String password, File userFile) throws Exception {
      return checkPassword(user, password, userFile,false);
   }

   private boolean checkPassword(String user, String password, File userFile, boolean basicSecurityManager) throws Exception {
      String storedPassword = getStoredPassword(user, userFile, basicSecurityManager);
      HashProcessor processor = PasswordMaskingUtil.getHashProcessor(storedPassword);
      return processor.compare(password.toCharArray(), storedPassword);
   }

   private void contains(JsonArray users, String username, String role) {
      contains(users, username, role, true);
   }

   private void contains(JsonArray users, String username, String role, boolean contains) {
      boolean userFound = false;
      boolean roleFound = false;
      for (int i = 0; i < users.size(); i++) {
         JsonObject user = users.getJsonObject(i);
         if (user.getString("username").equals(username)) {
            userFound = true;
            JsonArray roles = user.getJsonArray("roles");
            for (int j = 0; j < roles.size(); j++) {
               if (roles.getString(j).equals(role)) {
                  roleFound = true;
                  break;
               }
            }
         }
      }
      if (contains) {
         assertTrue("user " + username + " not found", userFound);
         assertTrue("role " + role + " not found", roleFound);
      } else {
         assertFalse("user " + username + " found", userFound);
         assertFalse("role " + role + " found", roleFound);
      }
   }

   public static class TestPasswordCodec implements SensitiveDataCodec<String> {

      @Override
      public String decode(Object mask) throws Exception {
         return mask.toString();
      }

      @Override
      public String encode(Object secret) throws Exception {
         return secret.toString();
      }
   }

}
