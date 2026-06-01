package org.apache.artemis.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.artemis.jsonschema.ir.IRBuilder;
import org.apache.artemis.jsonschema.ir.SchemaEmitter;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.junit.jupiter.api.Test;

public class OneOfValidationTest {
   @Test
   public void validateOneOfPattern() throws Exception {
      IRBuilder irGenerator = new IRBuilder();
      irGenerator.generateIR();
      SchemaIR ir = irGenerator.getIR();
      SchemaEmitter emitter = new SchemaEmitter(ir);
      Map<String, Object> schema = emitter.emitSchema();

      ObjectMapper mapper = new ObjectMapper();
      mapper.enable(SerializationFeature.INDENT_OUTPUT);

      Files.createDirectories(Paths.get("/tmp"));
      mapper.writeValue(Paths.get("/tmp/ir-schema-test.json").toFile(), schema);

      long size = mapper.writeValueAsString(schema).length();
      System.out.println("✓ Schema written to /tmp/ir-schema-test.json");
      System.out.println("✓ Size: " + size + " bytes (" + (size / 1024) + " KB)");
      System.out.println("✓ Size under 1MB: " + (size < 1_000_000));
      System.out.println("✓ $defs count: " + ((Map<?, ?>) schema.get("$defs")).size());

      // Verify oneOf is present for polymorphic types
      Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
      Map<?, ?> defs = (Map<?, ?>) schema.get("$defs");
      Map<?, ?> amqpConn = (Map<?, ?>) properties.get("AMQPConnection");
      Map<?, ?> amqpConnAddProps = (Map<?, ?>) amqpConn.get("additionalProperties");

      // Follow $ref if the class was extracted to $defs
      Map<?, ?> amqpConnProps;
      if (amqpConnAddProps.containsKey("$ref")) {
         String ref = (String) amqpConnAddProps.get("$ref");
         String defName = ref.substring(ref.lastIndexOf('/') + 1);
         Map<?, ?> defNode = (Map<?, ?>) defs.get(defName);
         amqpConnProps = (Map<?, ?>) defNode.get("properties");
      } else {
         amqpConnProps = (Map<?, ?>) amqpConnAddProps.get("properties");
      }

      Map<?, ?> connectionElements = (Map<?, ?>) amqpConnProps.get("connectionElements");
      Map<?, ?> connectionElementsAddProps =
            (Map<?, ?>) connectionElements.get("additionalProperties");

      if (connectionElementsAddProps.containsKey("oneOf")) {
         System.out.println("✓ oneOf pattern found for connectionElements");
      } else {
         System.out.println("✗ oneOf pattern NOT found for connectionElements");
      }
   }
}
