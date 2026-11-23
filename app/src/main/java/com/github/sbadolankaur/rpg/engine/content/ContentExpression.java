package com.github.sbadolankaur.rpg.engine.content;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.DiceLimits;
import com.github.sbadolankaur.rpg.engine.dice.DiceParseException;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** A compiled and typed content formula. No effects, I/O or ambient randomness. */
public final class ContentExpression {
  private static final int CHARACTERS = 256;
  private static final int NESTING = 16;
  private static final int CALLS = 8;
  private static final DiceLimits DICE = DiceLimits.DEFAULT;
  private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*(?::[a-z0-9_.-]+)?");

  public enum Type {
    INT,
    ROLL,
    BOOL,
    NAME
  }

  @FunctionalInterface
  public interface Lookup {
    TableColumn resolve(String table, String column);
  }

  public record TableColumn(Type indexType, Type resultType, Map<Object, Object> rows) {
    public TableColumn {
      rows = Map.copyOf(rows);
    }
  }

  private final Node root;
  private final String canonical;

  private ContentExpression(Node root, String canonical) {
    this.root = root;
    this.canonical = canonical;
  }

  public static ContentExpression parse(String input, Map<String, Type> variables) {
    return parse(input, variables, (table, column) -> null);
  }

  public static ContentExpression parse(String input, Map<String, Type> variables, Lookup tables) {
    if (input == null || input.isBlank() || input.length() > CHARACTERS) {
      throw invalid("Formula must contain between 1 and " + CHARACTERS + " characters");
    }
    try {
      var dice = DiceExpression.parse(input);
      if (dice.terms().stream().anyMatch(DiceExpression.DiceGroup.class::isInstance)) {
        return new ContentExpression(
            new Node(Type.ROLL, state -> state.roll(dice), null), dice.canonical());
      }
      return new ContentExpression(
          new Node(Type.INT, state -> (long) dice.roll(state.random).total(), null),
          dice.canonical());
    } catch (DiceParseException notDice) {
      var parser = new Parser(input, variables, tables);
      Node root = parser.expression(0, 0);
      if (!parser.done()) throw invalid("Unexpected formula token at " + parser.position);
      return new ContentExpression(root, input.strip());
    }
  }

  public Type type() {
    return root.type();
  }

  public String canonical() {
    return canonical;
  }

  public Object evaluate(Map<String, Object> variables, RandomSource random) {
    try {
      return root.eval().read(new State(variables, random));
    } catch (ArithmeticException | DiceParseException e) {
      throw new IllegalArgumentException("EXPRESSION_ERROR", e);
    }
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }

  private record Node(Type type, Evaluation eval, String literal) {
    static Node of(Type type, Evaluation eval) {
      return new Node(type, eval, null);
    }
  }

  @FunctionalInterface
  private interface Evaluation {
    Object read(State state);
  }

  private static final class State {
    private final Map<String, Object> values;
    private final RandomSource random;
    private int dice;

    State(Map<String, Object> values, RandomSource random) {
      this.values = values;
      this.random = Objects.requireNonNull(random);
    }

    long roll(DiceExpression expression) {
      for (var term : expression.terms()) {
        if (term instanceof DiceExpression.DiceGroup group) dice += group.count();
        if (dice > DICE.maxTotalDice()) throw new ArithmeticException("Dice count limit");
      }
      return expression.roll(random).total();
    }
  }

  private static final class Parser {
    private final String input;
    private final Map<String, Type> variables;
    private final Lookup tables;
    private int position;
    private int calls;

    Parser(String input, Map<String, Type> variables, Lookup tables) {
      this.input = input;
      this.variables = variables;
      this.tables = tables;
    }

    Node expression(int precedence, int depth) {
      if (depth > NESTING) throw invalid("Formula nesting exceeds " + NESTING);
      Node left = prefix(depth);
      boolean compared = false;
      while (true) {
        int start = position;
        String operator = operator();
        int strength = precedence(operator);
        if (strength == 0 || strength <= precedence) {
          position = start;
          return left;
        }
        if (strength == 3 && compared) throw invalid("A comparison cannot be chained");
        compared = strength == 3 || compared;
        Node right = expression(strength, depth + 1);
        left = binary(left, operator, right);
      }
    }

