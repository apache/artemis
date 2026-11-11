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

package org.apache.activemq.artemis.core.persistence.impl.journal;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

public class LargeServerMessageImplTest extends ServerTestBase {

   @Test
   public void testDeleteNoRecreateFile() throws Exception {
      NIOSequentialFileFactory factory = new NIOSequentialFileFactory(getTestDirfile(), 1);
      SequentialFile file = factory.createSequentialFile("1.msg");
      file.open();
      file.write(ActiveMQBuffers.wrappedBuffer(new byte[100]), true);
      file.close();
      LargeServerMessageImpl message = new LargeServerMessageImpl((byte)0, 1, new NullStorageManager(), file);
      file.delete();
      message.deleteFile();
      message.getBodyBufferSize();
      assertFalse(file.exists());
   }
}
