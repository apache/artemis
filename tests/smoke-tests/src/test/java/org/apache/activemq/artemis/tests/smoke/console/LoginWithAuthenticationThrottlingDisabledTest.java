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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.activemq.artemis.cli.commands.Create;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the {@link LoginTest} tests with the console authentication throttling
 * disabled, i.e. {@code -Dhawtio.authenticationThrottled=false}.
 */
//Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class LoginWithAuthenticationThrottlingDisabledTest extends LoginTest {

   @Override
   protected boolean isAuthenticationThrottled() {
      return false;
   }

   public LoginWithAuthenticationThrottlingDisabledTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @BeforeEach
   @Override
   public void before() throws Exception {
      Path profilePath = Paths.get(getServerLocation(serverName), "etc", Create.ETC_ARTEMIS_PROFILE);
      String content = Files.readString(profilePath);

      final String disableHawtioAuthenticationThrottlingComment = "# Disable hawtio authentication throttling";

      if (!content.contains(disableHawtioAuthenticationThrottlingComment)) {
         Files.writeString(profilePath, content + System.lineSeparator() + System.lineSeparator() +
            disableHawtioAuthenticationThrottlingComment + System.lineSeparator() +
            "JAVA_ARGS=\"$JAVA_ARGS -Dhawtio.authenticationThrottled=false\"" + System.lineSeparator());
      }

      super.before();
   }
}
