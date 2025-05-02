package digital.pragmatech.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class to access Spring's test context cache using reflection.
 * This class knows about the internal structure of DefaultContextCache.
 */
public class SpringContextCacheAccessor {

  private static final Logger logger = LoggerFactory.getLogger(SpringContextCacheAccessor.class);

  // Class names for reflection access
  private static final String DEFAULT_CONTEXT_CACHE_CLASS =
    "org.springframework.test.context.cache.DefaultContextCache";
  private static final String CONTEXT_CACHE_INTERFACE =
    "org.springframework.test.context.cache.ContextCache";
  private static final String TEST_CONTEXT_MANAGER_CLASS =
    "org.springframework.test.context.TestContextManager";
  private static final String CONTEXT_CACHE_UTILS_CLASS =
    "org.springframework.test.context.cache.ContextCacheUtils";

  // Singleton cache instance (located via reflection)
  private static Object contextCacheInstance = null;

  /**
   * Get the Spring TestContext DefaultContextCache instance using reflection.
   * This method tries multiple approaches to find the singleton instance.
   */
  public static Object getContextCacheInstance() {
    if (contextCacheInstance != null) {
      return contextCacheInstance;
    }

    try {
      // First try using a special field in recent Spring versions
      contextCacheInstance = findCacheViaTestContextManager();
      if (contextCacheInstance != null) {
        logger.debug("Found context cache via TestContextManager");
        return contextCacheInstance;
      }

      // Try looking through all loaded classes in the Spring package
      contextCacheInstance = findCacheViaClassLoading();
      if (contextCacheInstance != null) {
        logger.debug("Found context cache via class loading");
        return contextCacheInstance;
      }

      // Try creating a new instance
      contextCacheInstance = createNewContextCache();
      if (contextCacheInstance != null) {
        logger.debug("Created new DefaultContextCache instance");
        return contextCacheInstance;
      }
    } catch (Exception e) {
      logger.debug("Error finding Spring context cache: {}", e.getMessage());
    }

    logger.warn("Could not locate or create Spring test context cache");
    return null;
  }

  /**
   * Get statistics from the context cache.
   */
  public static Map<String, Object> getContextCacheStats() {
    Map<String, Object> stats = new HashMap<>();
    Object cache = getContextCacheInstance();

    if (cache == null) {
      return stats;
    }

    try {
      // These methods are part of the ContextCache interface
      Method sizeMethod = cache.getClass().getMethod("size");
      stats.put("size", sizeMethod.invoke(cache));

      Method getHitCountMethod = cache.getClass().getMethod("getHitCount");
      stats.put("hitCount", getHitCountMethod.invoke(cache));

      Method getMissCountMethod = cache.getClass().getMethod("getMissCount");
      stats.put("missCount", getMissCountMethod.invoke(cache));

      // These methods are specific to DefaultContextCache
      try {
        Method getMaxSizeMethod = cache.getClass().getMethod("getMaxSize");
        stats.put("maxSize", getMaxSizeMethod.invoke(cache));
      } catch (NoSuchMethodException e) {
        // Optional method
      }

      try {
        Method getParentContextCountMethod = cache.getClass().getMethod("getParentContextCount");
        stats.put("parentContextCount", getParentContextCountMethod.invoke(cache));
      } catch (NoSuchMethodException e) {
        // Optional method
      }

      // Access private fields that aren't exposed via methods
      try {
        Field contextMapField = cache.getClass().getDeclaredField("contextMap");
        contextMapField.setAccessible(true);
        Object contextMap = contextMapField.get(cache);
        if (contextMap instanceof Map) {
          stats.put("contextMapType", contextMap.getClass().getSimpleName());
        }
      } catch (NoSuchFieldException e) {
        // Optional field
      }

      try {
        Field hierarchyMapField = cache.getClass().getDeclaredField("hierarchyMap");
        hierarchyMapField.setAccessible(true);
        Object hierarchyMap = hierarchyMapField.get(cache);
        if (hierarchyMap instanceof Map) {
          stats.put("hierarchyMapSize", ((Map<?, ?>) hierarchyMap).size());
        }
      } catch (NoSuchFieldException e) {
        // Optional field
      }

      try {
        Field totalFailureCountField = cache.getClass().getDeclaredField("totalFailureCount");
        totalFailureCountField.setAccessible(true);
        Object totalFailureCount = totalFailureCountField.get(cache);
        if (totalFailureCount instanceof AtomicInteger) {
          stats.put("failureCount", ((AtomicInteger) totalFailureCount).get());
        }
      } catch (NoSuchFieldException e) {
        // Optional field
      }

    } catch (Exception e) {
      logger.debug("Error getting context cache stats: {}", e.getMessage());
    }

    return stats;
  }

