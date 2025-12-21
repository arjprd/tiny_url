# Tiny URL Application

A URL shortening service featuring user authentication, rate limiting, custom URL codes, expiration, and click analytics.

## Features

- **URL Shortening**: Convert long URLs into short, shareable links
- **Custom URL Codes**: Create memorable custom short URLs
- **URL Expiration**: Set expiration dates for short URLs
- **User Management**: Secure token-based authentication, user creation, change password and token revoke.
- **Rate Limiting**: Protect endpoints from abuse
- **Click Analytics**: Track click statistics for your URLs
- **Caching**: Redis-based caching for improved performance

## Prerequisites

### For Docker Setup (Recommended)
- **Docker** (version 20.10 or later)
- **Docker Compose** (version 2.0 or later)
- **Bash** (for running setup script on Linux/macOS)

### For Local Development
- **Java 21** (JDK 21 or later)
- **Maven 3.9+** (or use the included `mvnw` wrapper)
- **PostgreSQL 16+** (running locally or accessible)
- **Redis 7+** (running locally or accessible)

## Quick Start with Docker (Recommended)

The easiest way to run the application is using Docker Compose:

1. **Make the setup script executable** (Linux/macOS):
   ```bash
   chmod +x setup.sh
   ```

2. **Run the setup script**:
   ```bash
   ./setup.sh
   ```
   
   The script will prompt you for configuration values:
   - Database credentials (username, password, database name, port)
   - Application host and port
   - Redis port
   - Rate limiting settings
   - Cache TTL settings
   - Authentication settings (AES secret key, token settings)
   - Analytics time key format

   All values have sensible defaults - you can press Enter to accept them.

3. **The script will automatically**:
   - Create a `.env` file with your configuration
   - Build and start all services (PostgreSQL, Redis, and the application)
   - Wait for services to be healthy before starting the app

4. **Access the application**:
   - Application: http://localhost:8080
   - API Documentation (Swagger): http://localhost:8080/docs

### Manual Docker Compose Setup

If you prefer to set up manually:

1. **Create a `.env` file** with the following variables:
   ```bash
   # Database Configuration
   DB_USERNAME=postgres
   DB_PASSWORD=postgres
   DB_NAME=tinyurl
   DB_PORT=5432

   # Redis Configuration
   REDIS_PORT=6379

   # Application Configuration
   APP_HOST=http://localhost:8080
   APP_PORT=8080

   # Rate Limiting Configuration
   RATE_LIMIT_SHORTEN_GET_SIZE=10
   RATE_LIMIT_SHORTEN_GET_CAPACITY=10
   RATE_LIMIT_SHORTEN_POST_SIZE=5
   RATE_LIMIT_SHORTEN_POST_CAPACITY=5

   # Cache Configuration
   CACHE_SHORT_URL_TTL=3600
   CACHE_LOCK_TTL=10

   # Authentication Configuration
   AES_SECRET_KEY=12345678901234567890123456789012
   AUTH_TOKEN_RANDOM_LENGTH=32
   AUTH_TOKEN_TTL=3600

   # Analytics Configuration
   ANALYTICS_TIME_KEY_FORMAT=year.month.day.hour.minute
   ```

2. **Start the services**:
   ```bash
   docker compose up --build
   ```

## Running Locally (Without Docker)

### 1. Start PostgreSQL

Make sure PostgreSQL is running and accessible. Create a database:

```sql
CREATE DATABASE tinyurl;
```

### 2. Start Redis

Make sure Redis is running on the default port (6379) or configure the port in `application.properties`.

### 3. Configure Environment Variables

Set the following environment variables or create a `.env` file (if using a tool that supports it):

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=tinyurl
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

export REDIS_HOST=localhost
export REDIS_PORT=6379

export APP_HOST=http://localhost:8080

export RATE_LIMIT_SHORTEN_GET_SIZE=10
export RATE_LIMIT_SHORTEN_GET_CAPACITY=10
export RATE_LIMIT_SHORTEN_POST_SIZE=5
export RATE_LIMIT_SHORTEN_POST_CAPACITY=5

export CACHE_SHORT_URL_TTL=3600
export CACHE_LOCK_TTL=10

export AES_SECRET_KEY=12345678901234567890123456789012
export AUTH_TOKEN_RANDOM_LENGTH=32
export AUTH_TOKEN_TTL=3600

export ANALYTICS_TIME_KEY_FORMAT=year.month.day.hour.minute
```

### 4. Build and Run

Using Maven Wrapper (recommended):
```bash
./mvnw clean package
./mvnw spring-boot:run
```

Or using Maven directly:
```bash
mvn clean package
mvn spring-boot:run
```

Or run the JAR directly:
```bash
./mvnw clean package
java -jar target/tinyurl-0.0.1-SNAPSHOT.jar
```

### 5. Access the Application

- Application: http://localhost:8080
- API Documentation: http://localhost:8080/docs

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` (local) / `postgres` (Docker) |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `tinyurl` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `REDIS_HOST` | Redis host | `localhost` (local) / `redis` (Docker) |
| `REDIS_PORT` | Redis port | `6379` |
| `APP_HOST` | Application base URL | `http://localhost:8080` |
| `RATE_LIMIT_SHORTEN_GET_SIZE` | short url resolution rate limit window size (seconds) | `10` |
| `RATE_LIMIT_SHORTEN_GET_CAPACITY` | short url resolution rate limit max requests | `10` |
| `RATE_LIMIT_SHORTEN_POST_SIZE` | short url creation rate limit window size (seconds) | `5` |
| `RATE_LIMIT_SHORTEN_POST_CAPACITY` | short url creation rate limit max requests | `5` |
| `CACHE_SHORT_URL_TTL` | Short URL cache TTL (seconds) | `120` |
| `CACHE_LOCK_TTL` | Distributed lock TTL (seconds) | `10` |
| `AES_SECRET_KEY` | AES encryption key (32 characters) | - |
| `AUTH_TOKEN_RANDOM_LENGTH` | Random token length | `32` |
| `AUTH_TOKEN_TTL` | Auth token TTL (seconds) | `3600` |
| `ANALYTICS_TIME_KEY_FORMAT` | Analytics time key format | `year.month.day.hour.minute` |

