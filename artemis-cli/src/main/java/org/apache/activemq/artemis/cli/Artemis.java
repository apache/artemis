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
package org.apache.activemq.artemis.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.cli.commands.Action;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.AutoCompletion;
import org.apache.activemq.artemis.cli.commands.Completion;
import org.apache.activemq.artemis.cli.commands.Create;
import org.apache.activemq.artemis.cli.commands.Disconnect;
import org.apache.activemq.artemis.cli.commands.HelpAction;
import org.apache.activemq.artemis.cli.commands.InputAbstract;
import org.apache.activemq.artemis.cli.commands.InvalidOptionsError;
import org.apache.activemq.artemis.cli.commands.Kill;
import org.apache.activemq.artemis.cli.commands.Mask;
import org.apache.activemq.artemis.cli.commands.PWD;
import org.apache.activemq.artemis.cli.commands.PrintVersion;
import org.apache.activemq.artemis.cli.commands.Run;
import org.apache.activemq.artemis.cli.commands.Stop;
import org.apache.activemq.artemis.cli.commands.Upgrade;
import org.apache.activemq.artemis.cli.commands.activation.ActivationGroup;
import org.apache.activemq.artemis.cli.commands.address.AddressGroup;
import org.apache.activemq.artemis.cli.commands.check.CheckGroup;
import org.apache.activemq.artemis.cli.commands.messages.Browse;
import org.apache.activemq.artemis.cli.commands.Connect;
import org.apache.activemq.artemis.cli.commands.messages.Consumer;
import org.apache.activemq.artemis.cli.commands.messages.Producer;
import org.apache.activemq.artemis.cli.commands.messages.Transfer;
import org.apache.activemq.artemis.cli.commands.messages.perf.PerfGroup;
import org.apache.activemq.artemis.cli.commands.queue.QueueGroup;
import org.apache.activemq.artemis.cli.commands.tools.DataGroup;
import org.apache.activemq.artemis.cli.commands.tools.journal.PerfJournal;
import org.apache.activemq.artemis.cli.commands.user.UserGroup;
import org.apache.activemq.artemis.dto.ManagementContextDTO;
import org.apache.activemq.artemis.dto.XmlUtil;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Artemis is the main CLI entry point for managing/running a broker.
 * <p>
 * Want to start or debug a broker from an IDE?  This is probably the best class to run.  Make sure set the
 * -Dartemis.instance=path/to/instance system property. You should also use the 'apache-artemis' module for the class
 * path since that includes all artemis modules.
 * <p>
 * Notice that this class should not use any logging as it's part of the bootstrap and using logging here could disrupt
 * the order of bootstrapping on certain components (e.g. JMX being started from log4j)
 */
@Command(name = "artemis", description = "Apache Artemis Command Line")
public class Artemis implements Runnable {


   CommandLine commandLine;

   public CommandLine getCommandLine() {
      return commandLine;
   }

   public Artemis setCommandLine(CommandLine commandLine) {
      this.commandLine = commandLine;
      return this;
   }

   @Override
   public void run() {
      // We are running the shell by default.
      // if you type ./artemis we will go straight to the shell
      Shell.runShell(true);
   }

   public static void main(String... args) throws Exception {
      String home = System.getProperty("artemis.home");
      File fileHome = home != null ? new File(home) : null;
      String instance = System.getProperty("artemis.instance");
      File fileInstance = instance != null ? new File(instance) : null;


      String brokerEtc = System.getProperty("artemis.instance.etc");
      if (brokerEtc != null) {
         brokerEtc = brokerEtc.replace("\\", "/");
      } else {
         brokerEtc = instance + "/etc";
      }

      File fileBrokerETC = new File(brokerEtc);

      verifyManagementDTO(fileBrokerETC);

      Object result = execute(true, true, true, fileHome, fileInstance, fileBrokerETC, args);
      if (!Run.getEmbedded() && result instanceof Exception) {
         System.exit(1);
      }
   }


