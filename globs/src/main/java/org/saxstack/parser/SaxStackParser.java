package org.saxstack.parser;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.util.Stack;

public class SaxStackParser extends DefaultHandler {

  private Stack nodesStack = new Stack();
  private StringBuilder charBuffer;
  private org.xml.sax.XMLReader parser;

  private SaxStackParser(org.xml.sax.XMLReader parser) {
    this.parser = parser;
    parser.setErrorHandler(this);
    parser.setContentHandler(this);
    charBuffer = new StringBuilder();
  }

  public static void parse(XMLReader parser, XmlNode rootNode, Reader reader) throws ExceptionHolder {
    SaxStackParser saxStackParser = new SaxStackParser(parser);
    saxStackParser.parseXml(rootNode, reader);
  }

  private void parseXml(XmlNode rootNode, Reader reader) throws ExceptionHolder {
    nodesStack.clear();
    nodesStack.push(rootNode);
    try {
      parser.parse(new InputSource(reader));
    }
    catch (SAXException e) {
      throw new ExceptionHolder(e);
    }
    catch (IOException e) {
      throw new ExceptionHolder(e);
    }
  }

  public void startElement(String uri, String local, String qName, Attributes atts) {
    charBuffer.setLength(0);
    nodesStack.push(((XmlNode)nodesStack.peek()).getSubNode(local, atts));
  }

  public void characters(char[] chars, int start, int length) throws SAXException {
    charBuffer.append(chars, start, length);
  }

  public void endElement(String uri, String localName, String qName) throws SAXException {
    XmlNode node = (XmlNode)nodesStack.pop();
    if (charBuffer.length() != 0) {
      node.setValue(charBuffer.toString());
    }
    charBuffer.setLength(0);
    node.complete();
  }

}