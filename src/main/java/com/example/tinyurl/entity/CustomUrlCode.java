package com.example.tinyurl.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "custom_url_code")
@Getter
@Setter
@NoArgsConstructor
public class CustomUrlCode {

    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "url_id", nullable = false, foreignKey = @ForeignKey(name = "fk_custom_url_code_url_id"))
    private ShortUrl url;

    public CustomUrlCode(String code, ShortUrl url) {
        this.code = code;
        this.url = url;
    }
}