    private Node prefix(int depth) {
      if (word("not")) {
        Node operand = expression(2, depth + 1);
        require(operand, Type.BOOL);
        return Node.of(Type.BOOL, state -> !(Boolean) operand.eval().read(state));
      }
      if (take("-")) {
        Node term = prefix(depth + 1);
        if (depth >= NESTING || !numeric(term)) throw invalid("Negation needs an integer or roll");
        return Node.of(term.type(), state -> Math.negateExact(integer(term, state)));
      }
      if (take("+")) return prefix(depth + 1);
      Node count;
      if (dieMarker()) {
        count = literal(1);
      } else {
        count = primary(depth);
        if (!dieMarker()) return count;
      }
      require(count, Type.INT);
      Node size = primary(depth + 1);
      require(size, Type.INT);
      var modifiers = new StringBuilder();
      for (String candidate = nextDiceModifier();
          candidate != null;
          candidate = nextDiceModifier()) {
        modifiers.append(candidate).append(digits());
      }
      String tail = modifiers.toString();
      return Node.of(
          Type.ROLL,
          state ->
              state.roll(
                  DiceExpression.parse(integer(count, state) + "d" + integer(size, state) + tail)));
    }

    private Node primary(int depth) {
      if (take("(")) {
        Node inner = expression(0, depth + 1);
        expect(")");
        return inner;
      }
      if (take("'")) {
        int start = position;
        while (position < input.length() && input.charAt(position) != '\'') position++;
        String quoted = input.substring(start, position);
        expect("'");
        if (!quoted.matches("[a-z0-9_.:-]+")) throw invalid("Invalid name literal");
        return new Node(Type.NAME, ignored -> quoted, quoted);
      }
      skip();
      if (position < input.length() && digit(input.charAt(position))) {
        try {
          return literal(Long.parseLong(digits()));
        } catch (NumberFormatException outOfRange) {
          throw invalid("Integer literal is out of range");
        }
      }
      var matcher = IDENTIFIER.matcher(input).region(position, input.length());
      if (!matcher.lookingAt()) throw invalid("Expected a value at " + position);
      String name = matcher.group();
      position = matcher.end();
      if (take("(")) return call(name, depth + 1);
      Type type = variables.get(name);
      if (type == null) throw invalid("Variable is not available: " + name);
      return Node.of(
          type,
          state -> {
            Object value = state.values.get(name);
            if (value == null) throw new ArithmeticException("Missing formula variable");
            return value;
          });
    }

    private Node call(String name, int depth) {
      if (++calls > CALLS) throw invalid("Formula has more than " + CALLS + " calls");
      var args = new ArrayList<Node>();
      if (!take(")")) {
        do {
          args.add(expression(0, depth));
        } while (take(","));
        expect(")");
      }
      return switch (name) {
        case "min", "max" -> aggregate(name, args);
        case "round_div" -> {
          arity(args, 2);
          args.forEach(argument -> require(argument, Type.INT));
          yield Node.of(
              Type.INT,
              state -> {
                long a = integer(args.get(0), state);
                long b = integer(args.get(1), state);
                if (b <= 0) throw new ArithmeticException("Nonpositive divisor");
                return Math.floorDivExact(
                    Math.addExact(Math.multiplyExact(2, a), b), Math.multiplyExact(2, b));
              });
        }
        case "if" -> {
          arity(args, 3);
          require(args.get(0), Type.BOOL);
          Node yes = args.get(1);
          Node no = args.get(2);
          if (yes.type() != no.type() && !(numeric(yes) && numeric(no))) {
            throw invalid("Both if branches need compatible types");
          }
          Type type = yes.type() == Type.ROLL || no.type() == Type.ROLL ? Type.ROLL : yes.type();
          yield Node.of(
              type,
              state -> ((Boolean) args.get(0).eval().read(state) ? yes : no).eval().read(state));
        }
        case "lookup" -> lookup(args);
        default -> throw invalid("Unknown formula function: " + name);
      };
    }

    private Node lookup(List<Node> args) {
      if (args.size() != 2 && args.size() != 3)
        throw invalid("lookup expects two or three arguments");
      String table = args.get(0).literal();
      String column = args.size() == 3 ? args.get(2).literal() : null;
      if (table == null || !table.contains(":") || (args.size() == 3 && column == null)) {
        throw invalid("lookup needs a quoted full table key and an optional quoted column");
      }
      TableColumn selected = tables.resolve(table, column);
      if (selected == null) throw invalid("Unreachable lookup table or column");
      Node index = args.get(1);
      require(index, selected.indexType());
      return Node.of(
          selected.resultType(),
          state -> {
            Object value = selected.rows().get(index.eval().read(state));
            if (value == null) throw new ArithmeticException("Missing lookup row");
            return selected.resultType() == Type.ROLL
                ? state.roll(DiceExpression.parse((String) value))
                : value;
          });
    }

    private static Node aggregate(String name, List<Node> args) {
      if (args.size() < 2) throw invalid(name + " expects at least two arguments");
      args.forEach(arg -> require(arg, Type.INT));
      return Node.of(
          Type.INT,
          state -> {
            long current = integer(args.getFirst(), state);
            for (int i = 1; i < args.size(); i++) {
              long next = integer(args.get(i), state);
              current = name.equals("min") ? Math.min(current, next) : Math.max(current, next);
            }
            return current;
          });
    }

