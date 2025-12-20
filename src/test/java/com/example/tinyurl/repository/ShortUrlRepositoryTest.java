package com.example.tinyurl.repository;

import com.example.tinyurl.entity.ShortUrl;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Test create User, save, then create ShortUrl record with that user and save. Verify createdAt has valid values")
    void testCreateShortUrlWithValidUserAndVerifyCreatedAt() {
        // Create and save a User first
        String username = "shorturluser";
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        User user = new User(username, passwordHash);
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());

        // Create ShortUrl with the saved user
        String longUrl = "https://www.example.com";
        String longUrlHash = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";
        ShortUrl shortUrl = new ShortUrl(longUrl, longUrlHash, savedUser);

        // Record time before save
        OffsetDateTime beforeSave = OffsetDateTime.now();

        ShortUrl savedShortUrl = shortUrlRepository.save(shortUrl);

        // Record time after save
        OffsetDateTime afterSave = OffsetDateTime.now();

        assertNotNull(savedShortUrl);
        assertNotNull(savedShortUrl.getId());
        assertEquals(longUrl, savedShortUrl.getLongUrl());
        assertEquals(longUrlHash, savedShortUrl.getLongUrlHash());
        assertNotNull(savedShortUrl.getOwner());
        assertEquals(savedUser.getId(), savedShortUrl.getOwner().getId());

        // Check createdAt is set and is within expected time range
        OffsetDateTime createdAt = savedShortUrl.getCreatedAt();
        assertNotNull(createdAt, "createdAt should not be null");
        assertTrue(createdAt.isAfter(beforeSave.minusSeconds(1)) || createdAt.isEqual(beforeSave.minusSeconds(1)),
                "createdAt should be after or equal to beforeSave time");
        assertTrue(createdAt.isBefore(afterSave.plusSeconds(1)) || createdAt.isEqual(afterSave.plusSeconds(1)),
                "createdAt should be before or equal to afterSave time");
    }

    @Test
    @DisplayName("Test create ShortUrl with invalid user id should get exception")
    void testCreateShortUrlWithInvalidUserId() {
        // Create a User with an invalid/non-existent ID
        Long invalidUserId = 99999L;
        User invalidUser = new User(invalidUserId);
        invalidUser.setUsername("nonexistent");
        invalidUser.setPasswordHash("hash");

        // Create ShortUrl with invalid user
        String longUrl = "https://www.example.com";
        String longUrlHash = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";
        ShortUrl shortUrl = new ShortUrl(longUrl, longUrlHash, invalidUser);

        // Should throw DataIntegrityViolationException due to foreign key constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            shortUrlRepository.save(shortUrl);
            shortUrlRepository.flush(); // Force flush to trigger constraint check
        }, "Should not allow ShortUrl with invalid user ID");
    }

    @Test
    @DisplayName("Test create multiple ShortUrl records with valid user, same long_url_hash but different long_url values. Check if no exception happens and both values are present in db on get")
    void testMultipleShortUrlsWithSameHashButDifferentUrls() {
        // Create and save a User first
        String username = "multiuser";
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        User user = new User(username, passwordHash);
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());

        // Create first ShortUrl
        String longUrl1 = "https://www.example.com/page1";
        String longUrlHash = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";
        ShortUrl shortUrl1 = new ShortUrl(longUrl1, longUrlHash, savedUser);
        ShortUrl savedShortUrl1 = shortUrlRepository.save(shortUrl1);

        assertNotNull(savedShortUrl1);
        assertNotNull(savedShortUrl1.getId());

        // Create second ShortUrl with same hash but different URL
        String longUrl2 = "https://www.example.com/page2";
        ShortUrl shortUrl2 = new ShortUrl(longUrl2, longUrlHash, savedUser);
        ShortUrl savedShortUrl2 = shortUrlRepository.save(shortUrl2);

        assertNotNull(savedShortUrl2);
        assertNotNull(savedShortUrl2.getId());

        // Flush to ensure both are persisted
        shortUrlRepository.flush();

        // Verify both records are saved and can be retrieved
        Optional<ShortUrl> found1 = shortUrlRepository.findByLongUrlHashAndLongUrl(longUrlHash, longUrl1);
        assertTrue(found1.isPresent(), "First ShortUrl should be found");
        assertEquals(longUrl1, found1.get().getLongUrl());
        assertEquals(longUrlHash, found1.get().getLongUrlHash());

        Optional<ShortUrl> found2 = shortUrlRepository.findByLongUrlHashAndLongUrl(longUrlHash, longUrl2);
        assertTrue(found2.isPresent(), "Second ShortUrl should be found");
        assertEquals(longUrl2, found2.get().getLongUrl());
        assertEquals(longUrlHash, found2.get().getLongUrlHash());

        // Verify both records have different IDs
        assertNotEquals(savedShortUrl1.getId(), savedShortUrl2.getId(), "Both records should have different IDs");

        // Verify we can retrieve both by ID
        Optional<ShortUrl> retrieved1 = shortUrlRepository.findById(savedShortUrl1.getId());
        assertTrue(retrieved1.isPresent(), "First ShortUrl should be retrievable by ID");
        assertEquals(longUrl1, retrieved1.get().getLongUrl());

        Optional<ShortUrl> retrieved2 = shortUrlRepository.findById(savedShortUrl2.getId());
        assertTrue(retrieved2.isPresent(), "Second ShortUrl should be retrievable by ID");
        assertEquals(longUrl2, retrieved2.get().getLongUrl());
    }
}

