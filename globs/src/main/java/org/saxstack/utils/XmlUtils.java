package org.saxstack.utils;

import org.saxstack.parser.ExceptionHolder;
import org.saxstack.parser.XmlAttributeNotFoundException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Built-in entity reference Replaces character
 * &amp;    &
 * &lt;     <
 * &gt;     >
 * &quot;   "
 * &apos;   '
 */
public class XmlUtils {
  public static final int AMP_CHARACTER = '&';
  public static final int LT_CHARACTER = '<';
  public static final int GT_CHARACTER = '>';
  public static final int QUOTE_CHARACTER = '"';
  public static final int APOS_CHARACTER = '\'';

  private static final String AMP_STRING = "&";
  private static final String LT_STRING = "<";
  private static final String GT_STRING = ">";
  private static final String QUOTE_STRING = "\"";
  private static final String APOS_STRING = "'";

  private static final String AMP_ENTITY = "&amp;";
  private static final String LT_ENTITY = "&lt;";
  private static final String GT_ENTITY = "&gt;";
  private static final String QUOTE_ENTITY = "&quot;";
  private static final String APOS_ENTITY = "&apos;";
  public static final String LINE_SEPARATOR = System.getProperty("line.separator");

  public static String getAttrValue(String xmlAttrName, Attributes xmlAttributes) throws XmlAttributeNotFoundException {
    String val = xmlAttributes.getValue(xmlAttrName);
    if (val == null) {
      throw new XmlAttributeNotFoundException(xmlAttrName);
    }
    return val;
  }

  public static String getAttrValue(String xmlAttrName, Attributes xmlAttributes, String defaultValue) {
    String val = xmlAttributes.getValue(xmlAttrName);
    if (val == null) {
      val = defaultValue;
    }
    return val;
  }

  public static String convertEntities(String text) {
    if ("".equals(text.trim())) {
      return "";
    }
    if (text.indexOf(AMP_CHARACTER) != -1) {
      text = text.replaceAll(AMP_STRING, AMP_ENTITY);
    }
    if (text.indexOf(APOS_CHARACTER) != -1) {
      text = text.replaceAll(APOS_STRING, APOS_ENTITY);
    }
    if (text.indexOf(LT_CHARACTER) != -1) {
      text = text.replaceAll(LT_STRING, LT_ENTITY);
    }
    if (text.indexOf(GT_CHARACTER) != -1) {
      text = text.replaceAll(GT_STRING, GT_ENTITY);
    }
    if (text.indexOf(QUOTE_CHARACTER) != -1) {
      text = text.replaceAll(QUOTE_STRING, QUOTE_ENTITY);
    }
    return text;
  }

  public static String addInCDataValue(String xmlString) {
    return new StringBuilder("<![CDATA[").append(xmlString).append("]]>").toString();
  }

  public static XMLReader getXmlReader() {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      SAXParser parser = factory.newSAXParser();
      return parser.getXMLReader();
    }
    catch (ParserConfigurationException e) {
      throw new ExceptionHolder(e);
    }
    catch (SAXException e) {
      throw new ExceptionHolder(e);
    }
  }
}
