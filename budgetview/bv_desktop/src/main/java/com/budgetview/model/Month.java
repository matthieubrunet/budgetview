package com.budgetview.model;

import com.budgetview.model.util.TypeLoader;
import com.budgetview.shared.utils.GlobSerializer;
import com.budgetview.utils.Lang;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.annotations.Key;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.model.FieldSetter;
import org.globsframework.model.FieldValues;
import org.globsframework.model.GlobRepository;
import org.globsframework.utils.serialization.SerializedByteArrayOutput;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedInputOutputFactory;
import org.globsframework.utils.serialization.SerializedOutput;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class Month {

  public static GlobType TYPE;

  @Key
  public static IntegerField ID;

  static {
    TypeLoader.init(Month.class, "month");
  }

  public static final String DATE_FORMAT_STRING = "yyyy/MM/dd";

  public static String toString(int yyyymm) {
    return toYearString(yyyymm) + "/" + twoDigits(Month.toMonth(yyyymm));
  }

  public static String toString(int yyyymm, int day) {
    return toYearString(yyyymm) + "/" + twoDigits(Month.toMonth(yyyymm)) + "/" + twoDigits(day);
  }

  public static String toCompactString(int yyyymm, int day) {
    return toYearString(yyyymm) + twoDigits(Month.toMonth(yyyymm)) + twoDigits(day);
  }

  private static String twoDigits(int value) {
    return value < 10 ? "0" + value : Integer.toString(value);
  }

  public static String toYearString(int yyyymm) {
    return Integer.toString(Month.toYear(yyyymm));
  }

  public static int toYear(int yyyymm) {
    return yyyymm / 100;
  }

  public static int toMonth(int yyyymm) {
    return yyyymm % 100;
  }

  public static int toYyyyMm(int year, int month) {
    return year * 100 + month;
  }

  /**
   * Une Date est un instant : elle est lue, et créée par toDate, dans le fuseau courant, comme le faisait le
   * Calendar d'origine.
   */
  private static LocalDate toLocalDate(Date date) {
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  /**
   * Premier jour du mois, par addition comme le Calendar souple (lenient) d'origine : un mois 0 ou 13 déborde
   * sur l'année voisine au lieu de lever une exception.
   */
  private static LocalDate firstDay(int yyyymm) {
    return LocalDate.of(toYear(yyyymm), 1, 1).plusMonths(toMonth(yyyymm) - 1);
  }

  public static int getMonthId(Date date) {
    LocalDate localDate = toLocalDate(date);
    return toYyyyMm(localDate.getYear(), localDate.getMonthValue());
  }

  public static int getDay(Date date) {
    return toLocalDate(date).getDayOfMonth();
  }

  /** Minuit, heure locale. Un jour 0 ou 31 en avril déborde sur le mois voisin, comme avec le Calendar d'origine. */
  public static Date toDate(int yyyymm, int day) {
    LocalDate localDate = firstDay(yyyymm).plusDays(day - 1);
    return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  public static int offset(int yyyymm, int offset) {
    if (offset < 0) {
      return previous(yyyymm, -offset);
    }
    else if (offset > 0) {
      return next(yyyymm, offset);
    }
    return yyyymm;
  }

  public static int next(int yyyymm) {
    return next(yyyymm, 1);
  }

  public static int next(int yyyymm, int monthsLater) {
    int year = toYear(yyyymm);
    int month = toMonth(yyyymm);

    year += (monthsLater / 12);
    month += monthsLater % 12;

    if (month > 12) {
      year++;
      month = month - 12;
    }
    return toYyyyMm(year, month);
  }

  public static int previous(int yyyymm) {
    return previous(yyyymm, 1);
  }

  public static int previous(int yyyymm, int monthsBack) {
    int year = toYear(yyyymm);
    int month = toMonth(yyyymm);

    year -= (monthsBack / 12);
    month -= monthsBack % 12;

    if (month <= 0) {
      year--;
      month = 12 + month;
    }

    return toMonthId(year, month);
  }

  public static Iterable<Integer> range(final int minYyyyMm, final int maxYyyyMm) {
    return new Iterable<Integer>() {
      public Iterator<Integer> iterator() {
        return new RangeIterator(minYyyyMm, maxYyyyMm);
      }
    };
  }

  public static int toInt(int yyyymm, int dd) {
    return yyyymm * 100 + dd;
  }

  public static int toMonthId(int yyyy, int mm) {
    return yyyy * 100 + mm;
  }

  public static int intToMonthId(int yyyymmdd) {
    return yyyymmdd / 100;
  }

  public static int intToDay(int yyyymmdd) {
    return yyyymmdd % 100;
  }

  public static String getFullLabel(Integer monthId, boolean capitalizeAccordingToLanguage) {
    if (monthId == null) {
      return "";
    }
    int month = toMonth(monthId);
    int year = toYear(monthId);
    return getFullMonthLabel(toMonth(month), capitalizeAccordingToLanguage) + " " + year;
  }

  public static String getFullMonthLabel(Integer monthId, boolean capitalizeAccordingToLanguage) {
    if (monthId == null) {
      return "";
    }
    String text = Lang.get("month." + toMonth(monthId) + ".long");
    return capitalizeAccordingToLanguage ? Lang.capitalizeMonth(text) : text;
  }

  public static String getFullMonthLabelWith4DigitYear(int monthId, boolean capitalizeAccordingToLanguage) {
    return getFullMonthLabel(monthId, capitalizeAccordingToLanguage) + " " + toYear(monthId);
  }

  public static String getShortMonthLabel(Integer monthId) {
    if (monthId == null) {
      return "";
    }
    int month = toMonth(monthId);
    return Lang.get("month." + toMonth(month) + ".medium");
  }

  public static String getShortMonthLabelWithYear(Integer monthId) {
    if (monthId == null) {
      return "";
    }
    int month = toMonth(monthId);
    int year = toYear(monthId);
    return Lang.get("month." + toMonth(month) + ".medium") + " " + year;
  }

  public static String getShortMonthLabelWithShortYear(Integer monthId) {
    if (monthId == null) {
      return "";
    }
    int month = toMonth(monthId);
    int year = toYear(monthId);
    return Lang.get("month." + toMonth(month) + ".medium") + " " + (year % 100);
  }

  public static String getOneLetterMonthLabel(Integer month) {
    return Lang.get("month." + toMonth(month) + ".short").toUpperCase();
  }

  public static List<Integer> createMonths(int firstMonth, int lastMonth) {
    if (firstMonth > lastMonth) {
      int tmp = lastMonth;
      lastMonth = firstMonth;
      firstMonth = tmp;
    }
    List<Integer> month = new ArrayList<Integer>();
    int currentMonth = firstMonth;
    while (currentMonth <= lastMonth) {
      month.add(currentMonth);
      currentMonth = next(currentMonth);
    }
    return month;
  }

  public static int[] createCountMonthsWithFirst(int monthId, int count) {
    int[] monthIds = new int[count + 1];
    monthIds[0] = monthId;
    for (int i = 1; i < monthIds.length; i++) {
      monthId = next(monthId);
      monthIds[i] = monthId;
    }
    return monthIds;
  }

  public static int getLastDayNumber(int monthId) {
    return firstDay(monthId).lengthOfMonth();
  }

  public static Integer getDay(Integer day, int monthId) {
    int lastDay = getLastDayNumber(monthId);
    if (day == null || day <= 0 || day > lastDay) {
      return lastDay;
    }
    return day;
  }

  /** Garde l'heure locale, y compris au passage de l'heure d'été, comme Calendar.add(DAY_OF_MONTH). */
  public static Date addDays(Date date, int day) {
    return Date.from(date.toInstant().atZone(ZoneId.systemDefault()).plusDays(day).toInstant());
  }

  public static boolean isContinuousSequence(int[] months) {
    int previous = months[0];
    for (int i = 1; i < months.length; i++) {
      if (months[i] != next(previous)) {
        return false;
      }
      previous = months[i];
    }
    return true;
  }

  public static int distance(int fromMonth, int toMonth) {
    int distance = 0;
    if (fromMonth <= toMonth) {
      while (fromMonth < toMonth) {
        fromMonth = next(fromMonth);
        distance++;
      }
      return distance;
    }
    else {
      while (fromMonth > toMonth) {
        fromMonth = previous(fromMonth);
        distance--;
      }
      return distance;
    }
  }

  public static int distance(int fromMonth, int fromDay, int toMonth, int toDay) {
    if (fromMonth == toMonth) {
      return Math.abs(fromDay - toDay);
    }

    int distance = 0;
    if (fromMonth < toMonth) {
      distance += getLastDayNumber(fromMonth) - fromDay;
      for (int month = Month.next(fromMonth); month < toMonth; month = Month.next(month)) {
        distance += getLastDayNumber(month);
      }
      distance += toDay;
    }
    else {
      distance += getLastDayNumber(toMonth) - toDay;
      for (int month = Month.next(toMonth); month < fromMonth; month = Month.next(month)) {
        distance += getLastDayNumber(month);
      }
      distance += fromDay;
    }
    return distance;
  }

  public static Iterable<Integer> yearRange(int monthId) {
    int firstMonth = Month.toYear(monthId) * 100 + 1;
    int lastMonth = firstMonth + 11;
    return range(firstMonth, lastMonth);
  }

  public static int toFullDate(int monthId, int day) {
    return monthId * 100 + day;
  }

  public static int getDayFromFullDate(int fullDate){
    return fullDate % 100;
  }

  public static int getMonthIdFromFullDate(int fullDate){
    return fullDate / 100;
  }

  public static int toFullDate(Date date) {
    return toFullDate(getMonthId(date), getDay(date));
  }

  public static int getFullDate(Date date) {
    return toFullDate(date);
  }

  public static class RangeIterator implements Iterator<Integer> {
    private int current;
    private int max;

    public RangeIterator(int minYyyyMm, int maxYyyyMm) {
      this.current = minYyyyMm;
      this.max = maxYyyyMm;
    }

    public boolean hasNext() {
      return current <= max;
    }

    public Integer next() throws NoSuchElementException {
      if (current > max) {
        throw new NoSuchElementException();
      }
      int previous = current;
      current = Month.next(current);
      return previous;
    }

    public void remove() throws UnsupportedOperationException {
      throw new UnsupportedOperationException();
    }
  }

  public static class Serializer implements GlobSerializer {

    public byte[] serializeData(FieldValues values) {
      SerializedByteArrayOutput serializedByteArrayOutput = new SerializedByteArrayOutput();
      SerializedOutput outputStream = serializedByteArrayOutput.getOutput();
      outputStream.writeInteger(values.get(ID));
      return serializedByteArrayOutput.toByteArray();
    }

    public void deserializeData(int version, byte[] data, Integer id, FieldSetter fieldSetter) {
      if (version == 1) {
        deserializeDataV1(fieldSetter, data);
      }
    }

    private void deserializeDataV1(FieldSetter fieldSetter, byte[] data) {
      SerializedInput input = SerializedInputOutputFactory.init(data);
      fieldSetter.set(ID, input.readInteger());
    }

    public int getWriteVersion() {
      return 1;
    }

    public boolean shouldBeSaved(GlobRepository repository, FieldValues fieldValues) {
      return true;
    }
  }
}
