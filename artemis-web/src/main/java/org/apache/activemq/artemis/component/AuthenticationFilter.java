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

import javax.security.auth.Subject;
import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.eclipse.jetty.ee9.nested.Request;
import org.eclipse.jetty.ee9.nested.Response;

/**
 * This filter intercepts the login and audits its results
 */
public class AuthenticationFilter implements Filter {
   @Override
   public void init(FilterConfig filterConfig) {
   }

   @Override
   public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
      filterChain.doFilter(servletRequest, servletResponse);
      if (AuditLogger.isAnyLoggingEnabled()) {
         int status = ((Response) servletResponse).getStatus();
         if (status >= 200 && status < 299) {
            //Successful responses (200 – 299)
            //the user has been authenticated if the session isn't empty
            //the hawtio logout servlet cleans the session and redirects to the login servlet
            HttpSession session = ((Request) servletRequest).getSession(false);
            if (session != null) {
               AuditLogger.userSuccesfullyAuthenticatedInAudit((Subject) session.getAttribute("subject"));
            }
         } else if (status >= 400 && status < 599) {
            //Client error responses (400 – 499)
            //Server error responses (500 – 599)
            //the user authentication has failed
            AuditLogger.userFailedAuthenticationInAudit("" + status);
         }
      }
   }

   @Override
   public void destroy() {
   }
}
