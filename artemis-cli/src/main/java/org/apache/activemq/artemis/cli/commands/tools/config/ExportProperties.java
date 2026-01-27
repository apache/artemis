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

package org.apache.activemq.artemis.cli.commands.tools.config;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.Configurable;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import picocli.CommandLine;

@CommandLine.Command(name = "properties", description = "Export the broker's configuration as a properties file to be used with broker properties.")
public class ExportProperties extends Configurable {

   @CommandLine.Option(names = "--output", description = "Output name for the file.", defaultValue = "broker.properties")
   private File output;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      PrintStream out = context.out;
      OutputStream outputStream = null;

      System.out.println("Exporting configuration as broker.properties");

      if (output == null) {
         throw new RuntimeException("output is a required property");
      }


      if (output != null) {
         outputStream = new BufferedOutputStream(new FileOutputStream(output));
         PrintStream printStream = new PrintStream(outputStream);
         context.out = printStream;
      }

      FileConfiguration configuration = readConfiguration();
      configuration.exportAsProperties(output);

      return null;
   }

}
