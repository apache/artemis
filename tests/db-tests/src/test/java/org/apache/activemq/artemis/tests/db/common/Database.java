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
package org.apache.activemq.artemis.tests.db.common;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.utils.RandomUtil;

public enum Database {
   MYSQL("mysql"), POSTGRES("postgres"), ORACLE("oracle"), MSSQL("mssql"), DB2("db2"), JOURNAL("journal"), HSQL("hsql");

   private String dbname;
   private boolean load;
   private String jdbcURI;
   private String driverClass;
   private Driver driver;
   private ClassLoader dbClassLoader;

   Database(String dbname) {
      this.dbname = dbname;
      load = Boolean.parseBoolean(System.getProperty(dbname + ".load", "false"));
   }

   public static ClassLoader defineClassLoader(File location, ClassLoader parentClassLoader) throws Exception {
      File[] jars = location.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

      URL[] url = new URL[jars.length];

      for (int i = 0; i < jars.length; i++) {
         url[i] = jars[i].toURI().toURL();
      }

      return new URLClassLoader(url, parentClassLoader);
   }

   public Connection getConnection() throws Exception {
      return switch (this) {
         case HSQL -> DriverManager.getConnection(getJdbcURI(), "SA", "");
         case JOURNAL -> null;
         default -> getDriver().connect(getJdbcURI(), null);
      };
   }

   // There is one artemis server for each database we provide on the tests
   public ClassLoader getDBClassLoader() throws Exception {
      if (this != JOURNAL && this != HSQL && dbClassLoader == null) {
         String serverLocation = ParameterDBTestBase.getServerLocation(getName());
         File lib = new File(serverLocation + "/lib");
         dbClassLoader = defineClassLoader(lib, getClass().getClassLoader());
      }
      return dbClassLoader;
   }

   public String getName() {
      return dbname;
   }

   public String getJdbcURI() {
      if (jdbcURI == null) {
         switch (this) {
            case HSQL:
               jdbcURI = "jdbc:hsqldb:mem:dbtest";
               break;
            case JOURNAL:
               jdbcURI = null;
               break;
            default:
               jdbcURI = System.getProperty(dbname + ".uri");
               if (jdbcURI != null) {
                  jdbcURI = jdbcURI.replaceAll("&#38;", "&");
               }
         }
      }
      return jdbcURI;
   }

   public String getDriverClass() {
      if (driverClass != null) {
         return driverClass;
      }

      switch (this) {
         case HSQL:
            this.driverClass = ActiveMQDefaultConfiguration.getDefaultDriverClassName();
            break;
         case JOURNAL:
            this.driverClass = null;
            break;
         default:
            driverClass = System.getProperty(dbname + ".class");
      }

      return driverClass;
   }

   public Driver getDriver() throws Exception {
      if (driver == null) {
         String className = getDriverClass();
         ClassLoader loader = getDBClassLoader();
         driver = (Driver) loader.loadClass(className).getDeclaredConstructor().newInstance();
      }
      return driver;
   }

   // this must be called within the test classLoader (Thread Context Class Loader)
   public void registerDriver() throws Exception {
      if (driver != null) {
         DriverManager.registerDriver(driver);
      }
   }

   public boolean isLoad() {
      return load;
   }

   @Override
   public String toString() {
      return dbname;
   }


   // it will return a list of Databases selected to be tested
   public static List<Database> selectedList() {
      List<Database> dbList = new ArrayList<>();

      for (Database db : Database.values()) {
         if (db.isLoad()) {
            dbList.add(db);
         }
      }

      return dbList;
   }

   public static Database randomDB() {
      List<Database> selectedDatabases = selectedList();
      if (selectedDatabases.isEmpty()) {
         return null;
      } else {
         return selectedDatabases.get(RandomUtil.randomInterval(0, selectedDatabases.size()));
      }
   }

   public static List<Database> randomList() {
      List<Database> selectedDatabases = selectedList();
      List<Database> list = new ArrayList<>();
      Database randomDB = randomDB();
      if (randomDB != null) {
         list.add(randomDB);
      }
      return list;
   }

}
