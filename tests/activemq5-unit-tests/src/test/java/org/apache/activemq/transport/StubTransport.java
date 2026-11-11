/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.util.ServiceStopper;
import org.apache.activemq.wireformat.WireFormat;

public class StubTransport extends TransportSupport {

   private Queue<Object> queue = new ConcurrentLinkedQueue<>();
   private AtomicInteger receiveCounter;

   @Override
   protected void doStop(ServiceStopper stopper) throws Exception {
   }

   @Override
   public X509Certificate[] getPeerCertificates() {
      return new X509Certificate[0];
   }

   @Override
   public void setPeerCertificates(X509Certificate[] x509Certificates) {

   }

   @Override
   public WireFormat getWireFormat() {
      return null;
   }

   @Override
   protected void doStart() throws Exception {
   }

   @Override
   public void oneway(Object command) throws IOException {
      receiveCounter.incrementAndGet();
      queue.add(command);
   }

   public Queue<Object> getQueue() {
      return queue;
   }

   @Override
   public String getRemoteAddress() {
      return null;
   }

   @Override
   public int getReceiveCounter() {
      return receiveCounter.get();
   }

}
