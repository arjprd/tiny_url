package com.example.tinyurl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.example.tinyurl.config.TestRedisConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@TestPropertySource(properties = {
	"spring.data.redis.host=localhost",
	"spring.data.redis.port=6370"
})
class TinyurlApplicationTests {

	@Test
	void contextLoads() {
		// This test verifies that the Spring application context can be loaded
		// with test configuration (H2 database and mock Redis)
	}

}
