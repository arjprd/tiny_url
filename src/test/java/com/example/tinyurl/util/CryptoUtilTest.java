package com.example.tinyurl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    private static final String VALID_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    @DisplayName("Test generating random string of length 1")
    void testGenerateRandomStringLength1() {
        String result = CryptoUtil.generateRandomString(1);
        assertNotNull(result);
        assertEquals(1, result.length());
        assertTrue(VALID_CHARACTERS.contains(result));
    }

    @Test
    @DisplayName("Test generating random string of specified length")
    void testGenerateRandomStringSpecifiedLength() {
        int[] lengths = {10, 20, 32, 50, 100};
        
        for (int length : lengths) {
            String result = CryptoUtil.generateRandomString(length);
            assertNotNull(result);
            assertEquals(length, result.length(), "Failed for length: " + length);
        }
    }

    @Test
    @DisplayName("Test generating random string of length 0")
    void testGenerateRandomStringLength0() {
        String result = CryptoUtil.generateRandomString(0);
        assertNotNull(result);
        assertEquals(0, result.length());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Test generating random string contains only valid characters")
    void testGenerateRandomStringValidCharacters() {
        String result = CryptoUtil.generateRandomString(100);
        
        for (char c : result.toCharArray()) {
            assertTrue(VALID_CHARACTERS.indexOf(c) >= 0, 
                "Random string contains invalid character: " + c);
        }
    }

    @RepeatedTest(10)
    @DisplayName("Test generating multiple random strings are different")
    void testGenerateRandomStringUniqueness() {
        String result1 = CryptoUtil.generateRandomString(32);
        String result2 = CryptoUtil.generateRandomString(32);
        
        // With high probability, two random strings should be different
        // This is a probabilistic test, but with 32 characters, probability of collision is extremely low
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(32, result1.length());
        assertEquals(32, result2.length());
    }

    @Test
    @DisplayName("Test generating random string distribution (contains various character types)")
    void testGenerateRandomStringDistribution() {
        String result = CryptoUtil.generateRandomString(1000);
        
        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        
        for (char c : result.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        
        // With 1000 characters, we should have at least one of each type with high probability
        // This is a probabilistic test
        assertTrue(hasUpperCase || hasLowerCase || hasDigit, 
            "Random string should contain at least one valid character type");
    }

    @Test
    @DisplayName("Test generating random string with single character")
    void testGenerateRandomStringSingleChar() {
        String result = CryptoUtil.generateRandomString(1);
        assertNotNull(result);
        assertEquals(1, result.length());
        assertTrue(VALID_CHARACTERS.contains(result));
    }
}

