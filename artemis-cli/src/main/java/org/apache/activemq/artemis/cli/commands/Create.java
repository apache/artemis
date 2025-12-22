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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.cli.commands.util.HashUtil;
import org.apache.activemq.artemis.cli.commands.util.SyncCalculation;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioFile;
import org.apache.activemq.artemis.utils.FileUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI action that creates a broker instance directory.
 */
@Command(name = "create", description = "Create a new broker instance.")
public class Create extends InstallAbstract {

   private static final Integer DEFAULT_PORT = 61616;

   private static final Integer AMQP_PORT = 5672;

   private static final Integer STOMP_PORT = 61613;

   private static final Integer HQ_PORT = 5445;

   public static final String HTTP_HOST = "localhost";

   public static final Integer HTTP_PORT = 8161;

   private static final Integer MQTT_PORT = 1883;

   public static final Integer DEFAULT_MAX_HOPS = 1;

   /*  **********************************************************************************
    *  Note for developers: These are tested at StreamClassPathTest on the unit test.
    *  This is to make sure maven or something else is not hiding these resources.
    *  ********************************************************************************** */

   public static final String ARTEMIS_CMD = "artemis.cmd";
   public static final String BIN_ARTEMIS_CMD = "bin/" + ARTEMIS_CMD;
   public static final String ARTEMIS_SERVICE_EXE = "artemis-service.exe";
   public static final String BIN_ARTEMIS_SERVICE_EXE = "bin/" + ARTEMIS_SERVICE_EXE;
   public static final String ARTEMIS_SERVICE_EXE_CONFIG = "artemis-service.exe.config";
   public static final String BIN_ARTEMIS_SERVICE_EXE_CONFIG = "bin/" + ARTEMIS_SERVICE_EXE_CONFIG;
   public static final String ARTEMIS_SERVICE_XML = "artemis-service.xml";
   public static final String BIN_ARTEMIS_SERVICE_XML = "bin/" + ARTEMIS_SERVICE_XML;
   public static final String ETC_ARTEMIS_PROFILE_CMD = "artemis.profile.cmd";
   public static final String ETC_ARTEMIS_UTILITY_PROFILE_CMD = "artemis-utility.profile.cmd";
   public static final String ARTEMIS = "artemis";
   public static final String BIN_ARTEMIS = "bin/" + ARTEMIS;
   public static final String ARTEMIS_SERVICE = "artemis-service";
   public static final String BIN_ARTEMIS_SERVICE = "bin/" + ARTEMIS_SERVICE;
   public static final String ETC_ARTEMIS_PROFILE = "artemis.profile";
   public static final String ETC_ARTEMIS_UTILITY_PROFILE = "artemis-utility.profile";
   public static final String ETC_LOG4J2_PROPERTIES = "log4j2.properties";
   public static final String ETC_LOG4J2_UTILITY_PROPERTIES = "log4j2-utility.properties";
   public static final String ETC_BOOTSTRAP_XML = "bootstrap.xml";
   public static final String ETC_MANAGEMENT_XML = "management.xml";
   public static final String ETC_BROKER_XML = "broker.xml";

   public static final String ETC_ARTEMIS_ROLES_PROPERTIES = "artemis-roles.properties";
   public static final String ETC_ARTEMIS_USERS_PROPERTIES = "artemis-users.properties";
   private static final String ETC_LOGIN_CONFIG = "login.config";
   private static final String ETC_LOGIN_CONFIG_WITH_GUEST = "etc/login-with-guest.config";
   private static final String ETC_LOGIN_CONFIG_WITHOUT_GUEST = "etc/login-without-guest.config";
   public static final String ETC_REPLICATED_PRIMARY_SETTINGS_TXT = "etc/replicated-primary-settings.txt";
   public static final String ETC_REPLICATED_BACKUP_SETTINGS_TXT = "etc/replicated-backup-settings.txt";
   public static final String ETC_SHARED_STORE_SETTINGS_TXT = "etc/shared-store-settings.txt";
   public static final String ETC_CLUSTER_SECURITY_SETTINGS_TXT = "etc/cluster-security-settings.txt";
   public static final String ETC_CLUSTER_SETTINGS_TXT = "etc/cluster-settings.txt";
   public static final String ETC_CLUSTER_STATIC_SETTINGS_TXT = "etc/cluster-static-settings.txt";
   public static final String ETC_CONNECTOR_SETTINGS_TXT = "etc/connector-settings.txt";
   public static final String ETC_BOOTSTRAP_WEB_SETTINGS_TXT = "etc/bootstrap-web-settings.txt";
   public static final String ETC_JOURNAL_BUFFER_SETTINGS = "etc/journal-buffer-settings.txt";
   public static final String ETC_AMQP_ACCEPTOR_TXT = "etc/amqp-acceptor.txt";
   public static final String ETC_HORNETQ_ACCEPTOR_TXT = "etc/hornetq-acceptor.txt";
   public static final String ETC_MQTT_ACCEPTOR_TXT = "etc/mqtt-acceptor.txt";
   public static final String ETC_STOMP_ACCEPTOR_TXT = "etc/stomp-acceptor.txt";
   public static final String ETC_PING_TXT = "etc/ping-settings.txt";
   public static final String ETC_COMMENTED_PING_TXT = "etc/commented-ping-settings.txt";
   public static final String ETC_DATABASE_STORE_TXT = "etc/database-store.txt";
   public static final String ETC_JAAS_SECURITY_MANAGER_TXT = "etc/jaas-security-manager.txt";
   public static final String ETC_BASIC_SECURITY_MANAGER_TXT = "etc/basic-security-manager.txt";