  /**
   * Try to find the cache instance via TestContextManager class.
   */
  private static Object findCacheViaTestContextManager() {
    try {
      Class<?> testContextManagerClass = Class.forName(TEST_CONTEXT_MANAGER_CLASS);

      // Try to find a static field that holds the cache
      Field[] fields = testContextManagerClass.getDeclaredFields();
      for (Field field : fields) {
        field.setAccessible(true);
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
          Object fieldValue = field.get(null);
          if (fieldValue != null && isContextCache(fieldValue)) {
            return fieldValue;
          }
        }
      }

      // If we didn't find a static field, check if we can find an instance
      // and then get the cache from it
      Object testContextManager = createTestContextManager();
      if (testContextManager != null) {
        for (Field field : testContextManagerClass.getDeclaredFields()) {
          field.setAccessible(true);
          Object fieldValue = field.get(testContextManager);
          if (fieldValue != null && isContextCache(fieldValue)) {
            return fieldValue;
          }
        }
      }
    } catch (Exception e) {
      logger.debug("Error accessing TestContextManager: {}", e.getMessage());
    }

    return null;
  }

  /**
   * Try to find the cache by scanning loaded classes.
   */
  private static Object findCacheViaClassLoading() {
    try {
      Class<?> contextCacheClass = Class.forName(CONTEXT_CACHE_INTERFACE);
      Class<?> defaultContextCacheClass = Class.forName(DEFAULT_CONTEXT_CACHE_CLASS);

      // Look for static fields in ContextCacheUtils
      try {
        Class<?> contextCacheUtilsClass = Class.forName(CONTEXT_CACHE_UTILS_CLASS);
        Field[] fields = contextCacheUtilsClass.getDeclaredFields();
        for (Field field : fields) {
          field.setAccessible(true);
          if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            Object fieldValue = field.get(null);
            if (fieldValue != null && contextCacheClass.isInstance(fieldValue)) {
              return fieldValue;
            }
          }
        }

        // Try calling static methods that might return the cache
        Method[] methods = contextCacheUtilsClass.getDeclaredMethods();
        for (Method method : methods) {
          method.setAccessible(true);
          if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
            method.getParameterCount() == 0 &&
            contextCacheClass.isAssignableFrom(method.getReturnType())) {
            try {
              Object result = method.invoke(null);
              if (result != null) {
                return result;
              }
            } catch (Exception e) {
              // Skip this method
            }
          }
        }
      } catch (ClassNotFoundException e) {
        // ContextCacheUtils might not exist in older Spring versions
      }
    } catch (Exception e) {
      logger.debug("Error scanning for context cache: {}", e.getMessage());
    }

    return null;
  }

  /**
   * Create a new instance of DefaultContextCache.
   */
  private static Object createNewContextCache() {
    try {
      Class<?> defaultContextCacheClass = Class.forName(DEFAULT_CONTEXT_CACHE_CLASS);
      return defaultContextCacheClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      logger.debug("Could not create DefaultContextCache: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Create a new TestContextManager for a dummy test class.
   */
  private static Object createTestContextManager() {
    try {
      Class<?> testContextManagerClass = Class.forName(TEST_CONTEXT_MANAGER_CLASS);
      return testContextManagerClass.getDeclaredConstructor(Class.class)
        .newInstance(SpringContextCacheAccessor.class);
    } catch (Exception e) {
      logger.debug("Could not create TestContextManager: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Check if an object is a Spring ContextCache.
   */
  private static boolean isContextCache(Object obj) {
    try {
      Class<?> contextCacheClass = Class.forName(CONTEXT_CACHE_INTERFACE);
      return contextCacheClass.isInstance(obj);
    } catch (Exception e) {
      return false;
    }
  }
}
