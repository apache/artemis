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
package org.apache.activemq.artemis.tests.e2e.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import org.apache.activemq.artemis.tests.util.CFUtil;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * I am intentionally not depending directly into TestContainer. I intend in a near future to support kubernetes and
 * podman and I would like to keep an interface between our tests and the Container provider.
 */
public abstract class ContainerService {

   public static final ContainerService service;

   static {
      ContainerService loadingService;
      try {
         String providerName = System.getProperty(ContainerService.class.getName() + ".service");
         if (providerName == null) {
            loadingService = new TestContainerImpl();
         } else {
            loadingService = (ContainerService) Class.forName(providerName).getDeclaredConstructor().newInstance();
         }
      } catch (Throwable e) {
         e.printStackTrace();
         loadingService = null;
      }

      service = loadingService;
   }

   public static ContainerService getService() {
      return service;
   }

   public abstract Object newNetwork();

   public abstract Object newBrokerImage();

   public abstract Object newInterconnectImage();

   public abstract void setNetwork(Object container, Object network);

   public abstract void exposePorts(Object container, Integer... ports);

   public abstract void exposeFile(Object container, String hostPath, String containerPath);

   public abstract void exposeFolder(Object container, String hostPath, String containerPath);

   public abstract void copyFileToContainer(Object container, String hostPath, String containerPath);

   public abstract void exposeBrokerHome(Object container, String brokerHome);

   public abstract void startLogging(Object container, String prefix);

   public abstract void start(Object container);

   public abstract void kill(Object container);

   public abstract void stop(Object container);

   public abstract void restartWithStop(Object container);

   public abstract void restartWithKill(Object container);

   public abstract int getPort(Object container, int mappedPort);

   public abstract void exposeHosts(Object container, String... hosts);

   /**
    * prepare the instance folder to run inside the docker image
    */
   public abstract void prepareInstance(String home) throws Exception;

   public abstract String getHost(Object container);

   public abstract ConnectionFactory createCF(Object container, String protocol);

   public abstract String createURI(Object container, int port);

   public abstract ConnectionFactory createCF(Object container, String protocol, int port);
   public abstract ConnectionFactory createCF(Object container, String protocol, int port, String extraURL);

   public boolean waitForServerToStart(Object container, String username, String password, long timeout) throws InterruptedException {
      long realTimeout = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < realTimeout) {
         try {
            ConnectionFactory cf = createCF(container, "core");
            cf.createConnection(username, password).close();
            if (cf instanceof AutoCloseable autoCloseable) {
               autoCloseable.close();
            }
            System.out.println("server started");
         } catch (Exception e) {
            System.out.println("awaiting server start at ");
            Thread.sleep(500);
            continue;
         }
         return true;
      }

