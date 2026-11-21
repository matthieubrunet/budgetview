package com.budgetview.utils.generator;

import com.budgetview.model.Month;

public abstract class DayGenerator {
  public abstract int get(Integer month);

  private DayGenerator() {
  }

  public static DayGenerator anyDay() {
    return new DayGenerator() {
      public int get(Integer month) {
        int actualMaximum = Month.getLastDayNumber(month);
        return (int)(1 + Math.random() * actualMaximum);
      }
    };
  }

  public static DayGenerator dayBetween(final int min, final int max) {
    return new DayGenerator() {
      public int get(Integer month) {
        return min + (int)((max - min) * Math.random());
      }
    };
  }
}
