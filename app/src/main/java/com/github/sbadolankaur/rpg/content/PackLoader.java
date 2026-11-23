package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.Definition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public final class PackLoader {
  private final PackSchemas schemas = new PackSchemas();

  public ContentPack loadFolder(Path folder, List<ContentPack> available) throws IOException {
    return load(PackInput.readFolder(folder), false, available);
  }

  ContentPack load(Map<String, byte[]> files, boolean seeded, List<ContentPack> available) {
    // Stages 1 and 2 bound input and parse files. Later stages rely on those invariants.
    var input = PackInput.parse(files);
    var layout = PackLayout.check(input, schemas, seeded);
    var errors = new ArrayList<ContentFinding>();
    var warnings = new ArrayList<ContentFinding>();
    var dependencies = PackReferences.dependencies(layout.manifest(), available, errors);
    ContentInvalid.rejectIfPresent(errors);

    var vocabularyErrors = new ArrayList<ContentFinding>();
    for (var draft : layout.definitions()) {
      schemas.checkDefinition(draft, errors, vocabularyErrors);
      PackValidation.text(draft, input.text(), errors);
    }
    ContentInvalid.rejectIfPresent(errors);
    errors.addAll(vocabularyErrors);
    layout.definitions().forEach(draft -> ContentVocabulary.check(draft, schemas, errors));
    ContentInvalid.rejectIfPresent(errors);
    PackExpressions.check(layout, dependencies, schemas, errors);
    ContentInvalid.rejectIfPresent(errors);
    PackReferences.check(layout, dependencies, schemas, errors, warnings);
    ContentInvalid.rejectIfPresent(errors);
    layout
        .definitions()
        .forEach(draft -> PackValidation.consistency(draft, seeded ? errors : warnings));
    ContentInvalid.rejectIfPresent(errors);

    JsonNode manifest = layout.manifest();
    String packKey = manifest.path("id").asString();
    String version = manifest.path("version").asString();
    String manifestJson = PackJson.canonical(manifest);
    var digestInput = new StringBuilder(manifestJson).append(layout.notice());
    var definitions = new ArrayList<Definition>();
    for (var draft : layout.definitions()) {
      var body = PackLayout.inlineText(draft.body(), input.text());
      String hash = PackJson.hash(PackJson.canonical(body));
      definitions.add(ContentPack.definition(packKey, hash, body));
      digestInput.append(draft.file()).append('\n').append(hash).append('\n');
    }
    String hash = PackJson.hash(digestInput.toString());
    if (available.stream()
        .anyMatch(
            pack ->
                pack.key().equals(packKey)
                    && pack.version().equals(version)
                    && !pack.hash().equals(hash))) {
      throw new ContentInvalid(
          List.of(
              new ContentFinding(
                  "manifest.json",
                  "/version",
                  "CS-PKG-019",
                  "The loaded version has a different hash. Change the pack version before loading new content.")));
    }
    return new ContentPack(
        packKey,
        version,
        hash,
        manifest.path("ruleset").asString(),
        manifest.path("license").path("id").asString(),
        manifestJson,
        layout.notice(),
        definitions,
        dependencies.stream().map(ContentPack::hash).toList(),
        warnings);
  }
}
