package digital.pragmatech.demo;


import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Execution(ExecutionMode.CONCURRENT)
public class ThreadPoolLoggerTest {

  private static final Logger logger = LoggerFactory.getLogger(ThreadPoolLoggerTest.class);

  @Test
  void logThreadPoolInfo() {
    Thread currentThread = Thread.currentThread();
    String threadName = currentThread.getName();

    // Check if we're running in a ForkJoinPool worker thread
    if (threadName.startsWith("ForkJoinPool")) {
      ForkJoinPool pool = ForkJoinPool.commonPool();

      logger.info("JUnit Jupiter Thread Pool Information:");
      logger.info("Thread Name: {}", threadName);
      logger.info("Pool Name: ForkJoinPool.commonPool()");
      logger.info("Parallelism Level: {}", pool.getParallelism());
      logger.info("Pool Size: {}", pool.getPoolSize());
      logger.info("Active Thread Count: {}", pool.getActiveThreadCount());
      logger.info("Running Thread Count: {}", pool.getRunningThreadCount());
      logger.info("Queued Task Count: {}", pool.getQueuedTaskCount());
      logger.info("Steal Count: {}", pool.getStealCount());

      // Also log available processors
      logger.info("Available Processors: {}", Runtime.getRuntime().availableProcessors());
    } else {
      // We're not running in the ForkJoinPool
      logger.info("Not running in ForkJoinPool. Current thread: {}", threadName);
    }
  }

  @Test
  void logThreadPoolInfo2() {
    // Adding a second test to see multiple threads in action
    logThreadPoolInfo();
  }

  @Test
  void logThreadPoolInfo3() {
    // Adding a third test to see multiple threads in action
    logThreadPoolInfo();
  }
}