      return false;
   }

   public abstract void logWait(Object container, String log);

   public abstract Object newZookeeperImage();

   public abstract void withEnvVar(Object container, String varName, String varValue);

   public abstract void pause(Object container);

   public abstract void unpause(Object container);

   public abstract String getStatus(Object container);

   private static class TestContainerImpl extends ContainerService {

      @Override
      public ConnectionFactory createCF(Object container, String protocol) {
         return createCF(container, protocol, 61616);
      }

      @Override
      public void prepareInstance(String home) throws Exception {
         File homeFile = new File(home);
         assertTrue(homeFile.exists());
         assertTrue(homeFile.isDirectory());

         copyFile("artemis", home + "/bin/artemis");
         copyFile("artemis.profile", home + "/etc/artemis.profile");
         copyFile("bootstrap.xml", home + "/etc/bootstrap.xml");
      }

      private void copyFile(String from, String to) throws IOException {
         File file = new File(E2ETestBase.basedir + "/src/main/resources/containerService/" + from);
         Files.copy(file.toPath(), new File(to).toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      @Override
      public ConnectionFactory createCF(Object container, String protocol, int port) {
         return CFUtil.createConnectionFactory("amqp", "tcp://" + getHost(container) + ":" + getPort(container, port));
      }
      @Override
      public ConnectionFactory createCF(Object container, String protocol, int port, String extraURI) {
         System.out.println("tcp://" + getHost(container) + ":" + getPort(container, port) + extraURI);
         return CFUtil.createConnectionFactory("amqp", "tcp://" + getHost(container) + ":" + getPort(container, port) + extraURI);
      }

      @Override
      public Object newNetwork() {
         return Network.newNetwork();
      }

      @Override
      public Object newBrokerImage() {
         String imageVersion = System.getProperty("ContainerService.artemis-image.version", "latest");
         GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("activemq-artemis:" + imageVersion));
         String userId = System.getProperty("ContainerService.artemis-image.userid", "");
         if (!userId.isEmpty()) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withUser(userId));
         }
         return container;
      }

      @Override
      public Object newInterconnectImage() {
         return new GenericContainer<>(DockerImageName.parse("quay.io/interconnectedcloud/qdrouterd:latest"));
      }

      @Override
      public void setNetwork(Object container, Object network) {
         ((GenericContainer)container).setNetwork((Network)network);
      }

      @Override
      public void exposePorts(Object container, Integer... ports) {
         ((GenericContainer)container).withExposedPorts(ports);
      }

      @Override
      public void exposeFile(Object container, String hostPath, String containerPath) {
         File file = new File(hostPath);
         assertTrue(file.exists());
         assertFalse(file.isDirectory());
         ((GenericContainer)container).addFileSystemBind(hostPath, containerPath, BindMode.READ_WRITE, SelinuxContext.SHARED);
      }

      @Override
      public void exposeFolder(Object container, String hostPath, String containerPath) {
         File file = new File(hostPath);
         assertTrue(file.exists());
         assertTrue(file.isDirectory());
         ((GenericContainer)container).addFileSystemBind(hostPath, containerPath, BindMode.READ_WRITE, SelinuxContext.SHARED);
      }

      @Override
      public void copyFileToContainer(Object container, String hostPath, String containerPath) {
         File file = new File(hostPath);
         assertTrue(file.exists());
         assertFalse(file.isDirectory());
         ((GenericContainer)container).withCopyFileToContainer(MountableFile.forHostPath(hostPath), containerPath);
      }

      @Override
      public void exposeBrokerHome(Object container, String brokerHome) {
         exposeFolder(container, brokerHome, "/var/lib/artemis-instance");
      }

      @Override
      public void start(Object containerObj) {
         GenericContainer<?> container = (GenericContainer) containerObj;
         container.setStartupCheckStrategy(new IsRunningStartupCheckStrategy());
         container.start();
      }

      @Override
      public void restartWithStop(Object containerObj) {
         stop(containerObj);
         start(containerObj);
      }

      @Override
      public void restartWithKill(Object containerObj) {
         kill(containerObj);
         start(containerObj);
      }

      @Override
      public void kill(Object containerObj) {
         GenericContainer container = (GenericContainer) containerObj;
         container.getDockerClient().killContainerCmd(container.getContainerId()).exec();
         container.stop();
      }

      @Override
      public int getPort(Object container, int mappedPort) {
         return ((GenericContainer)container).getMappedPort(mappedPort);
      }

      @Override
      public void exposeHosts(Object container, String... hosts) {
         ((GenericContainer)container).withNetworkAliases(hosts);
      }

      @Override
      public void stop(Object container) {
         if (container != null) {
            ((GenericContainer) container).stop();
         }
      }

      @Override
      public void startLogging(Object container, String prefix) {
         ((GenericContainer)container).withLogConsumer((Consumer<OutputFrame>) outputFrame -> System.out.print(prefix + outputFrame.getUtf8String()));
      }

      @Override
      public void logWait(Object container, String log) {
         LogMessageWaitStrategy logMessageWaitStrategy = new LogMessageWaitStrategy();
         logMessageWaitStrategy.withRegEx(log);
         ((GenericContainer)container).setWaitStrategy(logMessageWaitStrategy);
      }

      @Override
      public String createURI(Object container, int port) {
         GenericContainer genericContainer = (GenericContainer) container;
         return "tcp://" + genericContainer.getHost() + ":" + genericContainer.getMappedPort(port);
      }

      @Override
      public Object newZookeeperImage() {
         return new GenericContainer<>(DockerImageName.parse("zookeeper:latest"));
      }

      @Override
      public void withEnvVar(Object container, String varName, String varValue) {
         ((GenericContainer)container).withEnv(varName, varValue);
      }

      @Override
      public void pause(Object containerObj) {
         GenericContainer container = (GenericContainer) containerObj;
         container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
      }

      @Override
      public void unpause(Object containerObj) {
         GenericContainer container = (GenericContainer) containerObj;
         container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
      }

      @Override
      public String getStatus(Object containerObj) {
         GenericContainer container = (GenericContainer) containerObj;
         return container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec().getState().getStatus();
      }

      @Override
      public String getHost(Object container) {
         return ((GenericContainer)container).getHost();
      }

   }

}
