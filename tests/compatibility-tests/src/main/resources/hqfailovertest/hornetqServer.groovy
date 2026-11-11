package hqfailovertest

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



// This is a failover test with a hornetq server.
// these configurations were taken out of hornetq's testsuite.. more specifically FailoverTestBase
// sorry for the mess of copy & paste here but i couldn't depend directly into hornetq's test jar
// and I coudn't either spawn a new VM here.
// it's not possible to do FileLock without spawning a new VM.. we had InVMNodeManagers that were part of the testsuite
//



import org.hornetq.api.core.*
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.core.config.*
import org.hornetq.core.config.impl.*

import org.hornetq.core.config.impl.ConfigurationImpl
import org.hornetq.core.server.*
import org.hornetq.core.server.impl.*
import org.hornetq.core.settings.impl.*
import org.hornetq.jms.server.config.impl.*
import org.hornetq.spi.core.security.*
import org.hornetq.core.remoting.impl.invm.*
import org.hornetq.core.remoting.impl.netty.*
import org.hornetq.utils.*

import javax.management.MBeanServer
import java.lang.management.ManagementFactory
import java.util.concurrent.Semaphore



folder = arg[0];
nodeManager = new InVMNodeManager(false, folder);

backupConfig = createDefaultConfig();
backupConfig.getAcceptorConfigurations().clear();
backupConfig.getAcceptorConfigurations().add(getAcceptorTransportConfiguration(false));
backupConfig.setSharedStore(true);
backupConfig.setBackup(true);
backupConfig.setFailbackDelay(1000);

TransportConfiguration liveConnector = getConnectorTransportConfiguration(true);
TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);
backupConfig.getConnectorConfigurations().put(liveConnector.getName(), liveConnector);
backupConfig.getConnectorConfigurations().put(backupConnector.getName(), backupConnector);
basicClusterConnectionConfig(backupConfig, backupConnector.getName(), liveConnector.getName());
backupServer = createTestableServer(backupConfig, nodeManager);

liveConfig = createDefaultConfig();
liveConfig.getAcceptorConfigurations().clear();
liveConfig.getAcceptorConfigurations().add(getAcceptorTransportConfiguration(true));
liveConfig.setSharedStore(true);
liveConfig.setFailbackDelay(1000);

basicClusterConnectionConfig(liveConfig, liveConnector.getName());
liveConfig.getConnectorConfigurations().put(liveConnector.getName(), liveConnector);
liveServer = createTestableServer(liveConfig, nodeManager);

liveServer.start();
backupServer.start();

liveServer.createQueue(SimpleString.toSimpleString("jms.queue.queue"), SimpleString.toSimpleString("jms.queue.queue"), null, true, false);




protected Configuration createDefaultConfig() throws Exception {
    return createDefaultConfig(new HashMap<String, Object>(), StaticProperties.INVM_ACCEPTOR_FACTORY, StaticProperties.NETTY_ACCEPTOR_FACTORY);
}


HornetQServer createInVMFailoverServer(final boolean realFiles,
                                       final Configuration configuration,
                                       NodeManager nodeManager,
                                       final int id) {
    HornetQServer server;
    HornetQSecurityManager securityManager = new HornetQSecurityManagerImpl();
    configuration.setPersistenceEnabled(realFiles);
    server = new InVMNodeManagerServer(configuration,
            ManagementFactory.getPlatformMBeanServer(),
            securityManager,
            nodeManager);

    server.setIdentity("Server " + id);

    AddressSettings defaultSetting = new AddressSettings();
    defaultSetting.setPageSizeBytes(10 * 1024 * 1024);
    defaultSetting.setMaxSizeBytes(1024 * 1024 * 1024);

    server.getAddressSettingsRepository().addMatch("#", defaultSetting);

    return server;
}

Configuration createDefaultConfig(final Map<String, Object> params, final String... acceptors) throws Exception {
    ConfigurationImpl configuration = createBasicConfig(-1);

    configuration.setFileDeploymentEnabled(false);
    configuration.setJMXManagementEnabled(false);

    configuration.getAcceptorConfigurations().clear();

    for (String acceptor : acceptors) {
        TransportConfiguration transportConfig = new TransportConfiguration(acceptor, params);
        configuration.getAcceptorConfigurations().add(transportConfig);
    }
    return configuration;
}

protected ConfigurationImpl createBasicConfig(final int serverID)
{
    ConfigurationImpl configuration = new ConfigurationImpl();
    configuration.setSecurityEnabled(false);
    configuration.setJournalMinFiles(2);
    configuration.setJournalFileSize(100 * 1024);

    configuration.setJournalType(JournalType.NIO);

    configuration.setJournalDirectory(folder)
    configuration.setBindingsDirectory(folder + "/bindings")
    configuration.setPagingDirectory(folder + "/paging")
    configuration.setLargeMessagesDirectory(folder + "/largemessages")

    configuration.setJournalCompactMinFiles(0);
    configuration.setJournalCompactPercentage(0);
    configuration.setClusterPassword("changeme");
    return configuration;
}



protected static final void basicClusterConnectionConfig(Configuration mainConfig, String connectorName,
                                                         String... connectors) {
    ArrayList<String> connectors0 = new ArrayList<String>();
    for (String c : connectors) {
        connectors0.add(c);
    }
    basicClusterConnectionConfig(mainConfig, connectorName, connectors0);
}

