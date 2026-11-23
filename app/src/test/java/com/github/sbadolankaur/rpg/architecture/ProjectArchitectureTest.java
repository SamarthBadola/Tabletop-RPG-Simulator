package com.github.sbadolankaur.rpg.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.architecture.fixture.ability.DirectAbilityStorage;
import com.github.sbadolankaur.rpg.architecture.fixture.other.OutsideDefinitionBuilder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
    noClasses()
        .that()
        .resideOutsideOfPackage("..identity..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.bouncycastle..", "org.springframework.security.crypto.argon2..")
        .check(application);
  }

  @Test
  void aDeliberateWebToPersistenceDependencyViolatesItsRule() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importPackages(
                "com.github.sbadolankaur.rpg.architecture.fixture.features.player.web",
                "com.github.sbadolankaur.rpg.architecture.fixture.features.dm.persistence");

    assertThatThrownBy(() -> noWebToPersistence().check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("PlayerPage")
        .hasMessageContaining("DmRepository");
  }

  @Test
  void aDeliberateFeatureCycleViolatesItsRule() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importPackages(
                "com.github.sbadolankaur.rpg.cyclealpha", "com.github.sbadolankaur.rpg.cyclebeta");

    assertThatThrownBy(() -> featurePackagesHaveNoCycles().check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Cycle detected");
  }

  @Test
  void directAbilityStorageOutsideTheAccessorViolatesItsRule() {
    JavaClasses fixture = new ClassFileImporter().importClasses(DirectAbilityStorage.class);

    assertThatThrownBy(() -> abilityStorageIsEncapsulated().check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DirectAbilityStorage");
  }

  @Test
  void aNonContentDefinitionBuilderViolatesItsRule() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importClasses(
                OutsideDefinitionBuilder.class,
                com.github.sbadolankaur.rpg.architecture.fixture.content.DefinitionBuilder.class);

    assertThatThrownBy(() -> onlyContentBuildsDefinitions().check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OutsideDefinitionBuilder");
  }

  private static ArchRule noWebToPersistence() {
    return classes()
        .that()
        .resideInAPackage("..web..")
        .should(
            new ArchCondition<>("avoid another feature's persistence classes") {
              @Override
              public void check(JavaClass webClass, ConditionEvents events) {
                String feature = webClass.getPackageName().split("\\.web(?:\\.|$)", 2)[0];
                for (var dependency : webClass.getDirectDependenciesFromSelf()) {
                  String target = dependency.getTargetClass().getPackageName();
                  String[] persistence = target.split("\\.persistence(?:\\.|$)", 2);
                  if (persistence.length == 2 && !persistence[0].equals(feature)) {
                    events.add(
                        SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                  }
                }
              }
            })
        .allowEmptyShould(true);
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
