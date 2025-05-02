package digital.pragmatech.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ThreadTrackerExtension.class)
class DemoTest {

  @Test
  void testOne() throws Exception {
    System.out.println("Doing stuff in testOne");
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
