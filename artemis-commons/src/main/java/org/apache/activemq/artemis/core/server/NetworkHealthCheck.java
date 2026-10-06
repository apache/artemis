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
package org.apache.activemq.artemis.core.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.security.PrivilegedAction;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.logs.ActiveMQUtilLogger;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This will use {@link InetAddress#isReachable(int)} to determine if the network is alive. It will have a set of
 * addresses, and if any address is reached the network will be considered alive.
 */
public class NetworkHealthCheck extends ActiveMQScheduledComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Set<ActiveMQComponent> componentList = ConcurrentHashMap.newKeySet();
   private final Set<String> addresses = ConcurrentHashMap.newKeySet();
   private final Set<URL> urls = ConcurrentHashMap.newKeySet();
   private NetworkInterface networkInterface;

   public static final String IPV6_DEFAULT_COMMAND = Env.isWindowsOs() ? "ping -n 1 -w %d000 %s" : "ping6 -c 1 %2$s";

   public static final String IPV4_DEFAULT_COMMAND = Env.isMacOs() ? "ping -c 1 -t %d %s" : Env.isWindowsOs() ? "cmd /C ping -n 1 -w %d000 %s | findstr /i TTL" : "ping -c 1 -w %d %s";

   private String ipv4Command = IPV4_DEFAULT_COMMAND;

   private String ipv6Command = IPV6_DEFAULT_COMMAND;

   // To be used on tests. As we use the loopback as a valid address on tests.
   private boolean ignoreLoopback = false;

   private boolean ownShutdown = false;

   /**
    * The timeout to be used on isReachable
    */
   private int networkTimeout;

   public NetworkHealthCheck() {
      this(null, 1000, 1000);
   }

   public NetworkHealthCheck(String nicName, long checkPeriod, int networkTimeout) {
      super(null, null, checkPeriod, TimeUnit.MILLISECONDS, false);
      this.networkTimeout = networkTimeout;
      this.setNICName(nicName);
   }

   public NetworkHealthCheck setNICName(String nicName) {
      NetworkInterface netToUse;
      try {
         if (nicName != null) {
            netToUse = NetworkInterface.getByName(nicName);
         } else {
            netToUse = null;
         }
      } catch (Exception e) {
         ActiveMQUtilLogger.LOGGER.failedToSetNIC(nicName, e);
         netToUse = null;
      }

      this.networkInterface = netToUse;
      return this;
   }

   public boolean isIgnoreLoopback() {
      return ignoreLoopback;
   }

   public NetworkHealthCheck setIgnoreLoopback(boolean ignoreLoopback) {
      this.ignoreLoopback = ignoreLoopback;
      return this;
   }

   public Set<String> getAddresses() {
      return addresses;
   }

   public Set<URL> getUrls() {
      return urls;
   }

   public String getNICName() {
      if (networkInterface != null) {
         return networkInterface.getName();
      } else {
         return null;
      }
   }

   public NetworkHealthCheck parseAddressList(String addressList) {
      if (addressList != null) {
         String[] addresses = addressList.split(",");

         for (String address : addresses) {
            if (!address.trim().isEmpty()) {
               try {
                  String strAddress = address.trim();
                  this.addAddress(strAddress);
               } catch (Exception e) {
                  ActiveMQUtilLogger.LOGGER.failedToParseAddressList(addressList, e);
               }
            }
         }
      }

      return this;
   }

   public NetworkHealthCheck parseURIList(String addressList) {
      if (addressList != null) {
         String[] addresses = addressList.split(",");

         for (String address : addresses) {
            if (!address.trim().isEmpty()) {
               try {
                  this.addURL(new URL(address.trim()));
               } catch (Exception e) {
                  ActiveMQUtilLogger.LOGGER.failedToParseUrlList(addressList, e);
               }
            }
         }
      }

      return this;
   }

   @Override
   protected ActiveMQThreadFactory getThreadFactory() {
      return new ActiveMQThreadFactory("network-checker", false, getThisClassLoader());
   }


   private ClassLoader getThisClassLoader() {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<ClassLoader>) () -> NetworkHealthCheck.this.getClass().getClassLoader());

   }

   public int getNetworkTimeout() {
      return networkTimeout;
   }

   @Override
   public synchronized NetworkHealthCheck setPeriod(long period) {
      super.setPeriod(period);
      return this;
   }

   @Override
   public synchronized NetworkHealthCheck setTimeUnit(TimeUnit timeUnit) {
      super.setTimeUnit(timeUnit);
      return this;
   }

   public NetworkHealthCheck setNetworkTimeout(int networkTimeout) {
      this.networkTimeout = networkTimeout;
      return this;
   }

   public NetworkHealthCheck addComponent(ActiveMQComponent component) {
      componentList.add(component);
      checkStart();
      return this;
   }

   public NetworkHealthCheck clearComponents() {
      componentList.clear();
      return this;
   }

   public NetworkHealthCheck addAddress(String straddress) {
      InetAddress address = internalCheck(straddress);
      if (address == null) {
         ActiveMQUtilLogger.LOGGER.addressWasntReacheable(straddress);
      }

      if (!ignoreLoopback && address != null && address.isLoopbackAddress()) {
         ActiveMQUtilLogger.LOGGER.addressloopback(straddress);
      } else {
         addresses.add(straddress);
         checkStart();
      }
      return this;
   }

   public NetworkHealthCheck removeAddress(String straddress) {
      addresses.remove(straddress);
      return this;
   }

   public NetworkHealthCheck clearAddresses() {
      addresses.clear();
      return this;
   }

   public NetworkHealthCheck addURL(URL url) {
      if (!check(url)) {
         ActiveMQUtilLogger.LOGGER.urlWasntReacheable(url.toString());
      }
      urls.add(url);
      checkStart();
      return this;
   }

   public NetworkHealthCheck removeURL(URL url) {
      urls.remove(url);
      return this;
   }

   public NetworkHealthCheck clearURL() {
      urls.clear();
      return this;
   }

   public String getIpv4Command() {
      return ipv4Command;
   }

   public NetworkHealthCheck setIpv4Command(String ipv4Command) {
      this.ipv4Command = ipv4Command;
      return this;
   }

   public String getIpv6Command() {
      return ipv6Command;
   }

   public NetworkHealthCheck setIpv6Command(String ipv6Command) {
      this.ipv6Command = ipv6Command;
      return this;
   }

   private void checkStart() {
      if (!isStarted() && (!addresses.isEmpty() || !urls.isEmpty()) && !componentList.isEmpty()) {
         try {
            this.run(); // run the first check immediately, this is to immediately shutdown the server if there's no net
         } finally {
            start();
         }
      }
   }

   @Override
   public void run() {
      boolean healthy = check();

      if (healthy) {
         for (ActiveMQComponent component : componentList) {
            if (!component.isStarted() && ownShutdown) {
               try {
                  ActiveMQUtilLogger.LOGGER.startingService(component.toString());
                  component.start();
               } catch (Exception e) {
                  ActiveMQUtilLogger.LOGGER.errorStartingComponent(component.toString(), e);
               }
            }
            ownShutdown = false;
         }
      } else {
         for (ActiveMQComponent component : componentList) {
            if (component.isStarted()) {
               ownShutdown = true;
               try {
                  ActiveMQUtilLogger.LOGGER.stoppingService(component.toString());
                  component.stop();
               } catch (Exception e) {
                  ActiveMQUtilLogger.LOGGER.errorStoppingComponent(component.toString(), e);
               }
            }
         }
      }

   }

   /**
    * {@return true if no checks were done or if one address/url responds; false if all addresses/urls fail}
    */
   public boolean check() {
      if (isEmpty()) {
         return true;
      }

      for (String address : addresses) {
         if (check(address)) {
            return true;
         }
      }

      for (URL url : urls) {
         if (check(url)) {
            return true;
         }
      }

      return false;
   }

   public boolean check(String straddress) {
      if (straddress == null) {
         return false;
      }

      return internalCheck(straddress) != null;
   }

   private InetAddress internalCheck(String straddress) {
      try {
         InetAddress address = InetAddress.getByName(straddress);
         address = InetAddress.getByName(address.getHostName());
         if (check(address)) {
            return address;
         } else {
            return null;
         }
      } catch (Exception e) {
         ActiveMQUtilLogger.LOGGER.failedToCheckAddress(straddress, e);
         return null;
      }
   }

   public boolean check(InetAddress address) throws IOException, InterruptedException {
      if (!hasCustomPingCommand() && isReachable(address)) {
         logger.trace("{} OK", address);
         return true;
      } else {
         return purePing(address);
      }
   }

   protected boolean isReachable(InetAddress address) throws IOException {
      return address.isReachable(networkInterface, 0, networkTimeout);
   }

   public boolean purePing(InetAddress address) throws IOException, InterruptedException {
      long timeout = Math.max(1, TimeUnit.MILLISECONDS.toSeconds(networkTimeout));
      // it did not work with a simple isReachable, it could be because there's no root access, so we will try ping executable

      logger.trace("purePing on canonical address {}", address.getCanonicalHostName());
      ProcessBuilder processBuilder;
      if (address instanceof Inet6Address) {
         processBuilder = buildProcess(ipv6Command, timeout, address.getCanonicalHostName());
      } else {
         processBuilder = buildProcess(ipv4Command, timeout, address.getCanonicalHostName());
      }

      Process pingProcess = processBuilder.start();

      readStream(pingProcess.getInputStream(), false);
      readStream(pingProcess.getErrorStream(), true);

      return pingProcess.waitFor() == 0;
   }


   private ProcessBuilder buildProcess(String expressionCommand, long timeout, String host) {
      String command = String.format(expressionCommand, timeout, host);

      logger.debug("executing ping:: {}", command);
      ProcessBuilder builder = new ProcessBuilder(command.split(" "));

      return builder;
   }

   private void readStream(InputStream stream, boolean error) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

      String inputLine;
      while ((inputLine = reader.readLine()) != null) {
         if (error) {
            ActiveMQUtilLogger.LOGGER.failedToReadFromStream(inputLine);
         } else {
            logger.debug(inputLine);
         }
      }

      reader.close();
   }

   public boolean check(URL url) {
      if (url == null) {
         return false;
      }

      try {
         URLConnection connection = url.openConnection();
         connection.setReadTimeout(networkTimeout);
         InputStream is = connection.getInputStream();
         is.close();
         return true;
      } catch (Exception e) {
         ActiveMQUtilLogger.LOGGER.failedToCheckURL(url.toString(), e);
         return false;
      }
   }

   public boolean isEmpty() {
      return addresses.isEmpty() && urls.isEmpty();
   }

   public boolean hasCustomPingCommand() {
      return !getIpv4Command().equals(IPV4_DEFAULT_COMMAND) || !getIpv6Command().equals(IPV6_DEFAULT_COMMAND);
   }
}
