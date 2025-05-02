package digital.pragmatech.demo;


import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ThreadTrackerExtension.class)
@Execution(ExecutionMode.CONCURRENT)
class ParallelTestWithLoggingTest {

  private static final Logger logger = LoggerFactory.getLogger(ParallelTestWithLoggingTest.class);

  @Test
  void logForkJoinPoolDetails() {
    ForkJoinPool commonPool = ForkJoinPool.commonPool();

    logger.info("===== ForkJoinPool Information =====");
    logger.info("Thread name: {}", Thread.currentThread().getName());
    logger.info("Parallelism level: {}", commonPool.getParallelism());
    logger.info("Common pool size: {}", commonPool.getPoolSize());
    logger.info("Active thread count: {}", commonPool.getActiveThreadCount());
    logger.info("Running thread count: {}", commonPool.getRunningThreadCount());
    logger.info("Queued submissions: {}", commonPool.getQueuedSubmissionCount());
    logger.info("Queued tasks: {}", commonPool.getQueuedTaskCount());
    logger.info("Steal count: {}", commonPool.getStealCount());
    logger.info("Available processors: {}", Runtime.getRuntime().availableProcessors());
  }

  @Test
  void logJMXThreadData() {
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    long[] threadIds = threadMXBean.getAllThreadIds();
    ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds);

    logger.info("===== JMX Thread Information =====");
    logger.info("Total thread count: {}", threadIds.length);

    // Filter for ForkJoinPool worker threads
    String fjpThreads = Arrays.stream(threadInfos)
      .filter(info -> info != null)
      .filter(info -> info.getThreadName().contains("ForkJoin"))
      .map(ThreadInfo::getThreadName)
      .collect(Collectors.joining(", "));

    logger.info("ForkJoinPool worker threads: {}", fjpThreads);
  }

  @RepeatedTest(5)
  void repeatedParallelTest(TestInfo testInfo) throws InterruptedException {
    logger.info("Executing test: {} on thread: {}",
      testInfo.getDisplayName(), Thread.currentThread().getName());

    // Sleep a bit to ensure we can see parallelism in action
    TimeUnit.MILLISECONDS.sleep(100);
  }
}
