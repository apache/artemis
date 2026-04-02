/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.server.federation;

import javax.security.auth.Subject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

public class FederationManager implements ActiveMQComponent {

   private final ActiveMQServer server;

   private Map<String, Federation> federations = new HashMap<>();
   private State state;
   private List<String> downstreamAuthorization;

   enum State {
      STOPPED,
      STOPPING,
      /**
       * Deployed means {@link FederationManager#deploy()} was called but
       * {@link FederationManager#start()} was not called.
       * <p>
       * We need the distinction if {@link FederationManager#stop()} is called before 'start'. As
       * otherwise we would leak locators.
       */
      DEPLOYED, STARTED,
   }


   public FederationManager(final ActiveMQServer server) {
      this.server = server;
      this.downstreamAuthorization = server.getConfiguration().getFederationDownstreamAuthorization();
   }

   @Override
   public synchronized void start() throws ActiveMQException {
      if (state == State.STARTED) return;
      deploy();
      for (Federation federation : federations.values()) {
         federation.start();
      }
      state = State.STARTED;
   }

   @Override
   public synchronized void stop() {
      if (state == State.STOPPED) return;
      state = State.STOPPING;


      for (Federation federation : federations.values()) {
         federation.stop();
      }
      federations.clear();
      state = State.STOPPED;
   }

   @Override
   public boolean isStarted() {
      return state == State.STARTED;
   }

   public synchronized void deploy() throws ActiveMQException {
      for (FederationConfiguration federationConfiguration : server.getConfiguration().getFederationConfigurations()) {
         deploy(federationConfiguration);
      }
      if (state != State.STARTED) {
         state = State.DEPLOYED;
      }
   }

   public synchronized boolean undeploy(String name) {
      Federation federation = federations.remove(name);
      if (federation != null) {
         federation.stop();
         return true;
      }
      return false;
   }



   public synchronized boolean deploy(FederationConfiguration federationConfiguration) throws ActiveMQException {
      Federation federation = federations.get(federationConfiguration.getName());
      if (federation == null) {
         federation = newFederation(federationConfiguration);
      } else if (!Objects.equals(federation.getConfig().getCredentials(), federationConfiguration.getCredentials())) {
         undeploy(federationConfiguration.getName());
         federation = newFederation(federationConfiguration);
      }
      federation.deploy();
      return true;
   }

   private synchronized Federation newFederation(FederationConfiguration federationConfiguration) throws ActiveMQException {
      Federation federation = new Federation(server, federationConfiguration);
      federations.put(federationConfiguration.getName(), federation);
      if (state == State.STARTED) {
         federation.start();
      }
      return federation;
   }

   public Federation get(String name) {
      return federations.get(name);
   }

   public void register(FederatedAbstract federatedAbstract) {
      server.registerBrokerPlugin(federatedAbstract);
   }

   public void unregister(FederatedAbstract federatedAbstract) {
      server.unRegisterBrokerPlugin(federatedAbstract);
   }

   public boolean authorizeDownstreamDeployment(Subject subject) {
      if (!server.getSecurityStore().isSecurityEnabled()) {
         return true;
      }
      for (RolePrincipal role : subject.getPrincipals(RolePrincipal.class)) {
         if (downstreamAuthorization.contains(role.getName())) {
            return true;
         }
      }
      return false;
   }

}
