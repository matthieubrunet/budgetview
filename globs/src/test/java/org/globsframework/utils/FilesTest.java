package org.globsframework.utils;

import junit.framework.TestCase;
import org.globsframework.utils.exceptions.ResourceAccessFailed;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import java.util.SortedSet;

/**
 * Fige le comportement de Files avant la modernisation de ses méthodes (audit 3d, lot A), défauts compris.
 */
public class FilesTest extends TestCase {

  private File dir;

  protected void setUp() throws Exception {
    super.setUp();
    dir = java.nio.file.Files.createTempDirectory("FilesTest").toFile();
  }

  protected void tearDown() throws Exception {
    Files.deleteWithSubtree(dir);
    super.tearDown();
  }

  public void testLoadFileToStringRewritesLineEndingsAndAddsAFinalOne() throws Exception {
    File file = write("a.txt", "un\r\ndeux\nsans fin");
    String sep = Strings.LINE_SEPARATOR;
    assertEquals("un" + sep + "deux" + sep + "sans fin" + sep, Files.loadFileToString(file));
    assertEquals("un" + sep + "deux" + sep + "sans fin" + sep, Files.loadFileToString(file.getAbsolutePath()));
    assertEquals("", Files.loadFileToString(write("vide.txt", "")));
  }

  public void testLoadFileToStringWithCharsetKeepsContentAsIs() throws Exception {
    File file = new File(dir, "latin.txt");
    try (OutputStream out = new FileOutputStream(file)) {
      out.write("été\r\n".getBytes(StandardCharsets.ISO_8859_1));
    }
    assertEquals("été\r\n", Files.loadFileToString(file.getAbsolutePath(), "ISO-8859-1"));
  }

  public void testLoadStreamToString() throws Exception {
    StringBuilder longText = new StringBuilder();
    for (int i = 0; i < 3000; i++) {
      longText.append((char)('a' + i % 26));
    }
    assertEquals(longText.toString(), Files.loadStreamToString(new StringReader(longText.toString())));
    assertEquals("", Files.loadStreamToString(new StringReader("")));
    assertEquals("ça\n", Files.loadStreamToString(new ByteArrayInputStream("ça\n".getBytes(StandardCharsets.UTF_8)), "UTF-8"));
  }

  public void testDumpStringToFileOverwrites() throws Exception {
    File file = new File(dir, "out.txt");
    assertEquals(file.getAbsolutePath(), Files.dumpStringToFile(file.getAbsolutePath(), "premier contenu"));
    Files.dumpStringToFile(file, "second");
    assertEquals("second", Files.loadStreamToString(new FileReader(file)));
    Files.dumpStringToFile((File)null, "rien");
  }

  public void testCopyStreamClosesOutputButNotInput() throws Exception {
    final boolean[] closed = {false, false};
    InputStream in = new ByteArrayInputStream(new byte[5000]) {
      public void close() throws IOException {
        closed[0] = true;
        super.close();
      }
    };
    ByteArrayOutputStream out = new ByteArrayOutputStream() {
      public void close() throws IOException {
        closed[1] = true;
        super.close();
      }
    };
    Files.copyStream(in, out);
    assertEquals(5000, out.size());
    assertFalse(closed[0]);
    assertTrue(closed[1]);
  }

  public void testCopyFileCreatesOrOverwritesTarget() throws Exception {
    File source = write("source.bin", "contenu copié");
    File target = write("cible.bin", "ancien contenu, plus long que le nouveau");
    Files.copyFile(source, target);
    assertEquals("contenu copié", Files.loadStreamToString(new FileReader(target)));
    File created = new File(dir, "nouveau.bin");
    Files.copyFile(source, created);
    assertEquals("contenu copié", Files.loadStreamToString(new FileReader(created)));
  }

  public void testCopyDirectory() throws Exception {
    File source = new File(dir, "src");
    new File(source, "sous").mkdirs();
    write("src/a.txt", "A");
    write("src/sous/b.txt", "B");
    write("src/ignore.txt", "X");
    File target = new File(dir, "dst");
    target.mkdir();
    assertTrue(Files.copyDirectory(source, target, Collections.singleton("ignore.txt")));
    assertEquals("A", Files.loadStreamToString(new FileReader(new File(target, "a.txt"))));
    assertEquals("B", Files.loadStreamToString(new FileReader(new File(target, "sous/b.txt"))));
    assertFalse(new File(target, "ignore.txt").exists());
    // Un sous-dossier déjà présent fait échouer la copie.
    assertFalse(Files.copyDirectory(source, target, Collections.<String>emptySet()));
  }

  public void testDelete() throws Exception {
    File root = new File(dir, "racine");
    new File(root, "sous").mkdirs();
    write("racine/sous/f.txt", "f");
    assertTrue(Files.deleteSubtreeOnly(root));
    assertTrue(root.exists());
    assertEquals(0, root.listFiles().length);
    assertTrue(Files.deleteWithSubtree(root));
    assertFalse(root.exists());
    assertFalse(Files.deleteWithSubtree(root));
  }

  public void testLoadPropertiesFromFile() throws Exception {
    File file = write("p.properties", "a=1\nb=deux\n");
    Properties properties = new Properties();
    Files.loadProperties(properties, file.getAbsolutePath());
    assertEquals("1", properties.getProperty("a"));
    assertEquals("deux", properties.getProperty("b"));
    try {
      Files.loadProperties(new Properties(), new File(dir, "absent.properties").getAbsolutePath());
      fail();
    }
    catch (ResourceAccessFailed e) {
      assertTrue(e.getMessage().startsWith("Properties file not found: "));
    }
  }

  public void testLoadPropertiesFromResource() throws Exception {
    Properties properties = Files.loadProperties(FilesTest.class, "/org/globsframework/utils/FilesTest.properties");
    assertEquals("valeur", properties.getProperty("clef"));
    SortedSet<Object> keys = Files.loadPropertyKeys("/org/globsframework/utils/FilesTest.properties", FilesTest.class);
    assertEquals("[autre, clef]", keys.toString());
    try {
      Files.loadProperties(FilesTest.class, "/absent.properties");
      fail();
    }
    catch (ResourceAccessFailed e) {
      assertTrue(e.getMessage().startsWith("Resource file '/absent.properties' not found"));
    }
  }

  public void testCopyResourceToTmpFile() throws Exception {
    String path = Files.copyResourceToTmpFile(this, "/org/globsframework/utils/FilesTest.properties");
    assertTrue(path.endsWith(".properties"));
    assertEquals("clef=valeur\nautre=2\n", Files.loadStreamToString(new FileReader(path)));
    new File(path).delete();
  }

  public void testCreateParentDirs() throws Exception {
    File file = new File(dir, "x/y/z.txt");
    Files.createParentDirs(file);
    assertTrue(new File(dir, "x/y").isDirectory());
    assertFalse(file.exists());
    Files.createParentDirs(null);
    Files.createParentDirs(new File("seul"));
  }

  private File write(String name, String content) throws IOException {
    File file = new File(dir, name);
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      writer.write(content);
    }
    return file;
  }
}
