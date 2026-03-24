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
package org.apache.activemq.artemis.core.filter.impl;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInvalidFilterExpressionException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.tests.util.SilentTestCase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the compliance with the ActiveMQ Artemis Filter syntax.
 */
public class FilterTest extends SilentTestCase {

   private Filter filter;

   private Message message;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      message = new CoreMessage().initBuffer(1024).setMessageID(1);
   }

   @Test
   public void testNewlineMatch() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("fooprop LIKE '%1234%'"));

      message.putStringProperty(new SimpleString("fooprop"), new SimpleString("hello1234\n"));

      Assert.assertTrue(filter.match(message));
   }

   @Test
   public void testFilterForgets() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("color = 'RED'"));

      message.putStringProperty(new SimpleString("color"), new SimpleString("RED"));
      Assert.assertTrue(filter.match(message));
      message = new CoreMessage();
      Assert.assertFalse(filter.match(message));
   }

   @Test
   public void testInvalidString() throws Exception {
      testInvalidFilter("color = 'red");
      testInvalidFilter(new SimpleString("color = 'red"));

      testInvalidFilter("3");
      testInvalidFilter(new SimpleString("3"));
   }

   @Test
   public void testNullFilter() throws Exception {
      Assert.assertNull(FilterImpl.createFilter((String) null));
      Assert.assertNull(FilterImpl.createFilter(""));
      Assert.assertNull(FilterImpl.createFilter((SimpleString) null));
      Assert.assertNull(FilterImpl.createFilter(new SimpleString("")));
   }

   @Test
   public void testAMQDurable() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("AMQDurable='DURABLE'"));

      message.setDurable(true);

      Assert.assertTrue(filter.match(message));

      message.setDurable(false);

      Assert.assertFalse(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("AMQDurable='NON_DURABLE'"));

      message = new CoreMessage();
      message.setDurable(true);

      Assert.assertFalse(filter.match(message));

      message.setDurable(false);

      Assert.assertTrue(filter.match(message));

   }

   @Test
   public void testAMQSize() throws Exception {
      message.setAddress(RandomUtil.randomSimpleString());

      int encodeSize = message.getEncodeSize();

      Filter moreThanSmall = FilterImpl.createFilter(new SimpleString("AMQSize > " + (encodeSize - 1)));
      Filter lessThanLarge = FilterImpl.createFilter(new SimpleString("AMQSize < " + (encodeSize + 1)));

      Filter lessThanSmall = FilterImpl.createFilter(new SimpleString("AMQSize < " + encodeSize));
      Filter moreThanLarge = FilterImpl.createFilter(new SimpleString("AMQSize > " + encodeSize));

      Assert.assertTrue(moreThanSmall.match(message));
      Assert.assertTrue(lessThanLarge.match(message));

      Assert.assertFalse(lessThanSmall.match(message));
      Assert.assertFalse(moreThanLarge.match(message));

   }

   @Test
   public void testAMQPriority() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("AMQPriority=3"));

      for (int i = 0; i < 10; i++) {
         message.setPriority((byte) i);

         if (i == 3) {
            Assert.assertTrue(filter.match(message));
         } else {
            Assert.assertFalse(filter.match(message));
         }
      }
   }

   @Test
   public void testAMQTimestamp() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("AMQTimestamp=12345678"));

      message.setTimestamp(87654321);

      Assert.assertFalse(filter.match(message));

      message.setTimestamp(12345678);

      Assert.assertTrue(filter.match(message));
   }

   @Test
   public void testBooleanTrue() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("MyBoolean=true"));

      testBoolean("MyBoolean", true);
   }

   @Test
   public void testIdentifier() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("MyBoolean"));

      testBoolean("MyBoolean", true);
   }

   @Test
   public void testDifferentNullString() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("prop <> 'foo'"));
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("NOT (prop = 'foo')"));
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("prop <> 'foo'"));
      doPutStringProperty("prop", "bar");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("prop <> 'foo'"));
      doPutStringProperty("prop", "foo");
      Assert.assertFalse(filter.match(message));
   }

   @Test
   public void testBooleanFalse() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("MyBoolean=false"));
      testBoolean("MyBoolean", false);
   }

   private void testBoolean(final String name, final boolean flag) throws Exception {
      message.putBooleanProperty(new SimpleString(name), flag);
      Assert.assertTrue(filter.match(message));

      message.putBooleanProperty(new SimpleString(name), !flag);
      Assert.assertTrue(!filter.match(message));
   }

   @Test
   public void testStringEquals() throws Exception {
      // First, simple test of string equality and inequality
      filter = FilterImpl.createFilter(new SimpleString("MyString='astring'"));

      doPutStringProperty("MyString", "astring");
      Assert.assertTrue(filter.match(message));

      doPutStringProperty("MyString", "NOTastring");
      Assert.assertTrue(!filter.match(message));

      // test empty string
      filter = FilterImpl.createFilter(new SimpleString("MyString=''"));

      doPutStringProperty("MyString", "");
      Assert.assertTrue("test 1", filter.match(message));

      doPutStringProperty("MyString", "NOTastring");
      Assert.assertTrue("test 2", !filter.match(message));

      // test literal apostrophes (which are escaped using two apostrophes
      // in selectors)
      filter = FilterImpl.createFilter(new SimpleString("MyString='test JBoss''s filter'"));

      // note: apostrophes are not escaped in string properties
      doPutStringProperty("MyString", "test JBoss's filter");
      // this test fails -- bug 530120
      // assertTrue("test 3", filter.match(message));

      doPutStringProperty("MyString", "NOTastring");
      Assert.assertTrue("test 4", !filter.match(message));

   }

   @Test
   public void testNOT_INWithNullProperty() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("myNullProp NOT IN ('foo','jms','test')"));

      assertFalse(filter.match(message));

      message.putStringProperty("myNullProp", "JMS");
      assertTrue(filter.match(message));
   }

   @Test
   public void testNOT_LIKEWithNullProperty() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("myNullProp NOT LIKE '1_3'"));

      assertFalse(filter.match(message));

      message.putStringProperty("myNullProp", "JMS");
      assertTrue(filter.match(message));
   }

   @Test
   public void testIS_NOT_NULLWithNullProperty() throws Exception {
      filter = FilterImpl.createFilter(new SimpleString("myNullProp IS NOT NULL"));

      assertFalse(filter.match(message));

      message.putStringProperty("myNullProp", "JMS");
      assertTrue(filter.match(message));
   }

   @Test
   public void testStringLike() throws Exception {
      // test LIKE operator with no wildcards
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'astring'"));
      Assert.assertFalse(filter.match(message));

      // test where LIKE operand matches
      doPutStringProperty("MyString", "astring");
      Assert.assertTrue(filter.match(message));

      // test one character string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'a'"));
      doPutStringProperty("MyString", "a");
      Assert.assertTrue(filter.match(message));

      // test empty string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE ''"));
      doPutStringProperty("MyString", "");
      Assert.assertTrue(filter.match(message));

      // tests where operand does not match
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'astring'"));

      // test with extra characters at beginning
      doPutStringProperty("MyString", "NOTastring");
      Assert.assertTrue(!filter.match(message));

      // test with extra characters at end
      doPutStringProperty("MyString", "astringNOT");
      Assert.assertTrue(!filter.match(message));

      // test with extra characters in the middle
      doPutStringProperty("MyString", "astNOTring");
      Assert.assertTrue(!filter.match(message));

      // test where operand is entirely different
      doPutStringProperty("MyString", "totally different");
      Assert.assertTrue(!filter.match(message));

      // test case sensitivity
      doPutStringProperty("MyString", "ASTRING");
      Assert.assertTrue(!filter.match(message));

      // test empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // test lower-case 'like' operator?
   }

   @Test
   public void testStringLikeUnderbarWildcard() throws Exception {
      // test LIKE operator with the _ wildcard, which
      // matches any single character

      // first, some tests with the wildcard by itself
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '_'"));
      Assert.assertFalse(filter.match(message));

      // test match against single character
      doPutStringProperty("MyString", "a");
      Assert.assertTrue(filter.match(message));

      // test match failure against multiple characters
      doPutStringProperty("MyString", "aaaaa");
      Assert.assertTrue(!filter.match(message));

      // test match failure against the empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // next, tests with wildcard at the beginning of the string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '_bcdf'"));

      // test match at beginning of string
      doPutStringProperty("MyString", "abcdf");
      Assert.assertTrue(filter.match(message));

      // match failure in first character after wildcard
      doPutStringProperty("MyString", "aXcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      Assert.assertTrue(!filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      Assert.assertTrue(!filter.match(message));

      // test that the _ wildcard does not match the 'empty' character
      doPutStringProperty("MyString", "bcdf");
      Assert.assertTrue(!filter.match(message));

      // next, tests with wildcard at the end of the string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'abcd_'"));

      // test match at end of string
      doPutStringProperty("MyString", "abcdf");
      Assert.assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "abcXf");
      Assert.assertTrue(!filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in first character
      doPutStringProperty("MyString", "Xbcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      Assert.assertTrue(!filter.match(message));

      // test that the _ wildcard does not match the 'empty' character
      doPutStringProperty("MyString", "abcd");
      Assert.assertTrue(!filter.match(message));

      // test match in middle of string

      // next, tests with wildcard in the middle of the string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'ab_df'"));

      // test match in the middle of string
      doPutStringProperty("MyString", "abcdf");
      Assert.assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "aXcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in first character after wildcard
      doPutStringProperty("MyString", "abcXf");
      Assert.assertTrue(!filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      Assert.assertTrue(!filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      Assert.assertTrue(!filter.match(message));

      // test that the _ wildcard does not match the 'empty' character
      doPutStringProperty("MyString", "abdf");
      Assert.assertTrue(!filter.match(message));

      // test match failures
   }

   @Test
   public void testNotLikeExpression() throws Exception {
      // Should evaluate to false when the property MyString is not set
      filter = FilterImpl.createFilter(new SimpleString("NOT (MyString LIKE '%')"));

      Assert.assertFalse(filter.match(message));
   }

   @Test
   public void testStringLikePercentWildcard() throws Exception {
      // test LIKE operator with the % wildcard, which
      // matches any sequence of characters
      // note many of the tests are similar to those for _

      // first, some tests with the wildcard by itself
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '%'"));
      Assert.assertFalse(filter.match(message));

      // test match against single character
      doPutStringProperty("MyString", "a");
      Assert.assertTrue(filter.match(message));

      // test match against multiple characters
      doPutStringProperty("MyString", "aaaaa");
      Assert.assertTrue(filter.match(message));

      doPutStringProperty("MyString", "abcdf");
      Assert.assertTrue(filter.match(message));

      // test match against the empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(filter.match(message));

      // next, tests with wildcard at the beginning of the string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '%bcdf'"));

      // test match with single character at beginning of string
      doPutStringProperty("MyString", "Xbcdf");
      Assert.assertTrue(filter.match(message));

      // match with multiple characters at beginning
      doPutStringProperty("MyString", "XXbcdf");
      Assert.assertTrue(filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      Assert.assertTrue(!filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      Assert.assertTrue(!filter.match(message));

      // test that the % wildcard matches the empty string
      doPutStringProperty("MyString", "bcdf");
      Assert.assertTrue(filter.match(message));

      // next, tests with wildcard at the end of the string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'abcd%'"));

      // test match of single character at end of string
      doPutStringProperty("MyString", "abcdf");
      Assert.assertTrue(filter.match(message));

      // test match of multiple characters at end of string
      doPutStringProperty("MyString", "abcdfgh");
      Assert.assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "abcXf");
      Assert.assertTrue(!filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in first character
      doPutStringProperty("MyString", "Xbcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      Assert.assertTrue(!filter.match(message));

      // test that the % wildcard matches the empty string
      doPutStringProperty("MyString", "abcd");
      Assert.assertTrue(filter.match(message));

      // next, tests with wildcard in the middle of the string
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'ab%df'"));

      // test match with single character in the middle of string
      doPutStringProperty("MyString", "abXdf");
      Assert.assertTrue(filter.match(message));

      // test match with multiple characters in the middle of string
      doPutStringProperty("MyString", "abXXXdf");
      Assert.assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "aXcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure in first character after wildcard
      doPutStringProperty("MyString", "abcXf");
      Assert.assertTrue(!filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      Assert.assertTrue(!filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      Assert.assertTrue(!filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      Assert.assertTrue(!filter.match(message));

      // test that the % wildcard matches the empty string
      doPutStringProperty("MyString", "abdf");
      Assert.assertTrue(filter.match(message));

   }

   @Test
   public void testStringLikePunctuation() throws Exception {
      // test proper handling of some punctuation characters.
      // non-trivial since the underlying implementation might
      // (and in fact currently does) use a general-purpose
      // RE library, which has a different notion of which
      // characters are wildcards

      // the particular tests here are motivated by the
      // wildcards of the current underlying RE engine,
      // GNU regexp.

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'a^$b'"));
      Assert.assertFalse(filter.match(message));

      doPutStringProperty("MyString", "a^$b");
      Assert.assertTrue(filter.match(message));

      // this one has a double backslash since backslash
      // is interpreted specially by Java
      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'a\\dc'"));
      doPutStringProperty("MyString", "a\\dc");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'a.c'"));
      doPutStringProperty("MyString", "abc");
      Assert.assertTrue(!filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '[abc]'"));
      doPutStringProperty("MyString", "[abc]");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '[^abc]'"));
      doPutStringProperty("MyString", "[^abc]");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '[a-c]'"));
      doPutStringProperty("MyString", "[a-c]");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '[:alpha]'"));
      doPutStringProperty("MyString", "[:alpha]");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc)'"));
      doPutStringProperty("MyString", "(abc)");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE 'a|bc'"));
      doPutStringProperty("MyString", "a|bc");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc)?'"));
      doPutStringProperty("MyString", "(abc)?");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc)*'"));
      doPutStringProperty("MyString", "(abc)*");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc)+'"));
      doPutStringProperty("MyString", "(abc)+");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc){3}'"));
      doPutStringProperty("MyString", "(abc){3}");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc){3,5}'"));
      doPutStringProperty("MyString", "(abc){3,5}");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(abc){3,}'"));
      doPutStringProperty("MyString", "(abc){3,}");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(?=abc)'"));
      doPutStringProperty("MyString", "(?=abc)");
      Assert.assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(new SimpleString("MyString LIKE '(?!abc)'"));
      doPutStringProperty("MyString", "(?!abc)");
      Assert.assertTrue(filter.match(message));
   }

   // TODO: re-implement this.
   //
   //   @Test
   //   public void testStringLongToken() throws Exception
   //   {
   //      String largeString;
   //
   //      {
   //         StringBuffer strBuffer = new StringBuffer();
   //         strBuffer.append('\'');
   //         for (int i = 0; i < 4800; i++)
   //         {
   //            strBuffer.append('a');
   //         }
   //         strBuffer.append('\'');
   //
   //         largeString = strBuffer.toString();
   //      }
   //
   //      FilterParser parse = new FilterParser();
   //      SimpleStringReader reader = new SimpleStringReader(new SimpleString(largeString));
   //      parse.ReInit(reader);
   //      // the server would fail at doing this when HORNETQ-545 wasn't solved
   //      parse.getNextToken();
   //   }

   private void doPutStringProperty(final String key, final String value) {
      message.putStringProperty(new SimpleString(key), new SimpleString(value));
   }

   private void testInvalidFilter(final String filterString) throws Exception {
      try {
         filter = FilterImpl.createFilter(filterString);
         Assert.fail("Should throw exception");
      } catch (ActiveMQInvalidFilterExpressionException ife) {
         //pass
      } catch (ActiveMQException e) {
         fail("Invalid exception type:" + e.getType());
      }
   }

   private void testInvalidFilter(final SimpleString filterString) throws Exception {
      try {
         filter = FilterImpl.createFilter(filterString);
         Assert.fail("Should throw exception");
      } catch (ActiveMQInvalidFilterExpressionException ife) {
         //pass
      } catch (ActiveMQException e) {
         fail("Invalid exception type:" + e.getType());
      }
   }

}