   public static final String ETC_GLOBAL_MAX_SPECIFIED_TXT = "etc/global-max-specified.txt";
   public static final String ETC_GLOBAL_MAX_DEFAULT_TXT = "etc/global-max-default.txt";
   public static final String ETC_PAGE_SYNC_SETTINGS = "etc/page-sync-settings.txt";
   public static final String ETC_JOLOKIA_ACCESS_XML = "jolokia-access.xml";

   @Option(names = "--host", description = "Broker's host name. Default: 0.0.0.0 or input if clustered).")
   private String host;

   @Option(names = "--http-host", description = "Embedded web server's host name. Default: localhost.")
   private String httpHost = HTTP_HOST;

   @Option(names = "--relax-jolokia", description = "Disable strict checking in jolokia-access.xml.")
   private boolean relaxJolokia;

   @Option(names = "--ping", description = "A comma separated string to be passed on to the broker config as network-check-list. The broker will shutdown when all these addresses are unreachable.")
   private String ping;

   @Option(names = "--default-port", description = "The port number to use for the main 'artemis' acceptor. Default: 61616.")
   private int defaultPort = DEFAULT_PORT;

   @Option(names = "--http-port", description = "Embedded web server's port. Default: 8161.")
   private int httpPort = HTTP_PORT;

   @Option(names = "--ssl-key", description = "Embedded web server's key store path.")
   private String sslKey;

   @Option(names = "--ssl-key-password", description = "The key store's password.")
   private String sslKeyPassword;

   @Option(names = "--use-client-auth", description = "Require client certificate authentication when connecting to the embedded web server.")
   private boolean useClientAuth;

   @Option(names = "--ssl-trust", description = "The trust store path in case of client authentication.")
   private String sslTrust;

   @Option(names = "--ssl-trust-password", description = "The trust store's password.")
   private String sslTrustPassword;

   @Option(names = "--name", description = "The name of the broker. Default: same as host name.")
   private String name;

   @Option(names = "--port-offset", description = "How much to off-set the ports of every acceptor.")
   private int portOffset;

   @Option(names = "--force", description = "Overwrite configuration at destination directory.")
   private boolean force;

   @Option(names = "--purge-page-folders", description = "Paged folders should be removed upon leaving page state.")
   private boolean purgePageFolders;

   @Option(names = "--clustered", description = "Enable clustering.")
   private boolean clustered = false;

   @Option(names = "--max-hops", description = "Number of hops on the cluster configuration.")
   private int maxHops = DEFAULT_MAX_HOPS;

   @Option(names = "--message-load-balancing", description = "Message load balancing policy for cluster. Default: ON_DEMAND. Valid values: ON_DEMAND, STRICT, OFF, OFF_WITH_REDISTRIBUTION.")
   private MessageLoadBalancingType messageLoadBalancing = MessageLoadBalancingType.ON_DEMAND;

   @Option(names = "--replicated", description = "Enable broker replication.")
   private boolean replicated = false;

   @Option(names = "--shared-store", description = "Enable broker shared store.")
   private boolean sharedStore = false;

   @Deprecated(forRemoval = true)
   @Option(names = "--slave", description = "Deprecated for removal. Use 'backup' instead.", hidden = true)
   private boolean slave;

   @Option(names = "--backup", description = "Be a backup broker. Valid for shared store or replication.")
   private boolean backup;

   @Option(names = "--failover-on-shutdown", description = "Whether broker shutdown will trigger failover for clients using the core protocol. Valid only for shared store. Default: false.")
   private boolean failoverOnShutodwn;

   @Option(names = "--cluster-user", description = "The user to use for clustering. Default: input.")
   private String clusterUser = null;

   @Option(names = "--cluster-password", description = "The password to use for clustering. Default: input.")
   private String clusterPassword = null;

   @Option(names = "--allow-anonymous", description = "Allow connections from users with no security credentials. Opposite of --require-login. Default: input.")
   private Boolean allowAnonymous = null;

   @Option(names = "--require-login", description = "Require security credentials from users for connection. Opposite of --allow-anonymous.")
   private Boolean requireLogin = null;

   @Option(names = "--paging", description = "Page messages to disk when address becomes full. Opposite of --blocking. Default: true.")
   private Boolean paging;

   @Option(names = "--blocking", description = "Block producers when address becomes full. Opposite of --paging. Default: false.")
   private Boolean blocking;

   @Option(names = "--no-autotune", description = "Disable auto tuning of the journal-buffer-timeout in broker.xml.")
   private boolean noAutoTune;

   @Option(names = "--no-autocreate", description = "Disable auto creation for addresses & queues.")
   private Boolean noAutoCreate;

   @Option(names = "--autocreate", description = "Allow automatic creation of addresses & queues. Default: true.")
   private Boolean autoCreate;

   @Option(names = "--autodelete", description = "Allow automatic deletion of addresses & queues. Default: false.")
   private boolean autoDelete;

   @Option(names = "--user", description = "The username. Default: input.")
   private String user;

   @Option(names = "--password", description = "The user's password. Default: input.")
   private String password;

   @Option(names = "--role", description = "The name for the role created. Default: amq.")
   private String role = "amq";

   @Option(names = "--no-web", description = "Whether to omit the web-server definition from bootstrap.xml.")
   private boolean noWeb;

   @Option(names = "--queues", description = "A comma separated list of queues with the option to specify a routing type, e.g. --queues myQueue1,myQueue2:multicast. Routing-type default: anycast.")
   private String queues;

   @Option(names = "--addresses", description = "A comma separated list of addresses with the option to specify a routing type, e.g. --addresses myAddress1,myAddress2:anycast. Routing-type default: multicast.")
   private String addresses;

   @Option(names = "--aio", description = "Set the journal as asyncio.")
   private boolean aio;

   @Option(names = "--nio", description = "Set the journal as nio.")
   private boolean nio;

   @Option(names = "--mapped", description = "Set the journal as mapped.")
   private boolean mapped;

   // this is used by the setupJournalType method
   private JournalType journalType;

