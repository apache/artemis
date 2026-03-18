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
package org.apache.activemq.artemis.tests.extensions;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.UnixOperatingSystemMXBean;

/**
 * This is useful to make sure you won't have leaking process files between tests
 */
public class OpenFilesCheckExtension implements Extension, AfterAllCallback {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

   private long maxFiles;

   public OpenFilesCheckExtension(long maxFiles) {
      this.maxFiles = maxFiles;
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      String testName = context.getRequiredTestClass().getName();

      logger.debug("Checking open files after {}", testName);

      if (!Wait.waitFor(() -> getOpenFD() < maxFiles, 10000, 0)) {
         String fileList = getOpenList();
         fail("Too many files open (" + getOpenFD()  + ">" + maxFiles + ") after " + testName + ". A possible list: " + fileList);
      }
   }

   public static long getOpenFD() {
      if (os instanceof UnixOperatingSystemMXBean bean) {
         return bean.getOpenFileDescriptorCount();
      } else {
         return 0;
      }
   }

   public static List<String> getOpenFiles(boolean filtered) {
      List<String> openFiles = new ArrayList<>();

      try {
         String outputLine;
         int processId = getProcessId();

         Process child = Runtime.getRuntime().exec("lsof -a -p " + processId + " -d ^txt,^mem,^cwd,^rtd,^DEL", new String[] {});

         try (BufferedReader processInput = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
            processInput.readLine();
            while ((outputLine = processInput.readLine()) != null) {
               if (!filtered || (!outputLine.endsWith(".jar") && !outputLine.endsWith(".so") && !outputLine.contains("type=STREAM"))) {
                  openFiles.add(outputLine);
               }
            }
         }
      } catch (Exception ignore) {
      }

      return openFiles;
   }

   private static int getProcessId() throws ReflectiveOperationException {
      java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
      java.lang.reflect.Field jvmField = runtime.getClass().getDeclaredField("jvm");
      jvmField.setAccessible(true);
      Object jvm = jvmField.get(runtime);
      java.lang.reflect.Method getProcessIdMethod = jvm.getClass().getDeclaredMethod("getProcessId");
      getProcessIdMethod.setAccessible(true);
      return (Integer) getProcessIdMethod.invoke(jvm);
   }

   private String getOpenList() {
      List<String> openFiles = getOpenFiles(true);
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      boolean first = true;
      for (String str : openFiles) {
         if (!first) {
            printWriter.print("\n");
         }
         first = false;
         printWriter.print(str);
      }
      return stringWriter.toString();
   }
}