### Analytics Time Key Format

The `ANALYTICS_TIME_KEY_FORMAT` determines the granularity of analytics data. Valid format parts (in order):
- `year`
- `month`
- `day`
- `hour`
- `minute`
- `seconds`

Examples:
- `year.month.day.hour` - Hourly granularity
- `year.month.day.hour.minute` - Minute granularity (default)
- `year.month.day` - Daily granularity

The scheduler automatically infers the cron schedule and offset from the format:
- If format ends with `minute`: cron = `1 * * * * *`, offset = `1 minutes`
- If format ends with `hour`: cron = `0 1 * * * *`, offset = `1 hours`

## API Documentation

Once the application is running, you can access the interactive API documentation at:

**http://localhost:8080/docs**

The Swagger UI provides:
- Complete API endpoint documentation
- Request/response schemas
- Try-it-out functionality
- Authentication support

## API Endpoints

### User Management
- `POST /user` - Create a new user
- `POST /user/login` - Login and get authentication token
- `POST /user/logout` - Logout (requires authentication)
- `PATCH /user` - Change password (requires authentication)

### URL Management
- `POST /shorten` - Shorten a URL (requires authentication)
- `GET /{shortUrlCode}` - Redirect to long URL
- `GET /url/{shortUrlCode}?start_date={timestamp}&end_date={timestamp}` - Get analytics (requires authentication)

## Testing

Run tests using Maven:

```bash
./mvnw test
```

## Test Coverage Report

View the test coverage report: [Coverage Report](https://htmlpreview.github.io/?https://github.com/arjprd/tiny_url/blob/main/coverage_report/index.html)

To generate a new coverage report:

```bash
./mvnw clean test jacoco:report
```

The report will be generated in `target/site/jacoco/index.html` and copied to `coverage_report/index.html`.

## Sample Curl Commands

### 1. Create a user

```bash
curl --location 'localhost:8080/user' \
--header 'Content-Type: application/json' \
--data '{
    "username": "arj",
    "password": "arjprd12"
}'
```

### 2. Login and get token

```bash
curl --location 'localhost:8080/user/login' \
--header 'Content-Type: application/json' \
--data '{
    "username": "arj",
    "password": "arjprd12"
}'
```

### 3. Create short url with custom short url and expiry

`expiry` and `shortUrl` are optional

```bash
curl --location 'localhost:8080/shorten' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer w+dwcNgYlNtUIxNqdF8ykg.sfjNerc1RtNeZoNkOtEUX12meyN2u6UH' \
--data '{
    "url": "https://chatgpt.com/c/69480e09-cacc-8320-b6a3-791b0af7837c",
    "expiry": "2025-12-21T06:56:31.328Z",
    "shortUrl": "FREE3"
}'
```

### 4. Get analytics for a short url

```bash
curl --location --request GET 'localhost:8080/url/FREE1?start_date=2025-12-21T00%3A37%3A32.530Z&end_date=2025-12-21T15%3A37%3A32.530Z' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer w+dwcNgYlNtUIxNqdF8ykg.pIIgV3XNfo15KTlSNzJhOGcox657YgVu' \
--data '{
    "all": true
}'
```

### 5. Change password

```bash
curl --location --request PATCH 'localhost:8080/user' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer dJK5otDoba8dvK7brZ49fg.Hsx5xy0DpvMZkCgIVCePrrRRWuNbNn3w' \
--data '{
    "oldPassword": "arjprd12",
    "newPassword": "arjprd"
}'
```

### 5. Logout All Device

```bash
curl --location 'localhost:8080/user/logout' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer w+dwcNgYlNtUIxNqdF8ykg.7VeEfaRIPkPrVufDfsLBQNoWYYxpaayl' \
--data '{
    "all": true
}'
```

### 6. Logout Current User

```bash
curl --location 'localhost:8080/user/logout' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer w+dwcNgYlNtUIxNqdF8ykg.7VeEfaRIPkPrVufDfsLBQNoWYYxpaayl' \
--data '{
    "me": true
}'
```


## LLM Queries & Planning Phases

* [LLM Queries](LLM_Queries.md)
* [Phase 1](PHASE1.md)
* [Phase 2](PHASE2.md)
* [Phase 3](PHASE3.md)
* [Phase 4](PHASE4.md)
* [Phase 5](PHASE5.md)
* [Infra Design](https://drive.google.com/file/d/1jtZU4kQAPGfJX-amyrOhSLpVM7o1_mob/view?usp=sharing) Not Implemented
