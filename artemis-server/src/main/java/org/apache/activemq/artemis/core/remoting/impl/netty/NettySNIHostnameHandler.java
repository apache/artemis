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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.AbstractSniHandler;
import io.netty.util.concurrent.Future;

public class NettySNIHostnameHandler extends AbstractSniHandler {

   private String hostname = null;

   public String getHostname() {
      return hostname;
   }

   @Override
   protected Future lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
      return ctx.executor().<Void>newPromise().setSuccess(null);
   }

   @Override
   protected void onLookupComplete(ChannelHandlerContext ctx, String hostname, Future future) throws Exception {
      this.hostname = hostname;
      ctx.pipeline().remove(this);
   }
}
