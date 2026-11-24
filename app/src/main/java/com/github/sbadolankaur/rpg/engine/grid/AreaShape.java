package com.github.sbadolankaur.rpg.engine.grid;

import java.math.BigInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Flat spell areas. Intersection coordinates are in units of half a five-foot square. */
public sealed interface AreaShape
    permits AreaShape.Sphere, AreaShape.Cone, AreaShape.Cube, AreaShape.Line {
  enum Coverage {
    HALF_ALL,
    DMG_XGTE
  }

  record Intersection(int x, int y) {
    public Intersection {
      if ((x & 1) != 0 || (y & 1) != 0)
        throw new IllegalArgumentException("Expected a grid intersection");
    }
  }

  record Square(int column, int row) {
    long centerX() {
      return 2L * column + 1;
    }

    long centerY() {
      return 2L * row + 1;
    }
  }

  Intersection origin();

  boolean contains(Square square, Coverage coverage);

  /** Cylinders use the same footprint as spheres in the flat grid. */
  record Sphere(Intersection origin, int radiusFeet) implements AreaShape {
    public Sphere {
      requirePositive(radiusFeet);
    }

    @Override
    public boolean contains(Square square, Coverage coverage) {
      var x = BigInteger.valueOf(5 * (square.centerX() - origin.x()));
      var y = BigInteger.valueOf(5 * (square.centerY() - origin.y()));
      var radius = BigInteger.valueOf(2L * radiusFeet);
      return x.pow(2).add(y.pow(2)).compareTo(radius.pow(2)) <= 0;
    }
  }

  record Cone(Intersection origin, Intersection towards, int lengthFeet) implements AreaShape {
    public Cone {
      requirePositive(lengthFeet);
      if (origin.equals(towards)) throw new IllegalArgumentException("Cone needs a direction");
    }

    @Override
    public boolean contains(Square square, Coverage coverage) {
      return AreaProjection.cone(origin, towards, lengthFeet, square, coverage);
    }
  }

  record Line(Intersection origin, Intersection towards, int lengthFeet, int widthFeet)
      implements AreaShape {
    public Line {
      requirePositive(lengthFeet);
      requirePositive(widthFeet);
      if (origin.equals(towards)) throw new IllegalArgumentException("Line needs a direction");
    }

    @Override
    public boolean contains(Square square, Coverage coverage) {
      return AreaProjection.line(origin, towards, lengthFeet, widthFeet, square, coverage);
    }
  }

  record Cube(Intersection origin, Intersection lowerCorner, int sideFeet) implements AreaShape {
    public Cube {
      requirePositive(sideFeet);
      long limit = 2L * sideFeet;
      long x = 5L * ((long) origin.x() - lowerCorner.x());
      long y = 5L * ((long) origin.y() - lowerCorner.y());
      if (x < 0
          || x > limit
          || y < 0
          || y > limit
          || !(x == 0 || x == limit || y == 0 || y == limit))
        throw new IllegalArgumentException("Cube origin must be on a face");
    }

    @Override
    public boolean contains(Square square, Coverage coverage) {
      long x = 5 * (square.centerX() - lowerCorner.x());
      long y = 5 * (square.centerY() - lowerCorner.y());
      long limit = 2L * sideFeet;
      if (coverage == Coverage.DMG_XGTE) {
        return x + 5 > 0 && y + 5 > 0 && x - 5 < limit && y - 5 < limit;
      }
      return x >= 0 && x <= limit && y >= 0 && y <= limit;
    }

    /** Grid-aligned placements with a full edge adjacent to a one-square caster. */
    public static Stream<Cube> fromCaster(Square caster, int sideFeet) {
      requirePositive(sideFeet);
      if (sideFeet % 5 != 0) throw new IllegalArgumentException("Expected whole squares");
      int x = caster.column();
      int y = caster.row();
      int side = sideFeet / 5;
      return IntStream.range(0, side)
          .boxed()
          .flatMap(
              offset ->
                  Stream.of(
                      new Cube(intersection(x + 1, y), intersection(x + 1, y - offset), sideFeet),
                      new Cube(intersection(x, y), intersection(x - side, y - offset), sideFeet),
                      new Cube(intersection(x, y + 1), intersection(x - offset, y + 1), sideFeet),
                      new Cube(intersection(x, y), intersection(x - offset, y - side), sideFeet)));
    }

    private static Intersection intersection(int column, int row) {
      return new Intersection(Math.multiplyExact(column, 2), Math.multiplyExact(row, 2));
    }
  }

  private static void requirePositive(int feet) {
    if (feet <= 0) throw new IllegalArgumentException("Area dimensions must be positive");
  }
}
