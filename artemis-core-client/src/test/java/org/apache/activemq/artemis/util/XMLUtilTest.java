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
package org.apache.activemq.artemis.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.utils.SilentTestCase;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLUtilTest extends SilentTestCase {


   @Test
   public void testGetTextContext_1() throws Exception {
      String document = "<blah>foo</blah>";

      Element e = XMLUtil.stringToElement(document);

      assertEquals("foo", XMLUtil.getTextContent(e));
   }

   @Test
   public void testGetTextContext_2() throws Exception {
      String document = "<blah someattribute=\"somevalue\">foo</blah>";

      Element e = XMLUtil.stringToElement(document);

      assertEquals("foo", XMLUtil.getTextContent(e));
   }

   @Test
   public void testGetTextContext_3() throws Exception {
      String document = "<blah someattribute=\"somevalue\"><a/></blah>";

      Element e = XMLUtil.stringToElement(document);

      String s = XMLUtil.getTextContent(e);

      Element subelement = XMLUtil.stringToElement(s);

      assertEquals("a", subelement.getNodeName());
   }

   @Test
   public void testGetTextContext_4() throws Exception {
      String document = "<blah someattribute=\"somevalue\"><a></a></blah>";

      Element e = XMLUtil.stringToElement(document);

      String s = XMLUtil.getTextContent(e);

      Element subelement = XMLUtil.stringToElement(s);

      assertEquals("a", subelement.getNodeName());
   }

   @Test
   public void testGetTextContext_5() throws Exception {
      String document = "<blah someattribute=\"somevalue\"><a><b/></a></blah>";

      Element e = XMLUtil.stringToElement(document);

      String s = XMLUtil.getTextContent(e);

      Element subelement = XMLUtil.stringToElement(s);

      assertEquals("a", subelement.getNodeName());
      NodeList nl = subelement.getChildNodes();

      // try to find <b>
      boolean found = false;
      for (int i = 0; i < nl.getLength(); i++) {
         Node n = nl.item(i);
         if ("b".equals(n.getNodeName())) {
            found = true;
         }
      }
      assertTrue(found);
   }

   @Test
   public void testEquivalent_1() throws Exception {
      String s = "<a/>";
      String s2 = "<a/>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   @Test
   public void testEquivalent_2() throws Exception {
      String s = "<a></a>";
      String s2 = "<a/>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   @Test
   public void testEquivalent_3() throws Exception {
      String s = "<a attr1=\"val1\" attr2=\"val2\"/>";
      String s2 = "<a attr2=\"val2\"/>";

      try {
         XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
         fail("this should throw exception");
      } catch (IllegalArgumentException e) {
         // expected
      }
   }

   @Test
   public void testEquivalent_4() throws Exception {
      String s = "<a attr1=\"val1\" attr2=\"val2\"/>";
      String s2 = "<a attr2=\"val2\" attr1=\"val1\"/>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   @Test
   public void testEquivalent_5() throws Exception {
      String s = "<a><b/></a>";
      String s2 = "<a><b/></a>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   @Test
   public void testEquivalent_6() throws Exception {
      String s = "<enclosing><a attr1=\"val1\" attr2=\"val2\"/></enclosing>";
      String s2 = "<enclosing><a attr2=\"val2\" attr1=\"val1\"/></enclosing>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   @Test
   public void testEquivalent_7() throws Exception {
      String s = "<a><b/><c/></a>";
      String s2 = "<a><c/><b/></a>";

      try {
         XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
         fail("this should throw exception");
      } catch (IllegalArgumentException e) {
         // OK
         e.printStackTrace();
      }
   }

   @Test
   public void testEquivalent_8() throws Exception {
      String s = "<a><!-- some comment --><b/><!--some other comment --><c/><!-- blah --></a>";
      String s2 = "<a><b/><!--blah blah--><c/></a>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   @Test
   public void testElementToString_1() throws Exception {
      String s = "<a b=\"something\">somethingelse</a>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   @Test
   public void testElementToString_2() throws Exception {
      String s = "<a b=\"something\"></a>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   @Test
   public void testElementToString_3() throws Exception {
      String s = "<a b=\"something\"/>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   @Test
   public void testElementToString_4() throws Exception {
      String s = "<a><![CDATA[somedata]]></a>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   @Test
   public void testReplaceSystemProperties() {
      String before = "<configuration>\n" + "   <test name=\"${sysprop1}\">content1</test>\n" + "   <test name=\"test2\">content2</test>\n" + "   <test name=\"test3\">content3</test>\n" + "   <test name=\"test4\">${sysprop2}</test>\n" + "   <test name=\"test5\">content5</test>\n" + "   <test name=\"test6\">content6</test>\n" + "</configuration>";
      String after = "<configuration>\n" + "   <test name=\"test1\">content1</test>\n" + "   <test name=\"test2\">content2</test>\n" + "   <test name=\"test3\">content3</test>\n" + "   <test name=\"test4\">content4</test>\n" + "   <test name=\"test5\">content5</test>\n" + "   <test name=\"test6\">content6</test>\n" + "</configuration>";
      System.setProperty("sysprop1", "test1");
      System.setProperty("sysprop2", "content4");
      String replaced = XMLUtil.replaceSystemPropsInString(before);
      assertEquals(after, replaced);
   }

   @Test
   public void testReplaceSystemPropertiesWithUnclosedPropertyReferenceInXML() {
      String before = "<configuration>\n" + "   <test name=\"${sysprop1}\">content1</test>\n" + "   <test name=\"test2\">content2</test>\n" + "   <test name=\"test3\">content3</test>\n" + "   <test name=\"test4\">${sysprop2</test>\n" + "   <test name=\"test5\">content5</test>\n" + "   <test name=\"test6\">content6</test>\n" + "</configuration>";
      String after = "<configuration>\n" + "   <test name=\"test1\">content1</test>\n" + "   <test name=\"test2\">content2</test>\n" + "   <test name=\"test3\">content3</test>\n" + "   <test name=\"test4\">${sysprop2</test>\n" + "   <test name=\"test5\">content5</test>\n" + "   <test name=\"test6\">content6</test>\n" + "</configuration>";
      System.setProperty("sysprop1", "test1");
      System.setProperty("sysprop2", "content4");
      String replaced = XMLUtil.replaceSystemPropsInString(before);
      assertEquals(after, replaced);
   }

   @Test
   public void testReplaceSystemPropertiesWithMiscCurlyBracesInXML() {
      String before = "<configuration>\n" + "   <test name=\"${sysprop1}\">content1{ }</test>\n" + "   <test name=\"test2\">content2 {</test>\n" + "   <test name=\"test3\">content3 }</test>\n" + "   <test name=\"test4\">${sysprop2}</test>\n" + "   <test name=\"test5\">content5{ }</test>\n" + "   <test name=\"test6\">content6 }</test>\n" + "</configuration>";
      String after = "<configuration>\n" + "   <test name=\"test1\">content1{ }</test>\n" + "   <test name=\"test2\">content2 {</test>\n" + "   <test name=\"test3\">content3 }</test>\n" + "   <test name=\"test4\">content4</test>\n" + "   <test name=\"test5\">content5{ }</test>\n" + "   <test name=\"test6\">content6 }</test>\n" + "</configuration>";
      System.setProperty("sysprop1", "test1");
      System.setProperty("sysprop2", "content4");
      String replaced = XMLUtil.replaceSystemPropsInString(before);
      assertEquals(after, replaced);
   }

   @Test
   public void testStripCDATA() throws Exception {
      String xml = "<![CDATA[somedata]]>";
      String stripped = XMLUtil.stripCDATA(xml);

      assertEquals("somedata", stripped);
   }

}
