package com.example.tinyurl.repository;

import com.example.tinyurl.entity.CustomUrlCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomUrlCodeRepository extends JpaRepository<CustomUrlCode, String> {
    
    Optional<CustomUrlCode> findByCode(String code);
}

