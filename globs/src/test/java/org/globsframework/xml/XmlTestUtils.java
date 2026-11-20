package org.globsframework.xml;

import junit.framework.Assert;
import org.saxstack.comparator.XmlComparator;
import org.saxstack.parser.SaxStackParser;
import org.saxstack.utils.XmlNodeToBuilder;
import org.saxstack.utils.XmlUtils;
import org.saxstack.writer.PrettyPrintRootXmlTag;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public class XmlTestUtils {
  private XmlTestUtils() {
  }

  public static void assertEquals(String expected, String actual) {
    XMLReader parser = XmlUtils.getXmlReader();
    try {
      if (!XmlComparator.areEqual(expected, actual, parser)) {
        showStringDiff(expected, actual, parser);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertEquivalent(String expected, String actual) {
    XMLReader parser = XmlUtils.getXmlReader();
    try {
      if (!XmlComparator.areEquivalent(expected, actual, parser)) {
        showStringDiff(expected, actual, parser);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertIsSubset(String expected, String actual) {
    XMLReader parser = XmlUtils.getXmlReader();
    try {
      if (XmlComparator.computeDiff(expected, actual, parser) != null) {
        showStringDiff(expected, actual, parser);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void showStringDiff(String expected, String actual, XMLReader parser) throws IOException {
    Assert.assertEquals(format(expected, parser, 4),
                        format(actual, parser, 4));
  }

  private static String format(String input, XMLReader parser, int attributeCountOnLine) throws IOException {
    Writer output = new StringWriter();
    SaxStackParser.parse(parser,
                         new XmlNodeToBuilder(new PrettyPrintRootXmlTag(output, attributeCountOnLine), null),
                         new StringReader(input));
    return output.toString();
  }
}
