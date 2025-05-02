package pragmatech.digital;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a JSON report from test execution data.
 */
public class JsonReportGenerator {

  /**
   * Generate a JSON report from test execution data.
   */
  public void generateReport(
    PrintWriter writer,
    List<TestResult> testResults,
    List<ClassResult> classResults,
    Duration totalDuration,
    int totalTestCount,
    Duration avgDuration,
    long slowTestCount,
    long slowTestThreshold,
    int topSlowTests,
    int topSlowClasses,
    String projectName) {

    writer.println("{");

    // Basic info
    writer.println("  \"projectName\": \"" + escapeJson(projectName) + "\",");
    writer.println("  \"timestamp\": \"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
    writer.println("  \"summary\": {");
    writer.println("    \"totalDuration\": " + totalDuration.toMillis() + ",");
    writer.println("    \"totalDurationFormatted\": \"" + formatDuration(totalDuration) + "\",");
    writer.println("    \"totalTestCount\": " + totalTestCount + ",");
    writer.println("    \"averageDuration\": " + avgDuration.toMillis() + ",");
    writer.println("    \"averageDurationFormatted\": \"" + formatDuration(avgDuration) + "\",");
    writer.println("    \"slowTestCount\": " + slowTestCount + ",");
    writer.println("    \"slowTestThreshold\": " + slowTestThreshold);
    writer.println("  },");

    // Test distribution
    Map<String, Long> distribution = calculateDistribution(testResults);
    writer.println("  \"distribution\": {");

    String[] brackets = {"< 100ms", "100ms-500ms", "500ms-1s", "1s-5s", "5s-30s", "> 30s"};
    for (int i = 0; i < brackets.length; i++) {
      String bracket = brackets[i];
      writer.print("    \"" + bracket + "\": " + distribution.getOrDefault(bracket, 0L));

      if (i < brackets.length - 1) {
        writer.println(",");
      } else {
        writer.println();
      }
    }
    writer.println("  },");

    // Test status counts
    long passCount = testResults.stream().filter(TestResult::isPassed).count();
    long failCount = testResults.stream().filter(TestResult::isFailed).count();
    long skipCount = testResults.stream().filter(TestResult::isSkipped).count();

    writer.println("  \"statusCounts\": {");
    writer.println("    \"passed\": " + passCount + ",");
    writer.println("    \"failed\": " + failCount + ",");
    writer.println("    \"skipped\": " + skipCount);
    writer.println("  },");

    // Slowest tests
    writer.println("  \"slowestTests\": [");

    int limit = Math.min(topSlowTests, testResults.size());
    for (int i = 0; i < limit; i++) {
      TestResult result = testResults.get(i);

      writer.println("    {");
      writer.println("      \"className\": \"" + escapeJson(result.getClassName()) + "\",");
      writer.println("      \"methodName\": \"" + escapeJson(result.getMethodName()) + "\",");
      writer.println("      \"displayName\": \"" + escapeJson(result.getDisplayName()) + "\",");
      writer.println("      \"duration\": " + result.getDuration().toMillis() + ",");
      writer.println("      \"durationFormatted\": \"" + formatDuration(result.getDuration()) + "\",");
      writer.println("      \"status\": \"" + result.getStatus() + "\",");

      if (result.getMessage() != null && !result.getMessage().isEmpty()) {
        writer.println("      \"message\": \"" + escapeJson(result.getMessage()) + "\",");
      }

      writer.println("      \"isSlow\": " + (result.getDuration().toMillis() > slowTestThreshold));
      writer.print("    }");

      if (i < limit - 1) {
        writer.println(",");
      } else {
        writer.println();
      }
    }

    writer.println("  ],");

    // Slowest classes
    writer.println("  \"slowestClasses\": [");

    limit = Math.min(topSlowClasses, classResults.size());
    for (int i = 0; i < limit; i++) {
      ClassResult result = classResults.get(i);

      writer.println("    {");
      writer.println("      \"className\": \"" + escapeJson(result.getClassName()) + "\",");
      writer.println("      \"totalDuration\": " + result.getTotalDuration().toMillis() + ",");
      writer.println("      \"totalDurationFormatted\": \"" + formatDuration(result.getTotalDuration()) + "\",");
      writer.println("      \"averageDuration\": " + result.getAverageDuration().toMillis() + ",");
      writer.println("      \"averageDurationFormatted\": \"" + formatDuration(result.getAverageDuration()) + "\",");
      writer.println("      \"testCount\": " + result.getTestCount() + ",");
      writer.println("      \"passCount\": " + result.getPassCount() + ",");
      writer.println("      \"failCount\": " + result.getFailCount() + ",");
      writer.println("      \"skipCount\": " + result.getSkipCount());
      writer.print("    }");

      if (i < limit - 1) {
        writer.println(",");
      } else {
        writer.println();
      }
    }

    writer.println("  ]");
    writer.println("}");
  }

  /**
   * Calculate distribution of test durations in different brackets.
   */
  private Map<String, Long> calculateDistribution(List<TestResult> testResults) {
    Map<String, Long> distribution = new HashMap<>();
    distribution.put("< 100ms", 0L);
    distribution.put("100ms-500ms", 0L);
    distribution.put("500ms-1s", 0L);
    distribution.put("1s-5s", 0L);
    distribution.put("5s-30s", 0L);
    distribution.put("> 30s", 0L);

    for (TestResult test : testResults) {
      long millis = test.getDuration().toMillis();
      String bracket;

      if (millis < 100) bracket = "< 100ms";
      else if (millis < 500) bracket = "100ms-500ms";
      else if (millis < 1000) bracket = "500ms-1s";
      else if (millis < 5000) bracket = "1s-5s";
      else if (millis < 30000) bracket = "5s-30s";
      else bracket = "> 30s";

      distribution.put(bracket, distribution.get(bracket) + 1);
    }

    return distribution;
  }

  /**
   * Format a Duration object into a readable string.
   */
  private String formatDuration(Duration duration) {
    long totalMillis = duration.toMillis();

    if (totalMillis < 1000) {
      return String.format("%d ms", totalMillis);
    }

    long minutes = duration.toMinutes();
    long seconds = duration.minusMinutes(minutes).getSeconds();
    long millis = duration.minusMinutes(minutes).minusSeconds(seconds).toMillis();

    if (minutes > 0) {
      return String.format("%d min %d.%03d sec", minutes, seconds, millis);
    } else {
      return String.format("%d.%03d sec", seconds, millis);
    }
  }

  /**
   * Escape special characters for JSON strings.
   */
  private String escapeJson(String text) {
    if (text == null) {
      return "";
    }

    return text.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
      .replace("\b", "\\b")
      .replace("\f", "\\f");
  }
}