    private boolean dieMarker() {
      skip();
      if (position + 1 >= input.length() || input.charAt(position) != 'd') return false;
      char following = input.charAt(position + 1);
      if (!digit(following) && following != '(' && !Character.isWhitespace(following)) {
        return false;
      }
      position++;
      return true;
    }

    private String nextDiceModifier() {
      for (String modifier : List.of("kh", "kl", "dh", "dl", "r<=", "min")) {
        if (input.startsWith(modifier, position)) {
          position += modifier.length();
          return modifier;
        }
      }
      return null;
    }

    private String digits() {
      int first = position;
      while (position < input.length() && digit(input.charAt(position))) position++;
      if (first == position) throw invalid("Expected an integer at " + position);
      return input.substring(first, position);
    }

    private String operator() {
      if (word("or")) return "or";
      if (word("and")) return "and";
      for (String token : List.of("==", "!=", "<=", ">=", "<", ">", "+", "-", "*", "/")) {
        if (take(token)) return token;
      }
      return "";
    }

    private static int precedence(String operator) {
      return switch (operator) {
        case "or" -> 1;
        case "and" -> 2;
        case "==", "!=", "<=", ">=", "<", ">" -> 3;
        case "+", "-" -> 4;
        case "*", "/" -> 5;
        default -> 0;
      };
    }

    private boolean word(String token) {
      skip();
      int end = position + token.length();
      if (!input.startsWith(token, position)
          || (end < input.length()
              && (Character.isLetterOrDigit(input.charAt(end)) || input.charAt(end) == '_'))) {
        return false;
      }
      position = end;
      return true;
    }

    private void expect(String token) {
      if (!take(token)) throw invalid("Expected " + token + " at " + position);
    }

    private boolean take(String token) {
      skip();
      if (!input.startsWith(token, position)) return false;
      position += token.length();
      return true;
    }

    private boolean done() {
      skip();
      return position == input.length();
    }

    private void skip() {
      while (position < input.length() && Character.isWhitespace(input.charAt(position)))
        position++;
    }
  }

  private static Node binary(Node left, String operator, Node right) {
    return switch (operator) {
      case "and", "or" -> {
        require(left, Type.BOOL);
        require(right, Type.BOOL);
        yield Node.of(
            Type.BOOL,
            state -> {
              boolean a = (Boolean) left.eval().read(state);
              return operator.equals("and")
                  ? a && (Boolean) right.eval().read(state)
                  : a || (Boolean) right.eval().read(state);
            });
      }
      case "+", "-" -> {
        if (!numeric(left) || !numeric(right))
          throw invalid("Addition and subtraction need numbers");
        Type type = left.type() == Type.ROLL || right.type() == Type.ROLL ? Type.ROLL : Type.INT;
        yield Node.of(
            type,
            state ->
                operator.equals("+")
                    ? Math.addExact(integer(left, state), integer(right, state))
                    : Math.subtractExact(integer(left, state), integer(right, state)));
      }
      case "*", "/", "<", ">", "<=", ">=" -> {
        require(left, Type.INT);
        require(right, Type.INT);
        Type type = operator.equals("*") || operator.equals("/") ? Type.INT : Type.BOOL;
        yield Node.of(
            type,
            state -> {
              long a = integer(left, state);
              long b = integer(right, state);
              return switch (operator) {
                case "*" -> Math.multiplyExact(a, b);
                case "/" -> Math.floorDivExact(a, b);
                case "<" -> a < b;
                case ">" -> a > b;
                case "<=" -> a <= b;
                default -> a >= b;
              };
            });
      }
      case "==", "!=" -> {
        if (left.type() != right.type() || left.type() == Type.ROLL) {
          throw invalid("Equality needs two non-roll values of the same type");
        }
        yield Node.of(
            Type.BOOL,
            state ->
                operator.equals("==")
                    == Objects.equals(left.eval().read(state), right.eval().read(state)));
      }
      default -> throw new IllegalStateException("Unknown binary operator");
    };
  }

  private static Node literal(long value) {
    return Node.of(Type.INT, ignored -> value);
  }

  private static boolean digit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean numeric(Node node) {
    return node.type() == Type.INT || node.type() == Type.ROLL;
  }

  private static long integer(Node node, State state) {
    return ((Number) node.eval().read(state)).longValue();
  }

  private static void require(Node node, Type type) {
    if (node.type() != type) throw invalid("Expected " + type + ", found " + node.type());
  }

  private static void arity(List<Node> args, int count) {
    if (args.size() != count) throw invalid("Expected " + count + " arguments");
  }
}