   @Option(names = "--disable-persistence", description = "Disable message persistence to the journal")
   private boolean disablePersistence;

   @Option(names = "--no-amqp-acceptor", description = "Disable the AMQP specific acceptor.")
   private boolean noAmqpAcceptor;

   @Option(names = "--no-mqtt-acceptor", description = "Disable the MQTT specific acceptor.")
   private boolean noMqttAcceptor;

   @Option(names = "--no-stomp-acceptor", description = "Disable the STOMP specific acceptor.")
   private boolean noStompAcceptor;

   @Option(names = "--no-hornetq-acceptor", description = "Disable the HornetQ specific acceptor.")
   private boolean noHornetQAcceptor;

   @Option(names = "--no-fsync", description = "Disable usage of fdatasync (channel.force(false) from Java NIO) on the journal.")
   private boolean noJournalSync;

   @Option(names = "--journal-device-block-size", description = "The block size of the journal's storage device. Default: 4096.")
   private int journalDeviceBlockSize = 4096;

   @Option(names = "--journal-retention", description = "Configure journal retention in days. If > 0 then enable journal-retention-directory from broker.xml allowing replay options.")
   private int retentionDays;

   @Option(names = "--journal-retention-max-bytes", description = "Maximum number of bytes to keep in the retention directory.")
   private String retentionMaxBytes;

   @Option(names = "--global-max-size", description = "Maximum amount of memory which message data may consume. Default: half of the JVM's max memory.")
   private String globalMaxSize;

   @Option(names = "--global-max-messages", description = "Maximum number of messages that will be accepted in memory before using address full policy mode. Default: undefined.")
   private long globalMaxMessages = -1;

   @Option(names = "--jdbc", description = "Store message data in JDBC instead of local files.")
   boolean jdbc;

   @Option(names = {"--staticCluster", "--static-cluster"}, description = "Cluster node connectors list separated by comma, e.g. \"tcp://server:61616,tcp://server2:61616,tcp://server3:61616\".")
   String staticNode;

   @Option(names = "--support-advisory", description = "Support advisory messages for the OpenWire protocol.")
   boolean supportAdvisory = false;

   @Option(names = "--suppress-internal-management-objects", description = "Do not register any advisory addresses/queues for the OpenWire protocol with the broker's management service.")
   boolean suppressInternalManagementObjects = false;

   public String[] getStaticNodes() {
      if (staticNode == null) {
         return new String[0];
      } else {
         return staticNode.split(",");
      }
   }

   @Option(names = "--security-manager", description = "Which security manager to use - jaas or basic. Default: jaas.")
   private String securityManager = "jaas";

   @Option(names = "--journal-max-io", description = "The journal-max-io value to use when also using the ASYNCIO journal-type. When using NIO or MAPPED this value is always '1'. Default: 4096")
   private int journalMaxIo = ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio();

   @Option(names = "--jdbc-bindings-table-name", description = "Name of the jdbc bindings table.")
   private String jdbcBindings = ActiveMQDefaultConfiguration.getDefaultBindingsTableName();

   @Option(names = "--jdbc-message-table-name", description = "Name of the jdbc messages table.")
   private String jdbcMessages = ActiveMQDefaultConfiguration.getDefaultMessageTableName();

   @Option(names = "--jdbc-large-message-table-name", description = "Name of the large messages table.")
   private String jdbcLargeMessages = ActiveMQDefaultConfiguration.getDefaultLargeMessagesTableName();

   @Option(names = "--jdbc-page-store-table-name", description = "Name of the page store messages table.")
   private String jdbcPageStore = ActiveMQDefaultConfiguration.getDefaultPageStoreTableName();

   @Option(names = "--jdbc-node-manager-table-name", description = "Name of the jdbc node manager table.")
   private String jdbcNodeManager = ActiveMQDefaultConfiguration.getDefaultNodeManagerStoreTableName();

   @Option(names = "--jdbc-connection-url", description = "The URL used for the database connection.")
   private String jdbcURL = null;

   @Option(names = "--jdbc-driver-class-name", description = "JDBC driver classname.")
   private String jdbcClassName = ActiveMQDefaultConfiguration.getDefaultDriverClassName();

   @Option(names = "--jdbc-network-timeout", description = "Network timeout (in milliseconds).")
   long jdbcNetworkTimeout = ActiveMQDefaultConfiguration.getDefaultJdbcNetworkTimeout();

   @Option(names = "--jdbc-lock-renew-period", description = "Lock Renew Period (in milliseconds).")
   long jdbcLockRenewPeriod = ActiveMQDefaultConfiguration.getDefaultJdbcLockRenewPeriodMillis();

   @Option(names = "--jdbc-lock-expiration", description = "Lock expiration (in milliseconds).")
   long jdbcLockExpiration = ActiveMQDefaultConfiguration.getDefaultJdbcLockExpirationMillis();

   private boolean isAutoCreate() {
      if (autoCreate == null) {
         if (noAutoCreate != null) {
            autoCreate = !noAutoCreate.booleanValue();
         }
      }

      if (autoCreate == null) {
         autoCreate = true;
      }

      return autoCreate;
   }

   public String getHost() {
      if (host == null) {
         host = "0.0.0.0";
      }
      return host;
   }

   private String getHostForClustered() {
      if (getHost().equals("0.0.0.0")) {
         host = input("--host", "Host " + host + " is not valid for clustering. Please provide a valid IP or hostname", "localhost");
      }

      return host;
   }

   public boolean isPurgePageFolders() {
      return purgePageFolders;
   }

   public void setPurgePageFolders(boolean purgePageFolders) {
      this.purgePageFolders = purgePageFolders;
   }

   public void setHost(String host) {
      this.host = host;
   }

   public boolean isForce() {
      return force;
   }

   public void setForce(boolean force) {
      this.force = force;
   }

