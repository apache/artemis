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
package hqclienttopologytest

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClusterTopologyListener;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.core.client.TopologyMember;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.hornetq.jms.client.HornetQJMSConnectionFactory;

import java.util.concurrent.CountDownLatch

/*
 * Creates HornetQ connection factory
 */


Map<String, Object> params = new HashMap<String, Object>();
params.put(TransportConstants.HOST_PROP_NAME, "localhost");
params.put(TransportConstants.PORT_PROP_NAME, 61616);
def tc = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);

latch = new CountDownLatch(1);
transportParams = new HashMap<String, Object>();

final def latchLocal = latch;
final def transportParamsLocal = transportParams;

ServerLocator locator = HornetQClient.createServerLocatorWithHA(tc);
locator.addClusterTopologyListener(new ClusterTopologyListener() {
    public void nodeUP(TopologyMember topologyMember, boolean b) {
        println("Node up: " + topologyMember.getNodeId() + " " + topologyMember.getLive().getParams().toString());
        transportParamsLocal.putAll(topologyMember.getLive().getParams());
        latchLocal.countDown();
    }

    public void nodeDown(long l, String s) {
    }
});

cf = new HornetQJMSConnectionFactory(locator);