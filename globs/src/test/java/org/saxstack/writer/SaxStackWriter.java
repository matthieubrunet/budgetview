package org.saxstack.writer;

import java.io.IOException;
import java.io.Writer;

/**
 * SaxStackWriter implement SaxStackBuilder
 */

public class SaxStackWriter implements SaxStackBuilder {
  public static final String XML_HEADER = "<?xml version='1.0' encoding='UTF-8'?>";
  Writer writer;

  public SaxStackWriter(Writer writer) {
    this.writer = writer;
  }

  public void write(XmlRootBuilder xmlBuilder) throws IOException {
    write(xmlBuilder, new FilterNone());
  }

  public void write(XmlRootBuilder rootBuilder, Filter filter) throws IOException {
    build(filter, rootBuilder, new RootXmlTag(writer));
  }

  public static void write(Writer writer, XmlRootBuilder xmlBuilder, Filter filter) throws IOException {
    new SaxStackWriter(writer).write(xmlBuilder, filter);
  }

  public static void write(Writer writer, XmlRootBuilder rootBuilder) throws IOException {
    new SaxStackWriter(writer).write(rootBuilder, new FilterNone());
  }

  private static void build(Filter filter, XmlRootBuilder rootBuilder, XmlTag root) throws IOException {
    if (filter == null) {
      filter = new FilterNone();
    }
    String tagName = rootBuilder.getTagName();
    XmlTag xmlTag = filter.enter(root, tagName);
    XmlNodeBuilder[] xmlExporterNodes = rootBuilder.process(xmlTag);
    write(xmlTag, xmlExporterNodes, filter);
    xmlTag.end();
    filter.leave();
  }

  private static void write(XmlTag parentTag, XmlNodeBuilder[] xmlWriters, Filter filter)
    throws IOException {
    for (int i = 0; i < xmlWriters.length; i++) {
      write(parentTag, xmlWriters[i], filter);
    }
  }

  private static void write(XmlTag parentTag, XmlNodeBuilder xmlBuilder, Filter factory) throws IOException {
    if (parentTag instanceof NullXmlTag) {
      return;
    }
    while (xmlBuilder.hasNext()) {
      String nextTagName = xmlBuilder.getNextTagName();
      XmlTag newTag = factory.enter(parentTag, nextTagName);
      write(newTag, xmlBuilder.processNext(newTag), factory);
      factory.leave();
      newTag.end();
    }
  }
}
