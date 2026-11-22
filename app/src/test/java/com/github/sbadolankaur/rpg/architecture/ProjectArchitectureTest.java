package com.github.sbadolankaur.rpg.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.architecture.fixture.ability.DirectAbilityStorage;
import com.github.sbadolankaur.rpg.architecture.fixture.other.OutsideDefinitionBuilder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.Slice;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectArchitectureTest {
  private static final Path APPLICATION_CLASSES =
      Path.of("target/classes/com/github/sbadolankaur/rpg");

  @Test
  void productionCodeFollowsTheProjectArchitectureRules() {
    JavaClasses application = new ClassFileImporter().importPath(APPLICATION_CLASSES);

    noWebToPersistence().check(application);
    featurePackagesHaveNoCycles().check(application);
    abilityStorageIsEncapsulated().check(application);
    onlyContentBuildsDefinitions().check(application);
  }

  @Test
  void aDeliberateWebToPersistenceDependencyViolatesItsRule() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importPackages(
                "com.github.sbadolankaur.rpg.architecture.fixture.features.player.web",
                "com.github.sbadolankaur.rpg.architecture.fixture.features.dm.persistence");

    assertThat(noWebToPersistence().evaluate(fixture).hasViolation()).isTrue();
  }

  @Test
  void aDeliberateFeatureCycleViolatesItsRule() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importPackages(
                "com.github.sbadolankaur.rpg.cyclealpha", "com.github.sbadolankaur.rpg.cyclebeta");

    assertThat(featurePackagesHaveNoCycles().evaluate(fixture).hasViolation()).isTrue();
  }

  @Test
  void directAbilityStorageOutsideTheAccessorViolatesItsRule() {
    JavaClasses fixture = new ClassFileImporter().importClasses(DirectAbilityStorage.class);

    assertThat(abilityStorageIsEncapsulated().evaluate(fixture).hasViolation()).isTrue();
  }

  @Test
  void aNonContentDefinitionBuilderViolatesItsRule() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importClasses(
                OutsideDefinitionBuilder.class,
                com.github.sbadolankaur.rpg.architecture.fixture.content.DefinitionBuilder.class);

    assertThat(onlyContentBuildsDefinitions().evaluate(fixture).hasViolation()).isTrue();
  }

  private static ArchRule noWebToPersistence() {
    return noClasses()
        .that()
        .resideInAPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..persistence..");
  }

  private static ArchRule featurePackagesHaveNoCycles() {
    return slices()
        .matching("..rpg.(*)..")
        .that(
            new DescribedPredicate<Slice>("business feature packages") {
              @Override
              public boolean test(Slice slice) {
                String packageName = slice.iterator().next().getPackageName();
                return !packageName.matches(".*\\.rpg\\.(platform|engine)(\\..*)?");
              }
            })
        .should()
        .beFreeOfCycles();
  }

  private static ArchRule abilityStorageIsEncapsulated() {
    return fields()
        .that()
        .haveNameMatching("(?i)(strength|dexterity|constitution|intelligence|wisdom|charisma)")
        .should()
        .beDeclaredInClassesThat()
        .haveSimpleName("AbilityScores")
        .allowEmptyShould(true);
  }

  private static ArchRule onlyContentBuildsDefinitions() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("..content..")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("DefinitionBuilder")
        .allowEmptyShould(true);
  }
}
