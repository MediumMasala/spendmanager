# LLM Transaction Parsing

## Overview

SpendManager uses LLMs to parse transaction notifications into structured data. This document covers the parsing strategy, schemas, prompts, and cost controls.

## Parsing Strategy

### Flow
```
Notification Text
       │
       ▼
┌─────────────┐
│   Cache     │ ──Hit──▶ Return cached result
│  (text_hash)│
└─────────────┘
       │ Miss
       ▼
┌─────────────┐
│  Heuristics │ ──High Confidence──▶ Cache & Return
│   Parser    │
└─────────────┘
       │ Low Confidence
       ▼
┌─────────────┐
│  LLM Parse  │
│  (OpenAI)   │
└─────────────┘
       │
       ▼
   Cache & Return
```

### Priority Order
1. **Cache Lookup** - Instant, free
2. **Heuristics** - <1ms, free, 85% confidence threshold
3. **LLM** - ~500ms, $0.0001-0.001 per call

## Structured Output Schema

### ParsedTransaction
```typescript
{
  isTransaction: boolean,      // Is this a financial transaction?
  amount: number | null,       // Transaction amount
  currency: string,            // Currency code (INR)
  direction: "DEBIT" | "CREDIT" | null,
  occurredAt: string | null,   // ISO 8601 timestamp
  merchant: string | null,     // Merchant/business name
  payee: string | null,        // Person for P2P transfers
  instrument: "UPI" | "CARD" | "NEFT" | "IMPS" | "WALLET" | "CASH" | "OTHER" | null,
  bankHint: string | null,     // Bank name
  appHint: string | null,      // App name (GPay, PhonePe, etc.)
  referenceId: string | null,  // Transaction reference
  confidence: number,          // 0.0 to 1.0
  flags: string[],             // Special flags
  reason: string | null        // If not a transaction, why
}
```

### OpenAI JSON Schema
Uses `response_format: { type: "json_schema" }` for guaranteed structured output.

## Prompts

### System Prompt
```
You are a financial transaction parser for Indian payment notifications.

Your task is to extract structured transaction data from notification text.
The notifications come from UPI apps (GPay, PhonePe, Paytm), banks (HDFC,
ICICI, SBI, etc.), and wallets.

Important guidelines:
1. Only extract actual financial transactions
2. Ignore promotional messages, OTPs, balance inquiries
3. Amounts may have redacted digits (e.g., "Rs.XXX.XX")
4. Phone numbers and accounts may be partially masked

Transaction direction rules:
- DEBIT: "debited", "paid", "sent", "transferred to"
- CREDIT: "credited", "received", "refund", "cashback"

Return isTransaction=false for:
- OTP messages
- Promotional offers
- Account alerts
- Balance checks
```

### User Prompt
```
Parse this notification from {app_source}:

"{text}"

Posted at: {posted_at}
Timezone: {timezone}
```

## Heuristics Parser

### Patterns Detected
- **Amount**: `Rs.`, `INR`, `₹` + numbers
- **Direction**: Keywords like "debited", "credited"
- **UPI ID**: `name@bank` format
- **Reference**: UTR, REF, TXN patterns

### High Confidence Criteria
Heuristic result is used without LLM if:
- Amount clearly detected
- Direction clearly detected
- Confidence ≥ 0.85

### Non-Transaction Detection
Immediate skip (no LLM) if text matches:
- OTP patterns
- Promotional keywords
- Login alerts
- Balance inquiries

## Caching

### Cache Key
`SHA256(normalized_redacted_text)`

### Cache Layers
1. **Redis** - 24 hour TTL, fast lookup
2. **PostgreSQL** - Persistent, hit counting

### Cache Stats
Track:
- Total entries
- Hit count per entry
- Last hit timestamp

### Cleanup
Daily job removes entries not hit in 30 days.

## Cost Controls

### Budgets
| Level | Default | Description |
|-------|---------|-------------|
| Global Daily | $10.00 | All users combined |
| Per-User Daily | $0.50 | Per user limit |

### Circuit Breaker
- **Threshold**: 5 consecutive failures
- **Timeout**: 60 seconds
- **Behavior**: Half-open after timeout

### Cost Estimation
Using GPT-4o-mini:
- Input: ~$0.00015 per 1K tokens
- Output: ~$0.0006 per 1K tokens
- Typical parse: ~100 input, ~100 output tokens
- **Cost per parse**: ~$0.000075

### Token Usage Tracking
Stored per request:
- User ID
- Provider
- Model
- Operation (parse/categorize)
- Input/Output tokens
- Cost USD

## OpenAI Integration

### Configuration
```typescript
{
  model: "gpt-4o-mini",
  max_tokens: 500,
  temperature: 0.1,  // Low for consistency
  response_format: { type: "json_schema", json_schema: ... },
  store: false  // Don't store requests (privacy)
}
```

### Retry Strategy
- Max 3 retries
- Exponential backoff: 1s, 2s, 4s
- Retry on: 429 (rate limit), 503 (service unavailable)
- No retry on: 400 (bad request), 401 (auth)

## Eval Harness

### Golden Set
Located in `/eval/`:
- `golden_events.json` - Sample notifications
- `expected_transactions.json` - Expected parse results

### Running Eval
```bash
npm run eval -- --provider=openai
npm run eval -- --provider=mock
```

### Metrics
- **Accuracy**: Correct parse rate
- **Field Accuracy**: Per-field match rate
- **False Positive Rate**: Non-transactions marked as transactions
- **Cost per 100**: Total cost for 100 parses

## Provider Abstraction

### Interface
```typescript
interface LlmProvider {
  name: string;
  parseTransaction(text, context): Promise<ParseResult>;
  categorize(merchant, payee, amount, direction): Promise<CategoryResult>;
  healthCheck(): Promise<boolean>;
}
```

### Implementations
- `OpenAIProvider` - Production, uses structured outputs
- `AnthropicProvider` - Stub for future use
- `MockProvider` - Testing, uses heuristics only

## Redaction Rules

### On-Device (Android)
Before upload, mask:
| Pattern | Example | Masked |
|---------|---------|--------|
| Account numbers | 1234567890 | 12XXXXXX90 |
| Card numbers | 4111-1111-1111-1111 | 4111-XXXX-XXXX-1111 |
| Phone numbers | 9876543210 | 98XXXXXX10 |
| UTR/Reference | UTR123456789012 | UTR1234XXXX9012 |

### Preserved
- Amount tokens (Rs/INR/₹ + numbers)
- Merchant names
- Transaction keywords
