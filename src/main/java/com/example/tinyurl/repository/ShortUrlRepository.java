package com.example.tinyurl.repository;

import com.example.tinyurl.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {
    
    Optional<ShortUrl> findByLongUrlHashAndLongUrl(String longUrlHash, String longUrl);
    
    Optional<ShortUrl> findById(Long id);
}

