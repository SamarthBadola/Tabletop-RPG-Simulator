package com.github.sbadolankaur.rpg.engine.dice;

import java.util.Arrays;

/** Test source supplying die faces (1-based) in draw order. */
public final class ForcedDice implements RandomSource {
  private final int[] faces;
  private int index;

  public ForcedDice(int... faces) {
    this.faces = Arrays.copyOf(faces, faces.length);
  }

  @Override
  public int nextInt(int bound) {
    if (index == faces.length) throw new IllegalStateException("No forced face remains");
    int face = faces[index++];
    if (face < 1 || face > bound)
      throw new IllegalStateException("Forced face " + face + " is outside 1.." + bound);
    return face - 1;
  }

  public void assertExhausted() {
    if (index != faces.length)
      throw new AssertionError("" + (faces.length - index) + " forced face(s) were not consumed");
  }
}
