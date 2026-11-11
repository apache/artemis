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
package org.apache.activemq.artemis.cli.commands;

import java.io.File;

import org.apache.activemq.artemis.dto.BrokerDTO;
import picocli.CommandLine.Command;

@Command(name = "stop", description = "Stop the broker.")
public class Stop extends Configurable {

   public static final String STOP_FILE_NAME = "STOP_ME";
   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      BrokerDTO broker = getBrokerDTO();

      File file = broker.server.getConfigurationFile().getParentFile();

      File stopFile = new File(file, STOP_FILE_NAME);

      stopFile.createNewFile();

      return null;
   }
}
