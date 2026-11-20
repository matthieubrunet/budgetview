package org.saxstack.parser;

import org.xml.sax.Attributes;

public class SilentXmlNode implements XmlNode {
  public static final SilentXmlNode INSTANCE = new SilentXmlNode();

  private SilentXmlNode() {
  }

  public XmlNode getSubNode(String childName, Attributes xmlAttrs) {
    return this;
  }

  public void setValue(String value) {
  }

  public void complete() {
  }
}
