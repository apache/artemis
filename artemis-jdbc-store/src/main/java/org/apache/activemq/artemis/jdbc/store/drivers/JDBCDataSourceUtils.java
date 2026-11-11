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
package org.apache.activemq.artemis.jdbc.store.drivers;

import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.commons.beanutils.BeanUtilsBean;

import javax.sql.DataSource;
import java.util.Map;
import java.util.stream.Collectors;

public class JDBCDataSourceUtils {

   public static DataSource getDataSource(String dataSourceClassName, Map<String, Object> dataSourceProperties) {
      ActiveMQJournalLogger.LOGGER.initializingJdbcDataSource(dataSourceClassName, dataSourceProperties
         .keySet()
         .stream()
         .map(key -> key + "=" + (key.equalsIgnoreCase("password") ? "****" : dataSourceProperties.get(key)))
         .collect(Collectors.joining(", ", "{", "}")));
      try {
         DataSource dataSource = (DataSource) ClassloadingUtil.getInstanceWithTypeCheck(dataSourceClassName, DataSource.class, JDBCDataSourceUtils.class.getClassLoader());
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

}
