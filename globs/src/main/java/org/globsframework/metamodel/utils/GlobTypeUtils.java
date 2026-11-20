package org.globsframework.metamodel.utils;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.annotations.NamingField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.utils.exceptions.InvalidParameter;

public class GlobTypeUtils {

  public static GlobType getType(Class targetClass) throws InvalidParameter {
    for (java.lang.reflect.Field field : targetClass.getFields()) {
      if (field.getType().equals(GlobType.class)) {
        try {
          return (GlobType)field.get(null);
        }
        catch (Exception e) {
          throw new InvalidParameter("Cannot access GlobType field in class " + targetClass.getName(), e);
        }
      }
    }
    throw new InvalidParameter("Class " + targetClass.getName() + " does not define a GlobType");
  }

  public static StringField findNamingField(GlobType type) {
    Field[] fields = type.getFieldsWithAnnotation(NamingField.class);
    if (fields.length == 1) {
      return stringField(fields, type);
    }
    return null;
  }

  private static StringField stringField(Field[] fields, GlobType type) {
    Field namingField = fields[0];
    if (!(namingField instanceof StringField)) {
      throw new InvalidParameter("Naming field of type '" + type + "' should be a StringField");
    }
    return (StringField)namingField;
  }
}
