package pragmatech.digital;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maven plugin that analyzes test execution times from Surefire and Failsafe reports
 * and generates a detailed timing report.
 */
@Mojo(name = "report", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST)
public class TestTimerMojo extends AbstractMojo {

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /**
   * Directory containing the Surefire reports.
   */
  @Parameter(defaultValue = "${project.build.directory}/surefire-reports", property = "surefireReportDirectory")
  private File surefireReportDirectory;

  /**
   * Directory containing the Failsafe reports.
   */
  @Parameter(defaultValue = "${project.build.directory}/failsafe-reports", property = "failsafeReportDirectory")
  private File failsafeReportDirectory;

  /**
   * Whether to include Failsafe reports in the analysis.
   */
  @Parameter(defaultValue = "true", property = "includeFailsafeReports")
  private boolean includeFailsafeReports;

  /**
   * Number of slowest tests to show.
   */
  @Parameter(defaultValue = "20", property = "topSlowTests")
  private int topSlowTests;

  /**
   * Number of slowest test classes to show.
   */
  @Parameter(defaultValue = "10", property = "topSlowClasses")
  private int topSlowClasses;

  /**
   * Threshold in milliseconds to mark tests as slow.
   */
  @Parameter(defaultValue = "1000", property = "slowTestThreshold")
  private long slowTestThreshold;

  /**
   * Whether to generate an HTML report.
   */
  @Parameter(defaultValue = "true", property = "generateHtmlReport")
  private boolean generateHtmlReport;

  /**
   * Output file for HTML report.
   */
  @Parameter(defaultValue = "${project.build.directory}/test-timer-report.html", property = "htmlReportFile")
  private File htmlReportFile;

  /**
   * Whether to generate a JSON report.
   */
  @Parameter(defaultValue = "false", property = "generateJsonReport")
  private boolean generateJsonReport;

  /**
   * Output file for JSON report.
   */
  @Parameter(defaultValue = "${project.build.directory}/test-timer-report.json", property = "jsonReportFile")
  private File jsonReportFile;

  /**
   * Whether to fail the build if there are slow tests.
   */
  @Parameter(defaultValue = "false", property = "failOnSlowTests")
  private boolean failOnSlowTests;

  /**
   * Threshold for the number of slow tests before failing the build.
   */
  @Parameter(defaultValue = "5", property = "slowTestsFailureThreshold")
  private int slowTestsFailureThreshold;

  /**
   * Whether to skip plugin execution.
   */
  @Parameter(defaultValue = "false", property = "skip")
  private boolean skip;

