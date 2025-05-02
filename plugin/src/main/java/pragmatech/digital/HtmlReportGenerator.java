package pragmatech.digital;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates an HTML report from test execution data.
 */
public class HtmlReportGenerator {

  /**
   * Generate an HTML report from test execution data.
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

    // HTML header
    writer.println("<!DOCTYPE html>");
    writer.println("<html lang=\"en\">");
    writer.println("<head>");
    writer.println("  <meta charset=\"UTF-8\">");
    writer.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
    writer.println("  <title>Test Execution Time Report</title>");
    writer.println("  <style>");
    writer.println("    body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; color: #333; }");
    writer.println("    .container { max-width: 1200px; margin: 0 auto; }");
    writer.println("    h1 { color: #2c3e50; text-align: center; margin-bottom: 30px; }");
    writer.println("    h2 { color: #3498db; margin-top: 40px; border-bottom: 2px solid #ecf0f1; padding-bottom: 10px; }");
    writer.println("    .summary { background-color: #f8f9fa; border-radius: 5px; padding: 20px; margin-bottom: 30px; }");
    writer.println("    .summary-item { margin-bottom: 10px; }");
    writer.println("    .summary-item strong { color: #2c3e50; }");
    writer.println("    .message { color: #e74c3c; font-style: italic; font-size: 0.9em; margin-top: 5px; }");
    writer.println("    table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
    writer.println("    th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #ddd; }");
    writer.println("    th { background-color: #f2f2f2; color: #333; font-weight: bold; }");
    writer.println("    tr:hover { background-color: #f5f5f5; }");
    writer.println("    .slow { color: #e74c3c; }");
    writer.println("    .fail { background-color: #ffe6e6; }");
    writer.println("    .skip { background-color: #fff9e6; }");
    writer.println("    .chart { height: 400px; margin-top: 30px; margin-bottom: 30px; }");
    writer.println("    .footer { margin-top: 40px; text-align: center; font-size: 0.8em; color: #7f8c8d; }");
    writer.println("    .badge { display: inline-block; padding: 3px 7px; border-radius: 10px; font-size: 12px; font-weight: bold; }");
    writer.println("    .badge-success { background-color: #d4edda; color: #155724; }");
    writer.println("    .badge-danger { background-color: #f8d7da; color: #721c24; }");
    writer.println("    .badge-warning { background-color: #fff3cd; color: #856404; }");
    writer.println("    .badge-info { background-color: #d1ecf1; color: #0c5460; }");
    writer.println("  </style>");
    writer.println("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>");
    writer.println("</head>");
    writer.println("<body>");

    writer.println("<div class=\"container\">");
    writer.println("  <h1>Test Execution Time Report</h1>");

    // Summary section
    writer.println("  <div class=\"summary\">");
    writer.println("    <div class=\"summary-item\"><strong>Project:</strong> " + projectName + "</div>");
    writer.println("    <div class=\"summary-item\"><strong>Total test execution time:</strong> " + formatDuration(totalDuration) + "</div>");
    writer.println("    <div class=\"summary-item\"><strong>Total number of tests:</strong> " + totalTestCount + "</div>");
    writer.println("    <div class=\"summary-item\"><strong>Average test execution time:</strong> " + formatDuration(avgDuration) + "</div>");
    writer.println("    <div class=\"summary-item\"><strong>Slow tests (>" + slowTestThreshold + " ms):</strong> " +
      slowTestCount + " (" + formatPercentage(slowTestCount, totalTestCount) + ")</div>");
    writer.println("    <div class=\"summary-item\"><strong>Generated on:</strong> " +
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</div>");
    writer.println("  </div>");

    // Slowest tests table
    writer.println("  <h2>Slowest Test Methods</h2>");
    writer.println("  <table>");
    writer.println("    <thead>");
    writer.println("      <tr>");
    writer.println("        <th>#</th>");
    writer.println("        <th>Test Method</th>");
    writer.println("        <th>Class</th>");
    writer.println("        <th>Duration</th>");
    writer.println("        <th>Status</th>");
    writer.println("      </tr>");
    writer.println("    </thead>");
    writer.println("    <tbody>");

    int limit = Math.min(topSlowTests, testResults.size());
    for (int i = 0; i < limit; i++) {
      TestResult result = testResults.get(i);
      String cssClass = "";

      if (result.getDuration().toMillis() > slowTestThreshold) {
        cssClass += " slow";
      }

      if (result.isFailed()) {
        cssClass += " fail";
      } else if (result.isSkipped()) {
        cssClass += " skip";
      }

      writer.println("      <tr" + (cssClass.isEmpty() ? "" : " class=\"" + cssClass.trim() + "\"") + ">");
      writer.println("        <td>" + (i + 1) + "</td>");
      writer.println("        <td>" + result.getMethodName() + "</td>");
      writer.println("        <td>" + result.getClassName() + "</td>");
      writer.println("        <td>" + formatDuration(result.getDuration()) + "</td>");
      writer.println("        <td>" + formatStatus(result.getStatus()) + "</td>");
      writer.println("      </tr>");

      // Display error message if available
      if (result.getMessage() != null && !result.getMessage().isEmpty()) {
        writer.println("      <tr>");
        writer.println("        <td colspan=\"5\" class=\"message\">" + result.getMessage() + "</td>");
        writer.println("      </tr>");
      }
    }

    writer.println("    </tbody>");
    writer.println("  </table>");

    // Slowest classes table
    writer.println("  <h2>Slowest Test Classes</h2>");
    writer.println("  <table>");
    writer.println("    <thead>");
    writer.println("      <tr>");
    writer.println("        <th>#</th>");
    writer.println("        <th>Class</th>");
    writer.println("        <th>Total Duration</th>");
    writer.println("        <th>Average Duration</th>");
    writer.println("        <th>Test Count</th>");
    writer.println("        <th>Status</th>");
    writer.println("      </tr>");
    writer.println("    </thead>");
    writer.println("    <tbody>");

    limit = Math.min(topSlowClasses, classResults.size());
    for (int i = 0; i < limit; i++) {
      ClassResult result = classResults.get(i);

      writer.println("      <tr>");
      writer.println("        <td>" + (i + 1) + "</td>");
      writer.println("        <td>" + result.getClassName() + "</td>");
      writer.println("        <td>" + formatDuration(result.getTotalDuration()) + "</td>");
      writer.println("        <td>" + formatDuration(result.getAverageDuration()) + "</td>");
      writer.println("        <td>" + result.getTestCount() + "</td>");
      writer.println("        <td>" +
        "<span class=\"badge badge-success\">" + result.getPassCount() + " passed</span> " +
        (result.getFailCount() > 0 ? "<span class=\"badge badge-danger\">" + result.getFailCount() + " failed</span> " : "") +
        (result.getSkipCount() > 0 ? "<span class=\"badge badge-warning\">" + result.getSkipCount() + " skipped</span>" : "") +
        "</td>");
      writer.println("      </tr>");
    }

    writer.println("    </tbody>");
    writer.println("  </table>");

    // Distribution chart
    writer.println("  <h2>Test Execution Distribution</h2>");
    writer.println("  <div class=\"chart\">");
    writer.println("    <canvas id=\"distributionChart\"></canvas>");
    writer.println("  </div>");

    // Calculate distribution
    Map<String, Long> distribution = calculateDistribution(testResults);

    // JavaScript for chart
    writer.println("  <script>");
    writer.println("    var ctx = document.getElementById('distributionChart').getContext('2d');");
    writer.println("    var distributionChart = new Chart(ctx, {");
    writer.println("      type: 'bar',");
    writer.println("      data: {");
    writer.println("        labels: ['" + String.join("', '",
      new String[]{"< 100ms", "100ms-500ms", "500ms-1s", "1s-5s", "5s-30s", "> 30s"}) + "'],");
    writer.println("        datasets: [{");
    writer.println("          label: 'Number of Tests',");
    writer.println("          data: [" + String.join(", ",
      new String[]{
        distribution.getOrDefault("< 100ms", 0L).toString(),
        distribution.getOrDefault("100ms-500ms", 0L).toString(),
        distribution.getOrDefault("500ms-1s", 0L).toString(),
        distribution.getOrDefault("1s-5s", 0L).toString(),
        distribution.getOrDefault("5s-30s", 0L).toString(),
        distribution.getOrDefault("> 30s", 0L).toString()
      }) + "],");
    writer.println("          backgroundColor: [");
    writer.println("            'rgba(75, 192, 192, 0.2)',");
    writer.println("            'rgba(54, 162, 235, 0.2)',");
    writer.println("            'rgba(255, 206, 86, 0.2)',");
    writer.println("            'rgba(255, 159, 64, 0.2)',");
    writer.println("            'rgba(255, 99, 132, 0.2)',");
    writer.println("            'rgba(153, 102, 255, 0.2)'");
    writer.println("          ],");
    writer.println("          borderColor: [");
    writer.println("            'rgba(75, 192, 192, 1)',");
    writer.println("            'rgba(54, 162, 235, 1)',");
    writer.println("            'rgba(255, 206, 86, 1)',");
    writer.println("            'rgba(255, 159, 64, 1)',");
    writer.println("            'rgba(255, 99, 132, 1)',");
    writer.println("            'rgba(153, 102, 255, 1)'");
    writer.println("          ],");
    writer.println("          borderWidth: 1");
    writer.println("        }]");
    writer.println("      },");
    writer.println("      options: {");
    writer.println("        responsive: true,");
    writer.println("        maintainAspectRatio: false,");
    writer.println("        plugins: {");
    writer.println("          title: {");
    writer.println("            display: true,");
    writer.println("            text: 'Test Duration Distribution'");
    writer.println("          },");
    writer.println("          legend: {");
    writer.println("            display: false");
    writer.println("          }");
    writer.println("        },");
    writer.println("        scales: {");
    writer.println("          y: {");
    writer.println("            beginAtZero: true,");
    writer.println("            title: {");
    writer.println("              display: true,");
    writer.println("              text: 'Number of Tests'");
    writer.println("            }");
    writer.println("          },");
    writer.println("          x: {");
    writer.println("            title: {");
    writer.println("              display: true,");
    writer.println("              text: 'Duration Range'");
    writer.println("            }");
    writer.println("          }");
    writer.println("        }");
    writer.println("      }");
    writer.println("    });");

    // Add pie chart for test status
    writer.println("    var statusCtx = document.getElementById('statusChart').getContext('2d');");
    writer.println("    var statusChart = new Chart(statusCtx, {");
    writer.println("      type: 'pie',");
    writer.println("      data: {");
    writer.println("        labels: ['Passed', 'Failed', 'Skipped'],");
    writer.println("        datasets: [{");
    writer.println("          data: [");

    long passCount = testResults.stream().filter(TestResult::isPassed).count();
    long failCount = testResults.stream().filter(TestResult::isFailed).count();
    long skipCount = testResults.stream().filter(TestResult::isSkipped).count();

    writer.println("            " + passCount + ", " + failCount + ", " + skipCount);
    writer.println("          ],");
    writer.println("          backgroundColor: [");
    writer.println("            'rgba(40, 167, 69, 0.7)',");
    writer.println("            'rgba(220, 53, 69, 0.7)',");
    writer.println("            'rgba(255, 193, 7, 0.7)'");
    writer.println("          ]");
    writer.println("        }]");
    writer.println("      },");
    writer.println("      options: {");
    writer.println("        responsive: true,");
    writer.println("        maintainAspectRatio: false,");
    writer.println("        plugins: {");
    writer.println("          title: {");
    writer.println("            display: true,");
    writer.println("            text: 'Test Status Distribution'");
    writer.println("          }");
    writer.println("        }");
    writer.println("      }");
    writer.println("    });");
    writer.println("  </script>");

    // Status chart
    writer.println("  <h2>Test Status Distribution</h2>");
    writer.println("  <div class=\"chart\">");
    writer.println("    <canvas id=\"statusChart\"></canvas>");
    writer.println("  </div>");

    // Footer
    writer.println("  <div class=\"footer\">");
    writer.println("    <p>Generated by Test Timer Maven Plugin - " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "</p>");
    writer.println("  </div>");

    writer.println("</div>");
    writer.println("</body>");
    writer.println("</html>");
  }

  /**
   * Format a status string with appropriate styling.
   */
  private String formatStatus(String status) {
    switch (status) {
      case "PASS":
        return "<span class=\"badge badge-success\">PASS</span>";
      case "FAIL":
        return "<span class=\"badge badge-danger\">FAIL</span>";
      case "ERROR":
        return "<span class=\"badge badge-danger\">ERROR</span>";
      case "SKIPPED":
        return "<span class=\"badge badge-warning\">SKIPPED</span>";
      default:
        return status;
    }
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
   * Format a percentage value.
   */
  private String formatPercentage(long numerator, long denominator) {
    if (denominator == 0) {
      return "0.0%";
    }

    double percentage = (double) numerator / denominator * 100;
    return String.format("%.1f%%", percentage);
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
}
