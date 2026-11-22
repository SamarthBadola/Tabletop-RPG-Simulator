package com.github.sbadolankaur.rpg.engine.dice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiceArchitectureTest {
  private static final String ENGINE_PACKAGE = "com.github.sbadolankaur.rpg.engine.dice";

  @Test
  void engineRemainsIndependentOfFrameworksAndExternalIo() {
    JavaClasses engineClasses =
        new ClassFileImporter()
            .importPath(Path.of("target/classes/com/github/sbadolankaur/rpg/engine/dice"));

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(ENGINE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "java.net.http..",
                "java.io.File*",
                "java.nio.file..");

    rule.check(engineClasses);
  }
}
