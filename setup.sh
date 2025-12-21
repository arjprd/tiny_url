#!/bin/bash

# Setup script for Tiny URL application
# This script prompts for credentials and sets up environment variables
# Then runs docker compose up --build

echo "========================================="
echo "Tiny URL Application Setup"
echo "========================================="
echo ""

# Check if .env file already exists
if [ -f .env ]; then
    echo "Warning: .env file already exists."
    read -p "Do you want to overwrite it? (y/n): " overwrite
    if [ "$overwrite" != "y" ] && [ "$overwrite" != "Y" ]; then
        echo "Using existing .env file"
        source .env
    else
        rm .env
    fi
fi

# Prompt for database credentials
echo "=== Database Configuration ==="
read -p "Database Username [postgres]: " DB_USERNAME
DB_USERNAME=${DB_USERNAME:-postgres}

read -sp "Database Password [postgres]: " DB_PASSWORD
echo ""
DB_PASSWORD=${DB_PASSWORD:-postgres}

read -p "Database Name [tinyurl]: " DB_NAME
DB_NAME=${DB_NAME:-tinyurl}

read -p "Database Port [5432]: " DB_PORT
DB_PORT=${DB_PORT:-5432}

echo ""
echo "=== Application Configuration ==="
read -p "Application Host [http://localhost:8080]: " APP_HOST
APP_HOST=${APP_HOST:-http://localhost:8080}

read -p "Application Port [8080]: " APP_PORT
APP_PORT=${APP_PORT:-8080}

echo ""
echo "=== Redis Configuration ==="
read -p "Redis Port [6379]: " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

echo ""
echo "=== Rate Limiting Configuration ==="
read -p "Rate Limit GET Size [10]: " RATE_LIMIT_SHORTEN_GET_SIZE
RATE_LIMIT_SHORTEN_GET_SIZE=${RATE_LIMIT_SHORTEN_GET_SIZE:-10}

read -p "Rate Limit GET Capacity [10]: " RATE_LIMIT_SHORTEN_GET_CAPACITY
RATE_LIMIT_SHORTEN_GET_CAPACITY=${RATE_LIMIT_SHORTEN_GET_CAPACITY:-10}

read -p "Rate Limit POST Size [5]: " RATE_LIMIT_SHORTEN_POST_SIZE
RATE_LIMIT_SHORTEN_POST_SIZE=${RATE_LIMIT_SHORTEN_POST_SIZE:-5}

read -p "Rate Limit POST Capacity [5]: " RATE_LIMIT_SHORTEN_POST_CAPACITY
RATE_LIMIT_SHORTEN_POST_CAPACITY=${RATE_LIMIT_SHORTEN_POST_CAPACITY:-5}

echo ""
echo "=== Cache Configuration ==="
read -p "Cache Short URL TTL (seconds) [120]: " CACHE_SHORT_URL_TTL
CACHE_SHORT_URL_TTL=${CACHE_SHORT_URL_TTL:-120}

read -p "Cache Lock TTL (seconds) [10]: " CACHE_LOCK_TTL
CACHE_LOCK_TTL=${CACHE_LOCK_TTL:-10}

echo ""
echo "=== Authentication Configuration ==="
read -sp "AES Secret Key (32 characters) [12345678901234567890123456789012]: " AES_SECRET_KEY
echo ""
AES_SECRET_KEY=${AES_SECRET_KEY:-12345678901234567890123456789012}

read -p "Auth Token Random Length [32]: " AUTH_TOKEN_RANDOM_LENGTH
AUTH_TOKEN_RANDOM_LENGTH=${AUTH_TOKEN_RANDOM_LENGTH:-32}

read -p "Auth Token TTL (seconds) [3600]: " AUTH_TOKEN_TTL
AUTH_TOKEN_TTL=${AUTH_TOKEN_TTL:-3600}

echo ""
echo "=== Analytics Configuration ==="
read -p "Analytics Time Key Format [year.month.day.hour.minute]: " ANALYTICS_TIME_KEY_FORMAT
ANALYTICS_TIME_KEY_FORMAT=${ANALYTICS_TIME_KEY_FORMAT:-year.month.day.hour.minute}

# Create .env file
cat > .env << EOF
# Database Configuration
DB_USERNAME=$DB_USERNAME
DB_PASSWORD=$DB_PASSWORD
DB_NAME=$DB_NAME
DB_PORT=$DB_PORT

# Redis Configuration
REDIS_PORT=$REDIS_PORT

# Application Configuration
APP_HOST=$APP_HOST
APP_PORT=$APP_PORT

# Rate Limiting Configuration
RATE_LIMIT_SHORTEN_GET_SIZE=$RATE_LIMIT_SHORTEN_GET_SIZE
RATE_LIMIT_SHORTEN_GET_CAPACITY=$RATE_LIMIT_SHORTEN_GET_CAPACITY
RATE_LIMIT_SHORTEN_POST_SIZE=$RATE_LIMIT_SHORTEN_POST_SIZE
RATE_LIMIT_SHORTEN_POST_CAPACITY=$RATE_LIMIT_SHORTEN_POST_CAPACITY

# Cache Configuration
CACHE_SHORT_URL_TTL=$CACHE_SHORT_URL_TTL
CACHE_LOCK_TTL=$CACHE_LOCK_TTL

# Authentication Configuration
AES_SECRET_KEY=$AES_SECRET_KEY
AUTH_TOKEN_RANDOM_LENGTH=$AUTH_TOKEN_RANDOM_LENGTH
AUTH_TOKEN_TTL=$AUTH_TOKEN_TTL

# Analytics Configuration
ANALYTICS_TIME_KEY_FORMAT=$ANALYTICS_TIME_KEY_FORMAT
EOF

echo ""
echo "========================================="
echo "Environment variables saved to .env file"
echo "========================================="
echo ""
echo "Starting Docker Compose..."
echo ""

# Export environment variables and run docker compose
export DB_USERNAME DB_PASSWORD DB_NAME DB_PORT
export REDIS_PORT
export APP_HOST APP_PORT
export RATE_LIMIT_SHORTEN_GET_SIZE RATE_LIMIT_SHORTEN_GET_CAPACITY
export RATE_LIMIT_SHORTEN_POST_SIZE RATE_LIMIT_SHORTEN_POST_CAPACITY
export CACHE_SHORT_URL_TTL CACHE_LOCK_TTL
export AES_SECRET_KEY AUTH_TOKEN_RANDOM_LENGTH AUTH_TOKEN_TTL
export ANALYTICS_TIME_KEY_FORMAT

# Run docker compose up --build
docker compose up --build

