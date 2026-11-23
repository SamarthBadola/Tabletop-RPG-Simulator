package com.github.sbadolankaur.rpg.engine.dice;

public record DieRoll(
    int rolled,
    Integer rerollFace,
    int value,
    boolean kept,
    boolean rerolled,
    boolean floored,
    boolean critical) {}
