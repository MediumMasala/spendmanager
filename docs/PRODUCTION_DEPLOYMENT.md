# Production Deployment Guide

## Overview

This guide covers deploying SpendManager to production with:
- **Database**: Neon (PostgreSQL) or Supabase
- **Cache**: Upstash (Redis)
- **Backend**: Render, Railway, or Fly.io
- **Android**: Play Store release

## Step 1: Database Setup (Neon - Recommended)

### Create Neon Account
1. Go to https://neon.tech
2. Create a new project "spendmanager-prod"
3. Copy the connection string

### Run Migrations
```bash
cd backend
DATABASE_URL="your-neon-connection-string" npx prisma migrate deploy
```

## Step 2: Redis Setup (Upstash)

### Create Upstash Account
1. Go to https://upstash.com
2. Create a new Redis database
3. Select region closest to your backend
4. Copy the Redis URL (starts with `rediss://`)

## Step 3: Backend Deployment

### Option A: Render (Recommended)

1. Create `render.yaml` in project root (already created below)
2. Connect GitHub repo to Render
3. Set environment variables in Render dashboard
4. Deploy

### Option B: Railway

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login and deploy
railway login
railway init
railway up
```

### Option C: Fly.io

```bash
# Install Fly CLI
curl -L https://fly.io/install.sh | sh

# Login and deploy
fly auth login
fly launch
fly deploy
```

## Step 4: Environment Variables (Production)

Set these in your hosting platform:

```env
# Database
DATABASE_URL=postgresql://user:pass@host/db?sslmode=require

# Redis
REDIS_URL=rediss://default:password@host:6379

# Server
NODE_ENV=production
PORT=3000
HOST=0.0.0.0

# Auth (generate with: openssl rand -hex 32)
JWT_SECRET=your-production-secret-minimum-32-characters

# OpenAI
OPENAI_API_KEY=sk-your-production-key
OPENAI_MODEL=gpt-4o-mini

# WhatsApp (optional)
WHATSAPP_PHONE_NUMBER_ID=your-phone-id
WHATSAPP_ACCESS_TOKEN=your-token
WHATSAPP_VERIFY_TOKEN=your-webhook-verify-token

# Cost Controls
LLM_DAILY_BUDGET_USD=50.00
LLM_PER_USER_DAILY_BUDGET_USD=1.00
```

## Step 5: Android Production Build

### Generate Signing Key
```bash
cd android-app

# Generate keystore (save password securely!)
keytool -genkey -v -keystore spendmanager-release.keystore \
  -alias spendmanager -keyalg RSA -keysize 2048 -validity 10000
```

### Configure Signing
Create `android-app/keystore.properties`:
```properties
storeFile=../spendmanager-release.keystore
storePassword=your-keystore-password
keyAlias=spendmanager
keyPassword=your-key-password
```

### Update API URL
Edit `android-app/app/build.gradle.kts`:
```kotlin
buildTypes {
    release {
        buildConfigField("String", "API_BASE_URL", "\"https://your-api-domain.com/v1\"")
    }
}
```

### Build Release AAB
```bash
./gradlew bundlePlayRelease
# Output: app/build/outputs/bundle/playRelease/app-play-release.aab
```

## Step 6: Play Store Submission

1. Create Google Play Developer account ($25 one-time)
2. Create new app in Play Console
3. Complete Data Safety form using `/docs/DATA_SAFETY_DRAFT.md`
4. Upload AAB file
5. Submit for review

## Step 7: WhatsApp Business Setup

1. Create Meta Business account
2. Set up WhatsApp Business API
3. Register phone number
4. Submit template for approval
5. Configure webhook URL: `https://your-api.com/webhook/whatsapp`

## Production Checklist

### Backend
- [ ] Database migrations applied
- [ ] Redis connected
- [ ] Environment variables set
- [ ] HTTPS enabled
- [ ] Rate limiting configured
- [ ] Error monitoring (Sentry)
- [ ] Logging configured

### Android
- [ ] Production API URL set
- [ ] Release keystore created
- [ ] ProGuard enabled
- [ ] App signed
- [ ] Play Store listing complete
- [ ] Privacy policy published

### External Services
- [ ] OpenAI API key (production tier)
- [ ] WhatsApp Business approved
- [ ] Domain configured
- [ ] SSL certificate active
