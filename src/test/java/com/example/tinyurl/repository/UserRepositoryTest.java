package com.example.tinyurl.repository;

import com.example.tinyurl.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.example.tinyurl.config.TestRedisConfig;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Test create user, save, and check existsByUsername returns true for existing username and false for different username")
    void testExistsByUsername() {
        // Create and save a user
        String username = "testuser";
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        User user = new User(username, passwordHash);
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());

        // Check existsByUsername returns true for the saved username
        boolean exists = userRepository.existsByUsername(username);
        assertTrue(exists, "User should exist with username: " + username);

        // Check existsByUsername returns false for a different username
        String differentUsername = "differentuser";
        boolean notExists = userRepository.existsByUsername(differentUsername);
        assertFalse(notExists, "User should not exist with username: " + differentUsername);
    }

    @Test
    @DisplayName("Test that 2 records with same username should not be possible")
    void testDuplicateUsernameNotAllowed() {
        // Create and save first user
        String username = "duplicateuser";
        String passwordHash1 = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        User user1 = new User(username, passwordHash1);
        User savedUser1 = userRepository.save(user1);

        assertNotNull(savedUser1);
        assertNotNull(savedUser1.getId());

        // Try to create and save second user with same username
        String passwordHash2 = "$2a$10$DifferentHashHere12345678901234567890";
        User user2 = new User(username, passwordHash2);

        // Should throw DataIntegrityViolationException due to unique constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.save(user2);
            userRepository.flush(); // Force flush to trigger constraint check
        }, "Should not allow duplicate usernames");
    }

    @Test
    @DisplayName("Test create user and check if createdAt and updatedAt have valid values")
    void testCreatedAtAndUpdatedAtAreSet() {
        // Create and save a user
        String username = "timestampuser";
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        User user = new User(username, passwordHash);

        // Record time before save
        OffsetDateTime beforeSave = OffsetDateTime.now();

        User savedUser = userRepository.save(user);

        // Record time after save
        OffsetDateTime afterSave = OffsetDateTime.now();

        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());

        // Check createdAt is set and is within expected time range
        OffsetDateTime createdAt = savedUser.getCreatedAt();
        assertNotNull(createdAt, "createdAt should not be null");
        assertTrue(createdAt.isAfter(beforeSave.minusSeconds(1)) || createdAt.isEqual(beforeSave.minusSeconds(1)),
                "createdAt should be after or equal to beforeSave time");
        assertTrue(createdAt.isBefore(afterSave.plusSeconds(1)) || createdAt.isEqual(afterSave.plusSeconds(1)),
                "createdAt should be before or equal to afterSave time");

        // Check updatedAt is set and is within expected time range
        OffsetDateTime updatedAt = savedUser.getUpdatedAt();
        assertNotNull(updatedAt, "updatedAt should not be null");
        assertTrue(updatedAt.isAfter(beforeSave.minusSeconds(1)) || updatedAt.isEqual(beforeSave.minusSeconds(1)),
                "updatedAt should be after or equal to beforeSave time");
        assertTrue(updatedAt.isBefore(afterSave.plusSeconds(1)) || updatedAt.isEqual(afterSave.plusSeconds(1)),
                "updatedAt should be before or equal to afterSave time");

        // Check that createdAt and updatedAt are equal (since it's a new record)
        assertEquals(createdAt, updatedAt, "createdAt and updatedAt should be equal for a new record");
    }
}

