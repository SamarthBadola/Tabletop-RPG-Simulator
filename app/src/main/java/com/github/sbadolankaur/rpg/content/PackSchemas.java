package com.github.sbadolankaur.rpg.content;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.path.PathType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import tools.jackson.databind.JsonNode;

final class PackSchemas {
  private static final String BASE = "https://schemas.tabletop-rpg.invalid/content/v1/";
  private static final String VOCABULARY = BASE + "vocabulary.json";
  private final SchemaRegistry registry = registry(PackSchemas::localSchema);
  private final SchemaRegistry structure = registry(PackSchemas::structuralSchema);

  private static SchemaRegistry registry(java.util.function.Function<String, String> resources) {
    return SchemaRegistry.withDialect(
        Dialects.getDraft202012(),
        builder ->
            builder
                .schemaLoader(loader -> loader.fetchRemoteResources(false))
                .schemaRegistryConfig(
                    SchemaRegistryConfig.builder().pathType(PathType.JSON_POINTER).build())
                .schemas(resources));
  }

  private static String structuralSchema(String id) {
    String source = localSchema(id);
    if (!id.equals(VOCABULARY) || source == null) return source;
    var shape = (tools.jackson.databind.node.ObjectNode) PackJson.parse(source);
    var definitions = (tools.jackson.databind.node.ObjectNode) shape.path("$defs");
    definitions.propertyNames().stream()
        .filter(name -> definitions.path(name).has("enum"))
        .toList()
        .forEach(name -> definitions.putObject(name).put("type", "string"));
    return PackJson.canonical(shape);
  }

  private static String localSchema(String id) {
    if (!id.startsWith(BASE)) return null;
    String name = id.substring(BASE.length());
    if (!name.matches("[a-zA-Z0-9.-]+[.]json")) return null;
    try (var resource = PackSchemas.class.getResourceAsStream("/content-schema/" + name)) {
      return resource == null ? null : new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
      throw new IllegalStateException("Could not read local content schema", error);
    }
  }

  private Schema schema(String file) {
    return registry.getSchema(SchemaLocation.of(BASE + file));
  }

  List<Shape> definitionShapes(ContentKind kind) {
    String filename = kind.id() + ".schema.json";
    String location = BASE + filename;
    var shapes = new java.util.ArrayList<Shape>();
    for (JsonNode part : schema(filename).getSchemaNode().path("allOf")) {
      shapes.add(dereference(new Shape(location, part)));
    }
    return List.copyOf(shapes);
  }

  Shape dereference(Shape shape) {
    if (!shape.node().path("$ref").isString()) return shape;
    String target =
        SchemaLocation.resolve(
            SchemaLocation.of(shape.location()), shape.node().get("$ref").asString());
    return new Shape(target, registry.getSchema(SchemaLocation.of(target)).getSchemaNode());
  }

  record Shape(String location, JsonNode node) {}

  void checkManifest(JsonNode node, List<ContentFinding> findings) {
    check(schema("manifest.schema.json"), "manifest.json", "CS-PKG-009", node, findings);
  }

  void checkDefinition(
      PackLayout.Draft draft,
      List<ContentFinding> findings,
      List<ContentFinding> vocabularyFindings) {
    var shape = structure.getSchema(SchemaLocation.of(BASE + draft.kind().id() + ".schema.json"));
    check(shape, draft.file(), "CS-ENV-001", draft.body(), findings);
    if (!findings.isEmpty()) return;
    for (var field : annotated(draft, "x-vocabulary")) {
      String name = field.node().asString();
      if (!ContentVocabulary.knows(field.value(), name)) {
        vocabularyFindings.add(
            new ContentFinding(
                draft.file(),
                field.pointer(),
                "CS-VOC-004",
                "Unknown vocabulary name: " + ContentVocabulary.safeName(name)));
      }
    }
  }

  void checkVocabulary(
      String category, String file, String pointer, JsonNode node, List<ContentFinding> findings) {
    check(
        schema("vocabulary.json#/$defs/" + category), file, "CS-VOC-008", node, findings, pointer);
  }

  List<Annotation> annotated(PackLayout.Draft draft, String attribute) {
    var found = new LinkedHashSet<Annotation>();
    String file = BASE + draft.kind().id() + ".schema.json";
    annotate(
        schema(file.substring(BASE.length())).getSchemaNode(),
        file,
        draft.body(),
        "",
        attribute,
        found);
    return List.copyOf(found);
  }

  private void annotate(
      JsonNode shape,
      String location,
      JsonNode data,
      String pointer,
      String attribute,
      LinkedHashSet<Annotation> found) {
    if (shape.path(attribute).isString() && data.isString()) {
      found.add(new Annotation(pointer, shape.get(attribute).asString(), data));
    }
    if (shape.path("$ref").isString()) {
      var target =
          SchemaLocation.resolve(SchemaLocation.of(location), shape.get("$ref").asString());
      String prefix = VOCABULARY + "#/$defs/";
      if (attribute.equals("x-vocabulary") && data.isString() && target.startsWith(prefix)) {
        found.add(new Annotation(pointer, target.substring(prefix.length()), data));
      }
      annotate(
          registry.getSchema(SchemaLocation.of(target)).getSchemaNode(),
          target,
          data,
          pointer,
          attribute,
          found);
    }
    for (String combinator : List.of("allOf", "anyOf", "oneOf")) {
      for (JsonNode alternative : shape.path(combinator)) {
        annotate(alternative, location, data, pointer, attribute, found);
      }
    }
    if (data.isObject()) {
      if (shape.path("propertyNames").isObject()) {
        for (String name : data.propertyNames()) {
          annotate(
              shape.get("propertyNames"),
              location,
              tools.jackson.databind.node.StringNode.valueOf(name),
              pointer + "/" + name.replace("~", "~0").replace("/", "~1"),
              attribute,
              found);
        }
      }
      for (var item : shape.path("properties").properties()) {
        JsonNode field = data.get(item.getKey());
        if (field != null) {
          annotate(
              item.getValue(), location, field, pointer + "/" + item.getKey(), attribute, found);
        }
      }
    }
    if (data.isArray() && shape.path("items").isObject()) {
      for (int i = 0; i < data.size(); i++) {
        annotate(shape.get("items"), location, data.get(i), pointer + "/" + i, attribute, found);
      }
    }
  }

  record Annotation(String pointer, String value, JsonNode node) {}

  private static void check(
      Schema schema, String file, String rule, JsonNode node, List<ContentFinding> findings) {
    check(schema, file, rule, node, findings, "");
  }

  private static void check(
      Schema schema,
      String file,
      String rule,
      JsonNode node,
      List<ContentFinding> findings,
      String basePointer) {
    schema.validate(node).forEach(error -> findings.add(finding(file, basePointer, rule, error)));
  }

  private static ContentFinding finding(
      String file, String basePointer, String rule, com.networknt.schema.Error error) {
    String pointer = error.getInstanceLocation().toString();
    String name = error.getProperty();
    if (name != null && !name.isEmpty()) {
      pointer += "/" + name.replace("~", "~0").replace("/", "~1");
    }
    return new ContentFinding(
        file,
        basePointer + pointer,
        rule,
        "Does not satisfy the schema keyword " + error.getKeyword());
  }
}
