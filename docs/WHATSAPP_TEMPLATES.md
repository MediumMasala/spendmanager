# WhatsApp Templates

## Overview

SpendManager uses WhatsApp Business Platform to send weekly spending summaries. This requires pre-approved template messages.

## Template: weekly_money_summary

### Template Name
`weekly_money_summary`

### Category
`UTILITY`

### Language
`en` (English)

### Header
None

### Body
```
📊 Your Weekly SpendManager Summary

You spent {{1}} this week across {{4}} transactions.

Top category: {{2}}
Week: {{3}}

Open the app to see detailed breakdown and trends.
```

### Variables
| Variable | Description | Example |
|----------|-------------|---------|
| {{1}} | Total spent (formatted) | ₹12,345 |
| {{2}} | Top spending category | Food & Dining |
| {{3}} | Week date range | Dec 30 - Jan 5 |
| {{4}} | Transaction count | 42 |

### Footer
None

### Buttons
None (utility templates don't need CTAs)

## Opt-In Requirements

### Pre-Opt-In Message
Before enabling WhatsApp summaries, users must:
1. Enable Cloud AI mode
2. Toggle "WhatsApp Summaries" ON
3. Provide their WhatsApp number
4. Verify number ownership (same as login phone)

### Opt-In Language (In-App)
```
"I agree to receive weekly spending summaries on WhatsApp.
I can disable this anytime in Settings."
```

### Record Keeping
Store in database:
- `whatsapp_opt_in_at` timestamp
- `whatsapp_e164` phone number

## Template Submission

### Submission Checklist
- [ ] Business verified on Meta
- [ ] Phone number registered
- [ ] Template submitted for approval
- [ ] Test in sandbox environment

### Sample Submission
```json
{
  "name": "weekly_money_summary",
  "category": "UTILITY",
  "language": "en",
  "components": [
    {
      "type": "BODY",
      "text": "📊 Your Weekly SpendManager Summary\n\nYou spent {{1}} this week across {{4}} transactions.\n\nTop category: {{2}}\nWeek: {{3}}\n\nOpen the app to see detailed breakdown and trends.",
      "example": {
        "body_text": [
          ["₹12,345", "Food & Dining", "Dec 30 - Jan 5", "42"]
        ]
      }
    }
  ]
}
```

## Sending Messages

### API Call
```typescript
POST https://graph.facebook.com/v18.0/{phone-number-id}/messages

{
  "messaging_product": "whatsapp",
  "to": "919876543210",
  "type": "template",
  "template": {
    "name": "weekly_money_summary",
    "language": { "code": "en" },
    "components": [
      {
        "type": "body",
        "parameters": [
          { "type": "text", "text": "₹12,345" },
          { "type": "text", "text": "Food & Dining" },
          { "type": "text", "text": "Dec 30 - Jan 5" },
          { "type": "text", "text": "42" }
        ]
      }
    ]
  }
}
```

## Rate Limits

### Template Messages
- Outside 24h window: Template messages only
- Inside 24h window: Free-form messages allowed

### Tier Limits
| Tier | Messages/Day | Unlock Criteria |
|------|--------------|-----------------|
| Tier 1 | 1,000 | New business |
| Tier 2 | 10,000 | Quality rating |
| Tier 3 | 100,000 | Higher quality |
| Tier 4 | Unlimited | Top quality |

## Error Handling

### Common Errors
| Error Code | Meaning | Action |
|------------|---------|--------|
| 131047 | Re-engagement message outside window | Use template |
| 131026 | Message undeliverable | Mark as failed |
| 130429 | Rate limit | Retry with backoff |

### Logging
Store all send attempts:
- `status`: PENDING, SENT, DELIVERED, READ, FAILED
- `wa_message_id`: For status tracking
- `error_message`: For debugging

## Quality Maintenance

### Best Practices
1. Only send to opted-in users
2. Honor unsubscribe requests immediately
3. Maintain good delivery rates
4. Monitor block rates

### If Quality Drops
1. Pause sending
2. Review opt-in flow
3. Check message frequency
4. Improve content relevance
