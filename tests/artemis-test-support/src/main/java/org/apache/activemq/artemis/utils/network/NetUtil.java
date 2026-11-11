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

package org.apache.activemq.artemis.utils.network;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.utils.ExecuteUtil;
import org.junit.jupiter.api.Test;

/**
 * This utility class will use sudo commands to start "fake" network cards on a given address. It's used on tests that
 * need to emmulate network outages and split brains.
 * <p>
 * If you write a new test using this class, please make special care on undoing the config, especially in case of
 * failures, by calling the {@link #cleanup()} method.
 * <p>
 * You need special sudo authorizations on your system to let this class work:
 * <p>
 * Add the following at the end of your /etc/sudoers (use the sudo visudo command)");
 * <pre>
 * # ------------------------------------------------------- ");
 * yourUserName ALL = NOPASSWD: /sbin/ifconfig");
 * # ------------------------------------------------------- ");
 * </pre>
 */
public class NetUtil extends ExecuteUtil {

   public static boolean checkIP(String ip) throws Exception {
      InetAddress ipAddress = null;
      try {
         ipAddress = InetAddress.getByName(ip);
      } catch (Exception e) {
         e.printStackTrace(); // not supposed to happen
         return false;
      }
      NetworkHealthCheck healthCheck = new NetworkHealthCheck(null, 100, 100);
      return healthCheck.check(ipAddress);
   }

   public static AtomicInteger nextDevice = new AtomicInteger(0);

   // IP / device (device being only valid on linux)
   public static Map<String, String> networks = new ConcurrentHashMap<>();

   private enum OS {
      MAC, LINUX, NON_SUPORTED;
   }

   static final OS osUsed;
   static final String user = System.getProperty("user.name");

   static {
      OS osTmp;

      String propOS = System.getProperty("os.name").toUpperCase();

      if (propOS.contains("MAC")) {
         osTmp = OS.MAC;
      } else if (propOS.contains("LINUX")) {
         osTmp = OS.LINUX;
      } else {
         osTmp = OS.NON_SUPORTED;
      }

      osUsed = osTmp;
   }

   public static void skipIfNotSudo() {
      skipIfNotSupportedOS();

      boolean canSudo = canSudo();
      if (!canSudo) {
         StringWriter writer = new StringWriter();
         PrintWriter out = new PrintWriter(writer);
         out.println("In order to run this test you must be able to sudo ifconfig.");
         out.println("E.g add the following at the end of your /etc/sudoers (use the visudo command)");
         out.println("# ------------------------------------------------------- ");
         out.println(user + " ALL = NOPASSWD: /sbin/ifconfig");
         out.println("# ------------------------------------------------------- ");

         System.out.println(writer.toString());

         assumeTrue(canSudo, "Not able to sudo ifconfig");
      }
   }

   public static void skipIfNotSupportedOS() {
      assumeTrue(osUsed != OS.NON_SUPORTED, "non supported OS");
   }

   public static void cleanup() {
      nextDevice.set(0);

      Set entrySet = networks.entrySet();
      Iterator iter = entrySet.iterator();
      while (iter.hasNext()) {
         Map.Entry<String, String> entry = (Map.Entry<String, String>) iter.next();
         try {
            netDown(entry.getKey(), entry.getValue(), true);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static void netUp(String ip) throws Exception {
      String deviceID = "lo:" + nextDevice.incrementAndGet();
      netUp(ip, deviceID);
   }

   public static void netUp(String ip, String deviceID) throws Exception {
      if (osUsed == OS.MAC) {
         if (runCommand(false, "sudo", "-n", "ifconfig", "lo0", "alias", ip) != 0) {
            fail("Cannot sudo ifconfig for ip " + ip);
         }
         networks.put(ip, "lo0");
      } else if (osUsed == OS.LINUX) {
         if (runCommand(false, "sudo", "-n", "ifconfig", deviceID, ip, "netmask", "255.0.0.0") != 0) {
            fail("Cannot sudo ifconfig for ip " + ip);
         }
         networks.put(ip, deviceID);
      } else {
         fail("OS not supported");
      }
   }

   public static void netDown(String ip) throws Exception {
      netDown(ip, false);
   }


   public static void netDown(String ip, boolean force) throws Exception {
      String device = networks.remove(ip);
      if (!force) {
         // in case the netDown is coming from a different VM (spawned tests)
         assertNotNull(device, "ip " + ip + "wasn't set up before");
      }
      netDown(ip, device, force);
   }

   public static void netDown(String ip, String device, boolean force) throws Exception {
      networks.remove(ip);

      if (osUsed == OS.MAC) {
         if (runCommand(false, "sudo", "-n", "ifconfig", "lo0", "-alias", ip) != 0) {
            if (!force) {
               fail("Cannot sudo ifconfig for ip " + ip);
            }
         }
      } else if (osUsed == OS.LINUX) {
         if (runCommand(false, "sudo", "-n", "ifconfig", device, "down") != 0) {
            if (!force) {
               fail("Cannot sudo ifconfig for ip " + ip);
            }
         }
      } else {
         fail("OS not supported");
      }
   }

   public static boolean canSudo() {
      try {
         return runCommand(false, "sudo", "-n", "ifconfig") == 0;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }

   @Test
   public void testCanSudo() throws Exception {
      assertTrue(canSudo());
   }
}
