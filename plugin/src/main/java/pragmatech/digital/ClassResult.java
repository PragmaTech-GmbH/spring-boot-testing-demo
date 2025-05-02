package pragmatech.digital;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Class representing aggregate results for a test class.
 */
public class ClassResult {
  private final String className;
  private Duration totalDuration = Duration.ZERO;
  private int testCount = 0;
  private int passCount = 0;
  private int failCount = 0;
  private int skipCount = 0;
  private final List<TestResult> tests = new ArrayList<>();

  public ClassResult(String className) {
    this.className = className;
  }

  /**
   * Add a test result to this class result.
   */
  public void addTest(TestResult test) {
    this.totalDuration = this.totalDuration.plus(test.getDuration());
    this.testCount++;

    if (test.isPassed()) {
      this.passCount++;
    } else if (test.isFailed()) {
      this.failCount++;
    } else if (test.isSkipped()) {
      this.skipCount++;
    }

    this.tests.add(test);
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

  public int getPassCount() {
    return passCount;
  }

  public int getFailCount() {
    return failCount;
  }

  public int getSkipCount() {
    return skipCount;
  }

  public List<TestResult> getTests() {
    return tests;
  }

  public Duration getAverageDuration() {
    return testCount > 0
      ? Duration.ofMillis(totalDuration.toMillis() / testCount)
      : Duration.ZERO;
  }

  public Duration getMaxDuration() {
    return tests.stream()
      .map(TestResult::getDuration)
      .max(Duration::compareTo)
      .orElse(Duration.ZERO);
  }

  public Duration getMinDuration() {
    return tests.stream()
      .map(TestResult::getDuration)
      .min(Duration::compareTo)
      .orElse(Duration.ZERO);
  }

  @Override
  public String toString() {
    return String.format("%s: %s total, %s avg (%d tests - %d passed, %d failed, %d skipped)",
      getClassName(),
      formatDuration(getTotalDuration()),
      formatDuration(getAverageDuration()),
      getTestCount(),
      getPassCount(),
      getFailCount(),
      getSkipCount());
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