   public void setHome(File home) {
      this.home = home;
   }

   public boolean isReplicated() {
      return replicated;
   }

   public void setReplicated(boolean replicated) {
      this.replicated = replicated;
   }

   public boolean isSharedStore() {
      return sharedStore;
   }

   public String getData() {
      return data;
   }

   public void setData(String data) {
      this.data = data;
   }

   public String getEtc() {
      return etc;
   }

   public void setEtc(String etc) {
      this.etc = etc;
   }

   protected void setNoAutoTune(boolean autTune) {
      this.noAutoTune = autTune;
   }

   public boolean isAutoDelete() {
      return autoDelete;
   }

   protected void setHttpHost(String httpHost) {
      this.httpHost = httpHost;
   }

   protected void setRelaxJolokia(boolean relaxJolokia) {
      this.relaxJolokia = relaxJolokia;
   }

   public Create setAutoDelete(boolean autoDelete) {
      this.autoDelete = autoDelete;
      return this;
   }

   private String getClusterUser() {
      if (clusterUser == null) {
         clusterUser = input("--cluster-user", "What is the cluster user?", "cluster-admin");
      }
      return clusterUser;
   }

   private String getClusterPassword() {
      if (clusterPassword == null) {
         clusterPassword = inputPassword("--cluster-password", "What is the cluster password?", "password-admin");
      }
      return clusterPassword;
   }

   private String getSslKeyPassword() {
      if (sslKeyPassword == null) {
         sslKeyPassword = inputPassword("--ssl-key-password", "What is the keystore password?", "password");
      }
      return sslKeyPassword;
   }

   private String getSslTrust() {
      if (sslTrust == null) {
         sslTrust = input("--ssl-trust", "What is the trust store path?", "/etc/truststore.jks");
      }
      return sslTrust;
   }

   private String getSslTrustPassword() {
      if (sslTrustPassword == null) {
         sslTrustPassword = inputPassword("--ssl-key-password", "What is the keystore password?", "password");
      }
      return sslTrustPassword;
   }

   private boolean isAllowAnonymous() {
      if (allowAnonymous == null) {
         allowAnonymous = inputBoolean("--allow-anonymous | --require-login", "Allow anonymous access?", true);
      }
      return allowAnonymous;
   }

   public boolean isPaging() {
      return paging;
   }

