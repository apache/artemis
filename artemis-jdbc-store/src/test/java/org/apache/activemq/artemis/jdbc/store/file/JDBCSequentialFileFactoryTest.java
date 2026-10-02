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
package org.apache.activemq.artemis.jdbc.store.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCDataSourceUtils;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCUtils;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.apache.activemq.artemis.tests.util.DBSupportUtil;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.hsqldb.jdbc.JDBCDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class JDBCSequentialFileFactoryTest extends ArtemisTestCase {

   private static String className = JDBCDriver.class.getCanonicalName();

   private JDBCSequentialFileFactory factory;

   private ExecutorService executor;

   private ScheduledExecutorService scheduledExecutorService;

   @Parameter(index = 0)
   public boolean useAuthentication;

   private String user = null;
   private String password = null;

   @Parameters(name = "authentication = {0}")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][]{{false}, {true}});
   }

   @BeforeEach
   public void setup() throws Exception {
      executor = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      Map<String, Object> dataSourceProperties = new HashMap<>();
      if (useAuthentication) {
         user = "testuser";
         password = "testpassword";
         dataSourceProperties.put("username", user);
         dataSourceProperties.put("password", password);
      } else {
         dataSourceProperties.put("username", "SA");
         dataSourceProperties.put("password", "");
      }
      dataSourceProperties.put("url", "jdbc:hsqldb:mem:filetest");
      dataSourceProperties.put("driverClassName", className);
      String tableName = "FILES";
      String jdbcDatasourceClass = ActiveMQDefaultConfiguration.getDefaultDataSourceClassName();
      factory = new JDBCSequentialFileFactory(new JDBCConnectionProvider(JDBCDataSourceUtils.getDataSource(jdbcDatasourceClass, dataSourceProperties)), JDBCUtils.getSQLProvider(dataSourceProperties, tableName, SQLProvider.DatabaseStoreType.PAGE), executor, scheduledExecutorService, 100, (code, message, file) -> {
      });
      factory.start();
   }

   @AfterEach
   public void tearDown() throws Exception {
      try {
         executor.shutdown();
         scheduledExecutorService.shutdown();
         factory.destroy();
      } finally {
         shutdownHSQL();
      }
   }

   private void shutdownHSQL() {
      try {
         String hsqlUser = useAuthentication ? user : "SA";
         String hsqlPassword = useAuthentication ? password : "";
         DBSupportUtil.shutdownHSQL("jdbc:hsqldb:mem:filetest", hsqlUser, hsqlPassword);
      } catch (Exception ignored) {
      }
   }

   @TestTemplate
   public void testJDBCFileFactoryStarted() throws Exception {
      assertTrue(factory.isStarted());
   }

   @TestTemplate
   public void testReadZeroBytesOnEmptyFile() throws Exception {
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();
      try {
         final ByteBuffer readBuffer = ByteBuffer.allocate(0);
         final int bytes = file.read(readBuffer);
         assertEquals(0, bytes);
      } finally {
         file.close();
      }
   }

   @TestTemplate
   public void testReadZeroBytesOnNotEmptyFile() throws Exception {
      final int fileLength = 8;
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();
      try {
         file.writeDirect(ByteBuffer.allocate(fileLength), true);
         assertEquals(fileLength, file.size());
         final ByteBuffer readBuffer = ByteBuffer.allocate(0);
         final int bytes = file.read(readBuffer);
         assertEquals(0, bytes);
      } finally {
         file.close();
      }
   }

   @TestTemplate
   public void testReadOutOfBoundsOnEmptyFile() throws Exception {
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();
      try {
         final ByteBuffer readBuffer = ByteBuffer.allocate(1);
         file.position(1);
         final int bytes = file.read(readBuffer);
         assertTrue(bytes < 0, "bytes read should be < 0");
      } finally {
         file.close();
      }
   }

   @TestTemplate
   public void testReadOutOfBoundsOnNotEmptyFile() throws Exception {
      final int fileLength = 8;
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();
      try {
         file.writeDirect(ByteBuffer.allocate(fileLength), true);
         assertEquals(fileLength, file.size());
         file.position(fileLength + 1);
         final ByteBuffer readBuffer = ByteBuffer.allocate(fileLength);
         final int bytes = file.read(readBuffer);
         assertTrue(bytes < 0, "bytes read should be < 0");
      } finally {
         file.close();
      }
   }

   @TestTemplate
   public void testCreateFiles() throws Exception {
      int noFiles = 100;
      List<SequentialFile> files = new LinkedList<>();

      Set<String> fileNames = new HashSet<>();
      for (int i = 0; i < noFiles; i++) {
         String fileName = UUID.randomUUID().toString() + ".txt";
         fileNames.add(fileName);
         SequentialFile file = factory.createSequentialFile(fileName);
         // We create files on Open
         file.open();
         files.add(file);
      }

      List<String> queryFileNames = factory.listFiles("txt");
      assertTrue(queryFileNames.containsAll(fileNames));

      for (SequentialFile file: files) {
         file.close();
      }

      assertEquals(0, factory.getNumberOfOpenFiles());
   }

   @TestTemplate
   public void testAsyncAppendToFile() throws Exception {

      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();

      // Create buffer and fill with test data
      int bufferSize = 1024;
      ActiveMQBuffer src = ActiveMQBuffers.fixedBuffer(bufferSize);
      for (int i = 0; i < bufferSize; i++) {
         src.writeByte((byte) 1);
      }

      IOCallbackCountdown callback = new IOCallbackCountdown(1);
      file.jdbcWrite(src, callback);

      callback.assertEmpty(5);
      checkData(file, src);
   }

   @TestTemplate
   public void testWriteToFile() throws Exception {
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();

      ActiveMQBuffer src = ActiveMQBuffers.fixedBuffer(1);
      src.writeByte((byte)7);

      file.jdbcWrite(src, null);
      checkData(file, src);
      assertEquals(1, file.size());
      file.close();

      file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();

      int bufferSize = 1024;
      src = ActiveMQBuffers.fixedBuffer(bufferSize);
      for (int i = 0; i < bufferSize; i++) {
         src.writeByte((byte)i);
      }
      file.jdbcWrite(src, null, false);
      checkData(file, src);
      assertEquals(bufferSize, file.size());
   }

   @TestTemplate
   public void testCopyFile() throws Exception {
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();

      // Create buffer and fill with test data
      int bufferSize = 1024;
      ActiveMQBuffer src = ActiveMQBuffers.fixedBuffer(bufferSize);
      for (int i = 0; i < bufferSize; i++) {
         src.writeByte((byte) 5);
      }

      IOCallbackCountdown callback = new IOCallbackCountdown(1);
      file.jdbcWrite(src, callback);

      JDBCSequentialFile copy = (JDBCSequentialFile) factory.createSequentialFile("copy.txt");
      file.copyTo(copy);

      checkData(file, src);
      checkData(copy, src);

      assertEquals(bufferSize, copy.size());
      assertEquals(bufferSize, file.size());
   }

   @TestTemplate
   public void testCloneFile() throws Exception {
      JDBCSequentialFile file = (JDBCSequentialFile) factory.createSequentialFile("test.txt");
      file.open();

      // Create buffer and fill with test data
      int bufferSize = 1024;
      ActiveMQBuffer src = ActiveMQBuffers.fixedBuffer(bufferSize);
      for (int i = 0; i < bufferSize; i++) {
         src.writeByte((byte) 5);
      }

      IOCallbackCountdown callback = new IOCallbackCountdown(1);
      file.jdbcWrite(src, callback);

      assertEquals(bufferSize, file.size());
      JDBCSequentialFile copy = (JDBCSequentialFile) file.cloneFile();
      copy.open();

      assertEquals(bufferSize, copy.size());
      assertEquals(bufferSize, file.size());
   }

   /**
    * Using a real file system users are not required to call file.open() in order to read the file size.  The file
    * descriptor has enough information.  However, with JDBC we do require that some information is loaded in order to
    * get the underlying BLOB.  This tests ensures that file.size() returns the correct value, without the user calling
    * file.open() with JDBCSequentialFile.
    */
   @TestTemplate
   public void testGetFileSizeWorksWhenNotOpen() throws Exception {
      // Create test file with some data.
      int testFileSize = 1024;
      String fileName = "testFile.txt";
      SequentialFile file = factory.createSequentialFile(fileName);
      file.open();

      // Write some data to the file
      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(new byte[1024]);
      file.write(buffer, true);
      file.close();

      try {
         // Create a new pointer to the test file and ensure file.size() returns the correct value.
         SequentialFile file2 = factory.createSequentialFile(fileName);
         assertEquals(testFileSize, file2.size());
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   private void checkData(JDBCSequentialFile file, ActiveMQBuffer expectedData) throws SQLException {
      expectedData.resetReaderIndex();

      byte[] resultingBytes = new byte[expectedData.readableBytes()];
      ByteBuffer byteBuffer = ByteBuffer.allocate(expectedData.readableBytes());

      file.read(byteBuffer, null);
      expectedData.getBytes(0, resultingBytes);

      assertArrayEquals(resultingBytes, byteBuffer.array());
   }

   private class IOCallbackCountdown implements IOCallback {

      private final CountDownLatch countDownLatch;

      private IOCallbackCountdown(int size) {
         this.countDownLatch = new CountDownLatch(size);
      }

      @Override
      public void done() {
         countDownLatch.countDown();
      }

      @Override
      public void onError(int errorCode, String errorMessage) {
         fail(errorMessage);
      }

      void assertEmpty(int timeout) throws InterruptedException {
         countDownLatch.await(timeout, TimeUnit.SECONDS);
         assertEquals(0, countDownLatch.getCount());
      }
   }
}
