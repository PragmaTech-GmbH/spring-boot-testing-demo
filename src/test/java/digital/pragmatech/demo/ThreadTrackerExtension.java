package digital.pragmatech.demo;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadTrackerExtension implements BeforeEachCallback, BeforeAllCallback, AfterAllCallback {

  private static final Logger logger = LoggerFactory.getLogger(ThreadTrackerExtension.class);
  private static final Set<String> usedThreads = ConcurrentHashMap.newKeySet();
  private static final Set<String> threadPoolNames = ConcurrentHashMap.newKeySet();

  @Override
  public void beforeAll(ExtensionContext context) {
    logger.info("Starting test execution with thread tracking");
    usedThreads.clear();
    threadPoolNames.clear();
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    Thread currentThread = Thread.currentThread();
    String threadName = currentThread.getName();

    usedThreads.add(threadName);

    // Extract thread pool name if it's a worker thread
    if (threadName.contains("-worker-")) {
      String[] parts = threadName.split("-worker-");
      if (parts.length > 0) {
        threadPoolNames.add(parts[0]);
      }
    }

    logger.info("Test '{}' executing on thread: {}", context.getDisplayName(), threadName);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    logger.info("Thread pool summary for test class: {}", context.getDisplayName());
    logger.info("Thread pools used: {}", threadPoolNames);
    logger.info("Total unique threads used: {}", usedThreads.size());
    logger.info("All threads used: {}", usedThreads);
  }
}