   public String getPassword() {

      if (password == null) {
         password = inputPassword("--password", "What is the default password?", "admin");
      }

      password = HashUtil.tryHash(getActionContext(), password);

      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getUser() {
      if (user == null) {
         user = input("--user", "What is the default username?", "admin");
      }
      return user;
   }

   public void setUser(String user) {
      this.user = user;
   }

   public String getRole() {
      return role;
   }

   public void setRole(String role) {
      this.role = role;
   }

   public int getJournalMaxIo() {
      return journalMaxIo;
   }

   public void setJournalMaxIo(int journalMaxIo) {
      this.journalMaxIo = journalMaxIo;
   }

   public boolean isAio() {
      return aio;
   }

   public void setAio(boolean aio) {
      this.aio = aio;
   }

   public boolean isNio() {
      return nio;
   }

   public void setNio(boolean nio) {
      this.nio = nio;
   }

   private boolean isBackup() {
      return slave || backup;
   }

   private boolean isFailoverOnShutodwn() {
      return failoverOnShutodwn;
   }

   private boolean isDisablePersistence() {
      return disablePersistence;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      this.checkDirectory();
      super.execute(context);

      return run(context);
   }

   /**
    * Checks that the directory provided either exists and is writable or doesn't exist but can be created.
    */
   private void checkDirectory() {
      if (!directory.exists()) {
         boolean created = directory.mkdirs();
         if (!created) {
            throw new RuntimeException(String.format("Unable to create the path '%s'.", directory));
         }
      } else if (!directory.canWrite()) {
         throw new RuntimeException(String.format("The path '%s' is not writable.", directory));
      }
   }

   private File createDirectory(String name, File root) {
      File directory = new File(name);
      if (!directory.isAbsolute()) {
         directory = new File(root, name);
      }
      directory.mkdirs();
      return directory;
   }

   @Override
   public Object run(ActionContext context) throws Exception {
      super.run(context);

      setupJournalType();

      // requireLogin should set allowAnonymous=false, to avoid user's questions
      if (requireLogin != null && requireLogin.booleanValue()) {
         allowAnonymous = Boolean.FALSE;
      }

      context.out.println(String.format("Creating Apache Artemis instance at: %s", directory.getCanonicalPath()));

      Map<String, String> filters = new LinkedHashMap<>();

      if (journalDeviceBlockSize % 512 != 0) {
         // This will generate a CLI error
         // no need to a logger here as this would be just a regular UI output
         throw new IllegalArgumentException("The device-block-size must be a multiple of 512");
      }

      filters.put("${device-block-size}", Integer.toString(journalDeviceBlockSize));

      filters.put("${primary-backup}", isBackup() ? "backup" : "primary");

      filters.put("${failover-on-shutdown}", isFailoverOnShutodwn() ? "true" : "false");

      filters.put("${persistence-enabled}", isDisablePersistence() ? "false" : "true");

      filters.put("${purge-page-folders}", isPurgePageFolders() ? "true" : "false");

      if (ping != null && !ping.isEmpty()) {
         filters.put("${ping}", ping);
         filters.put("${ping-config.settings}", readTextFile(ETC_PING_TXT, filters));
      } else {
         filters.put("${ping-config.settings}", readTextFile(ETC_COMMENTED_PING_TXT, filters));
      }

      if (staticNode != null) {
         clustered = true;
      }

      if (replicated) {
         clustered = true;
         filters.put("${replicated.settings}", readTextFile(isBackup() ? ETC_REPLICATED_BACKUP_SETTINGS_TXT : ETC_REPLICATED_PRIMARY_SETTINGS_TXT, filters));
      } else {
         filters.put("${replicated.settings}", "");
      }

      if (sharedStore) {
         clustered = true;
         filters.put("${shared-store.settings}", readTextFile(ETC_SHARED_STORE_SETTINGS_TXT, filters));
      } else {
         filters.put("${shared-store.settings}", "");
      }

      filters.put("${journal.settings}", journalType.name());

      if (sslKey != null) {
         filters.put("${web.protocol}", "https");
         getSslKeyPassword();
         String extraWebAttr = " keyStorePath=\"" + sslKey + "\" keyStorePassword=\"" + sslKeyPassword + "\"";
         if (useClientAuth) {
            getSslTrust();
            getSslTrustPassword();
            extraWebAttr += " clientAuth=\"true\" trustStorePath=\"" + sslTrust + "\" trustStorePassword=\"" + sslTrustPassword + "\"";
         }
         filters.put("${extra.web.attributes}", extraWebAttr);
      } else {
         filters.put("${web.protocol}", "http");
         filters.put("${extra.web.attributes}", "");
      }
      filters.put("${fsync}", String.valueOf(!noJournalSync));
      filters.put("${default.port}", String.valueOf(defaultPort + portOffset));
      filters.put("${support-advisory}", Boolean.toString(supportAdvisory));
      filters.put("${suppress-internal-management-objects}", Boolean.toString(suppressInternalManagementObjects));
      filters.put("${amqp.port}", String.valueOf(AMQP_PORT + portOffset));
      filters.put("${stomp.port}", String.valueOf(STOMP_PORT + portOffset));
      filters.put("${hq.port}", String.valueOf(HQ_PORT + portOffset));
      filters.put("${mqtt.port}", String.valueOf(MQTT_PORT + portOffset));
      filters.put("${http.host}", httpHost);
      filters.put("${http.port}", String.valueOf(httpPort + portOffset));
      filters.put("${data.dir}", data);
      filters.put("${max-hops}", String.valueOf(maxHops));

      filters.put("${message-load-balancing}", messageLoadBalancing.toString());
      filters.put("${user}", getUser());
      filters.put("${password}", getPassword());
      filters.put("${encoded.role}", role.replaceAll(" ", "\\\\ "));


      filters.put("${global-max-messages}", Long.toString(globalMaxMessages));

      if (globalMaxSize == null || globalMaxSize.trim().isEmpty()) {
         filters.put("${global-max-section}", readTextFile(ETC_GLOBAL_MAX_DEFAULT_TXT, filters));
      } else {
         filters.put("${global-max-size}", globalMaxSize);
         filters.put("${global-max-section}", readTextFile(ETC_GLOBAL_MAX_SPECIFIED_TXT, filters));
      }

      if (jdbc) {
         if (jdbcURL == null) {
            jdbcURL = "jdbc:derby:" + getInstance().getAbsolutePath() + "/data/derby/db;create=true";
         }
         filters.put("${jdbcBindings}", jdbcBindings);
         filters.put("${jdbcMessages}", jdbcMessages);
         filters.put("${jdbcLargeMessages}", jdbcLargeMessages);
         filters.put("${jdbcPageStore}", jdbcPageStore);
         filters.put("${jdbcNodeManager}", jdbcNodeManager);
         filters.put("${jdbcURL}", jdbcURL);
         filters.put("${jdbcClassName}", jdbcClassName);
         filters.put("${jdbcNetworkTimeout}", "" + jdbcNetworkTimeout);
         filters.put("${jdbcLockRenewPeriod}", "" + jdbcLockRenewPeriod);
         filters.put("${jdbcLockExpiration}", "" + jdbcLockExpiration);
         filters.put("${jdbc}", readTextFile(ETC_DATABASE_STORE_TXT, filters));
      } else {
         filters.put("${jdbc}", "");
      }


      if (clustered) {
         filters.put("${host}", getHostForClustered());
         if (name == null) {
            name = getHostForClustered();
         }
         String connectorSettings = getConnectors(filters);

         filters.put("${name}", name);
         filters.put("${connector-config.settings}", connectorSettings);
         filters.put("${cluster-security.settings}", readTextFile(ETC_CLUSTER_SECURITY_SETTINGS_TXT, filters));
         if (staticNode != null) {

            String staticCluster = readTextFile(ETC_CLUSTER_STATIC_SETTINGS_TXT, filters);
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            int countCluster = getStaticNodes().length;
            for (int i = 0; i < countCluster; i++) {
               printWriter.println("               <connector-ref>node" + i + "</connector-ref>");
            }
            filters.put("${connectors-list}", stringWriter.toString());

            staticCluster = applyFilters(staticCluster, filters);
            filters.put("${cluster.settings}", staticCluster);
         } else {
            filters.put("${cluster.settings}", readTextFile(ETC_CLUSTER_SETTINGS_TXT, filters));
         }
         filters.put("${cluster-user}", getClusterUser());
         filters.put("${cluster-password}", getClusterPassword());
      } else {
         if (name == null) {
            name = getHost();
         }
         filters.put("${name}", name);
         filters.put("${host}", getHost());
         filters.put("${connector-config.settings}", "");
         filters.put("${cluster-security.settings}", "");
         filters.put("${cluster.settings}", "");
         filters.put("${cluster-user}", "");
         filters.put("${cluster-password}", "");
      }

      applyAddressesAndQueues(filters);

      if (home != null) {
         filters.put("${home}", path(home));
      }

      new File(directory, "bin").mkdirs();
      File etcFolder = createDirectory(etc, directory);
      new File(directory, "tmp").mkdirs();
      new File(directory, "lib").mkdirs();
      File dataFolder = createDirectory(data, directory);
      File logFolder = createDirectory(LOG_DIRNAME, directory);
      File oomeDumpFile = new File(logFolder, OOM_DUMP_FILENAME);

      String processedJavaOptions = getJavaOptions();
      String processedJavaUtilityOptions = getJavaUtilityOptions();

      addScriptFilters(filters, getHome(), getInstance(), etcFolder, dataFolder, oomeDumpFile, javaMemory, processedJavaOptions, processedJavaUtilityOptions, role);

      boolean allowAnonymous = isAllowAnonymous();


      String retentionTag;
      if (retentionDays > 0) {
         if (retentionMaxBytes != null) {
            retentionTag = "<journal-retention-directory period=\"" + retentionDays + "\" unit=\"DAYS\" storage-limit=\"" + retentionMaxBytes + "\">" + data + "/retention</journal-retention-directory>";
         } else {
            retentionTag = "<journal-retention-directory period=\"" + retentionDays + "\" unit=\"DAYS\">" + data + "/retention</journal-retention-directory>";
         }
      } else {
         retentionTag = """

                  <!-- if you want to retain your journal uncomment this following configuration.

                  This will allow your system to keep 7 days of your data, up to 10G. Tweak it accordingly to your use case and capacity.

                  it is recommended to use a separate storage unit from the journal for performance considerations.

                  <journal-retention-directory period="7" unit="DAYS" storage-limit="10G">data/retention</journal-retention-directory>

                  You can also enable retention by using the argument journal-retention on the `artemis create` command -->

            """;
      }

      filters.put("${journal-retention}", retentionTag);

      filters.put("${java-opts}", processedJavaOptions);
      filters.put("${java-memory}", javaMemory);

      if (allowAnonymous) {
         write(ETC_LOGIN_CONFIG_WITH_GUEST, new File(etcFolder, ETC_LOGIN_CONFIG), filters, false, force);
      } else {
         write(ETC_LOGIN_CONFIG_WITHOUT_GUEST, new File(etcFolder, ETC_LOGIN_CONFIG), filters, false, force);
      }

      writeEtc(ETC_ARTEMIS_ROLES_PROPERTIES, etcFolder, filters, false);

      if (IS_WINDOWS) {
         write(BIN_ARTEMIS_CMD, filters, false);
         write(BIN_ARTEMIS_SERVICE_EXE, force);
         write(BIN_ARTEMIS_SERVICE_EXE_CONFIG, force);
         write(BIN_ARTEMIS_SERVICE_XML, filters, false);
         writeEtc(ETC_ARTEMIS_PROFILE_CMD, etcFolder, filters, false);
         writeEtc(ETC_ARTEMIS_UTILITY_PROFILE_CMD, etcFolder, filters, false);
      }

      if (IS_NIX) {
         write(BIN_ARTEMIS, filters, true);
         makeExec(BIN_ARTEMIS);
         write(BIN_ARTEMIS_SERVICE, filters, true);
         makeExec(BIN_ARTEMIS_SERVICE);
         writeEtc(ETC_ARTEMIS_PROFILE, etcFolder, filters, true);
         writeEtc(ETC_ARTEMIS_UTILITY_PROFILE, etcFolder, filters, true);
      }

      writeEtc(ETC_LOG4J2_PROPERTIES, etcFolder, null, false);
      writeEtc(ETC_LOG4J2_UTILITY_PROPERTIES, etcFolder, null, false);

      if (noWeb) {
         filters.put("${bootstrap-web-settings}", "");
      } else {
         filters.put("${bootstrap-web-settings}", readTextFile(ETC_BOOTSTRAP_WEB_SETTINGS_TXT, filters));
      }

      if (noAmqpAcceptor) {
         filters.put("${amqp-acceptor}", "");
      } else {
         filters.put("${amqp-acceptor}", readTextFile(ETC_AMQP_ACCEPTOR_TXT, filters));
      }

      if (noMqttAcceptor) {
         filters.put("${mqtt-acceptor}", "");
      } else {
         filters.put("${mqtt-acceptor}", readTextFile(ETC_MQTT_ACCEPTOR_TXT, filters));
      }

      if (noStompAcceptor) {
         filters.put("${stomp-acceptor}", "");
      } else {
         filters.put("${stomp-acceptor}", readTextFile(ETC_STOMP_ACCEPTOR_TXT, filters));
      }

      if (noHornetQAcceptor) {
         filters.put("${hornetq-acceptor}", "");
      } else {
         filters.put("${hornetq-acceptor}", readTextFile(ETC_HORNETQ_ACCEPTOR_TXT, filters));
      }

      if (paging == null && blocking == null) {
         filters.put("${full-policy}", "PAGE");
      } else if (paging != null && paging) {
         filters.put("${full-policy}", "PAGE");
      } else if (blocking != null && blocking) {
         filters.put("${full-policy}", "BLOCK");
      } else {
         filters.put("${full-policy}", "PAGE");
      }


      filters.put("${auto-create}", isAutoCreate() ? "true" : "false");
      filters.put("${auto-delete}", autoDelete ? "true" : "false");

      if (jdbc) {
         noAutoTune = true;
         context.out.println();
         printStar("Copy a jar containing the JDBC Driver '" + jdbcClassName + "' into " + directory.getAbsolutePath() + "/lib");
         context.out.println();
      }

      performAutoTune(filters, journalType, dataFolder);

      writeEtc(ETC_BROKER_XML, etcFolder, filters, false);
      writeEtc(ETC_ARTEMIS_USERS_PROPERTIES, etcFolder, filters, false);

      // we want this variable to remain unchanged so that it will use the value set in the profile
      if (SecurityManagerType.getType(securityManager) == SecurityManagerType.BASIC) {
         filters.put("${security-manager-settings}", readTextFile(ETC_BASIC_SECURITY_MANAGER_TXT, filters));
      } else {
         filters.put("${security-manager-settings}", readTextFile(ETC_JAAS_SECURITY_MANAGER_TXT, filters));
      }
      writeEtc(ETC_BOOTSTRAP_XML, etcFolder, filters, false);
      writeEtc(ETC_MANAGEMENT_XML, etcFolder, filters, false);

      if (relaxJolokia) {
         filters.put("${jolokia.options}", "<!-- option relax-jolokia used, so strict-checking will be removed here -->");
      } else {
         filters.put("${jolokia.options}", "<!-- Check for the proper origin on the server side, too -->\n" +
            "        <strict-checking/>");
      }
      writeEtc(ETC_JOLOKIA_ACCESS_XML, etcFolder, filters, false);

      context.out.println("");
      context.out.println("You can now start the broker by executing:  ");
      context.out.println("");
      context.out.println(String.format("   \"%s\" run", path(new File(directory, "bin/artemis"))));

      File service = new File(directory, BIN_ARTEMIS_SERVICE);
      context.out.println();

      if (IS_NIX) {
         context.out.println("Or you can run the broker in the background using:");
         context.out.println("");
         context.out.println(String.format("   \"%s\" start", path(service)));
         context.out.println("");
      }

      if (IS_WINDOWS) {
         service = new File(directory, BIN_ARTEMIS_SERVICE_EXE);
         context.out.println("Or you can setup the broker as Windows service and run it in the background:");
         context.out.println("");
         context.out.println(String.format("   \"%s\" install", path(service)));
         context.out.println(String.format("   \"%s\" start", path(service)));
         context.out.println("");
         context.out.println("   To stop the windows service:");
         context.out.println(String.format("      \"%s\" stop", path(service)));
         context.out.println("");
         context.out.println("   To uninstall the windows service");
         context.out.println(String.format("      \"%s\" uninstall", path(service)));
      }

      return null;
   }

   protected static void addScriptFilters(Map<String, String> filters,
                                          File home,
                                          File directory,
                                          File etcFolder,
                                          File dataFolder,
                                          File oomeDumpFile,
                                          String javaMemory,
                                          String javaOptions,
                                          String javaUtilityOptions,
                                          String role) throws IOException {
      filters.put("${artemis.home}", path(home));
      // I am using a different replacing pattern here, for cases where want an actual ${artemis.instance} in the output
      // so that's just to make a distinction
      filters.put("@artemis.instance@", path(directory));
      filters.put("${artemis.instance.name}", directory.getName());
      filters.put("${java.home}", path(System.getProperty("java.home")));

      filters.put("${artemis.instance.etc.uri}", etcFolder.toURI().toString());
      filters.put("${artemis.instance.etc}", path(etcFolder));
      filters.put("${artemis.instance.oome.dump}", path(oomeDumpFile));
      filters.put("${artemis.instance.data}", path(dataFolder));
      filters.put("${java-memory}", javaMemory);
      filters.put("${java-opts}", javaOptions);
      filters.put("${java-utility-opts}", javaUtilityOptions);
      filters.put("${role}", role);
   }

   private String getConnectors(Map<String, String> filters) throws IOException {
      if (staticNode != null) {
         StringWriter stringWriter = new StringWriter();
         PrintWriter printer = new PrintWriter(stringWriter);
         printer.println("      <connectors>");
         printer.println("            <!-- Connector used to be announced through cluster connections and notifications -->");
         printer.println(applyFilters("            <connector name=\"artemis\">tcp://${host}:${default.port}</connector>", filters));
         int counter = 0;
         for (String node: getStaticNodes()) {
            printer.println("            <connector name = \"node" + (counter++) + "\">" + node + "</connector>");
         }
         printer.println("      </connectors>");
         return stringWriter.toString();
      } else {
         return readTextFile(ETC_CONNECTOR_SETTINGS_TXT, filters);
      }
   }

   private void printStar(String message) {
      String separator = "*".repeat(Math.min(message.length(), 80));
      getActionContext().out.println(separator);
      getActionContext().out.println();
      getActionContext().out.println(message);
      getActionContext().out.println();
      getActionContext().out.println(separator);
   }

   private void setupJournalType() {

      if (noJournalSync && !mapped) {
         boolean useMapped = inputBoolean("--mapped", "Since you disabled syncs, it is recommended to use the Mapped Memory Journal. Do you want to use the Memory Mapped Journal", true);

         if (useMapped) {
            mapped = true;
            nio = false;
            aio = false;
         }
      }
      int countJournalTypes = countBoolean(aio, nio, mapped);
      if (countJournalTypes > 1) {
         throw new RuntimeException("You can only select one journal type (--nio | --aio | --mapped).");
      }

      if (countJournalTypes == 0) {
         if (supportsLibaio()) {
            aio = true;
         } else {
            nio = true;
         }
      }

      if (aio) {
         journalType = JournalType.ASYNCIO;
      } else if (nio) {
         journalType = JournalType.NIO;
      } else if (mapped) {
         journalType = JournalType.MAPPED;
      }
   }

   private static int countBoolean(boolean...b) {
      int count = 0;

      for (boolean itemB : b) {
         if (itemB) count++;
      }

      return count;
   }

   /**
    * It will create the address and queue configurations
    */
   private void applyAddressesAndQueues(Map<String, String> filters) {
      StringWriter writer = new StringWriter();
      PrintWriter printWriter = new PrintWriter(writer);
      printWriter.println();

      for (String str : getQueueList()) {
         String[] seg = str.split(":");
         String name = seg[0].trim();
         // default routing type to anycast if not specified
         String routingType = (seg.length == 2 ? seg[1].trim() : "anycast");
         try {
            RoutingType.valueOf(routingType.toUpperCase());
         } catch (Exception e) {
            e.printStackTrace();
            getActionContext().err.println("Invalid routing type: " + routingType);
         }
         printWriter.println("         <address name=\"" + name + "\">");
         printWriter.println("            <" + routingType + ">");
         printWriter.println("               <queue name=\"" + name + "\" />");
         printWriter.println("            </" + routingType + ">");
         printWriter.println("         </address>");
      }
      for (String str : getAddressList()) {
         String[] seg = str.split(":");
         String name = seg[0].trim();
         // default routing type to multicast if not specified
         String routingType = (seg.length == 2 ? seg[1].trim() : "multicast");
         try {
            RoutingType.valueOf(routingType.toUpperCase());
         } catch (Exception e) {
            e.printStackTrace();
            getActionContext().err.println("Invalid routing type: " + routingType);
         }
         printWriter.println("         <address name=\"" + name + "\">");
         printWriter.println("            <" + routingType + "/>");
         printWriter.println("         </address>");
      }
      filters.put("${address-queue.settings}", writer.toString());
   }

   private void performAutoTune(Map<String, String> filters, JournalType journalType, File dataFolder) {
      if (noAutoTune) {
         filters.put("${journal-buffer.settings}", "");
         filters.put("${page-sync.settings}", "");
      } else {
         try {
            int writes = 250;
            getActionContext().out.println("");
            getActionContext().out.println("Auto tuning journal ...");

            if (mapped && noJournalSync) {
               Map<String, String> syncFilter = new HashMap<>();
               syncFilter.put("${nanoseconds}", "0");
               syncFilter.put("${writesPerMillisecond}", "0");
               syncFilter.put("${maxaio}", "1");

               getActionContext().out.println("...Since you disabled sync and are using MAPPED journal, we are diabling buffer times");

               filters.put("${journal-buffer.settings}", readTextFile(ETC_JOURNAL_BUFFER_SETTINGS, syncFilter));

               filters.put("${page-sync.settings}", readTextFile(ETC_PAGE_SYNC_SETTINGS, syncFilter));
            } else {
               long time = SyncCalculation.syncTest(dataFolder, 4096, writes, 5, verbose, !noJournalSync, false, "journal-test.tmp", ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio(), journalType, getActionContext());
               long nanoseconds = SyncCalculation.toNanos(time, writes, verbose, getActionContext());
               double writesPerMillisecond = (double) writes / (double) time;

               String writesPerMillisecondStr = new DecimalFormat("###.##").format(writesPerMillisecond);

               Map<String, String> syncFilter = new HashMap<>();
               syncFilter.put("${nanoseconds}", Long.toString(nanoseconds));
               syncFilter.put("${writesPerMillisecond}", writesPerMillisecondStr);
               syncFilter.put("${maxaio}", journalType == JournalType.ASYNCIO ? "" + journalMaxIo : "1");

               getActionContext().out.println("done! Your system can make " + writesPerMillisecondStr +
                                                 " writes per millisecond, your journal-buffer-timeout will be " + nanoseconds);

               filters.put("${journal-buffer.settings}", readTextFile(ETC_JOURNAL_BUFFER_SETTINGS, syncFilter));

               if (noJournalSync) {
                  syncFilter.put("${nanoseconds}", "0");
               } else if (journalType != JournalType.NIO) {
                  long nioTime = SyncCalculation.syncTest(dataFolder, 4096, writes, 5, verbose, !noJournalSync, false, "journal-test.tmp", ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio(), JournalType.NIO, getActionContext());
                  long nioNanoseconds = SyncCalculation.toNanos(nioTime, writes, verbose, getActionContext());
                  syncFilter.put("${nanoseconds}", Long.toString(nioNanoseconds));
               }

               filters.put("${page-sync.settings}", readTextFile(ETC_PAGE_SYNC_SETTINGS, syncFilter));
            }



         } catch (Exception e) {
            filters.put("${journal-buffer.settings}", "");
            filters.put("${page-sync.settings}", "");
            e.printStackTrace();
            getActionContext().err.println("Couldn't perform sync calculation, using default values");
         }
      }
   }

   public boolean supportsLibaio() {
      if (IS_WINDOWS) {
         return false;
      }
      if (LibaioContext.isLoaded()) {
         try (LibaioContext context = new LibaioContext(1, true, true)) {
            File tmpFile = new File(directory, "validateAIO.bin");
            boolean supportsLibaio = true;
            try {
               LibaioFile file = context.openFile(tmpFile, true);
               file.close();
            } catch (Exception e) {
               supportsLibaio = false;
            }
            tmpFile.delete();
            if (!supportsLibaio) {
               getActionContext().err.println("The filesystem used on " + directory + " doesn't support libAIO and O_DIRECT files, switching journal-type to NIO");
            }
            return supportsLibaio;
         }
      } else {
         return false;
      }
   }

   private void makeExec(String path) throws IOException {
      FileUtil.makeExec(new File(directory, path));
   }

   private String[] getQueueList() {
      if (queues == null) {
         return new String[0];
      } else {
         return queues.split(",");
      }
   }

   private String[] getAddressList() {
      if (addresses == null) {
         return new String[0];
      } else {
         return addresses.split(",");
      }
   }

   private static String path(String value) throws IOException {
      return path(new File(value));
   }

   private static String path(File value) throws IOException {
      return value.getCanonicalPath();
   }

   private void write(String source, Map<String, String> filters, boolean unixTarget) throws Exception {
      write(source, new File(directory, source), filters, unixTarget, force);
   }

   private void writeEtc(String source, File etcFolder, Map<String, String> filters, boolean unixTarget) throws Exception {
      write("etc/" + source, new File(etcFolder, source), filters, unixTarget, force);
   }

   private enum SecurityManagerType {
      JAAS, BASIC;

      public static SecurityManagerType getType(String type) {
         type = type.toLowerCase();
         return switch (type) {
            case "jaas" -> JAAS;
            case "basic" -> BASIC;
            default -> null;
         };
      }
   }
}