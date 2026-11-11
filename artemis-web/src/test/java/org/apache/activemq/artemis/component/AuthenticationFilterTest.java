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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.jetty.ee9.servlet.FilterHolder;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AuthenticationFilterTest {

   private final String TEXT = "Filter applied!";

   private Server server;

   @BeforeEach
   public void setUp() throws Exception {
      server = new Server(8080);

      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/");
      server.setHandler(context);

      FilterHolder filterHolder = new FilterHolder(new AuthenticationFilter());
      context.addFilter(filterHolder, "/*", null);

      ServletHolder servletHolder = new ServletHolder(new HttpServlet() {
         @Override
         protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().write(TEXT);
         }
      });
      context.addServlet(servletHolder, "/");

      server.start();

      // turn on audit logging to fully exercise AuthenticationFilter
      Configurator.setLevel(LogManager.getLogger(AuditLogger.BASE_LOGGER.getLogger().getName()), Level.INFO);
   }

   @AfterEach
   public void tearDown() throws Exception {
      if (server != null) {
         server.stop();
      }
   }

   @Test
   public void testAuthenticationFilter() throws IOException {
      HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:8080/").openConnection();
      connection.setRequestMethod("GET");
      assertEquals(TEXT, new String(connection.getInputStream().readAllBytes()));
   }
}
