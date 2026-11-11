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
package org.apache.activemq.artemis.osgi;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import static org.easymock.EasyMock.expect;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ProtocolTrackerTest {

   @Test
   public void testLifecycle() throws Exception {
      IMocksControl c = EasyMock.createControl();
      BundleContext context = c.createMock(BundleContext.class);
      String[] requiredProtocols = {"a", "b"};
      ServerTrackerCallBack callback = c.createMock(ServerTrackerCallBack.class);

      RefFact protA = new RefFact(c, context, new String[]{"a"});
      RefFact protB = new RefFact(c, context, new String[]{"b"});

      callback.addFactory(protA.factory);
      EasyMock.expectLastCall();

      callback.addFactory(protB.factory);
      EasyMock.expectLastCall();
      callback.start();
      EasyMock.expectLastCall();

      callback.removeFactory(protA.factory);
      EasyMock.expectLastCall();
      callback.stop();
      EasyMock.expectLastCall();

      c.replay();
      ProtocolTracker tracker = new ProtocolTracker("test", context, requiredProtocols, callback);
      tracker.addingService(protA.ref);
      tracker.addingService(protB.ref);
      tracker.removedService(protA.ref, protA.factory);
      c.verify();
   }

   class RefFact {

      ServiceReference<ProtocolManagerFactory<Interceptor>> ref;
      ProtocolManagerFactory factory;

      RefFact(IMocksControl c, BundleContext context, String[] protocols) {
         ref = c.createMock(ServiceReference.class);
         factory = c.createMock(ProtocolManagerFactory.class);
         expect(factory.getProtocols()).andReturn(protocols).atLeastOnce();
         expect(context.getService(ref)).andReturn(factory).atLeastOnce();
      }
   }

}
