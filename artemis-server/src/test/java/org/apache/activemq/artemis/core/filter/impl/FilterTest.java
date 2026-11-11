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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInvalidFilterExpressionException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SilentTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the compliance with the Apache Artemis Filter syntax.
 */
public class FilterTest extends SilentTestCase {

   private Filter filter;

   private Message message;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      message = new CoreMessage().initBuffer(1024).setMessageID(1);
   }

   @Test
   public void testNewlineMatch() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("fooprop LIKE '%1234%'"));

      message.putStringProperty(SimpleString.of("fooprop"), SimpleString.of("hello1234\n"));

      assertTrue(filter.match(message));
   }

   @Test
   public void testFilterForgets() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("color = 'RED'"));

      message.putStringProperty(SimpleString.of("color"), SimpleString.of("RED"));
      assertTrue(filter.match(message));
      message = new CoreMessage();
      assertFalse(filter.match(message));
   }

   @Test
   public void testInvalidString() throws Exception {
      testInvalidFilter("color = 'red");
      testInvalidFilter(SimpleString.of("color = 'red"));

      testInvalidFilter("3");
      testInvalidFilter(SimpleString.of("3"));
   }

   @Test
   public void testNullFilter() throws Exception {
      assertNull(FilterImpl.createFilter((String) null));
      assertNull(FilterImpl.createFilter(""));
      assertNull(FilterImpl.createFilter((SimpleString) null));
      assertNull(FilterImpl.createFilter(SimpleString.of("")));
   }

   @Test
   public void testAMQDurable() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("AMQDurable='DURABLE'"));

      message.setDurable(true);

      assertTrue(filter.match(message));

      message.setDurable(false);

      assertFalse(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("AMQDurable='NON_DURABLE'"));

      message = new CoreMessage();
      message.setDurable(true);

      assertFalse(filter.match(message));

      message.setDurable(false);

      assertTrue(filter.match(message));
   }

   @Test
   public void testAMQSize() throws Exception {
      message.setAddress(RandomUtil.randomUUIDSimpleString());

      int encodeSize = message.getEncodeSize();

      Filter moreThanSmall = FilterImpl.createFilter(SimpleString.of("AMQSize > " + (encodeSize - 1)));
      Filter lessThanLarge = FilterImpl.createFilter(SimpleString.of("AMQSize < " + (encodeSize + 1)));

      Filter lessThanSmall = FilterImpl.createFilter(SimpleString.of("AMQSize < " + encodeSize));
      Filter moreThanLarge = FilterImpl.createFilter(SimpleString.of("AMQSize > " + encodeSize));

      assertTrue(moreThanSmall.match(message));
      assertTrue(lessThanLarge.match(message));

      assertFalse(lessThanSmall.match(message));
      assertFalse(moreThanLarge.match(message));
   }

   @Test
   public void testAMQPriority() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("AMQPriority=3"));

      for (int i = 0; i < 10; i++) {
         message.setPriority((byte) i);

         if (i == 3) {
            assertTrue(filter.match(message));
         } else {
            assertFalse(filter.match(message));
         }
      }
   }

   @Test
   public void testAMQTimestamp() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("AMQTimestamp=12345678"));

      message.setTimestamp(87654321);

      assertFalse(filter.match(message));

      message.setTimestamp(12345678);

      assertTrue(filter.match(message));
   }

   // AMQUserID, i.e. 'user-provided message ID', or what is typically thought of as the MessageID.
   @Test
   public void testAMQUserID() throws Exception {
      // This 'MessageID' is the Artemis 'internal numeric id' assigned to each message
      message = new CoreMessage().initBuffer(1024).setMessageID(1);

      UUID randomUUID = UUID.randomUUID();
      org.apache.activemq.artemis.utils.UUID artemisUUID = new org.apache.activemq.artemis.utils.UUID(randomUUID);

      filter = FilterImpl.createFilter(SimpleString.of("AMQUserID='ID:" + randomUUID + "'"));
      Filter nullFilter = FilterImpl.createFilter(SimpleString.of("AMQUserID IS NULL"));
      Filter notNullFilter = FilterImpl.createFilter(SimpleString.of("AMQUserID IS NOT NULL"));

      assertFalse(filter.match(message));
      assertTrue(nullFilter.match(message));
      assertFalse(notNullFilter.match(message));

      // This 'UserID' is not about user names etc, but what is typically considered as a MessageID.
      message.setUserID(artemisUUID);

      assertTrue(filter.match(message));
      assertFalse(nullFilter.match(message));
      assertTrue(notNullFilter.match(message));
   }

   @Test
   public void testBooleanTrue() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("MyBoolean=true"));

      testBoolean("MyBoolean", true);
   }

   @Test
   public void testIdentifier() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("MyBoolean"));

      testBoolean("MyBoolean", true);
   }

   @Test
   public void testDifferentNullString() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("prop <> 'foo'"));
      assertFalse(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("prop <> 'foo' OR prop IS NULL"));
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("NOT (prop = 'foo')"));
      assertFalse(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("NOT (prop = 'foo') OR prop IS NULL"));
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("prop <> 'foo'"));
      doPutStringProperty("prop", "bar");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("prop <> 'foo'"));
      doPutStringProperty("prop", "foo");
      assertFalse(filter.match(message));
   }

   @Test
   public void testBooleanFalse() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("MyBoolean=false"));
      testBoolean("MyBoolean", false);
   }

   private void testBoolean(final String name, final boolean flag) throws Exception {
      message.putBooleanProperty(SimpleString.of(name), flag);
      assertTrue(filter.match(message));

      message.putBooleanProperty(SimpleString.of(name), !flag);
      assertFalse(filter.match(message));
   }

   @Test
   public void testStringEquals() throws Exception {
      // First, simple test of string equality and inequality
      filter = FilterImpl.createFilter(SimpleString.of("MyString='astring'"));

      doPutStringProperty("MyString", "astring");
      assertTrue(filter.match(message));

      doPutStringProperty("MyString", "NOTastring");
      assertFalse(filter.match(message));

      // test empty string
      filter = FilterImpl.createFilter(SimpleString.of("MyString=''"));

      doPutStringProperty("MyString", "");
      assertTrue(filter.match(message), "test 1");

      doPutStringProperty("MyString", "NOTastring");
      assertFalse(filter.match(message), "test 2");

      // test literal apostrophes (which are escaped using two apostrophes
      // in selectors)
      filter = FilterImpl.createFilter(SimpleString.of("MyString='test JBoss''s filter'"));

      // note: apostrophes are not escaped in string properties
      doPutStringProperty("MyString", "test JBoss's filter");
      // this test fails -- bug 530120
      // assertTrue("test 3", filter.match(message));

      doPutStringProperty("MyString", "NOTastring");
      assertFalse(filter.match(message), "test 4");

   }

   @Test
   public void testNOT_INWithNullProperty() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("myNullProp NOT IN ('foo','jms','test')"));

      assertFalse(filter.match(message));

      message.putStringProperty("myNullProp", "JMS");
      assertTrue(filter.match(message));
   }

   @Test
   public void testNOT_LIKEWithNullProperty() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("myNullProp NOT LIKE '1_3'"));

      assertFalse(filter.match(message));

      message.putStringProperty("myNullProp", "JMS");
      assertTrue(filter.match(message));
   }

   @Test
   public void testIS_NOT_NULLWithNullProperty() throws Exception {
      filter = FilterImpl.createFilter(SimpleString.of("myNullProp IS NOT NULL"));

      assertFalse(filter.match(message));

      message.putStringProperty("myNullProp", "JMS");
      assertTrue(filter.match(message));
   }

   @Test
   public void testStringLike() throws Exception {
      // test LIKE operator with no wildcards
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'astring'"));
      assertFalse(filter.match(message));

      // test where LIKE operand matches
      doPutStringProperty("MyString", "astring");
      assertTrue(filter.match(message));

      // test one character string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'a'"));
      doPutStringProperty("MyString", "a");
      assertTrue(filter.match(message));

      // test empty string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE ''"));
      doPutStringProperty("MyString", "");
      assertTrue(filter.match(message));

      // tests where operand does not match
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'astring'"));

      // test with extra characters at beginning
      doPutStringProperty("MyString", "NOTastring");
      assertFalse(filter.match(message));

      // test with extra characters at end
      doPutStringProperty("MyString", "astringNOT");
      assertFalse(filter.match(message));

      // test with extra characters in the middle
      doPutStringProperty("MyString", "astNOTring");
      assertFalse(filter.match(message));

      // test where operand is entirely different
      doPutStringProperty("MyString", "totally different");
      assertFalse(filter.match(message));

      // test case sensitivity
      doPutStringProperty("MyString", "ASTRING");
      assertFalse(filter.match(message));

      // test empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // test lower-case 'like' operator?
   }

   @Test
   public void testStringLikeUnderbarWildcard() throws Exception {
      // test LIKE operator with the _ wildcard, which
      // matches any single character

      // first, some tests with the wildcard by itself
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '_'"));
      assertFalse(filter.match(message));

      // test match against single character
      doPutStringProperty("MyString", "a");
      assertTrue(filter.match(message));

      // test match failure against multiple characters
      doPutStringProperty("MyString", "aaaaa");
      assertFalse(filter.match(message));

      // test match failure against the empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // next, tests with wildcard at the beginning of the string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '_bcdf'"));

      // test match at beginning of string
      doPutStringProperty("MyString", "abcdf");
      assertTrue(filter.match(message));

      // match failure in first character after wildcard
      doPutStringProperty("MyString", "aXcdf");
      assertFalse(filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      assertFalse(filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      assertFalse(filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      assertFalse(filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      assertFalse(filter.match(message));

      // test that the _ wildcard does not match the 'empty' character
      doPutStringProperty("MyString", "bcdf");
      assertFalse(filter.match(message));

      // next, tests with wildcard at the end of the string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'abcd_'"));

      // test match at end of string
      doPutStringProperty("MyString", "abcdf");
      assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "abcXf");
      assertFalse(filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      assertFalse(filter.match(message));

      // match failure in first character
      doPutStringProperty("MyString", "Xbcdf");
      assertFalse(filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      assertFalse(filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      assertFalse(filter.match(message));

      // test that the _ wildcard does not match the 'empty' character
      doPutStringProperty("MyString", "abcd");
      assertFalse(filter.match(message));

      // test match in middle of string

      // next, tests with wildcard in the middle of the string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'ab_df'"));

      // test match in the middle of string
      doPutStringProperty("MyString", "abcdf");
      assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "aXcdf");
      assertFalse(filter.match(message));

      // match failure in first character after wildcard
      doPutStringProperty("MyString", "abcXf");
      assertFalse(filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      assertFalse(filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      assertFalse(filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      assertFalse(filter.match(message));

      // test that the _ wildcard does not match the 'empty' character
      doPutStringProperty("MyString", "abdf");
      assertFalse(filter.match(message));

      // test match failures
   }

   @Test
   public void testNotLikeExpression() throws Exception {
      // Should evaluate to false when the property MyString is not set
      filter = FilterImpl.createFilter(SimpleString.of("NOT (MyString LIKE '%')"));

      assertFalse(filter.match(message));
   }

   @Test
   public void testStringLikePercentWildcard() throws Exception {
      // test LIKE operator with the % wildcard, which
      // matches any sequence of characters
      // note many of the tests are similar to those for _

      // first, some tests with the wildcard by itself
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '%'"));
      assertFalse(filter.match(message));

      // test match against single character
      doPutStringProperty("MyString", "a");
      assertTrue(filter.match(message));

      // test match against multiple characters
      doPutStringProperty("MyString", "aaaaa");
      assertTrue(filter.match(message));

      doPutStringProperty("MyString", "abcdf");
      assertTrue(filter.match(message));

      // test match against the empty string
      doPutStringProperty("MyString", "");
      assertTrue(filter.match(message));

      // next, tests with wildcard at the beginning of the string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '%bcdf'"));

      // test match with single character at beginning of string
      doPutStringProperty("MyString", "Xbcdf");
      assertTrue(filter.match(message));

      // match with multiple characters at beginning
      doPutStringProperty("MyString", "XXbcdf");
      assertTrue(filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      assertFalse(filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      assertFalse(filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      assertFalse(filter.match(message));

      // test that the % wildcard matches the empty string
      doPutStringProperty("MyString", "bcdf");
      assertTrue(filter.match(message));

      // next, tests with wildcard at the end of the string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'abcd%'"));

      // test match of single character at end of string
      doPutStringProperty("MyString", "abcdf");
      assertTrue(filter.match(message));

      // test match of multiple characters at end of string
      doPutStringProperty("MyString", "abcdfgh");
      assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "abcXf");
      assertFalse(filter.match(message));

      // match failure in middle character
      doPutStringProperty("MyString", "abXdf");
      assertFalse(filter.match(message));

      // match failure in first character
      doPutStringProperty("MyString", "Xbcdf");
      assertFalse(filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      assertFalse(filter.match(message));

      // test that the % wildcard matches the empty string
      doPutStringProperty("MyString", "abcd");
      assertTrue(filter.match(message));

      // next, tests with wildcard in the middle of the string
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'ab%df'"));

      // test match with single character in the middle of string
      doPutStringProperty("MyString", "abXdf");
      assertTrue(filter.match(message));

      // test match with multiple characters in the middle of string
      doPutStringProperty("MyString", "abXXXdf");
      assertTrue(filter.match(message));

      // match failure in first character before wildcard
      doPutStringProperty("MyString", "aXcdf");
      assertFalse(filter.match(message));

      // match failure in first character after wildcard
      doPutStringProperty("MyString", "abcXf");
      assertFalse(filter.match(message));

      // match failure in last character
      doPutStringProperty("MyString", "abcdX");
      assertFalse(filter.match(message));

      // match failure with empty string
      doPutStringProperty("MyString", "");
      assertFalse(filter.match(message));

      // match failure due to extra characters at beginning
      doPutStringProperty("MyString", "XXXabcdf");
      assertFalse(filter.match(message));

      // match failure due to extra characters at the end
      doPutStringProperty("MyString", "abcdfXXX");
      assertFalse(filter.match(message));

      // test that the % wildcard matches the empty string
      doPutStringProperty("MyString", "abdf");
      assertTrue(filter.match(message));

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

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'a^$b'"));
      assertFalse(filter.match(message));

      doPutStringProperty("MyString", "a^$b");
      assertTrue(filter.match(message));

      // this one has a double backslash since backslash
      // is interpreted specially by Java
      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'a\\dc'"));
      doPutStringProperty("MyString", "a\\dc");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'a.c'"));
      doPutStringProperty("MyString", "abc");
      assertFalse(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '[abc]'"));
      doPutStringProperty("MyString", "[abc]");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '[^abc]'"));
      doPutStringProperty("MyString", "[^abc]");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '[a-c]'"));
      doPutStringProperty("MyString", "[a-c]");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '[:alpha]'"));
      doPutStringProperty("MyString", "[:alpha]");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc)'"));
      doPutStringProperty("MyString", "(abc)");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE 'a|bc'"));
      doPutStringProperty("MyString", "a|bc");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc)?'"));
      doPutStringProperty("MyString", "(abc)?");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc)*'"));
      doPutStringProperty("MyString", "(abc)*");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc)+'"));
      doPutStringProperty("MyString", "(abc)+");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc){3}'"));
      doPutStringProperty("MyString", "(abc){3}");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc){3,5}'"));
      doPutStringProperty("MyString", "(abc){3,5}");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(abc){3,}'"));
      doPutStringProperty("MyString", "(abc){3,}");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(?=abc)'"));
      doPutStringProperty("MyString", "(?=abc)");
      assertTrue(filter.match(message));

      filter = FilterImpl.createFilter(SimpleString.of("MyString LIKE '(?!abc)'"));
      doPutStringProperty("MyString", "(?!abc)");
      assertTrue(filter.match(message));
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
   //      SimpleStringReader reader = new SimpleStringReader(SimpleString.of(largeString));
   //      parse.ReInit(reader);
   //      parse.getNextToken();
   //   }

   private void doPutStringProperty(final String key, final String value) {
      message.putStringProperty(SimpleString.of(key), SimpleString.of(value));
   }

   private void testInvalidFilter(final String filterString) throws Exception {
      try {
         filter = FilterImpl.createFilter(filterString);
         fail("Should throw exception");
      } catch (ActiveMQInvalidFilterExpressionException ife) {
         //pass
      } catch (ActiveMQException e) {
         fail("Invalid exception type:" + e.getType());
      }
   }

   private void testInvalidFilter(final SimpleString filterString) throws Exception {
      try {
         filter = FilterImpl.createFilter(filterString);
         fail("Should throw exception");
      } catch (ActiveMQInvalidFilterExpressionException ife) {
         //pass
      } catch (ActiveMQException e) {
         fail("Invalid exception type:" + e.getType());
      }
   }

}
