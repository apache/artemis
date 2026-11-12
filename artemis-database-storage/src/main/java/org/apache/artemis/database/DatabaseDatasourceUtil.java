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
package org.apache.artemis.database;

import javax.sql.DataSource;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.commons.beanutils.BeanUtilsBean;

public class DatabaseDatasourceUtil {

   public static DataSource getDataSource(String dataSourceClassName, Map<String, Object> dataSourceProperties) {
      return getDataSource(null, null, null, null, null, dataSourceClassName, dataSourceProperties);
   }

   public static DataSource getDataSource(String jdbcDriverClassName,
                                          String jdbcConnectionUrl,
                                          String jdbcUser,
                                          String jdbcPassword,
                                          String maxTotal,
                                          String dataSourceClassName,
                                          Map<String, Object> dataSourceProperties) {

      // the next settings are going to be applied only if the datasource is the default one
      if (ActiveMQDefaultConfiguration.getDefaultDataSourceClassName().equals(dataSourceClassName)) {
         // these default settings will be applied only if a custom configuration won't override them
         if (jdbcDriverClassName != null && !dataSourceProperties.containsKey("driverClassName")) {
            addDataSourceProperty(dataSourceProperties, "driverClassName", jdbcDriverClassName);
         }
         if (jdbcConnectionUrl != null && !dataSourceProperties.containsKey("url")) {
            addDataSourceProperty(dataSourceProperties, "url", jdbcConnectionUrl);
         }
         if (jdbcUser != null && !dataSourceProperties.containsKey("username")) {
            if (jdbcUser != null) {
               addDataSourceProperty(dataSourceProperties, "username", jdbcUser);
            }
         }
         if (jdbcPassword != null && !dataSourceProperties.containsKey("password")) {
            if (jdbcPassword != null) {
               addDataSourceProperty(dataSourceProperties, "password", jdbcPassword);
            }
         }
         if (maxTotal != null && !dataSourceProperties.containsKey("maxTotal")) {
            // Let the pool to have unbounded number of connections by default to prevent connection starvation
            addDataSourceProperty(dataSourceProperties, "maxTotal", maxTotal);
         }
         if (!dataSourceProperties.containsKey("poolPreparedStatements")) {
            // Let the pool to have unbounded number of cached prepared statements to save the initialization cost
            addDataSourceProperty(dataSourceProperties, "poolPreparedStatements", "true");
         }
      }

      ActiveMQJournalLogger.LOGGER.initializingJdbcDataSource(dataSourceClassName, dataSourceProperties.keySet().stream().map(key -> key + "=" + (key.equalsIgnoreCase("password") ? "****" : dataSourceProperties.get(key))).collect(Collectors.joining(", ", "{", "}")));
      try {
         DataSource dataSource = (DataSource) ClassloadingUtil.getInstanceWithTypeCheck(dataSourceClassName, DataSource.class, DatabaseDatasourceUtil.class.getClassLoader());
         for (Map.Entry<String, Object> entry : dataSourceProperties.entrySet()) {
            BeanUtilsBean.getInstance().setProperty(dataSource, entry.getKey(), entry.getValue());
         }
         return dataSource;
      } catch (ClassNotFoundException cnfe) {
         throw new RuntimeException("Could not find class: " + dataSourceClassName);
      } catch (Exception e) {
         throw new RuntimeException("Unable to instantiate DataSource", e);
      }
   }

   public static void addDataSourceProperty(Map<String, Object> dataSourceProperties, String key, String value) {
      if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
         dataSourceProperties.put(key, Boolean.parseBoolean(value.toLowerCase()));
      } else {
         try {
            int i = Integer.parseInt(value);
            dataSourceProperties.put(key, i);
         } catch (NumberFormatException nfe) {
            dataSourceProperties.put(key, value);
         }
      }
   }

}
