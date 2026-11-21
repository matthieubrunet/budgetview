package org.globsframework.utils;

import org.globsframework.utils.exceptions.IOFailure;
import org.globsframework.utils.exceptions.ItemNotFound;
import org.globsframework.utils.exceptions.ResourceAccessFailed;

import java.io.*;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class Files {
  private Files() {
  }

  public static String loadFileToString(String fileName) throws IOFailure {
    File file = new File(fileName);
    return loadFileToString(file);
  }

  /** Réécrit les fins de ligne en LINE_SEPARATOR et en ajoute une à la dernière ligne : ce n'est pas Files.readString. */
  public static String loadFileToString(File file) {
    StringBuilder builder = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append(Strings.LINE_SEPARATOR);
      }
      return builder.toString();
    }
    catch (IOException e) {
      throw new IOFailure(e);
    }
  }

  public static String loadFileToString(String fileName, final String charsetName) throws IOFailure, ItemNotFound {
    try {
      return loadStreamToString(new InputStreamReader(new FileInputStream(fileName), charsetName));
    }
    catch (UnsupportedEncodingException e) {
      throw new IOFailure(e);
    }
    catch (FileNotFoundException e) {
      throw new ItemNotFound(e);
    }
  }

  public static String loadStreamToString(InputStream stream, final String charsetName) throws IOFailure {
    try {
      return loadStreamToString(new InputStreamReader(stream, charsetName));
    }
    catch (UnsupportedEncodingException e) {
      throw new IOFailure(e);
    }
  }

  public static String loadStreamToString(Reader input) throws IOFailure {
    try {
      StringWriter result = new StringWriter();
      input.transferTo(result);
      return result.toString();
    }
    catch (IOException e) {
      throw new IOFailure(e);
    }
  }

  public static void dumpStringToFile(File file, String content) throws IOFailure {
    if (file == null) {
      return;
    }
    dumpStringToFile(file.getAbsolutePath(), content);
  }

  public static String dumpStringToFile(String fileName, String content) throws IOFailure {
    try (FileWriter writer = new FileWriter(fileName)) {
      writer.append(content);
      return fileName;
    }
    catch (IOException e) {
      throw new IOFailure(e);
    }
  }

  public static boolean deleteWithSubtree(File directory) {
    return delete(directory, true);
  }

  public static boolean deleteSubtreeOnly(File directory) {
    return delete(directory, false);
  }

  private static boolean delete(File directory, boolean andRoot) {
    File[] files = directory.listFiles();
    if (files == null) {
      return false;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        deleteWithSubtree(file);
      }
      else {
        file.delete();
      }
    }
    return andRoot ? directory.delete() : true;
  }

  public static void copyStreamTofile(InputStream inputStream, String file) throws IOException {
    copyStream(inputStream, new FileOutputStream(file));
  }

  public static String copyResourceToTmpFile(Object obj, String resourceName) throws IOException {
    InputStream stream = obj.getClass().getResourceAsStream(resourceName);
    if (stream == null) {
      throw new FileNotFoundException("Failed to find " + resourceName);
    }
    int i = resourceName.indexOf(".");
    String extention = "tmp";
    if (i > -1) {
      extention = resourceName.substring(i, resourceName.length());
    }

    File tmp = File.createTempFile("tmp", extention);
    tmp.deleteOnExit();
    String path = tmp.getAbsolutePath();
    copyStreamTofile(stream, path);
    return path;
  }

  /** Ferme le flux de sortie, pas celui d'entrée. */
  public static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
    try (OutputStream output = outputStream) {
      inputStream.transferTo(output);
    }
  }

  public static void loadProperties(Properties properties, String propertiesFile) throws ResourceAccessFailed {
    File file = new File(propertiesFile);
    if (!file.exists()) {
      throw new ResourceAccessFailed("Properties file not found: " + file.getAbsolutePath());
    }
    try (InputStream stream = new FileInputStream(propertiesFile)) {
      properties.load(stream);
    }
    catch (IOException e) {
      throw new ResourceAccessFailed("Could not load properties file: " + propertiesFile, e);
    }
  }

  public static Properties loadProperties(Class refClass, String fileName) throws ResourceAccessFailed {
    Properties properties = new Properties();
    try (InputStream stream = getStream(refClass, fileName)) {
      properties.load(stream);
    }
    catch (IOException e) {
      throw new ResourceAccessFailed("Could not load properties file");
    }
    return properties;
  }

  public static SortedSet<Object> loadPropertyKeys(String fileName, Class refClass) throws IOException {
    Properties root = new Properties();
    try (InputStream stream = getStream(refClass, fileName)) {
      root.load(stream);
    }
    TreeSet<Object> result = new TreeSet<Object>();
    result.addAll(root.keySet());
    return result;
  }

  public static InputStream getStream(Class refClass, String fileName) throws ResourceAccessFailed {
    InputStream stream = refClass.getResourceAsStream(fileName);
    if (stream == null) {
      throw new ResourceAccessFailed("Resource file '" + fileName + "' not found for class: " + refClass.getName());
    }
    return stream;
  }

  public static void createParentDirs(File file) {
    if (file == null) {
      return;
    }
    File parent = file.getParentFile();
    if (parent == null) {
      return;
    }
    parent.mkdirs();
  }

  public static boolean copyDirectory(File source, File target, Set<String> filesToIgnore) {
    try {
      File[] files = source.listFiles();
      if (files != null && files.length != 0) {
        for (File file : files) {
          File targetFile = new File(target, file.getName());
          if (!filesToIgnore.contains(targetFile.getName())) {
            if (file.isDirectory()) {
              if (!targetFile.mkdir()) {
                return false;
              }
              if (!copyDirectory(file, targetFile, filesToIgnore)) {
                return false;
              }
            }
            else {
              copyFile(file, targetFile);
            }
          }
        }
      }
      return true;
    }
    catch (IOException e) {
      return false;
    }
  }

  public static void copyFile(File sourceFile, File targetFile) throws IOException {
    try (InputStream inputStream = new FileInputStream(sourceFile)) {
      copyStream(inputStream, new FileOutputStream(targetFile));
    }
  }
}
