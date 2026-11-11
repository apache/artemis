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
package org.apache.activemq.artemis.core.server.management.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerGuard;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class HawtioSecurityControlImplTest {

   protected GuardInvocationHandler guard;

   @BeforeEach
   public void initGuard() {
      guard = Mockito.mock(ArtemisMBeanServerGuard.class);
   }

   @Test
   public void testCanInvokeMBean() throws Exception {
      String objectName = "foo.bar.testing:type=SomeMBean";
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      Mockito.when(guard.canInvoke(objectName, null)).thenReturn(true);

      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
      assertTrue(control.canInvoke(objectName));
   }

   @Test
   public void testCanInvokeMBean2() throws Exception {
      String objectName = "foo.bar.testing:type=SomeMBean";
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      Mockito.when(guard.canInvoke(objectName, null)).thenReturn(false);

      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
      assertFalse(control.canInvoke(objectName));
   }

   @Test
   public void testCanInvokeMBeanThrowsException() throws Exception {
      assertThrows(Exception.class, () -> {
         String objectName = "foo.bar.testing:type=SomeMBean";
         StorageManager storageManager = Mockito.mock(StorageManager.class);
         Mockito.when(guard.canInvoke(objectName, null)).thenThrow(new Exception());

         HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
         control.canInvoke(objectName);
         fail("Should have thrown an exception");
      });
   }

   @Test
   public void testCanInvokeMBeanNoGuard() throws Exception {
      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(null, null);
      assertTrue(control.canInvoke("foo.bar.testing:type=SomeMBean"));
   }

   @Test
   public void testCanInvokeMethod() throws Exception {
      String objectName = "foo.bar.testing:type=SomeMBean";
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      Mockito.when(guard.canInvoke(objectName, "testMethod")).thenReturn(true);
      Mockito.when(guard.canInvoke(objectName, "otherMethod")).thenReturn(false);

      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
      assertTrue(control.canInvoke(objectName, "testMethod", new String[]{"long"}));
      assertTrue(control.canInvoke(objectName, "testMethod", new String[]{"java.lang.String"}));
      assertFalse(control.canInvoke(objectName, "otherMethod", new String[]{"java.lang.String", "java.lang.String"}));
   }

   @Test
   public void testCanInvokeMethodException() throws Exception {
      assertThrows(Exception.class, () -> {
         String objectName = "foo.bar.testing:type=SomeMBean";
         StorageManager storageManager = Mockito.mock(StorageManager.class);
         Mockito.when(guard.canInvoke(objectName, "testMethod")).thenThrow(new Exception());

         HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
         control.canInvoke(objectName, "testMethod", new String[]{});
         fail("Should have thrown an exception");
      });
   }

   @Test
   public void testCanInvokeMethodNoGuard() throws Exception {
      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(null, null);
      assertTrue(control.canInvoke("foo.bar.testing:type=SomeMBean", "someMethod", new String[]{}));
   }

   @Test
   public void testCanInvokeBulk() throws Exception {
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      String objectName = "foo.bar.testing:type=SomeMBean";
      Mockito.when(guard.canInvoke(objectName, "testMethod")).thenReturn(true);
      Mockito.when(guard.canInvoke(objectName, "testMethod2")).thenReturn(false);
      Mockito.when(guard.canInvoke(objectName, "otherMethod")).thenReturn(true);
      String objectName2 = "foo.bar.testing:type=SomeOtherMBean";
      Mockito.when(guard.canInvoke(objectName2, null)).thenReturn(true);
      String objectName3 = "foo.bar.foo.testing:type=SomeOtherMBean";
      Mockito.when(guard.canInvoke(objectName3, null)).thenReturn(false);

      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
      Map<String, List<String>> query = new HashMap<>();
      query.put(objectName, Arrays.asList("otherMethod", "testMethod(long)", "testMethod2(java.lang.String)"));
      query.put(objectName2, Collections.emptyList());
      query.put(objectName3, Collections.emptyList());
      TabularData result = control.canInvoke(query);
      assertEquals(5, result.size());

      CompositeData cd = result.get(new Object[]{objectName, "testMethod(long)"});
      assertEquals(objectName, cd.get("ObjectName"));
      assertEquals("testMethod(long)", cd.get("Method"));
      assertEquals(true, cd.get("CanInvoke"));
      CompositeData cd2 = result.get(new Object[]{objectName, "testMethod2(java.lang.String)"});
      assertEquals(objectName, cd2.get("ObjectName"));
      assertEquals("testMethod2(java.lang.String)", cd2.get("Method"));
      assertEquals(false, cd2.get("CanInvoke"));
      CompositeData cd3 = result.get(new Object[]{objectName, "otherMethod"});
      assertEquals(objectName, cd3.get("ObjectName"));
      assertEquals("otherMethod", cd3.get("Method"));
      assertEquals(true, cd3.get("CanInvoke"));
      CompositeData cd4 = result.get(new Object[]{objectName2, ""});
      assertEquals(objectName2, cd4.get("ObjectName"));
      assertEquals("", cd4.get("Method"));
      assertEquals(true, cd4.get("CanInvoke"));
      CompositeData cd5 = result.get(new Object[]{objectName3, ""});
      assertEquals(objectName3, cd5.get("ObjectName"));
      assertEquals("", cd5.get("Method"));
      assertEquals(false, cd5.get("CanInvoke"));
   }

   @Test
   public void testCanInvokeBulkWithDuplicateMethods() throws Exception {
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      String objectName = "foo.bar.testing:type=SomeMBean";
      Mockito.when(guard.canInvoke(objectName, "duplicateMethod1")).thenReturn(true);
      Mockito.when(guard.canInvoke(objectName, "duplicateMethod2")).thenReturn(false);

      HawtioSecurityControlImpl control = new HawtioSecurityControlImpl(guard, storageManager);
      Map<String, List<String>> query = new HashMap<>();
      query.put(objectName, Arrays.asList("duplicateMethod1(long)", "duplicateMethod1(java.lang.String)", "duplicateMethod1(long)", "duplicateMethod2", "duplicateMethod2"));
      TabularData result = control.canInvoke(query);
      assertEquals(3, result.size());

      CompositeData cd = result.get(new Object[]{objectName, "duplicateMethod1(long)"});
      assertEquals(objectName, cd.get("ObjectName"));
      assertEquals("duplicateMethod1(long)", cd.get("Method"));
      assertEquals(true, cd.get("CanInvoke"));
      CompositeData cd2 = result.get(new Object[]{objectName, "duplicateMethod1(java.lang.String)"});
      assertEquals(objectName, cd2.get("ObjectName"));
      assertEquals("duplicateMethod1(java.lang.String)", cd2.get("Method"));
      assertEquals(true, cd2.get("CanInvoke"));
      CompositeData cd3 = result.get(new Object[]{objectName, "duplicateMethod2"});
      assertEquals(objectName, cd3.get("ObjectName"));
      assertEquals("duplicateMethod2", cd3.get("Method"));
      assertEquals(false, cd3.get("CanInvoke"));
   }
}
