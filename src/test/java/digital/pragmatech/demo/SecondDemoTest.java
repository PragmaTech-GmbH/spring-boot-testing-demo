package digital.pragmatech.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ExtendWith({SpringContextCacheStatsExtension.class})
class SecondDemoTest {

  @Test
  void testOne() throws Exception {
    System.out.println("Doing stuff in testOne");
  }

  @Test
  @DirtiesContext
  void testDirtyTwo() throws Exception {
    System.out.println("Doing stuff in testTwo");
  }

  @Test
  void testThree() throws Exception {
    System.out.println("Doing stuff in testThree");
  }
}
