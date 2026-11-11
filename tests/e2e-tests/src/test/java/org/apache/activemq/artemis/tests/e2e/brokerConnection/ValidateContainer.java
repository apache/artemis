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
package org.apache.activemq.artemis.tests.e2e.brokerConnection;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.apache.activemq.artemis.tests.e2e.common.ContainerService;

/**
 * The purpose of this class is to validate if the container and Docker (or an equivalent) is available on the
 * environment. Tests can use an assume to be ignored in case the image is not available. The test will also cache the
 * result by creating a file target/org.apache.activemq.artemis.tests.smoke.brokerConnection.ValidateContainer.ok So, we
 * won't keep redoing the check during development on an IDE.
 */
public class ValidateContainer {

   private static final boolean hasContainer;
   static {
      File fileHasImage = new File("target/" + ValidateContainer.class.getName() + ".ok");
      boolean internalHasContainer = true;
      if (fileHasImage.exists()) {
         // this is to speed up execution when inside the IDE. Just reuse the last execution's file. If the file exists from a previous run, we know the container is available
         internalHasContainer = true;
      } else {
         try {
            ContainerService service = ContainerService.getService();

            Object brokerService = service.newBrokerImage();
            service.exposePorts(brokerService, 61616);
            service.start(brokerService);
            service.stop(brokerService);
            fileHasImage.createNewFile();
         } catch (Throwable e) {
            e.printStackTrace();
            internalHasContainer = false;
         }
      }

      hasContainer = internalHasContainer;
   }

   public static boolean hasContainer() {
      return hasContainer;
   }

   /**
    * assume clause to validate the Artemis Container and the Container provider are available
    */
   public static void assumeArtemisContainer() {
      assumeTrue(hasContainer(), "Please build the container using 'mvn install -De2e-tests.skipImageBuild=false' before running these tests");
   }

}
