package org.globsframework.metamodel.utils;

import junit.framework.TestCase;
import org.globsframework.metamodel.DummyObject;
import org.globsframework.metamodel.DummyObject2;

public class GlobTypeUtilsTest extends TestCase {
  public void testGetType() throws Exception {
    assertSame(DummyObject.TYPE, GlobTypeUtils.getType(DummyObject.class));

    try {
      GlobTypeUtils.getType(String.class);
      fail();
    }
    catch (Exception e) {
      assertEquals("Class java.lang.String does not define a GlobType", e.getMessage());
    }
  }

  public void testFindNamingField() throws Exception {
    assertSame(DummyObject.NAME, GlobTypeUtils.findNamingField(DummyObject.TYPE));
    assertNull(GlobTypeUtils.findNamingField(DummyObject2.TYPE));
  }
}
