package com.budgetview.audit5b;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.Method;

/** Audit 5b : un vidage JaCoCo par test, sous le nom du test (copie hors dépôt uniquement). */
public class PerTestCoverageListener extends RunListener {
  private Object agent;
  private Method reset, setSessionId, dump;

  private boolean init() {
    if (agent != null) return true;
    try {
      Class<?> rt = Class.forName("org.jacoco.agent.rt.RT");
      Class<?> iagent = Class.forName("org.jacoco.agent.rt.IAgent");
      agent = rt.getMethod("getAgent").invoke(null);
      reset = iagent.getMethod("reset");
      setSessionId = iagent.getMethod("setSessionId", String.class);
      dump = iagent.getMethod("dump", boolean.class);
      return true;
    }
    catch (Exception e) {
      System.err.println("audit5b: pas d'agent JaCoCo " + e);
      return false;
    }
  }

  public void testStarted(Description d) throws Exception {
    if (!init()) return;
    dump.invoke(agent, true); // ce qui précède (chargement de la classe, @BeforeClass) part dans une session à part
    setSessionId.invoke(agent, "T|" + d.getClassName() + "#" + d.getMethodName());
  }

  public void testFinished(Description d) throws Exception {
    if (!init()) return;
    dump.invoke(agent, true);
    setSessionId.invoke(agent, "X|" + d.getClassName());
  }

  public void testFailure(org.junit.runner.notification.Failure f) {
    System.err.println("audit5b: échec " + f.getDescription());
  }
}
