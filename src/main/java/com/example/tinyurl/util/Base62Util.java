package com.example.tinyurl.util;

public class Base62Util {

    private static final String BASE62_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = 62;

    /**
     * Encodes a number to Base62 string
     * @param number The number to encode
     * @return Base62 encoded string
     */
    public static String encode(long number) {
        if (number == 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }

        StringBuilder encoded = new StringBuilder();
        while (number > 0) {
            encoded.append(BASE62_CHARS.charAt((int) (number % BASE)));
            number /= BASE;
        }

        return encoded.reverse().toString();
    }

    /**
     * Decodes a Base62 string to a number
     * @param encoded The Base62 encoded string
     * @return The decoded number
     */
    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string cannot be null or empty");
        }

        long decoded = 0;
        long power = 1;

        for (int i = encoded.length() - 1; i >= 0; i--) {
            char c = encoded.charAt(i);
            int index = BASE62_CHARS.indexOf(c);
            
            if (index == -1) {
                throw new IllegalArgumentException("Invalid character in Base62 string: " + c);
            }

            decoded += index * power;
            power *= BASE;
        }

        return decoded;
    }
}

