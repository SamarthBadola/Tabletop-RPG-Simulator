package com.github.sbadolankaur.rpg.engine.grid;

import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Coverage;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Intersection;
import com.github.sbadolankaur.rpg.engine.grid.AreaShape.Square;
import java.math.BigInteger;
import java.util.List;

/** Compares exact projections without evaluating the direction vector's square root. */
final class AreaProjection {
  private static final BigInteger ZERO = BigInteger.ZERO;
  private static final BigInteger FIVE = BigInteger.valueOf(5);
  private final BigInteger dx;
  private final BigInteger dy;
  private final BigInteger normSquared;
  private final BigInteger along;
  private final BigInteger across;
  private final BigInteger length;
  private final BigInteger halfWidth;
  private final boolean tapered;

  private AreaProjection(
      Intersection origin,
      Intersection towards,
      int lengthFeet,
      int widthFeet,
      Square square,
      boolean tapered) {
    dx = BigInteger.valueOf((long) towards.x() - origin.x());
    dy = BigInteger.valueOf((long) towards.y() - origin.y());
    normSquared = dx.pow(2).add(dy.pow(2));
    var x = BigInteger.valueOf(square.centerX() - origin.x());
    var y = BigInteger.valueOf(square.centerY() - origin.y());
    // Along and across use the dot product and signed cross product. Multiplying by five
    // accommodates feet-based dimensions without rounding the half-square grid units.
    along = dx.multiply(x).add(dy.multiply(y)).multiply(FIVE);
    across = dx.multiply(y).subtract(dy.multiply(x)).multiply(FIVE);
    length = BigInteger.valueOf(2L * lengthFeet);
    halfWidth = BigInteger.valueOf(widthFeet);
    this.tapered = tapered;
  }

  static boolean cone(
      Intersection origin, Intersection towards, int lengthFeet, Square square, Coverage coverage) {
    return new AreaProjection(origin, towards, lengthFeet, lengthFeet, square, true)
        .contains(coverage);
  }

  static boolean line(
      Intersection origin,
      Intersection towards,
      int lengthFeet,
      int widthFeet,
      Square square,
      Coverage coverage) {
    return new AreaProjection(origin, towards, lengthFeet, widthFeet, square, false)
        .contains(coverage);
  }

  private boolean contains(Coverage coverage) {
    if (coverage == Coverage.HALF_ALL) {
      return along.signum() > 0
          && compare(along, length) <= 0
          && (tapered
              ? across.abs().shiftLeft(1).compareTo(along) <= 0
              : compare(across.abs(), halfWidth) <= 0);
    }

    // Separating axes come from the square's edges and the area's polygon edges.
    if (!overlaps(dx, dy.negate()) || !overlaps(dy, dx)) return false;
    var areaAxes =
        tapered
            ? List.of(new Axis(1, 0), new Axis(1, 2), new Axis(1, -2))
            : List.of(new Axis(1, 0), new Axis(0, 1));
    return areaAxes.stream()
        .allMatch(
            axis -> overlaps(BigInteger.valueOf(axis.along()), BigInteger.valueOf(axis.across())));
  }

  private boolean overlaps(BigInteger a, BigInteger b) {
    var squareCenter = a.multiply(along).add(b.multiply(across));
    var squareRadius =
        a.multiply(dx)
            .subtract(b.multiply(dy))
            .abs()
            .add(a.multiply(dy).add(b.multiply(dx)).abs())
            .multiply(FIVE);

    // Area corners on each axis are coefficients of the same direction-vector length.
    var end = a.multiply(length);
    var width = b.abs().multiply(halfWidth);
    var min = tapered ? end.subtract(width).min(ZERO) : end.min(ZERO).subtract(width);
    var max = tapered ? end.add(width).max(ZERO) : end.max(ZERO).add(width);
    return compare(squareCenter.add(squareRadius), min) > 0
        && compare(squareCenter.subtract(squareRadius), max) < 0;
  }

  private int compare(BigInteger coordinate, BigInteger coefficient) {
    // Compare coordinate to coefficient times the vector's length, preserving the signs.
    int signs = Integer.compare(coordinate.signum(), coefficient.signum());
    if (signs != 0) return signs;
    return coordinate.signum()
        * coordinate.pow(2).compareTo(coefficient.pow(2).multiply(normSquared));
  }

  private record Axis(int along, int across) {}
}
