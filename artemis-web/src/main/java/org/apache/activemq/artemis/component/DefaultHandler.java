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
package org.apache.activemq.artemis.component;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class DefaultHandler extends org.eclipse.jetty.server.handler.DefaultHandler {
   private String rootRedirectLocation;

   public String getRootRedirectLocation() {
      return rootRedirectLocation;
   }

   public void setRootRedirectLocation(String rootRedirectLocation) {
      this.rootRedirectLocation = rootRedirectLocation;
   }

   @Override
   public boolean handle(Request request, Response response, Callback callback) throws Exception {
      if (rootRedirectLocation != null && request.getHttpURI().getPath().matches("^$|/")) {
         Response.sendRedirect(request, response, callback, Response.toRedirectURI(request, rootRedirectLocation));
         callback.succeeded();
         return true;
      } else {
         return super.handle(request, response, callback);
      }
   }
}
