# SpendManager

AI-powered money tracking system for India with privacy-first design.

## Features

- **Automatic Transaction Capture**: Reads payment notifications from UPI apps (GPay, PhonePe, Paytm), banks, and wallets
- **Privacy First**: On-device redaction by default, opt-in cloud features
- **AI Parsing**: LLM-powered transaction parsing with OpenAI
- **Smart Categorization**: Rules-based + AI categorization for Indian merchants
- **Weekly Summaries**: Automated summaries delivered via WhatsApp
- **Play Store Compliant**: Uses NotificationListener instead of SMS permissions

## Architecture

```
/android-app    - Kotlin + Jetpack Compose Android app
/backend        - TypeScript + Fastify API server
/infra          - Docker Compose infrastructure
/docs           - Comprehensive documentation
/sample-data    - Synthetic test data
/eval           - Parser evaluation harness
```

## Quick Start

### Prerequisites

- Node.js 20+
- Docker & Docker Compose
- Android Studio (for Android development)
- OpenAI API key (for production)

### Backend Setup

```bash
# Start infrastructure
cd infra
docker-compose up -d

# Install dependencies
cd ../backend
npm install

# Configure environment
cp .env.example .env
# Edit .env with your settings

# Run migrations
npm run db:migrate

# Start development server
npm run dev
```

Backend runs at http://localhost:3000

### Android Setup

```bash
cd android-app

# Build Play flavor (Play Store compliant)
./gradlew assemblePlayDebug

# Build Sideload flavor (with SMS support)
./gradlew assembleSideloadDebug
```

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL connection string |
| `REDIS_URL` | Redis connection string |
| `JWT_SECRET` | JWT signing secret (min 32 chars) |

### Optional

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | - |
| `OPENAI_MODEL` | Model to use | `gpt-4o-mini` |
| `WHATSAPP_PHONE_NUMBER_ID` | WhatsApp Business phone ID | - |
| `WHATSAPP_ACCESS_TOKEN` | WhatsApp access token | - |
| `LLM_DAILY_BUDGET_USD` | Daily LLM cost limit | `10.00` |
| `LLM_PER_USER_DAILY_BUDGET_USD` | Per-user daily limit | `0.50` |

## API Endpoints

### Authentication
- `POST /v1/auth/request-otp` - Request OTP
- `POST /v1/auth/verify-otp` - Verify OTP and login

### User
- `GET /v1/user/me` - Get current user
- `PUT /v1/user/consent` - Update privacy settings
- `POST /v1/user/opt-in-whatsapp` - Opt into WhatsApp summaries
- `DELETE /v1/user/delete` - Delete account
- `GET /v1/user/export` - Export data

### Events
- `POST /v1/events/ingest` - Upload transaction events
- `POST /v1/events/retry-failed` - Retry failed parses
- `GET /v1/transactions/recent` - Get recent transactions

### Summary
- `GET /v1/summary/latest` - Get latest weekly summary
- `POST /v1/summary/compute` - Compute current week summary

## Privacy Modes

### Local-Only (Default)
- All data stays on device
- No network uploads
- Basic local features

### Cloud AI
- Redacted text uploaded
- AI parsing enabled
- Weekly summaries available

### Upload Raw (Opt-in)
- Full notification text uploaded
- Better parsing accuracy
- Explicitly enabled by user

## Play Store Compliance

The Play Store flavor:
- ❌ No SMS permissions
- ❌ No READ_SMS/RECEIVE_SMS
- ✅ Uses NotificationListenerService
- ✅ User manually enables in Settings

See [PLAY_STORE_CHECKLIST.md](docs/PLAY_STORE_CHECKLIST.md) for details.

## WhatsApp Integration

Weekly summaries require:
1. WhatsApp Business Platform account
2. Approved message template
3. User opt-in with verified number

See [WHATSAPP_TEMPLATES.md](docs/WHATSAPP_TEMPLATES.md) for template setup.

## Running Tests

```bash
# Backend tests
cd backend
npm test

# Run eval harness
npm run eval -- --provider=mock
npm run eval -- --provider=openai

# Android tests
cd android-app
./gradlew testPlayDebugUnitTest
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) - System design with diagrams
- [Play Store Checklist](docs/PLAY_STORE_CHECKLIST.md) - Compliance guide
- [Privacy Policy](docs/PRIVACY_POLICY_DRAFT.md) - Privacy policy template
- [Data Safety](docs/DATA_SAFETY_DRAFT.md) - Play Store data safety form
- [WhatsApp Templates](docs/WHATSAPP_TEMPLATES.md) - Template setup guide
- [LLM Parsing](docs/LLM_PARSING.md) - Parser implementation details
- [Security](docs/SECURITY.md) - Security model and threat analysis

## License

Proprietary - All rights reserved
