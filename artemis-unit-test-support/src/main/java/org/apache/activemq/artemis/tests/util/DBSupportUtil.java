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
package org.apache.activemq.artemis.tests.util;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBSupportUtil {
   private static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static void dropHSQLDatabase(String connectionUrl, String user, String password) throws SQLException {
      logger.debug("dropHSQLDatabase on {}", connectionUrl);
      try (Connection connection = getConnection(connectionUrl, user, password);
           Statement statement = connection.createStatement()) {
         statement.execute("DROP SCHEMA PUBLIC CASCADE");
      } catch (SQLException sqlE) {
         logger.debug("{} / {}", sqlE.getMessage(), sqlE.getSQLState(), sqlE);
      }
   }

   public static void shutdownHSQL(String connectionUrl, String user, String password) throws SQLException {
      try (Connection connection = getConnection(connectionUrl, user, password);
           Statement statement = connection.createStatement()) {
         statement.execute("SHUTDOWN");
      } catch (SQLException sqlE) {
         logger.debug("{} / {}", sqlE.getMessage(), sqlE.getSQLState(), sqlE);
      }
   }

   private static Connection getConnection(String connectionUrl, String user, String password) throws SQLException {
      if (user == null) {
         return DriverManager.getConnection(connectionUrl);
      } else {
         return DriverManager.getConnection(connectionUrl, user, password);
      }
   }

}