protected static final void basicClusterConnectionConfig(Configuration mainConfig,
                                                         String connectorName,
                                                         List<String> connectors) {
    ClusterConnectionConfiguration ccc = null;

    ccc = new ClusterConnectionConfiguration("cluster1", "jms", connectorName, 10, false, false, 1, 1, connectors,
            false);

    mainConfig.getClusterConfigurations().add(ccc);
}

public final class InVMNodeManager extends NodeManager
{

    private final Semaphore liveLock;

    private final Semaphore backupLock;

    public enum State {LIVE, PAUSED, FAILING_BACK, NOT_STARTED}

    public State state = State.NOT_STARTED;

    public long failoverPause = 0l;

    public InVMNodeManager(boolean replicatedBackup)
    {
        this(replicatedBackup, null);
        if (replicatedBackup)
            throw new RuntimeException("if replicated-backup, we need its journal directory");
    }

    public InVMNodeManager(boolean replicatedBackup, String directory)
    {
        super(replicatedBackup, directory);
        liveLock = new Semaphore(1);
        backupLock = new Semaphore(1);
        setUUID(UUIDGenerator.getInstance().generateUUID());
    }

    public void awaitLiveNode() throws Exception
    {
        while (true)
        {
            while (state == State.NOT_STARTED)
            {
                Thread.sleep(2000);
            }

            liveLock.acquire();

            if (state == State.PAUSED)
            {
                liveLock.release();
                Thread.sleep(2000);
            }
            else if (state == State.FAILING_BACK)
            {
                liveLock.release();
                Thread.sleep(2000);
            }
            else if (state == State.LIVE)
            {
                break;
            }
        }
        if(failoverPause > 0l)
        {
            Thread.sleep(failoverPause);
        }
    }

    public void startBackup() throws Exception
    {
        backupLock.acquire();
    }

    public void startLiveNode() throws Exception
    {
        state = State.FAILING_BACK;
        liveLock.acquire();
        state = State.LIVE;
    }

    public void pauseLiveServer() throws Exception
    {
        state = State.PAUSED;
        liveLock.release();
    }

    public void crashLiveServer() throws Exception
    {
        //overkill as already set to live
        state = State.LIVE;
        liveLock.release();
    }

    public boolean isAwaitingFailback() throws Exception
    {
        return state == State.FAILING_BACK;
    }

    public boolean isBackupLive() throws Exception
    {
        return liveLock.availablePermits() == 0;
    }

    public void interrupt()
    {
        //
    }

    public void releaseBackup()
    {
        if (backupLock != null)
        {
            backupLock.release();
        }
    }

    public SimpleString readNodeId() throws HornetQIllegalStateException, IOException
    {
        return getNodeId();
    }
}

final class InVMNodeManagerServer extends HornetQServerImpl {
    final NodeManager nodeManager;

    public InVMNodeManagerServer(final NodeManager nodeManager) {
        super();
        this.nodeManager = nodeManager;
    }

    public InVMNodeManagerServer(final Configuration configuration, final NodeManager nodeManager) {
        super(configuration);
        this.nodeManager = nodeManager;
    }

    public InVMNodeManagerServer(final Configuration configuration,
                                 final MBeanServer mbeanServer,
                                 final NodeManager nodeManager) {
        super(configuration, mbeanServer);
        this.nodeManager = nodeManager;
    }

    public InVMNodeManagerServer(final Configuration configuration,
                                 final HornetQSecurityManager securityManager,
                                 final NodeManager nodeManager) {
        super(configuration, securityManager);
        this.nodeManager = nodeManager;
    }

    public InVMNodeManagerServer(final Configuration configuration,
                                 final MBeanServer mbeanServer,
                                 final HornetQSecurityManager securityManager,
                                 final NodeManager nodeManager) {
        super(configuration, mbeanServer, securityManager);
        this.nodeManager = nodeManager;
    }

    @Override
    protected NodeManager createNodeManager(
            final String directory, final String nodeGroupName, boolean replicatingBackup) {
        nodeManager.setNodeGroupName(nodeGroupName);
        return nodeManager;
    }

}


private static TransportConfiguration transportConfiguration(String classname, boolean live, int server)
{
    if (classname.contains("netty"))
    {
        Map<String, Object> serverParams = new HashMap<String, Object>();
        Integer port = live ? 5445 : 5545;
        serverParams.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, port);
        return new TransportConfiguration(classname, serverParams);
    }

    Map<String, Object> serverParams = new HashMap<String, Object>();
    serverParams.put(TransportConstants.SERVER_ID_PROP_NAME, live ? server : server + 100);
    return new TransportConfiguration(classname, serverParams);
}



protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live)
{
    return transportConfiguration(StaticProperties.NETTY_ACCEPTOR_FACTORY, live, 1);
}

protected TransportConfiguration getConnectorTransportConfiguration(final boolean live)
{
    return transportConfiguration(StaticProperties.NETTY_CONNECTOR_FACTORY, live, 1);
}

protected HornetQServer createTestableServer(Configuration config, NodeManager nodeManager)
{
    return createInVMFailoverServer(true, config, nodeManager, 1)
}


class StaticProperties {
    static String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();
    static String INVM_CONNECTOR_FACTORY = InVMConnectorFactory.class.getCanonicalName();
    static String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();
    static String NETTY_CONNECTOR_FACTORY = NettyConnectorFactory.class.getCanonicalName();
}
