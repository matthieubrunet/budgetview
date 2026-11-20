package org.globsframework.utils.serialization;

import org.globsframework.utils.Strings;

import java.util.Base64;

public class Encoder {
    private Encoder() {
    }

    public static String byteToString(byte[] cryptedBytes) {
        Base64.Encoder encoder = Base64.getEncoder();
        return Strings.removeNewLine(new String(encoder.encode(cryptedBytes)));
    }

}
