package digital.pragmatech.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.cache.ContextCacheUtils;

@SpringBootTest
@ExtendWith({SpringContextCacheStatsExtension.class, TestTimerExtension.class})
class DemoTest {

  @Test
  void testOne() throws Exception {
    System.out.println("Doing stuff in testOne");

    System.out.println(ContextCacheUtils.retrieveMaxCacheSize());
  }

  @Test
  void testTwo() throws Exception {
    System.out.println("Doing stuff in testTwo");
  }

  @Test
  void testThree() throws Exception {
    System.out.println("Doing stuff in testThree");
  }
}
