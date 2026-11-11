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
package org.apache.activemq.artemis.tests.integration.karaf;

import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.json.JsonArray;
import javax.security.auth.Subject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.apache.karaf.jaas.boot.principal.UserPrincipal;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.apache.log4j.Logger;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.UrlReference;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.util.tracker.ServiceTracker;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

// uncomment this to be able to debug it
// import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;

/**
 * Useful docs about this test: https://ops4j1.jira.com/wiki/display/paxexam/FAQ
 */
@RunWith(PaxExam.class)
public class ArtemisFeatureTest extends Assert {

   private static Logger log = Logger.getLogger(ArtemisFeatureTest.class.getName());

   @Inject
   BundleContext bundleContext;

   @Inject
   SessionFactory sessionFactory;

   ExecutorService executor = Executors.newCachedThreadPool();

   public static final long ASSERTION_TIMEOUT = 30000L;
   public static final long COMMAND_TIMEOUT = 30000L;
   public static final String USER = "artemis";
   public static final String PASSWORD = "artemis";

   @ProbeBuilder
   public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
      probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE, "*,org.ops4j.pax.exam.options.*,org.apache.felix.service.*;status=provisional");
      return probe;
   }

   @Configuration
   public static Option[] configure() {
      return configure("artemis");
   }

   public static Option[] configure(String... features) {

      List<String> f = new ArrayList<>();
      f.addAll(Arrays.asList(features));

      Option[] options = new Option[]{karafDistributionConfiguration().frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf").type("tar.gz").versionAsInProject()).unpackDirectory(new File("target/paxexam/unpack/")),

         KarafDistributionOption.keepRuntimeFolder(), logLevel(LogLevelOption.LogLevel.INFO), editConfigurationFilePut("etc/config.properties", "karaf.startlevel.bundle", "50"),
         // add artemis user
         editConfigurationFilePut("etc/users.properties", USER, PASSWORD + ",manager"),
         // [KARAF-6600] Use https URL for Maven Central
         editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg", "org.ops4j.pax.url.mvn.repositories", "https://repo1.maven.org/maven2@id=central, https://repository.apache.org/content/groups/snapshots-group@id=apache@snapshots@noreleases, https://oss.sonatype.org/content/repositories/ops4j-snapshots@id=ops4j.sonatype.snapshots.deploy@snapshots@noreleases"),
         when(System.getProperty("maven.repo.local") != null).useOptions(
            editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg", "org.ops4j.pax.url.mvn.localRepository", System.getProperty("maven.repo.local"))),
         // uncomment this to debug it.
         // debugConfiguration("5005", true),
         features(getArtemisMQKarafFeatureUrl(), f.toArray(new String[f.size()]))};

      return options;
   }

   public static UrlReference getArtemisMQKarafFeatureUrl() {
      String type = "xml/features";
      UrlReference urlReference = mavenBundle().groupId("org.apache.artemis").
         artifactId("artemis-features").versionAsInProject().type(type);
      log.debug("FeatureURL: {}", urlReference.getURL());
      return urlReference;
   }

   @Test(timeout = 5 * 60 * 1000)
   public void test() throws Throwable {
      executeCommand("bundle:list");

      withinReason(() -> {
         assertTrue("artemis bundle installed", verifyBundleInstalled("artemis-server-osgi"));
         return true;
      });

      Object service = waitForService("(objectClass=org.apache.activemq.artemis.core.server.ActiveMQServer)", 30000);
      assertNotNull(service);
      log.debug("have service {}", service);

      executeCommand("service:list -n");

      Connection connection = null;
      try {
         JmsConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:5672");
         connection = factory.createConnection(USER, PASSWORD);
         connection.start();

         QueueSession sess = (QueueSession) connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
         Queue queue = sess.createQueue("exampleQueue");
         MessageProducer producer = sess.createProducer(queue);
         producer.send(sess.createTextMessage("TEST"));

         // Test browsing
         try (QueueBrowser browser = sess.createBrowser(queue)) {
            Enumeration<?> messages = browser.getEnumeration();
            while (messages.hasMoreElements()) {
               messages.nextElement();
            }
         }

         // Test management
         Queue managementQueue = sess.createQueue("activemq.management");
         QueueRequestor requestor = new QueueRequestor(sess, managementQueue);
         connection.start();
         TextMessage m = sess.createTextMessage();
         m.setStringProperty("_AMQ_ResourceName", "broker");
         m.setStringProperty("_AMQ_OperationName", "getQueueNames");
         m.setText("[\"ANYCAST\"]");
         Message reply = requestor.request(m);
         String json = ((TextMessage) reply).getText();
         JsonArray array = JsonLoader.readArray(new StringReader(json));
         JsonArray queues = array.getJsonArray(0);
         assertNotNull(queues);
         assertFalse(queues.isEmpty());

         MessageConsumer consumer = sess.createConsumer(queue);
         Message msg = consumer.receive(5000);
         assertNotNull(msg);
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   protected String executeCommand(final String command, final Long timeout, final Boolean silent) {
      String response;
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      final PrintStream printStream = new PrintStream(byteArrayOutputStream);
      final Session commandSession = sessionFactory.create(System.in, printStream, printStream);
      commandSession.put("APPLICATION", System.getProperty("karaf.name", "root"));
      FutureTask<String> commandFuture = new FutureTask<>(() -> {

         Subject subject = new Subject();
         subject.getPrincipals().add(new UserPrincipal("admin"));
         subject.getPrincipals().add(new RolePrincipal("admin"));
         subject.getPrincipals().add(new RolePrincipal("manager"));
         subject.getPrincipals().add(new RolePrincipal("viewer"));
         return SecurityManagerShim.callAs(subject, (Callable<String>) () -> {
            try {
               if (!silent) {
                  System.out.println(command);
                  System.out.flush();
               }
               commandSession.execute(command);
            } catch (Exception e) {
               e.printStackTrace(System.err);
            }
            printStream.flush();
            return byteArrayOutputStream.toString();
         });
      });

      try {
         executor.submit(commandFuture);
         response = commandFuture.get(timeout, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
         e.printStackTrace(System.err);
         response = "SHELL COMMAND TIMED OUT: ";
      }
      log.debug("Execute: {} - Response:{}", command, response);
      return response;
   }

   protected String executeCommand(final String command) {
      return executeCommand(command, COMMAND_TIMEOUT, false);
   }

   protected boolean withinReason(Callable<Boolean> callable) throws Throwable {
      long max = System.currentTimeMillis() + ASSERTION_TIMEOUT;
      while (true) {
         try {
            return callable.call();
         } catch (Throwable t) {
            if (System.currentTimeMillis() < max) {
               TimeUnit.SECONDS.sleep(1);
               continue;
            } else {
               throw t;
            }
         }
      }
   }

   public boolean verifyBundleInstalled(final String bundleName) throws Exception {
      boolean found = false;
      for (Bundle bundle : bundleContext.getBundles()) {
         log.debug("Checking: {}", bundle.getSymbolicName());
         if (bundle.getSymbolicName().contains(bundleName)) {
            found = true;
            break;
         }
      }
      return found;
   }

   protected Object waitForService(String filter, long timeout) throws InvalidSyntaxException, InterruptedException {
      ServiceTracker<Object, Object> st = new ServiceTracker<>(bundleContext, bundleContext.createFilter(filter), null);
      try {
         st.open();
         return st.waitForService(timeout);
      } finally {
         st.close();
      }
   }
}
