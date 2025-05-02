package pragmatech.digital;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses JUnit XML report files to extract test execution times.
 * Compatible with both JUnit 4 and JUnit 5 XML formats.
 */
public class JUnitXmlParser {

  /**
   * Parse a JUnit XML report file.
   */
  public List<TestResult> parseFile(Path file) throws Exception {
    List<TestResult> results = new ArrayList<>();

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    // Disable DTD validation and external entity processing for security
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(file.toFile());
    document.getDocumentElement().normalize();

    // Try both JUnit 4 and JUnit 5 formats
    parseJUnit4Format(document, results);
    parseJUnit5Format(document, results);

    return results;
  }

  /**
   * Parse JUnit 4 XML format.
   */
  private void parseJUnit4Format(Document document, List<TestResult> results) {
    // In JUnit 4, testcase elements are used
    NodeList testcaseNodes = document.getElementsByTagName("testcase");

    for (int i = 0; i < testcaseNodes.getLength(); i++) {
      Node node = testcaseNodes.item(i);

      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) node;

        String className = element.getAttribute("classname");
        String methodName = element.getAttribute("name");
        String timeStr = element.getAttribute("time");

        // Skip if missing essential attributes
        if (className.isEmpty() || methodName.isEmpty() || timeStr.isEmpty()) {
          continue;
        }

        // Convert time to Duration (JUnit 4 reports time in seconds)
        double seconds = Double.parseDouble(timeStr);
        Duration duration = Duration.ofMillis((long) (seconds * 1000));

        // Default to PASS, then check for failure or error elements
        String status = "PASS";
        String message = null;

        NodeList failures = element.getElementsByTagName("failure");
        if (failures.getLength() > 0) {
          status = "FAIL";
          Element failure = (Element) failures.item(0);
          message = failure.getAttribute("message");
        }

        NodeList errors = element.getElementsByTagName("error");
        if (errors.getLength() > 0) {
          status = "ERROR";
          Element error = (Element) errors.item(0);
          message = error.getAttribute("message");
        }

        NodeList skipped = element.getElementsByTagName("skipped");
        if (skipped.getLength() > 0) {
          status = "SKIPPED";
          Element skip = (Element) skipped.item(0);
          message = skip.getAttribute("message");
        }

        // Use method name as display name if not available
        String displayName = element.getAttribute("displayName");
        if (displayName.isEmpty()) {
          displayName = methodName;
        }

        results.add(new TestResult(className, methodName, displayName, duration, status, message));
      }
    }
  }

  /**
   * Parse JUnit 5 XML format.
   */
  private void parseJUnit5Format(Document document, List<TestResult> results) {
    // In JUnit 5, testcase elements are still used but with different attributes
    NodeList testcaseNodes = document.getElementsByTagName("testcase");

    for (int i = 0; i < testcaseNodes.getLength(); i++) {
      Node node = testcaseNodes.item(i);

      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) node;

        // JUnit 5 may use name with fully qualified class name and method name
        String name = element.getAttribute("name");
        String className = element.getAttribute("classname");
        String methodName;

        // If classname is missing, try to extract from name
        if (className.isEmpty() && name.contains("(")) {
          // Format is typically "methodName(className)"
          int openParen = name.indexOf('(');
          int closeParen = name.indexOf(')', openParen);

          if (openParen > 0 && closeParen > openParen) {
            methodName = name.substring(0, openParen);
            className = name.substring(openParen + 1, closeParen);
          } else {
            // Cannot parse, skip
            continue;
          }
        } else {
          methodName = name;
        }

        // Skip if missing essential attributes
        if (className.isEmpty() || methodName.isEmpty()) {
          continue;
        }

        String timeStr = element.getAttribute("time");
        if (timeStr.isEmpty()) {
          // Try alternative attribute
          timeStr = element.getAttribute("duration");
        }

        if (timeStr.isEmpty()) {
          // Skip if no timing information
          continue;
        }

        // Convert time to Duration (JUnit reports time in seconds)
        double seconds = Double.parseDouble(timeStr);
        Duration duration = Duration.ofMillis((long) (seconds * 1000));

        // Default to PASS, then check for failure or error elements
        String status = "PASS";
        String message = null;

        NodeList failures = element.getElementsByTagName("failure");
        if (failures.getLength() > 0) {
          status = "FAIL";
          Element failure = (Element) failures.item(0);
          message = failure.getAttribute("message");
        }

        NodeList errors = element.getElementsByTagName("error");
        if (errors.getLength() > 0) {
          status = "ERROR";
          Element error = (Element) errors.item(0);
          message = error.getAttribute("message");
        }

        NodeList skipped = element.getElementsByTagName("skipped");
        if (skipped.getLength() > 0) {
          status = "SKIPPED";
          Element skip = (Element) skipped.item(0);
          message = skip.getAttribute("message");
        }

        // Use method name as display name if not available
        String displayName = element.getAttribute("displayName");
        if (displayName.isEmpty()) {
          displayName = methodName;
        }

        // Only add if this result isn't already in the list
        String testIdentifier = className + "#" + methodName;
        boolean alreadyAdded = results.stream()
          .anyMatch(r -> (r.getClassName() + "#" + r.getMethodName()).equals(testIdentifier));

        if (!alreadyAdded) {
          results.add(new TestResult(className, methodName, displayName, duration, status, message));
        }
      }
    }
  }
}
