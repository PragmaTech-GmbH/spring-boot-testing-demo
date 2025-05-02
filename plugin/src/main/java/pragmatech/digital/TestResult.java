package pragmatech.digital;


import java.time.Duration;

/**
 * Class representing a single test result with timing information.
 */
public class TestResult {
  private final String className;
  private final String methodName;
  private final String displayName;
  private final Duration duration;
  private final String status; // PASS, FAIL, ERROR, SKIPPED
  private final String message; // Error message if applicable

  public TestResult(String className, String methodName, String displayName, Duration duration, String status) {
    this(className, methodName, displayName, duration, status, null);
  }

  public TestResult(String className, String methodName, String displayName, Duration duration, String status, String message) {
    this.className = className;
    this.methodName = methodName;
    this.displayName = displayName;
    this.duration = duration;
    this.status = status;
    this.message = message;
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

  public String getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public boolean isPassed() {
    return "PASS".equals(status);
  }

  public boolean isFailed() {
    return "FAIL".equals(status) || "ERROR".equals(status);
  }

  public boolean isSkipped() {
    return "SKIPPED".equals(status);
  }

  @Override
  public String toString() {
    return String.format("%s#%s: %s (%s)", className, methodName, formatDuration(duration), status);
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
