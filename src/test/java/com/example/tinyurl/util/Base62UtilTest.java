package com.example.tinyurl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class Base62UtilTest {

    @Test
    @DisplayName("Test encoding zero")
    void testEncodeZero() {
        String encoded = Base62Util.encode(0);
        assertEquals("a", encoded);
    }

    @Test
    @DisplayName("Test encoding single digit numbers")
    void testEncodeSingleDigit() {
        assertEquals("b", Base62Util.encode(1));
        assertEquals("c", Base62Util.encode(2));
        assertEquals("k", Base62Util.encode(10));
        assertEquals("A", Base62Util.encode(26));
        assertEquals("0", Base62Util.encode(52));
    }

    @Test
    @DisplayName("Test encoding and decoding round trip")
    void testEncodeDecodeRoundTrip() {
        long[] testValues = {1, 10, 100, 1000, 10000, 100000, 1000000, 999999999L, Long.MAX_VALUE};
        
        for (long value : testValues) {
            String encoded = Base62Util.encode(value);
            long decoded = Base62Util.decode(encoded);
            assertEquals(value, decoded, "Failed for value: " + value);
        }
    }

    @Test
    @DisplayName("Test decoding valid Base62 strings")
    void testDecodeValidStrings() {
        assertEquals(0, Base62Util.decode("a"));
        assertEquals(1, Base62Util.decode("b"));
        assertEquals(10, Base62Util.decode("k"));
        assertEquals(26, Base62Util.decode("A"));
        assertEquals(52, Base62Util.decode("0"));
        assertEquals(61, Base62Util.decode("9"));
    }

    @Test
    @DisplayName("Test encoding and decoding with large numbers")
    void testEncodeDecodeLargeNumbers() {
        long largeNumber = 9223372036854775807L; // Long.MAX_VALUE
        String encoded = Base62Util.encode(largeNumber);
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());
        
        long decoded = Base62Util.decode(encoded);
        assertEquals(largeNumber, decoded);
    }

    @Test
    @DisplayName("Test decoding null string throws exception")
    void testDecodeNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            Base62Util.decode(null);
        });
    }

    @Test
    @DisplayName("Test decoding empty string throws exception")
    void testDecodeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            Base62Util.decode("");
        });
    }

    @Test
    @DisplayName("Test decoding string with invalid character throws exception")
    void testDecodeInvalidCharacter() {
        assertThrows(IllegalArgumentException.class, () -> {
            Base62Util.decode("abc@def"); // @ is not a valid Base62 character
        });
    }

    @Test
    @DisplayName("Test decoding string with space throws exception")
    void testDecodeWithSpace() {
        assertThrows(IllegalArgumentException.class, () -> {
            Base62Util.decode("abc def"); // space is not a valid Base62 character
        });
    }

    @Test
    @DisplayName("Test encoding and decoding with boundary values")
    void testEncodeDecodeBoundaryValues() {
        // Test base values
        assertEquals(62, Base62Util.decode(Base62Util.encode(62)));
        assertEquals(63, Base62Util.decode(Base62Util.encode(63)));
        assertEquals(3844, Base62Util.decode(Base62Util.encode(3844))); // 62^2
    }

    @Test
    @DisplayName("Test that encoded strings are shorter for large numbers")
    void testEncodedStringLength() {
        long smallNumber = 10;
        long largeNumber = 1000000;
        
        String smallEncoded = Base62Util.encode(smallNumber);
        String largeEncoded = Base62Util.encode(largeNumber);
        
        // Large number should produce a longer or equal length encoded string
        assertTrue(largeEncoded.length() >= smallEncoded.length());
    }

    @Test
    @DisplayName("Test encoding produces only valid Base62 characters")
    void testEncodedStringContainsOnlyValidCharacters() {
        String validChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        
        for (long i = 0; i < 1000; i++) {
            String encoded = Base62Util.encode(i);
            for (char c : encoded.toCharArray()) {
                assertTrue(validChars.indexOf(c) >= 0, 
                    "Encoded string contains invalid character: " + c + " in " + encoded);
            }
        }
    }

    @Test
    @DisplayName("Test encoding and decoding with sequential numbers")
    void testEncodeDecodeSequential() {
        for (long i = 0; i < 100; i++) {
            String encoded = Base62Util.encode(i);
            long decoded = Base62Util.decode(encoded);
            assertEquals(i, decoded, "Failed for sequential value: " + i);
        }
    }
}

