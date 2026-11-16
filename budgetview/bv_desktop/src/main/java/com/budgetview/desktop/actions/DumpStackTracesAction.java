package com.budgetview.desktop.actions;

import com.budgetview.utils.Lang;
import org.globsframework.utils.Dates;
import org.globsframework.utils.Log;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.Map;

/**
 * Menu de dev : relève toutes les 4 s la pile de chaque thread et la mémoire, puis l'écrit dans le log à l'arrêt.
 * (Jusqu'en 6.0.1, le relevé était aussi envoyé au support par le serveur, qui ne répond plus.)
 */
public class DumpStackTracesAction extends AbstractAction {
  private Thread thread;
  private boolean stop;
  private StringBuilder buffer = new StringBuilder();

  public DumpStackTracesAction() {
    super(Lang.get("dumpThread"));
  }

  public void actionPerformed(ActionEvent e) {
    if (thread == null) {
      stop = false;
      dump();
      this.putValue(Action.NAME, Lang.get("sendThread"));
    }
    else {
      this.putValue(Action.NAME, Lang.get("dumpThread"));
      stop();
    }
  }

  private void stop() {
    try {
      synchronized (this) {
        stop = true;
        notifyAll();
      }
      while (thread.isAlive()) {
        thread.interrupt();
        thread.join(200);
      }
      thread = null;

      Log.write(buffer.toString());
      buffer.setLength(0);
    }
    catch (InterruptedException e) {
      Log.write("[DumpStack] Interrupted thread");
    }
  }

  private void dump() {
    thread = new Thread() {
      public void run() {
        try {
          synchronized (this) {
            wait(1000);
            while (!stop) {
              buffer.append("-------------------").append(Dates.toTimestampString(new Date())).append('\n');
              Runtime runtime = Runtime.getRuntime();
              buffer
                .append("free mem: ").append(runtime.freeMemory())
                .append(" max mem: ").append(runtime.maxMemory())
                .append(" total mem: ").append(runtime.totalMemory())
                .append('\n');
              Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
              for (Map.Entry<Thread, StackTraceElement[]> entry : map.entrySet()) {
                if (thread != entry.getKey()) {
                  buffer.append("Thread: \n")
                    .append(entry.getKey().getName())
                    .append("\n");
                  for (StackTraceElement element : entry.getValue()) {
                    buffer
                      .append("at ")
                      .append(element.getClassName())
                      .append(".")
                      .append(element.getMethodName())
                      .append("(")
                      .append(element.getFileName())
                      .append(":")
                      .append(element.getLineNumber())
                      .append(")")
                      .append("\n");
                  }
                }
              }
              wait(4000);
            }
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    thread.setDaemon(true);
    thread.start();

  }
}
