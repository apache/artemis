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

package org.apache.activemq.artemis.cli.commands.check;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine;

@CommandLine.Command(name = "cluster", description = "Verify if all the nodes on the cluster match the same topology and time configuration.")
public class ClusterCheck extends ConnectionAbstract {

   @CommandLine.Option(names = "--variance", description = "Allowed variance in milliseconds before considered a failure. (default=1000)")
   public long variance = 1000;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      createConnectionFactory().close();

      try (ClusterNodeVerifier clusterVerifier = new ClusterNodeVerifier(brokerURL, user, password, variance).open()) {
         return clusterVerifier.verify(context);
      }
   }
}