   // Notice this has to happen before any Log4j is used.
   //        otherwise Log4j's JMX will start the JMX before this property was able to tbe set
   public static void verifyManagementDTO(File etc) {
      if (etc != null) {
         File management = new File(etc, "management.xml");
         if (management.exists()) {
            try {
               ManagementContextDTO managementContextDTO = XmlUtil.decode(ManagementContextDTO.class, management);
               if (managementContextDTO != null && managementContextDTO.getAuthorisation() != null) {
                  System.setProperty("javax.management.builder.initial", "org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerBuilder");
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   public static Object internalExecute(String... args) throws Exception {
      return internalExecute(false, null, null, null, args);
   }

   public static Object execute(File artemisHome, File artemisInstance, File etcFolder, List<String> args) throws Exception {
      return execute(false, false, false, artemisHome, artemisInstance, etcFolder, args.toArray(new String[args.size()]));
   }

   public static Object execute(boolean inputEnabled, boolean useSystemOut, boolean shellEnabled, File artemisHome, File artemisInstance, File etcFolder, String... args) throws Exception {

      // using a default etc in case that is not set in the variables
      if (etcFolder == null && artemisInstance != null) {
         etcFolder = new File(artemisInstance, "etc");
      }

      verifyManagementDTO(etcFolder);

      if (inputEnabled) {
         InputAbstract.enableInput();
      }

      final ActionContext context;

      if (useSystemOut) {
         context = new ActionContext();
      } else {
         context = new ActionContext(InputStream.nullInputStream(), new PrintStream(OutputStream.nullOutputStream()), System.err);
      }

      ActionContext.setSystem(context);

      try {
         return internalExecute(shellEnabled, artemisHome, artemisInstance, etcFolder, args, context);
      } catch (ConfigurationException configException) {
         context.err.println(configException.getMessage());
         context.out.println();
         context.out.println("Configuration should be specified as 'scheme:location'. Default configuration is 'xml:${ARTEMIS_INSTANCE}/etc/bootstrap.xml'");
         return configException;
      } catch (CLIException cliException) {
         context.err.println(cliException.getMessage());
         return cliException;
      } catch (NullPointerException e) {
         // Yeah.. I really meant System.err..
         // this is the CLI and System.out and System.err are common places for interacting with the user
         // this is a programming error that must be visualized and corrected
         e.printStackTrace();
         return e;
      } catch (RuntimeException | InvalidOptionsError re) {
         context.err.println(re.getMessage());
         context.out.println();
         HelpAction.help(buildCommand(true, true, shellEnabled), "help");
         return re;
      } finally {
         ActionContext.setSystem(new ActionContext());
      }
   }

   /**
    * This method is used to validate exception returns. Useful on test cases.
    */
   private static Object internalExecute(boolean shellEnabled, File artemisHome, File artemisInstance, File etcFolder, String[] args) throws Exception {
      return internalExecute(shellEnabled, artemisHome, artemisInstance, etcFolder, args, new ActionContext());
   }

   public static Object internalExecute(boolean shellEnabled, File artemisHome, File artemisInstance, File etcFolder, String[] args, ActionContext context) throws Exception {
      boolean isInstance = artemisInstance != null || System.getProperty("artemis.instance") != null;
      CommandLine commandLine = buildCommand(isInstance, !isInstance, shellEnabled);

      Object userObject = parseAction(commandLine, args);

      Objects.requireNonNull(userObject, "Picocli action command should never be null");
      assert userObject != null;

      if (userObject instanceof Action action) {
         action.setHomeValues(artemisHome, artemisInstance, etcFolder);
         if (action.isVerbose()) {
            context.out.print("Executing " + action.getClass().getName() + " ");
            for (String arg : args) {
               context.out.print(arg + " ");
            }
            context.out.println();
            context.out.println("Home::" + action.getBrokerHome() + ", Instance::" + action.getBrokerInstance());
         }

         return action.execute(context);
      } else {
         if (userObject instanceof Runnable runnable) {
            runnable.run();
         } else {
            throw new IllegalArgumentException(userObject.getClass() + " should implement either " + Action.class.getName() + " or " + Runnable.class.getName());
         }
      }
      return null;
   }

   /*
    Pico-cli traditionally would execute user objects that implement Runnable.
    However as we used airline before, we needed parse for the proper action.
    This method here is parsing the arg and find the proper user object in the hierarchy of sub-commands
    and return it to the caller.
    */
   private static Object parseAction(CommandLine line, String[] args) {
      CommandLine.ParseResult parseResult = line.parseArgs(args);
      if (parseResult != null) {
         while (parseResult.hasSubcommand()) {
            parseResult = parseResult.subcommand();
         }
      }
      if (parseResult == null) {
         throw new RuntimeException("Cannot match arg::" + Arrays.toString(args));
      }
      return parseResult.commandSpec().userObject();
   }

   public static CommandLine buildCommand(boolean includeInstanceCommands, boolean includeHomeCommands, boolean shellEnabled) {
      Artemis artemis = new Artemis();

      CommandLine commandLine = new CommandLine(artemis);
      artemis.setCommandLine(commandLine);

      HelpAction help = new HelpAction();
      help.setCommandLine(commandLine);
      commandLine.addSubcommand(help);

      commandLine.addSubcommand(new PWD());

      commandLine.addSubcommand(new AutoCompletion());

      // we don't include the shell in the shell
      if (shellEnabled) {
         commandLine.addSubcommand(new Shell(commandLine));
      }

      commandLine.addSubcommand(new Producer()).addSubcommand(new Transfer()).addSubcommand(new Consumer()).addSubcommand(new Browse()).addSubcommand(new Mask()).addSubcommand(new PrintVersion());

      commandLine.addSubcommand(new PerfGroup(commandLine));
      commandLine.addSubcommand(new CheckGroup(commandLine));
      commandLine.addSubcommand(new QueueGroup(commandLine));
      commandLine.addSubcommand(new AddressGroup(commandLine));

      if (Shell.inShell()) {
         commandLine.addSubcommand(new Connect());
         commandLine.addSubcommand(new Disconnect());
      }

      if (includeInstanceCommands) {
         commandLine.addSubcommand(new ActivationGroup(commandLine));
         commandLine.addSubcommand(new DataGroup(commandLine));
         commandLine.addSubcommand(new UserGroup(commandLine));

         if (!Shell.inShell()) {
            commandLine.addSubcommand(new Run());
         }

         commandLine.addSubcommand(new Stop());
         commandLine.addSubcommand(new Kill());
         commandLine.addSubcommand(new PerfJournal());
      }

      if (includeHomeCommands) {
         if (!includeInstanceCommands) {
            // Data is already present in InstanceCommands
            commandLine.addSubcommand(new DataGroup(commandLine));
         }
         commandLine.addSubcommand(new Create());
         commandLine.addSubcommand(new Upgrade());
      }

      commandLine.addSubcommand(new Completion());

      return commandLine;
   }

   public static void printBanner(PrintStream out) throws Exception {
      copy(Artemis.class.getResourceAsStream("banner.txt"), out);
   }


   public static String getNameFromBanner() throws Exception {
      try (InputStream inputStream = Artemis.class.getResourceAsStream("banner.txt")) {
         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
         String lastLine = "";
         while (reader.ready()) {
            String line = reader.readLine();
            if (!line.trim().isEmpty()) {
               lastLine = line;
            }
         }
         return lastLine.trim();
      }
   }

   private static long copy(InputStream in, OutputStream out) throws Exception {
      try {
         byte[] buffer = new byte[1024];
         int len = in.read(buffer);
         while (len != -1) {
            out.write(buffer, 0, len);
            len = in.read(buffer);
         }
         return len;
      } finally {
         in.close();
      }
   }

}
