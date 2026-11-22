package com.github.sbadolankaur.rpg.engine.dice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.architecture.fixture.ForbiddenFrameworkDependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiceArchitectureTest {
  private static final String ENGINE_PACKAGE = "com.github.sbadolankaur.rpg.engine";

  @Test
  void engineRemainsIndependentOfFrameworksAndExternalIo() {
    JavaClasses engineClasses =
        new ClassFileImporter()
            .importPath(Path.of("target/classes/com/github/sbadolankaur/rpg/engine"));

    pureEngineRule().check(engineClasses);
  }

  @Test
  void aDeliberateFrameworkImportViolatesThePureEngineRule() {
    JavaClasses violatingClasses =
        new ClassFileImporter().importClasses(ForbiddenFrameworkDependency.class);

    assertThat(pureEngineRule().evaluate(violatingClasses).hasViolation()).isTrue();
  }

  private static ArchRule pureEngineRule() {
    return noClasses()
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
  }
}
