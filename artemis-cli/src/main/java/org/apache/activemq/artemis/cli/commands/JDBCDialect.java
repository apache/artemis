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

package org.apache.activemq.artemis.cli.commands;

public enum JDBCDialect {

   POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432),
   MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306),
   MARIADB("MariaDB", "org.mariadb.jdbc.Driver", 3306),
   ORACLE("Oracle", "oracle.jdbc.OracleDriver", 1521),
   MSSQL("MS SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433),
   DB2("DB2", "com.ibm.db2.jcc.DB2Driver", 50000),
   HSQLDB("HSQLDB", "org.hsqldb.jdbc.JDBCDriver", 9001);

   private final String displayName;
   private final String defaultDriverClassName;
   private final int defaultPort;

   JDBCDialect(String displayName, String defaultDriverClassName, int defaultPort) {
      this.displayName = displayName;
      this.defaultDriverClassName = defaultDriverClassName;
      this.defaultPort = defaultPort;
   }

   public String getDisplayName() {
      return displayName;
   }

   public String getDefaultDriverClassName() {
      return defaultDriverClassName;
   }

   public int getDefaultPort() {
      return defaultPort;
   }

   public String buildConnectionUrl(String host, int port, String database) {
      return switch (this) {
         case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
         case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database;
         case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database;
         case ORACLE -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
         case MSSQL -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database;
         case DB2 -> "jdbc:db2://" + host + ":" + port + "/" + database;
         case HSQLDB -> "jdbc:hsqldb:hsql://" + host + ":" + port + "/" + database;
      };
   }

   public static JDBCDialect detectFromDriverClass(String driverClassName) {
      if (driverClassName == null) {
         return null;
      }
      for (JDBCDialect dialect : values()) {
         if (dialect.defaultDriverClassName.equals(driverClassName)) {
            return dialect;
         }
      }
      return null;
   }

   public static String[] getDisplayNames() {
      JDBCDialect[] dialects = values();
      String[] names = new String[dialects.length];
      for (int i = 0; i < dialects.length; i++) {
         names[i] = dialects[i].displayName;
      }
      return names;
   }
}
