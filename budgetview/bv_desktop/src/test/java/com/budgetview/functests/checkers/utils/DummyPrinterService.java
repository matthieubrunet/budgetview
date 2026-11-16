package com.budgetview.functests.checkers.utils;

import com.budgetview.desktop.printing.PrintableReport;
import com.budgetview.desktop.printing.PrinterService;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import org.globsframework.utils.exceptions.OperationFailed;
import org.uispec4j.assertion.Assertion;
import org.uispec4j.assertion.UISpecAssert;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

public class DummyPrinterService implements PrinterService {
  private String jobName;
  private PrintableReport report;
  private String exceptionMessage;

  public void print(String jobName, PrintableReport report) throws OperationFailed {
    this.jobName = jobName;
    this.report = report;
    if (exceptionMessage != null) {
      throw new OperationFailed(exceptionMessage);
    }
    render(report);
  }

  /**
   * Prépare puis dessine chaque page comme DefaultPrinterService et PrinterJob, dans une image en mémoire :
   * sans cela, les tests d'impression ne vérifient que la composition des pages, jamais leur dessin.
   * init() n'est pas idempotent (un second appel ajoute de nouveau les pages) : les vérificateurs inspectent
   * le rapport tel que préparé ici, sans le rappeler.
   */
  private static void render(PrintableReport report) {
    PageFormat format = getPageFormat();
    report.init(format);
    BufferedImage image = new BufferedImage((int)format.getWidth(), (int)format.getHeight(),
                                            BufferedImage.TYPE_INT_RGB);
    for (int i = 0; i < report.getNumberOfPages(); i++) {
      Graphics2D g2 = image.createGraphics();
      try {
        int result = report.getPrintable(i).print(g2, report.getPageFormat(i), i);
        Assert.assertEquals("page " + i + " non dessinée", Printable.PAGE_EXISTS, result);
      }
      catch (PrinterException e) {
        throw new AssertionFailedError("page " + i + " : " + e);
      }
      finally {
        g2.dispose();
      }
    }
  }

  /** Format de page des tests : zone imprimable de 1000 x 900, dont dépendent les paginations attendues. */
  private static PageFormat getPageFormat() {
    Paper paper = new Paper();
    paper.setImageableArea(0, 0, 1000, 900);
    PageFormat format = new PageFormat();
    format.setPaper(paper);
    return format;
  }

  public void setException(String exceptionMessage) {
    this.exceptionMessage = exceptionMessage;
  }

  public void checkLastReportName(String expectedName) {
    Assert.assertEquals(expectedName, jobName);
  }

  public PrintableReport getLastReport() {
    UISpecAssert.assertTrue(new Assertion() {
      public void check() {
        if (report == null) {
          throw new AssertionFailedError("No report was printed");
        }
      }
    });
    return report;
  }

  public void clear() {
    report = null;
    exceptionMessage = null;
    jobName = null;
  }
}