  /**
   * Execute the plugin.
   */
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Skipping test-timer-maven-plugin execution");
      return;
    }

    // Parse all test results
    List<TestResult> testResults = parseTestResults();

    if (testResults.isEmpty()) {
      getLog().info("No test results found");
      return;
    }

    // Sort by duration (slowest first)
    testResults.sort(Comparator.comparing(TestResult::getDuration).reversed());

    // Calculate class results
    Map<String, ClassResult> classResults = calculateClassResults(testResults);
    List<ClassResult> sortedClasses = new ArrayList<>(classResults.values());
    sortedClasses.sort(Comparator.comparing(ClassResult::getTotalDuration).reversed());

    // Calculate totals
    Duration totalDuration = testResults.stream()
      .map(TestResult::getDuration)
      .reduce(Duration.ZERO, Duration::plus);

    int totalTestCount = testResults.size();
    Duration avgDuration = totalTestCount > 0
      ? Duration.ofMillis(totalDuration.toMillis() / totalTestCount)
      : Duration.ZERO;

    // Count slow tests
    long slowTestCount = testResults.stream()
      .filter(test -> test.getDuration().toMillis() > slowTestThreshold)
      .count();

    // Print report to console
    printConsoleReport(testResults, sortedClasses, totalDuration, totalTestCount, avgDuration, slowTestCount);

    // Generate HTML report
    if (generateHtmlReport) {
      try {
        generateHtmlReport(testResults, sortedClasses, totalDuration, totalTestCount, avgDuration, slowTestCount);
        getLog().info("HTML report generated at: " + htmlReportFile.getAbsolutePath());
      } catch (Exception e) {
        getLog().error("Failed to generate HTML report", e);
      }
    }

    // Generate JSON report
    if (generateJsonReport) {
      try {
        generateJsonReport(testResults, sortedClasses, totalDuration, totalTestCount, avgDuration, slowTestCount);
        getLog().info("JSON report generated at: " + jsonReportFile.getAbsolutePath());
      } catch (Exception e) {
        getLog().error("Failed to generate JSON report", e);
      }
    }

    // Maybe fail the build
    if (failOnSlowTests && slowTestCount >= slowTestsFailureThreshold) {
      throw new MojoExecutionException(
        String.format("Build failed because %d tests exceeded the slow test threshold of %d ms",
          slowTestCount, slowTestThreshold));
    }
  }

  /**
   * Parse all test results from Surefire and Failsafe XML reports.
   */
  private List<TestResult> parseTestResults() {
    List<TestResult> results = new ArrayList<>();

    // Parse Surefire reports
    parseSurefireReports(results);

    // Parse Failsafe reports if enabled
    if (includeFailsafeReports) {
      parseFailsafeReports(results);
    }

    return results;
  }

  /**
   * Parse Surefire reports.
   */
  private void parseSurefireReports(List<TestResult> results) {
    if (surefireReportDirectory == null || !surefireReportDirectory.exists() || !surefireReportDirectory.isDirectory()) {
      getLog().debug("Surefire report directory not found: " +
        (surefireReportDirectory != null ? surefireReportDirectory.getAbsolutePath() : "null"));
      return;
    }

    parseReports(surefireReportDirectory.toPath(), results);
  }

  /**
   * Parse Failsafe reports.
   */
  private void parseFailsafeReports(List<TestResult> results) {
    if (failsafeReportDirectory == null || !failsafeReportDirectory.exists() || !failsafeReportDirectory.isDirectory()) {
      getLog().debug("Failsafe report directory not found: " +
        (failsafeReportDirectory != null ? failsafeReportDirectory.getAbsolutePath() : "null"));
      return;
    }

    parseReports(failsafeReportDirectory.toPath(), results);
  }

  /**
   * Parse all XML reports in a directory.
   */
  private void parseReports(Path directoryPath, List<TestResult> results) {
    try {
      try (Stream<Path> paths = Files.walk(directoryPath)) {
        List<Path> xmlFiles = paths
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".xml"))
          .collect(Collectors.toList());

        if (xmlFiles.isEmpty()) {
          getLog().debug("No XML files found in " + directoryPath);
        }

        JUnitXmlParser parser = new JUnitXmlParser();

        for (Path xmlFile : xmlFiles) {
          try {
            getLog().debug("Parsing XML file: " + xmlFile);
            List<TestResult> fileResults = parser.parseFile(xmlFile);
            results.addAll(fileResults);
          } catch (Exception e) {
            getLog().warn("Error parsing file " + xmlFile + ": " + e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      getLog().warn("Error parsing test results: " + e.getMessage());
    }
  }

  /**
   * Calculate class results from test results.
   */
  private Map<String, ClassResult> calculateClassResults(List<TestResult> testResults) {
    Map<String, ClassResult> classResults = new HashMap<>();

    for (TestResult result : testResults) {
      String className = result.getClassName();
      ClassResult classResult = classResults.computeIfAbsent(
        className, k -> new ClassResult(className));
      classResult.addTest(result);
    }

    return classResults;
  }

  /**
   * Print the report to the console.
   */
  private void printConsoleReport(
    List<TestResult> testResults,
    List<ClassResult> classResults,
    Duration totalDuration,
    int totalTestCount,
    Duration avgDuration,
    long slowTestCount) {

    getLog().info("");
    getLog().info("🕒 TEST EXECUTION TIME REPORT 🕒");
    getLog().info("======================================");
    getLog().info("Total test execution time: " + formatDuration(totalDuration));
    getLog().info("Total number of tests: " + totalTestCount);
    getLog().info("Average test execution time: " + formatDuration(avgDuration));
    getLog().info("Slow tests (>" + slowTestThreshold + " ms): " + slowTestCount);

    // Print slowest tests
    getLog().info("");
    getLog().info("⏱️ SLOWEST TEST METHODS ⏱️");
    getLog().info("======================================");

    int limit = Math.min(topSlowTests, testResults.size());
    for (int i = 0; i < limit; i++) {
      TestResult result = testResults.get(i);
      String slowMarker = result.getDuration().toMillis() > slowTestThreshold ? "⚠️ " : "";
      getLog().info(String.format("%3d. %s%s",
        i + 1,
        slowMarker,
        formatTestResult(result)));
    }

    // Print slowest classes
    getLog().info("");
    getLog().info("⏱️ SLOWEST TEST CLASSES ⏱️");
    getLog().info("======================================");

    limit = Math.min(topSlowClasses, classResults.size());
    for (int i = 0; i < limit; i++) {
      ClassResult result = classResults.get(i);
      getLog().info(String.format("%3d. %s", i + 1, formatClassResult(result)));
    }

    // Distribution
    getLog().info("");
    getLog().info("📊 TEST EXECUTION DISTRIBUTION 📊");
    getLog().info("======================================");
    Map<String, Long> distribution = calculateDistribution(testResults);
    printDistribution(distribution, totalTestCount);

    getLog().info("");
  }

  /**
   * Format a test result for console output.
   */
  private String formatTestResult(TestResult result) {
    return String.format("%-60s : %s",
      result.getClassName() + "#" + result.getMethodName(),
      formatDuration(result.getDuration()));
  }

  /**
   * Format a class result for console output.
   */
  private String formatClassResult(ClassResult result) {
    return String.format("%-60s : %s total, %s avg (%d tests)",
      result.getClassName(),
      formatDuration(result.getTotalDuration()),
      formatDuration(result.getAverageDuration()),
      result.getTestCount());
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
   * Print the distribution of test durations.
   */
  private void printDistribution(Map<String, Long> distribution, int totalTests) {
    String[] brackets = {"< 100ms", "100ms-500ms", "500ms-1s", "1s-5s", "5s-30s", "> 30s"};

    for (String bracket : brackets) {
      long count = distribution.getOrDefault(bracket, 0L);
      double percentage = totalTests > 0 ? (count * 100.0) / totalTests : 0;
      getLog().info(String.format("%-12s : %4d tests (%5.1f%%)",
        bracket, count, percentage));
    }
  }

  /**
   * Generate an HTML report of the test timing results.
   */
  private void generateHtmlReport(
    List<TestResult> testResults,
    List<ClassResult> classResults,
    Duration totalDuration,
    int totalTestCount,
    Duration avgDuration,
    long slowTestCount) throws Exception {

    // Ensure parent directory exists
    htmlReportFile.getParentFile().mkdirs();

    try (PrintWriter writer = new PrintWriter(new FileWriter(htmlReportFile))) {
      // Generate the HTML report
      new HtmlReportGenerator().generateReport(
        writer,
        testResults,
        classResults,
        totalDuration,
        totalTestCount,
        avgDuration,
        slowTestCount,
        slowTestThreshold,
        topSlowTests,
        topSlowClasses,
        project.getName());
    }
  }

  /**
   * Generate a JSON report of the test timing results.
   */
  private void generateJsonReport(
    List<TestResult> testResults,
    List<ClassResult> classResults,
    Duration totalDuration,
    int totalTestCount,
    Duration avgDuration,
    long slowTestCount) throws Exception {

    // Ensure parent directory exists
    jsonReportFile.getParentFile().mkdirs();

    try (PrintWriter writer = new PrintWriter(new FileWriter(jsonReportFile))) {
      // Generate the JSON report
      new JsonReportGenerator().generateReport(
        writer,
        testResults,
        classResults,
        totalDuration,
        totalTestCount,
        avgDuration,
        slowTestCount,
        slowTestThreshold,
        topSlowTests,
        topSlowClasses,
        project.getName());
    }
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
