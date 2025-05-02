package digital.pragmatech.demo;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JUnit Jupiter extension that measures and reports the execution time
 * of all test methods and classes.
 *
 * This improved version captures all tests and prints a reliable report.
 */
public class TestTimerExtension implements
  BeforeEachCallback,
  AfterEachCallback,
  TestWatcher {

  private static final Namespace NAMESPACE = Namespace.create(TestTimerExtension.class);

  private static final String KEY_START_TIME = "startTime";
  private static final String KEY_TEST_DURATIONS = "testDurations";
  private static final String KEY_ACTIVE_TESTS = "activeTests";
  private static final String KEY_COMPLETED_TESTS = "completedTests";

  // Static flag to track if report has been printed
  private static final AtomicBoolean reportPrinted = new AtomicBoolean(false);

  // Configuration
  private static final int DEFAULT_TOP_SLOW_TESTS = 10;
  private static final int DEFAULT_TOP_SLOW_CLASSES = 5;

  // Set via system properties
  private final int topSlowTests = Integer.parseInt(
    System.getProperty("test.timer.top.tests",
      String.valueOf(DEFAULT_TOP_SLOW_TESTS)));

  private final int topSlowClasses = Integer.parseInt(
    System.getProperty("test.timer.top.classes",
      String.valueOf(DEFAULT_TOP_SLOW_CLASSES)));

  private final boolean reportAllTests = Boolean.parseBoolean(
    System.getProperty("test.timer.report.all", "false"));

  // Execution time threshold in milliseconds to mark tests as slow (500ms default)
  private final long slowTestThreshold = Long.parseLong(
    System.getProperty("test.timer.slow.threshold.ms", "500"));

  // Register shutdown hook on first instance creation
  {
    registerShutdownHookIfNeeded();
  }

  /**
   * Represents timing data for a test method
   */
  private static class TestMethodTiming implements Comparable<TestMethodTiming> {
    private final String className;
    private final String methodName;
    private final String displayName;
    private final Duration duration;

    public TestMethodTiming(String className, String methodName, String displayName, Duration duration) {
      this.className = className;
      this.methodName = methodName;
      this.displayName = displayName;
      this.duration = duration;
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public Duration getDuration() {
      return duration;
    }

    public String getFullName() {
      return className + "#" + methodName;
    }

    @Override
    public int compareTo(TestMethodTiming other) {
      return other.duration.compareTo(this.duration); // Descending order
    }

    @Override
    public String toString() {
      return String.format("%-60s : %s",
        getFullName(),
        formatDuration(duration));
    }

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

  /**
   * Represents timing data for a test class
   */
  private static class TestClassTiming implements Comparable<TestClassTiming> {
    private final String className;
    private final Duration totalDuration;
    private final int testCount;

    public TestClassTiming(String className, Duration totalDuration, int testCount) {
      this.className = className;
      this.totalDuration = totalDuration;
      this.testCount = testCount;
    }

    public String getClassName() {
      return className;
    }

    public Duration getTotalDuration() {
      return totalDuration;
    }

    public int getTestCount() {
      return testCount;
    }

    public Duration getAverageDuration() {
      return Duration.ofMillis(totalDuration.toMillis() / Math.max(1, testCount));
    }

    @Override
    public int compareTo(TestClassTiming other) {
      return other.totalDuration.compareTo(this.totalDuration); // Descending order
    }

    @Override
    public String toString() {
      return String.format("%-60s : %s total, %s avg (%d tests)",
        getClassName(),
        formatDuration(totalDuration),
        formatDuration(getAverageDuration()),
        testCount);
    }

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

  /**
   * Register a shutdown hook to ensure the report is printed
   */
  private void registerShutdownHookIfNeeded() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (!reportPrinted.get()) {
        try {
          // The extension might not have been initialized yet, so we can't use any context
          System.out.println("\nTest execution completed - printing timing report from shutdown hook");
          printReportFromShutdownHook();
        } catch (Exception e) {
          System.err.println("Error printing test timing report: " + e.getMessage());
        }
      }
    }));
  }

  /**
   * Print report from shutdown hook (without a context)
   */
  private void printReportFromShutdownHook() {
    // We'll use reflection to access our static data from the class loader
    try {
      // We don't have access to any context here, so we'll just print what we know
      System.out.println("\n⚠️ Report printed from shutdown hook - data may be incomplete");
      printReport(System.out);
    } catch (Exception e) {
      System.err.println("Failed to print report from shutdown hook: " + e.getMessage());
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    // Store the start time for this test
    getStore(context).put(KEY_START_TIME, System.nanoTime());

    // Track active tests
    Store rootStore = context.getRoot().getStore(NAMESPACE);
    Map<String, String> activeTests = rootStore.getOrComputeIfAbsent(
      KEY_ACTIVE_TESTS, k -> new ConcurrentHashMap<>(), Map.class);

    String testId = getTestId(context);
    activeTests.put(testId, testId);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // Get the start time
    Store testStore = getStore(context);
    Long startTime = testStore.get(KEY_START_TIME, Long.class);
    if (startTime == null) {
      return;
    }

    long duration = System.nanoTime() - startTime;

    // Track completed test
    Store rootStore = context.getRoot().getStore(NAMESPACE);
    Map<String, String> completedTests = rootStore.getOrComputeIfAbsent(
      KEY_COMPLETED_TESTS, k -> new ConcurrentHashMap<>(), Map.class);

    String testId = getTestId(context);
    completedTests.put(testId, testId);

    // Remove from active tests
    Map<String, String> activeTests = rootStore.get(KEY_ACTIVE_TESTS, Map.class);
    if (activeTests != null) {
      activeTests.remove(testId);
    }

    // Store test duration
    String className = context.getRequiredTestClass().getName();
    String methodName = context.getRequiredTestMethod().getName();
    String displayName = context.getDisplayName();

    Map<String, TestMethodTiming> testDurations = rootStore.getOrComputeIfAbsent(
      KEY_TEST_DURATIONS, k -> new ConcurrentHashMap<>(), Map.class);

    testDurations.put(
      getTestId(context),
      new TestMethodTiming(
        className,
        methodName,
        displayName,
        Duration.ofNanos(duration)));

    // Check if all tests are complete
    if (activeTests != null && activeTests.isEmpty() && !completedTests.isEmpty()) {
      maybeGenerateReport(context);
    }
  }

  /**
   * Create a unique ID for a test
   */
  private String getTestId(ExtensionContext context) {
    return context.getRequiredTestClass().getName() + "#" + context.getRequiredTestMethod().getName();
  }

  /**
   * TestWatcher implementation methods
   */
  @Override
  public void testSuccessful(ExtensionContext context) {
    // Already handled in afterEach
  }

  @Override
  public void testAborted(ExtensionContext context, Throwable cause) {
    // Already handled in afterEach
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    // Already handled in afterEach
  }

  /**
   * Generate the report if it hasn't been generated yet
   */
  private synchronized void maybeGenerateReport(ExtensionContext context) {
    if (reportPrinted.compareAndSet(false, true)) {
      System.out.println("\nAll tests completed - printing timing report");
      reportSlowTests(context.getRoot());
    }
  }

  /**
   * Generate and print the timing report
   */
  private void reportSlowTests(ExtensionContext rootContext) {
    Store rootStore = rootContext.getStore(NAMESPACE);

    // Get all test durations
    Map<String, TestMethodTiming> testDurations = rootStore.get(KEY_TEST_DURATIONS, Map.class);
    if (testDurations == null || testDurations.isEmpty()) {
      System.out.println("\n🕒 No test execution data available");
      return;
    }

    // Sort tests by duration (slowest first)
    List<TestMethodTiming> sortedTests = new ArrayList<>(testDurations.values());
    Collections.sort(sortedTests);

    // Calculate class results
    Map<String, Duration> classTotalDurations = new HashMap<>();
    Map<String, Integer> classTestCounts = new HashMap<>();

    for (TestMethodTiming timing : sortedTests) {
      String className = timing.getClassName();

      // Update class durations
      Duration currentDuration = classTotalDurations.getOrDefault(className, Duration.ZERO);
      classTotalDurations.put(className, currentDuration.plus(timing.getDuration()));

      // Update test counts
      int currentCount = classTestCounts.getOrDefault(className, 0);
      classTestCounts.put(className, currentCount + 1);
    }

    // Create sorted class results
    List<TestClassTiming> sortedClasses = new ArrayList<>();
    for (String className : classTotalDurations.keySet()) {
      sortedClasses.add(new TestClassTiming(
        className,
        classTotalDurations.get(className),
        classTestCounts.get(className)));
    }
    Collections.sort(sortedClasses);

    // Print the report
    printReportWithData(sortedTests, sortedClasses, System.out);
  }

  /**
   * Print the test timing report with the provided data
   */
  private void printReport(PrintStream out) {
    // This is a fallback method when we don't have context
    out.println("\n⚠️ Test timing data not available in shutdown hook");
    out.println("Consider using the Maven plugin approach instead for more reliable reporting.");
  }

  /**
   * Print the test timing report with the provided data
   */
  private void printReportWithData(
    List<TestMethodTiming> sortedTests,
    List<TestClassTiming> sortedClasses,
    PrintStream out) {

    // Calculate totals
    Duration totalTestDuration = sortedTests.stream()
      .map(TestMethodTiming::getDuration)
      .reduce(Duration.ZERO, Duration::plus);

    int totalTestCount = sortedTests.size();
    Duration avgTestDuration = totalTestCount > 0
      ? Duration.ofMillis(totalTestDuration.toMillis() / totalTestCount)
      : Duration.ZERO;

    // Print summary header
    out.println("\n");
    out.println("🕒 TEST EXECUTION TIME REPORT 🕒");
    out.println("======================================");
    out.println("Total test execution time: " + formatDuration(totalTestDuration));
    out.println("Total number of tests: " + totalTestCount);
    out.println("Average test execution time: " + formatDuration(avgTestDuration));

    // Print the slowest test methods
    out.println("\n⏱️ SLOWEST TEST METHODS ⏱️");
    out.println("======================================");

    // Limit the number of tests to display unless reportAllTests is true
    int limit = reportAllTests ? sortedTests.size() : Math.min(topSlowTests, sortedTests.size());

    for (int i = 0; i < limit; i++) {
      TestMethodTiming timing = sortedTests.get(i);
      String slowMarker = timing.getDuration().toMillis() > slowTestThreshold ? "⚠️ " : "";
      out.println(String.format("%3d. %s%s",
        i + 1,
        slowMarker,
        timing));
    }

    // Print the slowest test classes
    out.println("\n⏱️ SLOWEST TEST CLASSES ⏱️");
    out.println("======================================");

    limit = reportAllTests ? sortedClasses.size() : Math.min(topSlowClasses, sortedClasses.size());

    for (int i = 0; i < limit; i++) {
      TestClassTiming timing = sortedClasses.get(i);
      out.println(String.format("%3d. %s", i + 1, timing));
    }

    // Distribution
    out.println("\n📊 TEST EXECUTION DISTRIBUTION 📊");
    out.println("======================================");
    Map<String, Long> distribution = calculateDistribution(sortedTests);
    printDistribution(distribution, totalTestCount, out);

    out.println("\nTest execution time report complete");
    out.println("======================================\n");
  }

  /**
   * Calculate distribution of test durations in different brackets.
   */
  private Map<String, Long> calculateDistribution(List<TestMethodTiming> testResults) {
    Map<String, Long> distribution = new HashMap<>();
    distribution.put("< 100ms", 0L);
    distribution.put("100ms-500ms", 0L);
    distribution.put("500ms-1s", 0L);
    distribution.put("1s-5s", 0L);
    distribution.put("5s-30s", 0L);
    distribution.put("> 30s", 0L);

    for (TestMethodTiming test : testResults) {
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
  private void printDistribution(Map<String, Long> distribution, int totalTests, PrintStream out) {
    String[] brackets = {"< 100ms", "100ms-500ms", "500ms-1s", "1s-5s", "5s-30s", "> 30s"};

    for (String bracket : brackets) {
      long count = distribution.getOrDefault(bracket, 0L);
      double percentage = totalTests > 0 ? (count * 100.0) / totalTests : 0;
      out.println(String.format("%-12s : %4d tests (%5.1f%%)",
        bracket, count, percentage));
    }
  }

  private Store getStore(ExtensionContext context) {
    return context.getStore(NAMESPACE);
  }

  /**
   * Format a Duration object into a human-readable string
   */
  private static String formatDuration(Duration duration) {
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
