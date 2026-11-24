package com.github.sbadolankaur.rpg.engine.grid;

import static com.github.sbadolankaur.rpg.engine.grid.AreaShape.Coverage.DMG_XGTE;
import static com.github.sbadolankaur.rpg.engine.grid.AreaShape.Coverage.HALF_ALL;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Cone;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Cube;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Intersection;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Line;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Sphere;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Square;
import java.util.List;
import org.junit.jupiter.api.Test;

class AreaShapeTest {
  private static final Intersection ORIGIN = new Intersection(0, 0);

  @Test
  void referenceSphereSquareCountsMatchTheRuleset() {
    int[] radii = {5, 10, 15, 20, 30};
    int[] expected = {4, 12, 32, 52, 112};
    for (int i = 0; i < radii.length; i++) {
      var sphere = new Sphere(ORIGIN, radii[i]);
      assertThat(covered(sphere, HALF_ALL)).hasSize(expected[i]);
      assertThat(covered(sphere, DMG_XGTE)).containsExactlyElementsOf(covered(sphere, HALF_ALL));
    }
  }

  @Test
  void positiveOverlapExcludesContactAlongAnEdge() {
    var line = new Line(ORIGIN, new Intersection(2, 0), 10, 10);
    for (var coverage : AreaShape.Coverage.values()) {
      assertThat(line.contains(new Square(1, 0), coverage)).isTrue();
      assertThat(line.contains(new Square(2, 0), coverage)).isFalse();
      assertThat(line.contains(new Square(1, 1), coverage)).isFalse();
    }
    var cone = new Cone(ORIGIN, new Intersection(6, 2), 15);
    assertThat(cone.contains(new Square(0, -1), HALF_ALL)).isFalse();
    assertThat(cone.contains(new Square(0, -1), DMG_XGTE)).isTrue();
  }

  @Test
  void nonAxisDirectionsRotateAndReflectInBothCoverageModes() {
    for (var coverage : AreaShape.Coverage.values()) {
      for (int[] direction : List.of(new int[] {2, 0}, new int[] {2, 2}, new int[] {6, 2})) {
        int x = direction[0];
        int y = direction[1];
        var original = new Intersection(x, y);
        var rotated = new Intersection(-y, x);
        var reflected = new Intersection(x, -y);
        for (var square : candidates()) {
          var quarterTurn = new Square(-square.row() - 1, square.column());
          var mirror = new Square(square.column(), -square.row() - 1);
          assertThat(new Cone(ORIGIN, original, 20).contains(square, coverage))
              .isEqualTo(new Cone(ORIGIN, rotated, 20).contains(quarterTurn, coverage))
              .isEqualTo(new Cone(ORIGIN, reflected, 20).contains(mirror, coverage));
          assertThat(new Line(ORIGIN, original, 20, 5).contains(square, coverage))
              .isEqualTo(new Line(ORIGIN, rotated, 20, 5).contains(quarterTurn, coverage))
              .isEqualTo(new Line(ORIGIN, reflected, 20, 5).contains(mirror, coverage));
        }
      }
    }
  }

  @Test
  void eachSelfCubePlacementCoversNineSquaresAndExcludesTheCaster() {
    var caster = new Square(0, 0);
    var placements = Cube.fromCaster(caster, 15).toList();
    assertThat(placements).hasSize(12).doesNotHaveDuplicates();
    for (var cube : placements) {
      assertThat(covered(cube, HALF_ALL)).hasSize(9).doesNotContain(caster);
      assertThat(covered(cube, DMG_XGTE)).containsExactlyElementsOf(covered(cube, HALF_ALL));
    }
  }

  private static List<Square> covered(AreaShape shape, AreaShape.Coverage coverage) {
    return candidates().stream().filter(square -> shape.contains(square, coverage)).toList();
  }

  private static List<Square> candidates() {
    return java.util.stream.IntStream.range(-8, 8)
        .boxed()
        .flatMap(x -> java.util.stream.IntStream.range(-8, 8).mapToObj(y -> new Square(x, y)))
        .toList();
  }
}
