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

package org.apache.activemq.artemis.cli.commands.helper;

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelperBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   File artemisHome;
   File artemisInstance;

   HelperBase(String homeProperty) {
      setArtemisHome(getHome(homeProperty));
      logger.debug("using artemisHome as {}", artemisHome);
   }

   HelperBase(File artemisHome) {
      setArtemisHome(artemisHome);
      logger.debug("using artemisHome as {}", artemisHome);
   }


   public static File getHome(String homeProperty) {
      String valueHome = System.getProperty(homeProperty);
      if (valueHome == null) {
         throw new IllegalArgumentException("System property " + valueHome + " not defined");
      }
      return new File(valueHome);
   }

   public File getArtemisHome() {
      return artemisHome;
   }

   public HelperBase setArtemisHome(File artemisHome) {
      this.artemisHome = artemisHome;
      if (!artemisHome.exists()) {
         throw new IllegalArgumentException(artemisHome + " folder does not exist in the file system");
      }
      if (!new File(artemisHome, "/bin").exists() || !new File(artemisHome, "/bin/artemis").exists()) {
         throw new IllegalArgumentException("invalid bin folder");
      }

      return this;
   }

   public File getArtemisInstance() {
      return artemisInstance;
   }

   public HelperBase setArtemisInstance(File artemisInstance) {
      this.artemisInstance = artemisInstance;
      return this;
   }

   public String[] getArgs() {
      return args;
   }

   public HelperBase setArgs(String... args) {
      this.args = args;
      return this;
   }

   public HelperBase addArgs(String... args) {
      int initialLength = this.args == null ? 0 : this.args.length;
      String[] newArgs = new String[initialLength + args.length];
      for (int i = 0; i < initialLength; i++) {
         newArgs[i] = this.args[i];
      }
      for (int i = 0; i < args.length; i++) {
         newArgs[i + initialLength] = args[i];
      }
      this.args = newArgs;
      return this;
   }

   String[] args = new String[0];
}
