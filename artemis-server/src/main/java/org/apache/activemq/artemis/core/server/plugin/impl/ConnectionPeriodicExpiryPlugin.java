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
package org.apache.activemq.artemis.core.server.plugin.impl;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionPeriodicExpiryPlugin implements ActiveMQServerBasePlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private String name;
   private long periodSeconds;
   private int accuracyWindowSeconds;
   private String acceptorMatchRegex;

   private ScheduledExecutorService executor;
   private RemotingService remotingService;
   private Pattern matchPattern;
   private ScheduledFuture<?> task;

   public ConnectionPeriodicExpiryPlugin() {
      periodSeconds = TimeUnit.MINUTES.toSeconds(15);
      accuracyWindowSeconds = 30;
   }

   @Override
   public void registered(ActiveMQServer server) {

      sanityCheckConfig();

      executor = server.getScheduledPool();
      remotingService = server.getRemotingService();
      matchPattern = Pattern.compile(acceptorMatchRegex);
      final long periodMillis = TimeUnit.SECONDS.toMillis(periodSeconds);

      task = executor.scheduleWithFixedDelay(() -> {
         try {
            final long currentTime = System.currentTimeMillis();
            for (Acceptor acceptor : remotingService.getAcceptors().values()) {
               if (matchPattern.matcher(acceptor.getName()).matches()) {
                  if (acceptor instanceof NettyAcceptor nettyAcceptor) {

                     for (NettyServerConnection nettyServerConnection : nettyAcceptor.getConnections().values()) {
                        RemotingConnection remotingConnection = remotingService.getConnection(nettyServerConnection.getID());
                        if (remotingConnection != null && currentTime > remotingConnection.getCreationTime() + periodMillis) {
                           executor.schedule(() -> {
                              remotingService.removeConnection(remotingConnection.getID());
                              remotingConnection.fail(new ActiveMQDisconnectedException("terminated by session expiry plugin"));
                           }, RandomUtil.randomMax(accuracyWindowSeconds), TimeUnit.SECONDS);
                        }
                     }
                  }
               }
            }
         } catch (Exception trapToStayScheduled) {
            logger.debug("error on connection expiry plugin scheduled task, will retry", trapToStayScheduled);
         }
      }, accuracyWindowSeconds, accuracyWindowSeconds, TimeUnit.SECONDS);
   }

   @Override
   public void unregistered(ActiveMQServer server) {
      if (task != null) {
         task.cancel(true);
      }
   }

   @Override
   public void init(Map<String, String> properties) {
      name = properties.getOrDefault("name", name);
      periodSeconds = Long.parseLong(properties.getOrDefault("periodSeconds", Long.toString(periodSeconds)));
      accuracyWindowSeconds = Integer.parseInt(properties.getOrDefault("accuracyWindowSeconds", Long.toString(accuracyWindowSeconds)));
      acceptorMatchRegex = properties.getOrDefault("acceptorMatchRegex", acceptorMatchRegex);

      sanityCheckConfig();
   }

   private void sanityCheckConfig() {
      if (accuracyWindowSeconds <= 0) {
         throw new IllegalArgumentException("accuracyWindowSeconds must be > 0");
      }

      if (acceptorMatchRegex == null) {
         throw new IllegalArgumentException("acceptorMatchRegex must be configured");
      }
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public long getPeriodSeconds() {
      return periodSeconds;
   }

   public void setPeriodSeconds(long periodSeconds) {
      this.periodSeconds = periodSeconds;
   }

   public int getAccuracyWindowSeconds() {
      return accuracyWindowSeconds;
   }

   public void setAccuracyWindowSeconds(int accuracyWindowSeconds) {
      this.accuracyWindowSeconds = accuracyWindowSeconds;
   }

   public void setAcceptorMatchRegex(String acceptorMatchRegex) {
      this.acceptorMatchRegex = acceptorMatchRegex;
   }

   public String getAcceptorMatchRegex() {
      return acceptorMatchRegex;
   }
}
