package org.prevayler.foundation;

import java.io.File;
import java.io.IOException;

public class FileManager {


  public static void produceDirectory(File directory) throws IOException {
    if (!directory.exists() && !directory.mkdirs()) {
      throw new InvalidDirectoryException("Directory doesn't exist and could not be created: " + directory);
    }
    if (!directory.isDirectory()) {
      throw new InvalidDirectoryException("Path exists but is not a directory: " + directory);
    }
  }

}
