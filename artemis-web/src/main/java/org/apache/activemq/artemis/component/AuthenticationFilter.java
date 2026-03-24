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

import org.apache.activemq.artemis.logs.AuditLogger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/*
* This filter intercepts the login and audits its results
* */
public class AuthenticationFilter implements Filter {
   @Override
   public void init(FilterConfig filterConfig) {
   }

   @Override
   public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
      filterChain.doFilter(servletRequest, servletResponse);
      if (AuditLogger.isAnyLoggingEnabled()) {
         int status = ((Response) servletResponse).getStatus();
         //status 200 means that the user has been authenticated, anything else must be a failure
         if (status == 200) {
            //the hawtio logout servlet cleans the session and redirects to the login servlet
            HttpSession session = ((Request) servletRequest).getSession(false);
            if (session != null) {
               AuditLogger.userSuccesfullyAuthenticatedInAudit(session != null ? (Subject) session.getAttribute("subject") : null);
            }
         } else {
            AuditLogger.userFailedAuthenticationInAudit("" + status);
         }
      }
   }

   @Override
   public void destroy() {
   }
}
