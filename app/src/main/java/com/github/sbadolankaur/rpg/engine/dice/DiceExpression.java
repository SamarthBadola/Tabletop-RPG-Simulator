package com.github.sbadolankaur.rpg.engine.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable, label-free dice expression parsed from the approved ASCII notation. */
public final class DiceExpression {
  public sealed interface Term permits DiceGroup, FixedValue {
    int sign();

    String canonical();
  }

  public enum Selection {
    NONE,
    KEEP_HIGH,
    KEEP_LOW,
    DROP_HIGH,
    DROP_LOW
  }

  public record DiceGroup(
      int sign,
      int count,
      int size,
      Selection selection,
      int selectionCount,
      Integer rerollAtOrBelow,
      Integer floorAtLeast)
      implements Term {
    public DiceGroup {
      Objects.requireNonNull(selection);
    }

    @Override
    public String canonical() {
      StringBuilder out = new StringBuilder();
      if (sign < 0) out.append('-');
      out.append(count).append('d').append(size);
      switch (selection) {
        case KEEP_HIGH -> out.append("kh").append(selectionCount);
        case KEEP_LOW -> out.append("kl").append(selectionCount);
        case DROP_HIGH -> out.append("dh").append(selectionCount);
        case DROP_LOW -> out.append("dl").append(selectionCount);
        case NONE -> {}
      }
      if (rerollAtOrBelow != null) out.append("r<=").append(rerollAtOrBelow);
      if (floorAtLeast != null) out.append("min").append(floorAtLeast);
      return out.toString();
    }
  }

  public record FixedValue(int sign, int value) implements Term {
    @Override
    public String canonical() {
      return (sign < 0 ? "-" : "") + value;
    }
  }

  private final List<Term> terms;

  DiceExpression(List<Term> terms) {
    this.terms = List.copyOf(terms);
  }

  public static DiceExpression parse(String text) {
    return parse(text, DiceLimits.DEFAULT);
  }

