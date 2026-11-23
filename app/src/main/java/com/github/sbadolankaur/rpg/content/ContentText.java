package com.github.sbadolankaur.rpg.content;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the content prose subset; only tags constructed here enter the output. */
public final class ContentText {
  private static final Pattern NUMBERED = Pattern.compile("^[0-9]+[.] (.+)$");
  private static final Pattern EMPHASIS =
      Pattern.compile("\\*\\*([^*\\n]+)\\*\\*|\\*([^*\\n]+)\\*");

  private ContentText() {}

  public static String html(String text) {
    StringBuilder result = new StringBuilder();
    var lines = text.lines().map(String::strip).toList();
    for (int i = 0; i < lines.size(); ) {
      String line = lines.get(i);
      if (line.isEmpty()) {
        i++;
      } else if (line.startsWith("- ") || NUMBERED.matcher(line).matches()) {
        i = list(lines, i, result);
      } else if (i + 1 < lines.size() && line.contains("|") && tableRule(lines.get(i + 1))) {
        i = table(lines, i, result);
      } else {
        var paragraph = new ArrayList<String>();
        do {
          paragraph.add(lines.get(i++));
        } while (i < lines.size()
            && !lines.get(i).isBlank()
            && !lines.get(i).startsWith("- ")
            && !NUMBERED.matcher(lines.get(i)).matches()
            && !(i + 1 < lines.size()
                && lines.get(i).contains("|")
                && tableRule(lines.get(i + 1))));
        result.append("<p>").append(inline(String.join(" ", paragraph))).append("</p>");
      }
    }
    return result.toString();
  }

  private static int list(List<String> lines, int start, StringBuilder result) {
    boolean ordered = NUMBERED.matcher(lines.get(start)).matches();
    String tag = ordered ? "ol" : "ul";
    result.append('<').append(tag).append('>');
    int at = start;
    while (at < lines.size()) {
      String line = lines.get(at);
      var match = NUMBERED.matcher(line);
      if (ordered ? !match.matches() : !line.startsWith("- ")) break;
      result
          .append("<li>")
          .append(inline(ordered ? match.group(1) : line.substring(2)))
          .append("</li>");
      ++at;
    }
    result.append("</").append(tag).append('>');
    return at;
  }

  private static boolean tableRule(String line) {
    if (!line.contains("|")) return false;
    return cells(line).stream().allMatch(cell -> cell.matches("^:?-{3,}:?$"));
  }

  private static int table(List<String> lines, int start, StringBuilder result) {
    result.append("<table><thead>");
    row(lines.get(start), "th", result);
    result.append("</thead><tbody>");
    int at = start + 2;
    while (at < lines.size() && lines.get(at).contains("|")) {
      row(lines.get(at++), "td", result);
    }
    result.append("</tbody></table>");
    return at;
  }

  private static void row(String line, String element, StringBuilder result) {
    result.append("<tr>");
    for (String item : cells(line)) {
      result
          .append('<')
          .append(element)
          .append('>')
          .append(inline(item))
          .append("</")
          .append(element)
          .append('>');
    }
    result.append("</tr>");
  }

  private static List<String> cells(String line) {
    String trimmed = line.startsWith("|") ? line.substring(1) : line;
    if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
    return java.util.Arrays.stream(trimmed.split("\\|", -1)).map(String::strip).toList();
  }

  private static String inline(String value) {
    Matcher match = EMPHASIS.matcher(value);
    var result = new StringBuilder();
    int cursor = 0;
    while (match.find()) {
      result.append(escape(value.substring(cursor, match.start())));
      boolean bold = match.group(1) != null;
      result
          .append(bold ? "<strong>" : "<em>")
          .append(escape(match.group(bold ? 1 : 2)))
          .append(bold ? "</strong>" : "</em>");
      cursor = match.end();
    }
    return result.append(escape(value.substring(cursor))).toString();
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
