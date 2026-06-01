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

package org.apache.artemis.jsonschema.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public class PropertyMetadataMergeTest {

   @Test
   public void xsdDescriptionWinsOverJavadoc() {
      PropertyMetadata base = new PropertyMetadata();
      base.setDescription("javadoc description");

      PropertyMetadata xsd = new PropertyMetadata();
      xsd.setDescription("xsd description");

      base.merge(xsd, PropertySource.XSD, PropertySource.JAVADOC);

      assertEquals("xsd description", base.getDescription());
   }

   @Test
   public void javadocDescriptionFillsGap() {
      PropertyMetadata base = new PropertyMetadata();

      PropertyMetadata javadoc = new PropertyMetadata();
      javadoc.setDescription("javadoc description");

      base.merge(javadoc, PropertySource.JAVADOC, PropertySource.REFLECTION);

      assertEquals("javadoc description", base.getDescription());
   }

   @Test
   public void reflectionTypeWinsOverXsd() {
      PropertyMetadata base = new PropertyMetadata();
      base.setType(new SchemaType(SchemaType.Kind.STRING));

      PropertyMetadata reflection = new PropertyMetadata();
      reflection.setType(new SchemaType(SchemaType.Kind.INTEGER));

      base.merge(reflection, PropertySource.REFLECTION, PropertySource.XSD);

      assertEquals(new SchemaType(SchemaType.Kind.INTEGER), base.getType());
   }

   @Test
   public void firstNonNullDefaultWins() {
      PropertyMetadata base = new PropertyMetadata();
      base.setDefaultValue("first-default");

      PropertyMetadata other = new PropertyMetadata();
      other.setDefaultValue("second-default");

      base.merge(other, PropertySource.XSD, PropertySource.REFLECTION);

      assertEquals("first-default", base.getDefaultValue());
   }

   @Test
   public void firstNonNullEnumWinsNoOverride() {
      PropertyMetadata base = new PropertyMetadata();
      base.setEnumValues(List.of("A", "B"));

      PropertyMetadata other = new PropertyMetadata();
      other.setEnumValues(List.of("X", "Y", "Z"));

      base.merge(other, PropertySource.XSD, PropertySource.REFLECTION);

      assertEquals(List.of("A", "B"), base.getEnumValues());
   }

   @Test
   public void firstNonNullMinMaxWinsNoOverride() {
      PropertyMetadata base = new PropertyMetadata();
      base.setMinimum(0);
      base.setMaximum(100);

      PropertyMetadata other = new PropertyMetadata();
      other.setMinimum(10);
      other.setMaximum(200);

      base.merge(other, PropertySource.XSD, PropertySource.REFLECTION);

      assertEquals(0, base.getMinimum());
      assertEquals(100, base.getMaximum());
   }

   @Test
   public void nullValuesDoNotOverwriteExisting() {
      PropertyMetadata base = new PropertyMetadata();
      base.setType(new SchemaType(SchemaType.Kind.BOOLEAN));
      base.setDescription("existing");
      base.setDefaultValue(true);
      base.setAccess("RW");

      PropertyMetadata empty = new PropertyMetadata();

      base.merge(empty, PropertySource.XSD, PropertySource.REFLECTION);

      assertEquals(new SchemaType(SchemaType.Kind.BOOLEAN), base.getType());
      assertEquals("existing", base.getDescription());
      assertEquals(true, base.getDefaultValue());
      assertEquals("RW", base.getAccess());
   }

   @Test
   public void nullBaseFieldsGetFilledByMerge() {
      PropertyMetadata base = new PropertyMetadata();

      PropertyMetadata other = new PropertyMetadata();
      other.setType(new SchemaType(SchemaType.Kind.INTEGER));
      other.setMinimum(0);
      other.setMaximum(65535);
      other.setEnumValues(List.of("A"));

      base.merge(other, PropertySource.JAVADOC, PropertySource.REFLECTION);

      assertEquals(new SchemaType(SchemaType.Kind.INTEGER), base.getType());
      assertEquals(0, base.getMinimum());
      assertEquals(65535, base.getMaximum());
      assertEquals(List.of("A"), base.getEnumValues());
   }
}