  public static DiceExpression parse(String text, DiceLimits limits) {
    Objects.requireNonNull(limits, "limits");
    if (text == null) throw error(DiceErrorCode.DICE_EMPTY, 0, "", null, null, null);
    boolean blank = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        blank = false;
        break;
      }
    }
    if (blank) throw error(DiceErrorCode.DICE_EMPTY, 0, text, null, null, null);
    if (text.length() > limits.maxExpressionLength()) {
      throw limit(text, limits, 0, "expression length", limits.maxExpressionLength());
    }
    return new Parser(text, limits).parse();
  }

  public List<Term> terms() {
    return terms;
  }

  public String canonical() {
    return toString();
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    for (Term term : terms) {
      if (out.length() > 0 && term.sign() > 0) out.append('+');
      out.append(term.canonical());
    }
    return out.toString();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DiceExpression expression && terms.equals(expression.terms);
  }

  @Override
  public int hashCode() {
    return terms.hashCode();
  }

  public RollResult roll(RandomSource source) {
    RollPlan.Builder builder = RollPlan.builder();
    for (Term term : terms) {
      if (term instanceof FixedValue fixed) {
        builder.fixed(fixed.canonical(), null, fixed.sign() * fixed.value());
      } else {
        builder.dice(term.canonical(), null, new DiceExpression(List.of(term)), false);
      }
    }
    return builder.build().roll(source);
  }

  static DiceParseException error(
      DiceErrorCode code, int pos, String token, String field, String limit, Integer value) {
    return new DiceParseException(code, pos, token, field, limit, value);
  }

  static DiceParseException limit(String text, DiceLimits limits, int pos, String name, int value) {
    return error(DiceErrorCode.DICE_LIMIT_EXCEEDED, pos, text, null, name, value);
  }

  private static final class Parser {
    private final String text;
    private final DiceLimits limits;
    private final List<Term> terms = new ArrayList<>();
    private int pos;
    private int totalDice;
    private String lastNumberToken;

    Parser(String text, DiceLimits limits) {
      this.text = text;
      this.limits = limits;
    }

    DiceExpression parse() {
      skipWs();
      int sign = 1;
      if (at('+') || at('-')) sign = text.charAt(pos++) == '-' ? -1 : 1;
      parseTerm(sign);
      while (true) {
        skipWs();
        if (pos == text.length()) break;
        if (!at('+') && !at('-')) syntax(pos, String.valueOf(text.charAt(pos)));
        sign = text.charAt(pos++) == '-' ? -1 : 1;
        skipWs();
        if (pos == text.length() || at('+') || at('-'))
          syntax(pos, pos == text.length() ? "" : String.valueOf(text.charAt(pos)));
        parseTerm(sign);
      }
      if (totalDice > limits.maxTotalDice())
        throw limit(text, limits, 0, "total dice", limits.maxTotalDice());
      return new DiceExpression(terms);
    }

    private void parseTerm(int sign) {
      int start = pos;
      if (terms.size() >= limits.maxTerms())
        throw limit(text, limits, start, "terms", limits.maxTerms());
      if (pos >= text.length()) syntax(pos, "");
      if (isAsciiDigit(text.charAt(pos))) {
        int numberStart = pos;
        int first = readNumber("literal");
        if (pos < text.length() && lower(text.charAt(pos)) == 'd') {
          pos++;
          int count = first;
          range(count >= 1 && count <= limits.maxDicePerGroup(), numberStart, "count");
          parseDice(sign, count, start);
        } else {
          range(first >= 0 && first <= limits.maxIntegerLiteral(), numberStart, "literal");
          add(new FixedValue(sign, first), start);
        }
      } else if (lower(text.charAt(pos)) == 'd') {
        pos++;
        parseDice(sign, 1, start);
      } else {
        syntax(pos, String.valueOf(text.charAt(pos)));
      }
    }

    private void parseDice(int sign, int count, int start) {
      int sizeStart = pos;
      int size = readNumber("size");
      range(size >= limits.minDieSize() && size <= limits.maxDieSize(), sizeStart, "size");
      Selection selection = Selection.NONE;
      int selectionCount = 0;
      Integer reroll = null;
      Integer floor = null;
      while (pos < text.length() && isAsciiLetter(text.charAt(pos))) {
        int modifierStart = pos;
        char c = lower(text.charAt(pos));
        if (c == 'k' || c == 'd') {
          if (selection != Selection.NONE) conflict(modifierStart);
          pos++;
          if (pos >= text.length()) syntax(modifierStart, text.substring(modifierStart));
          char direction = lower(text.charAt(pos++));
          if (c == 'k' && direction == 'h') selection = Selection.KEEP_HIGH;
          else if (c == 'k' && direction == 'l') selection = Selection.KEEP_LOW;
          else if (c == 'd' && direction == 'h') selection = Selection.DROP_HIGH;
          else if (c == 'd' && direction == 'l') selection = Selection.DROP_LOW;
          else syntax(modifierStart, text.substring(modifierStart, pos));
          selectionCount = readNumber("keep-drop number");
          int max =
              (selection == Selection.DROP_HIGH || selection == Selection.DROP_LOW)
                  ? count - 1
                  : count;
          range(selectionCount >= 1 && selectionCount <= max, modifierStart, "keep-drop number");
        } else if (c == 'r') {
          if (reroll != null) conflict(modifierStart);
          pos++;
          if (pos + 1 >= text.length() || text.charAt(pos) != '<' || text.charAt(pos + 1) != '=')
            syntax(modifierStart, text.substring(modifierStart));
          pos += 2;
          int n = readNumber("reroll threshold");
          range(n >= 1 && n < size, modifierStart, "reroll threshold");
          reroll = n;
        } else if (c == 'm') {
          if (floor != null) conflict(modifierStart);
          pos++;
          if (pos >= text.length()
              || lower(text.charAt(pos++)) != 'i'
              || pos >= text.length()
              || lower(text.charAt(pos++)) != 'n')
            syntax(modifierStart, text.substring(modifierStart, pos));
          int n = readNumber("floor");
          range(n >= 2 && n <= size, modifierStart, "floor");
          floor = n;
        } else {
          syntax(pos, String.valueOf(text.charAt(pos)));
        }
      }
      totalDice += count;
      add(new DiceGroup(sign, count, size, selection, selectionCount, reroll, floor), start);
    }

    private int readNumber(String field) {
      int start = pos;
      while (pos < text.length() && isAsciiDigit(text.charAt(pos))) pos++;
      if (start == pos) syntax(pos, pos == text.length() ? "" : String.valueOf(text.charAt(pos)));
      String token = text.substring(start, pos);
      lastNumberToken = token;
      if (token.length() > 9)
        throw error(DiceErrorCode.DICE_VALUE_OUT_OF_RANGE, start, token, field, null, null);
      int value = 0;
      for (int i = 0; i < token.length(); i++) value = value * 10 + token.charAt(i) - '0';
      return value;
    }

    private void range(boolean valid, int at, String field) {
      if (!valid)
        throw error(DiceErrorCode.DICE_VALUE_OUT_OF_RANGE, at, lastNumberToken, field, null, null);
    }

    private void add(Term term, int at) {
      terms.add(term);
    }

    private void conflict(int at) {
      throw error(DiceErrorCode.DICE_MODIFIER_CONFLICT, at, tokenAt(at), null, null, null);
    }

    private void syntax(int at, String token) {
      throw error(DiceErrorCode.DICE_SYNTAX, at, token, null, null, null);
    }

    private String tokenAt(int at) {
      int end = at;
      while (end < text.length()) {
        char c = text.charAt(end);
        if (!isAsciiLetter(c) && !isAsciiDigit(c) && c != '<' && c != '=') break;
        end++;
      }
      return text.substring(at, end);
    }

    private boolean at(char c) {
      return pos < text.length() && text.charAt(pos) == c;
    }

    private void skipWs() {
      while (pos < text.length() && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t')) pos++;
    }
  }

  static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static char lower(char c) {
    return c >= 'A' && c <= 'Z' ? String.valueOf(c).toLowerCase(Locale.ROOT).charAt(0) : c;
  }
}
