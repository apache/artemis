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
package org.apache.activemq.artemis.jms.tests.util;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This class will proxy any JUnit assertions and send then to our log outputs.
 */
public class ProxyAssertSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static void assertTrue(final java.lang.String string, final boolean b) {
      try {
         Assertions.assertTrue(b, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertTrue(final boolean b) {
      try {
         Assertions.assertTrue(b);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertFalse(final java.lang.String string, final boolean b) {
      try {
         Assertions.assertFalse(b, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertFalse(final boolean b) {
      try {
         Assertions.assertFalse(b);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void fail(final java.lang.String string) {
      try {
         Assertions.fail(string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void fail() {
      try {
         Assertions.fail();
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string,
                                   final java.lang.Object object,
                                   final java.lang.Object object1) {
      try {
         Assertions.assertEquals(object, object1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.Object object, final java.lang.Object object1) {
      try {
         Assertions.assertEquals(object, object1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string,
                                   final java.lang.String string1,
                                   final java.lang.String string2) {
      try {
         Assertions.assertEquals(string1, string2, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final java.lang.String string1) {
      try {
         Assertions.assertEquals(string, string1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final double v, final double v1, final double v2) {
      try {
         Assertions.assertEquals(v, v1, v2, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final double v, final double v1, final double v2) {
      try {
         Assertions.assertEquals(v, v1, v2);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final float v, final float v1, final float v2) {
      try {
         Assertions.assertEquals(v, v1, v2, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final float v, final float v1, final float v2) {
      try {
         Assertions.assertEquals(v, v1, v2);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final long l, final long l1) {
      try {
         Assertions.assertEquals(l, l1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final long l, final long l1) {
      try {
         Assertions.assertEquals(l, l1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final boolean b, final boolean b1) {
      try {
         Assertions.assertEquals(b, b1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final boolean b, final boolean b1) {
      try {
         Assertions.assertEquals(b, b1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final byte b, final byte b1) {
      try {
         Assertions.assertEquals(b, b1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final byte b, final byte b1) {
      try {
         Assertions.assertEquals(b, b1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final char c, final char c1) {
      try {
         Assertions.assertEquals(c, c1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final char c, final char c1) {
      try {
         Assertions.assertEquals(c, c1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final short i, final short i1) {
      try {
         Assertions.assertEquals(i, i1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final short i, final short i1) {
      try {
         Assertions.assertEquals(i, i1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final java.lang.String string, final int i, final int i1) {
      try {
         Assertions.assertEquals(i, i1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertEquals(final int i, final int i1) {
      try {
         Assertions.assertEquals(i, i1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertNotNull(final java.lang.Object object) {
      try {
         Assertions.assertNotNull(object);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertNotNull(final java.lang.String string, final java.lang.Object object) {
      try {
         Assertions.assertNotNull(object, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertNull(final java.lang.Object object) {
      try {
         Assertions.assertNull(object);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertNull(final java.lang.String string, final java.lang.Object object) {
      try {
         Assertions.assertNull(object, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertSame(final java.lang.String string,
                                 final java.lang.Object object,
                                 final java.lang.Object object1) {
      try {
         Assertions.assertSame(object, object1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertSame(final java.lang.Object object, final java.lang.Object object1) {
      try {
         Assertions.assertSame(object, object1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertNotSame(final java.lang.String string,
                                    final java.lang.Object object,
                                    final java.lang.Object object1) {
      try {
         Assertions.assertNotSame(object, object1, string);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }

   public static void assertNotSame(final java.lang.Object object, final java.lang.Object object1) {
      try {
         Assertions.assertNotSame(object, object1);
      } catch (AssertionError e) {
         logger.warn("AssertionFailure::", e);
         throw e;
      }
   }
}
