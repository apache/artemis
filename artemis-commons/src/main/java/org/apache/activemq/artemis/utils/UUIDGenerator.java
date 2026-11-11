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
package org.apache.activemq.artemis.utils;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class UUIDGenerator {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final UUIDGenerator sSingleton = new UUIDGenerator();

   // Windows has some fake adapters that will return the same HARDWARE ADDRESS on any computer. We need to ignore those
   private static final byte[][] DENY_LIST = new byte[][]{{2, 0, 84, 85, 78, 1}};

   /**
    * Random-generator, used by various UUID-generation methods:
    */
   private Random mRnd = null;

   private final Object mTimerLock = new Object();

   private UUIDTimer mTimer = null;

   private byte[] address;

   /**
    * Constructor is private to enforce singleton access.
    */
   private UUIDGenerator() {
   }

   /**
    * Method used for accessing the singleton generator instance.
    *
    * @return Instance of UUID Generator
    */
   public static UUIDGenerator getInstance() {
      return UUIDGenerator.sSingleton;
   }

   /**
    * Method for getting the shared random number generator used for generating the UUIDs. This way the initialization
    * cost is only taken once; access need not be synchronized (or in cases where it has to, SecureRandom takes care of
    * it); it might even be good for getting really 'random' stuff to get shared access..
    *
    * @return A Random number generator
    */
   public Random getRandomNumberGenerator() {
      /*
       * Could be synchronized, but since side effects are trivial (ie.
       * possibility of generating more than one SecureRandom, of which all but
       * one are dumped) let's not add synchronization overhead:
       */
      if (mRnd == null) {
         mRnd = new SecureRandom();
      }
      return mRnd;
   }

   public UUID generateTimeBasedUUID(final byte[] byteAddr) {
      byte[] contents = new byte[16];
      int pos = 10;
      System.arraycopy(byteAddr, 0, contents, pos, 6);

      synchronized (mTimerLock) {
         if (mTimer == null) {
            mTimer = new UUIDTimer(getRandomNumberGenerator());
         }

         mTimer.getTimestamp(contents);
      }

      return new UUID(UUID.TYPE_TIME_BASED, contents);
   }

   public UUID fromJavaUUID(java.util.UUID uuid) {
      return new UUID(uuid);
   }

   public byte[] generateDummyAddress() {
      Random rnd = getRandomNumberGenerator();
      byte[] dummy = new byte[6];
      rnd.nextBytes(dummy);
      /*
       * Need to set the broadcast bit to indicate it's not a real
       * address.
       */
      dummy[0] |= (byte) 0x01;

      if (logger.isDebugEnabled()) {
         logger.debug("using dummy address {}", UUIDGenerator.asString(dummy));
      }
      return dummy;
   }

   /**
    * If running java 6 or above, returns {@link NetworkInterface#getHardwareAddress()}, else return {@code null}. The
    * first hardware address is returned when iterating all the NetworkInterfaces
    *
    * @return A byte array containing the hardware address
    */
   public static byte[] getHardwareAddress() {
      try {
         // check if we have enough security permissions to create and shutdown an executor
         ExecutorService executor = Executors.newFixedThreadPool(1, ActiveMQThreadFactory.defaultThreadFactory(UUIDGenerator.class.getName()));
         executor.shutdownNow();
      } catch (Throwable t) {
         // not enough security permission
         return null;
      }

      try {
         List<NetworkInterface> ifaces = getAllNetworkInterfaces();

         if (ifaces.isEmpty()) {
            return null;
         }

         byte[] address = findFirstMatchingHardwareAddress(ifaces);
         if (address != null) {
            if (logger.isDebugEnabled()) {
               logger.debug("using hardware address {}", UUIDGenerator.asString(address));
            }
            return address;
         }
         return null;
      } catch (Exception e) {
         return null;
      }
   }

   public SimpleString generateSimpleStringUUID() {
      return SimpleString.of(generateStringUUID());
   }

   public UUID generateUUID() {
      byte[] address = getAddressBytes();

      UUID uid = generateTimeBasedUUID(address);

      return uid;
   }

   public String generateStringUUID() {
      byte[] address = getAddressBytes();

      if (address == null) {
         return java.util.UUID.randomUUID().toString();
      } else {
         return generateTimeBasedUUID(address).toString();
      }
   }

   public static byte[] getZeroPaddedSixBytes(final byte[] bytes) {
      if (bytes == null) {
         return null;
      }
      if (bytes.length > 0 && bytes.length <= 6) {
         if (bytes.length == 6) {
            return bytes;
         } else {
            // pad with zeroes to have a 6-byte array
            byte[] paddedAddress = new byte[6];
            System.arraycopy(bytes, 0, paddedAddress, 0, bytes.length);
            for (int i = bytes.length; i < 6; i++) {
               paddedAddress[i] = 0;
            }
            return paddedAddress;
         }
      }
      return null;
   }


   private static boolean isDenyList(final byte[] address) {
      for (byte[] denyList : UUIDGenerator.DENY_LIST) {
         if (Arrays.equals(address, denyList)) {
            return true;
         }
      }
      return false;
   }

   private byte[] getAddressBytes() {
      if (address == null) {
         address = UUIDGenerator.getHardwareAddress();
         if (address == null) {
            address = generateDummyAddress();
         }
      }

      return address;
   }

   private static String asString(final byte[] bytes) {
      if (bytes == null) {
         return null;
      }

      StringBuilder s = new StringBuilder();
      for (int i = 0; i < bytes.length - 1; i++) {
         s.append(Integer.toHexString(bytes[i]));
         s.append(":");
      }
      s.append(bytes[bytes.length - 1]);
      return s.toString();
   }

   private static List<NetworkInterface> getAllNetworkInterfaces() {
      Enumeration<NetworkInterface> networkInterfaces;
      try {
         networkInterfaces = NetworkInterface.getNetworkInterfaces();

         if (networkInterfaces == null) {
            return Collections.emptyList();
         }

         List<NetworkInterface> ifaces = new ArrayList<>();
         while (networkInterfaces.hasMoreElements()) {
            ifaces.add(networkInterfaces.nextElement());
         }
         return ifaces;
      } catch (SocketException e) {
         return Collections.emptyList();
      }
   }

   private static byte[] findFirstMatchingHardwareAddress(List<NetworkInterface> ifaces) {
      ExecutorService executor = Executors.newFixedThreadPool(ifaces.size(), ActiveMQThreadFactory.defaultThreadFactory(UUIDGenerator.class.getName()));
      Collection<Callable<byte[]>> tasks = new ArrayList<>(ifaces.size());

      for (final NetworkInterface networkInterface : ifaces) {
         tasks.add(() -> {
            boolean up = networkInterface.isUp();
            boolean loopback = networkInterface.isLoopback();
            boolean virtual = networkInterface.isVirtual();

            if (loopback || virtual || !up) {
               throw new Exception("not suitable interface");
            }

            byte[] address = networkInterface.getHardwareAddress();
            if (address != null) {

               byte[] paddedAddress = UUIDGenerator.getZeroPaddedSixBytes(address);

               if (UUIDGenerator.isDenyList(address)) {
                  throw new Exception("deny listed address");
               }

               if (paddedAddress != null) {
                  return paddedAddress;
               }
            }

            throw new Exception("invalid network interface");
         });
      }
      try {
         // we wait 5 seconds to get the first matching hardware address. After that, we give up and return null
         byte[] address = executor.invokeAny(tasks, 5, TimeUnit.SECONDS);
         return address;
      } catch (Exception e) {
         return null;
      } finally {
         executor.shutdownNow();
      }
   }
}
