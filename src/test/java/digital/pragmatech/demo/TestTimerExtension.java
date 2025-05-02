package digital.pragmatech.demo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * JUnit Jupiter extension that measures and reports the execution time
 * of all test methods and classes, listing the slowest ones at the end.
 * <p>
 * Usage:
 * 1. Register the extension globally via the ServiceLoader mechanism
 * 2. Add directly to a test class: @ExtendWith(TestTimerExtension.class)
 * 3. Register programmatically
 */
public class TestTimerExtension implements
  BeforeTestExecutionCallback,
  AfterTestExecutionCallback,
  AfterAllCallback {

  private static final Namespace NAMESPACE = Namespace.create(TestTimerExtension.class);

  private static final String KEY_START_TIME = "startTime";
  private static final String KEY_TEST_DURATIONS = "testDurations";
  private static final String KEY_CLASS_DURATIONS = "classDurations";

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
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) {
    getStore(context).put(KEY_START_TIME, System.nanoTime());
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    Store store = getStore(context);
    long startTime = store.get(KEY_START_TIME, long.class);
    long duration = System.nanoTime() - startTime;

    // Get the test method details
    String className = context.getRequiredTestClass().getName();
    String methodName = context.getRequiredTestMethod().getName();
    String displayName = context.getDisplayName();

    // Store test duration in the root store
    Store rootStore = context.getRoot().getStore(NAMESPACE);
    Map<String, TestMethodTiming> testDurations = rootStore.getOrComputeIfAbsent(
      KEY_TEST_DURATIONS, k -> new ConcurrentHashMap<>(), Map.class);

    testDurations.put(
      className + "#" + methodName,
      new TestMethodTiming(
        className,
        methodName,
        displayName,
        Duration.ofNanos(duration)));

    // Update class durations
    Map<String, ClassStats> classDurations = rootStore.getOrComputeIfAbsent(
      KEY_CLASS_DURATIONS, k -> new ConcurrentHashMap<>(), Map.class);

    classDurations.compute(className, (key, stats) -> {
      if (stats == null) {
        stats = new ClassStats();
      }
      stats.addTest(duration);
      return stats;
    });
  }

  /**
   * Helper class to track class execution stats
   */
  private static class ClassStats {
    private long totalDuration = 0;
    private int testCount = 0;

    public void addTest(long duration) {
      totalDuration += duration;
      testCount++;
    }

    public long getTotalDuration() {
      return totalDuration;
    }

    public int getTestCount() {
      return testCount;
    }
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Only report after the root test container completes
    reportSlowTests(context.getRoot());

  }

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

    // Get class durations
    Map<String, ClassStats> classDurations = rootStore.get(KEY_CLASS_DURATIONS, Map.class);
    if (classDurations == null) {
      classDurations = Collections.emptyMap();
    }

    // Create class timing objects
    List<TestClassTiming> sortedClasses = createSortedClassTimings(classDurations);

    // Calculate totals
    Duration totalTestDuration = calculateTotalDuration(sortedTests);
    int totalTestCount = sortedTests.size();
    Duration avgTestDuration = totalTestCount > 0
      ? Duration.ofMillis(totalTestDuration.toMillis() / totalTestCount)
      : Duration.ZERO;

    // Print summary header
    System.out.println("\n");
    System.out.println("🕒 TEST EXECUTION TIME REPORT 🕒");
    System.out.println("======================================");
    System.out.println(String.format("Total test execution time: %s", formatDuration(totalTestDuration)));
    System.out.println(String.format("Total number of tests: %d", totalTestCount));
    System.out.println(String.format("Average test execution time: %s", formatDuration(avgTestDuration)));

    // Print the slowest test methods
    System.out.println("\n⏱️ SLOWEST TEST METHODS ⏱️");
    System.out.println("======================================");

    // Limit the number of tests to display unless reportAllTests is true
    int limit = reportAllTests ? sortedTests.size() : Math.min(topSlowTests, sortedTests.size());

    for (int i = 0; i < limit; i++) {
      TestMethodTiming timing = sortedTests.get(i);
      String slowMarker = timing.getDuration().toMillis() > slowTestThreshold ? "⚠️ " : "";
      System.out.println(String.format("%3d. %s%s",
        i + 1,
        slowMarker,
        timing));
    }

    // Print the slowest test classes
    System.out.println("\n⏱️ SLOWEST TEST CLASSES ⏱️");
    System.out.println("======================================");

    limit = reportAllTests ? sortedClasses.size() : Math.min(topSlowClasses, sortedClasses.size());

    for (int i = 0; i < limit; i++) {
      TestClassTiming timing = sortedClasses.get(i);
      System.out.println(String.format("%3d. %s", i + 1, timing));
    }

    System.out.println("\n📊 TEST EXECUTION DISTRIBUTION 📊");
    System.out.println("======================================");
    printTestDurationDistribution(sortedTests);

    System.out.println("\nTest execution time report complete");
    System.out.println("======================================\n");
  }

  /**
   * Creates a sorted list of TestClassTiming objects from the raw class stats
   */
  private List<TestClassTiming> createSortedClassTimings(Map<String, ClassStats> classDurations) {
    List<TestClassTiming> sortedClasses = new ArrayList<>();

    for (Map.Entry<String, ClassStats> entry : classDurations.entrySet()) {
      ClassStats stats = entry.getValue();
      sortedClasses.add(new TestClassTiming(
        entry.getKey(),
        Duration.ofNanos(stats.getTotalDuration()),
        stats.getTestCount()));
    }

    Collections.sort(sortedClasses);
    return sortedClasses;
  }

  /**
   * Calculates the total duration of all tests
   */
  private Duration calculateTotalDuration(List<TestMethodTiming> tests) {
    return tests.stream()
      .map(TestMethodTiming::getDuration)
      .reduce(Duration.ZERO, Duration::plus);
  }

  /**
   * Prints the distribution of test durations in different time brackets
   */
  private void printTestDurationDistribution(List<TestMethodTiming> tests) {
    Map<String, Long> distribution = new HashMap<>();
    distribution.put("< 100ms", 0L);
    distribution.put("100ms-500ms", 0L);
    distribution.put("500ms-1s", 0L);
    distribution.put("1s-5s", 0L);
    distribution.put("5s-30s", 0L);
    distribution.put("> 30s", 0L);

    for (TestMethodTiming test : tests) {
      long millis = test.getDuration().toMillis();
      String bracket;

      if (millis < 100) {
        bracket = "< 100ms";
      }
      else if (millis < 500) {
        bracket = "100ms-500ms";
      }
      else if (millis < 1000) {
        bracket = "500ms-1s";
      }
      else if (millis < 5000) {
        bracket = "1s-5s";
      }
      else if (millis < 30000) {
        bracket = "5s-30s";
      }
      else {
        bracket = "> 30s";
      }

      distribution.put(bracket, distribution.get(bracket) + 1);
    }

    // Display in order
    String[] brackets = {"< 100ms", "100ms-500ms", "500ms-1s", "1s-5s", "5s-30s", "> 30s"};
    for (String bracket : brackets) {
      long count = distribution.get(bracket);
      double percentage = tests.isEmpty() ? 0 : (count * 100.0) / tests.size();
      System.out.println(String.format("%-12s : %4d tests (%5.1f%%)",
        bracket, count, percentage));
    }
  }

  private Store getStore(ExtensionContext context) {
    return context.getStore(NAMESPACE);
  }

  /**
   * Formats a Duration object into a human-readable string
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
    }
    else {
      return String.format("%d.%03d sec", seconds, millis);
    }
  }
}
