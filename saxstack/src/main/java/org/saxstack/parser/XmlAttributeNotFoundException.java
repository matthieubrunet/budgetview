package org.saxstack.parser;

public class XmlAttributeNotFoundException extends XmlParsingException {
  public XmlAttributeNotFoundException(String xmlAttrName) {
    super("Xml attribute " + xmlAttrName + " is missing");
  }
}
