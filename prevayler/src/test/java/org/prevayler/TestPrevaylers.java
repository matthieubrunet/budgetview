package org.prevayler;

/** Remplace, pour les tests, les fabriques statiques de PrevaylerFactory retirées en audit 2c (jamais appelées par l'application). */
public class TestPrevaylers {

  /** Journal et snapshots dans le dossier donné. */
  public static Prevayler persistent(Object prevalentSystem, String directory) throws Exception {
    PrevaylerFactory factory = new PrevaylerFactory();
    factory.configurePrevalentSystem(prevalentSystem);
    factory.configurePrevalenceDirectory(directory);
    return factory.create();
  }

  /** Sans journal ; les snapshots vont dans le dossier donné. */
  public static Prevayler transientMode(Object prevalentSystem, String directory) throws Exception {
    PrevaylerFactory factory = new PrevaylerFactory();
    factory.configurePrevalentSystem(prevalentSystem);
    factory.configurePrevalenceDirectory(directory);
    factory.configureTransientMode(true);
    return factory.create();
  }
}
