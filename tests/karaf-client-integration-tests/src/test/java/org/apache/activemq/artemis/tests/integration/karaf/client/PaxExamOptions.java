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
package org.apache.activemq.artemis.tests.integration.karaf.client;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.DefaultCompositeOption;

import java.io.File;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;

public enum PaxExamOptions {
   KARAF(
           karafDistributionConfiguration()
                   .frameworkUrl(
                           maven()
                                   .groupId("org.apache.karaf")
                                   .artifactId("apache-karaf")
                                   .versionAsInProject()
                                   .type("zip"))
                   .name("Apache Karaf")
                   .useDeployFolder(false)
                   .unpackDirectory(new File("target/paxexam/unpack/")),
           keepRuntimeFolder(),
           configureConsole().ignoreLocalConsole(),
           when(System.getProperty("maven.repo.local") != null).useOptions(
              editConfigurationFilePut("etc/org.ops4j.pax.url.mvn.cfg",
                                       "org.ops4j.pax.url.mvn.localRepository",
                                       System.getProperty("maven.repo.local"))),
           logLevel(LogLevelOption.LogLevel.INFO)
   ),
   ARTEMIS_CORE_CLIENT(
           features(
                   maven()
                           .groupId("org.apache.artemis")
                           .artifactId("artemis-features")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(),
                   "artemis-core-client")
   ),
   ARTEMIS_JMS_CLIENT(
           features(
                   maven()
                           .groupId("org.apache.artemis")
                           .artifactId("artemis-features")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(),
                   "artemis-jms-client")
   ),
   ARTEMIS_AMQP_CLIENT(
           features(
                   maven()
                           .groupId("org.apache.artemis")
                           .artifactId("artemis-features")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(),
                   "artemis-amqp-client")
   ),
   ARTEMIS_OPENWIRE_CLIENT(
           features(
                   maven()
                           .groupId("org.apache.artemis")
                           .artifactId("artemis-features")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(),
                   "artemis-openwire-client")
   ),
   ARTEMIS_TRANSACTION_MANAGER(
           features(
                   maven()
                           .groupId("org.apache.karaf.features")
                           .artifactId("enterprise")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(),
                   "transaction-manager-narayana")
   );

   private final Option[] options;

   PaxExamOptions(Option... options) {
      this.options = options;
   }

   public Option option() {
      return new DefaultCompositeOption(options);
   }
}
