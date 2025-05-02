package digital.pragmatech.demo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;

/**
 * JUnit Jupiter extension that logs detailed statistics about Spring's test context cache.
 * <p>
 * This extension:
 * - Tracks cache hits and misses for each test
 * - Detects context creation and reuse
 * - Identifies @DirtiesContext usage
 * - Shows memory usage and performance impacts
 * <p>
 * Usage:
 * Add @ExtendWith(SpringContextCacheStatsExtension.class) to your Spring Boot test classes.
 */
public class SpringContextCacheStatsExtension implements BeforeEachCallback, AfterEachCallback {

  private static final Logger logger = LoggerFactory.getLogger(SpringContextCacheStatsExtension.class);

  // Namespace for storing extension data
  private static final Namespace NAMESPACE = Namespace.create(SpringContextCacheStatsExtension.class);

  // Keys for the extension store
  private static final String KEY_TEST_COUNT = "testCount";
  private static final String KEY_CACHE_STATS = "cacheStats";
  private static final String KEY_START_TIME = "startTime";
  private static final String KEY_START_MEMORY = "startMemory";

  // Runtime tracking
  private static final Runtime runtime = Runtime.getRuntime();

  @Override
  public void beforeEach(ExtensionContext context) {
    // Get or create test counter in the root store
    Store rootStore = context.getRoot().getStore(NAMESPACE);
    AtomicInteger testCount = rootStore.getOrComputeIfAbsent(KEY_TEST_COUNT,
      k -> new AtomicInteger(0), AtomicInteger.class);

    // Increment test count
    int currentTest = testCount.incrementAndGet();

    // Store timing and memory info for this test
    Store testStore = context.getStore(NAMESPACE);
    testStore.put(KEY_START_TIME, System.currentTimeMillis());
    testStore.put(KEY_START_MEMORY, getUsedMemory());

    // Capture current cache statistics
    Map<String, Object> beforeStats = SpringContextCacheAccessor.getContextCacheStats();

    // Store the snapshot
    Map<String, Map<String, Object>> cacheStats = rootStore.getOrComputeIfAbsent(
      KEY_CACHE_STATS, k -> new HashMap<>(), Map.class);
    cacheStats.put("before-" + currentTest, beforeStats);

    // Log test start
    logger.debug("Starting test {} ({}.{})",
      currentTest,
      context.getRequiredTestClass().getSimpleName(),
      context.getRequiredTestMethod().getName());
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // Get the root store
    Store rootStore = context.getRoot().getStore(NAMESPACE);
    Store testStore = context.getStore(NAMESPACE);

    // Get test count
    AtomicInteger testCount = rootStore.get(KEY_TEST_COUNT, AtomicInteger.class);
    if (testCount == null) {
      logger.warn("No test count found in extension context");
      return;
    }
    int currentTest = testCount.get();

    // Take a snapshot of cache statistics after test execution
    Map<String, Object> afterStats = SpringContextCacheAccessor.getContextCacheStats();

    // Get cache stats map
    Map<String, Map<String, Object>> cacheStats = rootStore.get(KEY_CACHE_STATS, Map.class);
    if (cacheStats == null) {
      logger.warn("No cache stats found in extension context");
      return;
    }

    // Store after-test stats
    cacheStats.put("after-" + currentTest, afterStats);

    // Get before-test stats
    Map<String, Object> beforeStats = cacheStats.get("before-" + currentTest);
    if (beforeStats == null) {
      logger.warn("No before-test stats found for test {}", currentTest);
      return;
    }

    // Get test timing and memory info
    Long startTime = testStore.get(KEY_START_TIME, Long.class);
    long executionTime = System.currentTimeMillis() - (startTime != null ? startTime : 0);

    Long startMemory = testStore.get(KEY_START_MEMORY, Long.class);
    long memoryDelta = getUsedMemory() - (startMemory != null ? startMemory : 0);

    // Calculate cache statistics differences
    int beforeSize = getIntValue(beforeStats, "size");
    int afterSize = getIntValue(afterStats, "size");
    int beforeHits = getIntValue(beforeStats, "hitCount");
    int afterHits = getIntValue(afterStats, "hitCount");
    int beforeMisses = getIntValue(beforeStats, "missCount");
    int afterMisses = getIntValue(afterStats, "missCount");

    boolean isHit = afterHits > beforeHits;
    boolean isMiss = afterMisses > beforeMisses;

    // Log detailed statistics
    logger.info("=== Spring Test Context Cache Statistics ===");
    logger.info("Test: {}.{}",
      context.getRequiredTestClass().getSimpleName(),
      context.getRequiredTestMethod().getName());
    logger.info("Execution #: {}", currentTest);
    logger.info("Execution time: {} ms", executionTime);
    logger.info("Memory change: {} KB", memoryDelta / 1024);

    // Log cache size and counts
    logger.info("Cache size: {} → {} (delta: {})",
      beforeSize, afterSize, afterSize - beforeSize);
    logger.info("Cache hits: {} → {} (delta: {})",
      beforeHits, afterHits, afterHits - beforeHits);
    logger.info("Cache misses: {} → {} (delta: {})",
      beforeMisses, afterMisses, afterMisses - beforeMisses);

    // Log hit/miss status
    if (isHit) {
      logger.info("✅ Cache HIT: This test reused an existing ApplicationContext");
    }
    else if (isMiss) {
      logger.info("❌ Cache MISS: This test created a new ApplicationContext");
      // Check for @DirtiesContext
      if (hasDirtiesContextAnnotation(context)) {
        logger.info("💡 @DirtiesContext detected - this is causing cache misses");
      }
    }
    else {
      logger.info("⚠️ No cache hit or miss detected for this test");
    }

    // Log max size and other stats
    int maxSize = getIntValue(afterStats, "maxSize");
    if (maxSize > 0) {
      logger.info("Maximum cache size: {}", maxSize);
      double fillPercentage = (double) afterSize / maxSize * 100.0;
      logger.info("Cache fill: {:.1f}%", fillPercentage);
    }

    // Log parent contexts
    int parentContexts = getIntValue(afterStats, "parentContextCount");
    if (parentContexts > 0) {
      logger.info("Parent contexts: {}", parentContexts);
    }

    // Log failures if any
    int failureCount = getIntValue(afterStats, "failureCount");
    if (failureCount > 0) {
      logger.info("Context loading failures: {}", failureCount);
    }

    // Log hierarchy info if available
    Integer hierarchyMapSize = (Integer) afterStats.get("hierarchyMapSize");
    if (hierarchyMapSize != null && hierarchyMapSize > 0) {
      logger.info("Hierarchy map size: {}", hierarchyMapSize);
    }

    // Log overall test context reuse efficiency
    double totalHitRate = (double) afterHits / (afterHits + afterMisses) * 100.0;
    logger.info("Overall cache hit rate: {:.1f}%", totalHitRate);

    // Recommendations based on statistics
    if (afterSize >= maxSize * 0.9 && maxSize > 0) {
      logger.warn("⚠️ Cache is nearly full ({}/{}). Consider increasing the max size.", afterSize, maxSize);
      logger.warn("    Set -Dspring.test.context.cache.maxSize=64 (or higher)");
    }

    if (totalHitRate < 20.0 && (afterHits + afterMisses > 10)) {
      logger.warn("⚠️ Low cache hit rate: {:.1f}%. Check test configurations for uniqueness.", totalHitRate);
    }

    logger.info("===========================================");
  }

  /**
   * Get integer value from stats map with default of 0.
   */
  private int getIntValue(Map<String, Object> stats, String key) {
    Object value = stats.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return 0;
  }

  /**
   * Get the current memory usage in bytes.
   */
  private long getUsedMemory() {
    runtime.gc(); // Request garbage collection to get more accurate readings
    return runtime.totalMemory() - runtime.freeMemory();
  }

  /**
   * Check if the test class or method has a @DirtiesContext annotation.
   */
  private boolean hasDirtiesContextAnnotation(ExtensionContext context) {
    // Check class annotation
    if (context.getRequiredTestClass().isAnnotationPresent(DirtiesContext.class)) {
      return true;
    }

    // Check method annotation
    return context.getRequiredTestMethod().isAnnotationPresent(DirtiesContext.class);
  }
}
