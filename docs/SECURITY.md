# Security Documentation

## Threat Model

### Assets
1. **User phone numbers** - For authentication
2. **Transaction data** - Financial information
3. **API credentials** - OpenAI, WhatsApp tokens
4. **Session tokens** - JWT authentication

### Threat Actors
1. **External attackers** - Internet-based attacks
2. **Malicious apps** - On-device data theft
3. **Man-in-the-middle** - Network interception
4. **Insider threats** - Unauthorized data access

## Security Controls

### Authentication

#### Phone OTP
- 6-digit numeric OTP
- 10-minute expiry
- Max 5 attempts per OTP
- Rate limited: 3 requests per minute per phone

#### JWT Tokens
- HS256 signed
- 30-day expiry
- Contains: user ID, device ID
- Validated on every request

### Data Protection

#### At Rest
| Data | Protection |
|------|------------|
| Phone numbers | SHA256 hashed (for indexing) |
| WhatsApp numbers | Plain (needed for sending) |
| Transaction text | Redacted by default |
| API keys | Environment variables |
| Passwords | N/A (no passwords used) |

#### In Transit
- All API calls over HTTPS
- TLS 1.3 preferred
- Certificate pinning (Android)

### API Security

#### Rate Limiting
```
100 requests per minute per IP
10 OTP requests per hour per phone
```

#### Input Validation
- Zod schema validation on all inputs
- Max request body size: 1MB
- Event batch limit: 100 events

#### CORS
```
Production: https://spendmanager.app only
Development: Permissive
```

### Android Security

#### Data Storage
- Room database (encrypted on modern Android)
- DataStore for preferences
- No plaintext sensitive data in SharedPrefs

#### Network Security
- Network security config (HTTPS only in production)
- No cleartext traffic allowed

#### Backup Prevention
```xml
android:allowBackup="false"
android:fullBackupContent="false"
```

## Privacy by Design

### Default Settings
- Local-only mode by default
- Cloud AI disabled by default
- Raw upload disabled by default
- WhatsApp disabled by default

### On-Device Redaction
Before any data leaves device:
1. Account numbers masked
2. Card numbers masked
3. Phone numbers masked
4. Reference IDs masked

### Minimal Data Collection
Only collect what's necessary:
- Phone for auth
- Transaction data for core feature
- Device ID for session management

### Data Retention
| Data Type | Retention |
|-----------|-----------|
| Events | 90 days |
| Transactions | 1 year |
| Summaries | 1 year |
| OTP requests | Until expiry |
| Logs | 30 days |

## Incident Response

### Detection
- Monitor error rates
- Track authentication failures
- Alert on unusual API patterns

### Response Plan
1. **Identify** - Determine scope
2. **Contain** - Isolate affected systems
3. **Eradicate** - Remove threat
4. **Recover** - Restore services
5. **Learn** - Post-incident review

### User Notification
If data breach affects users:
1. Notify within 72 hours
2. Explain impact
3. Provide mitigation steps

## Security Checklist

### Development
- [ ] No secrets in code
- [ ] No debug logs with PII
- [ ] Input validation on all endpoints
- [ ] SQL injection prevention (Prisma)

### Deployment
- [ ] HTTPS only
- [ ] Secure environment variables
- [ ] Database encrypted at rest
- [ ] Regular security updates

### Monitoring
- [ ] Failed auth attempts logged
- [ ] Rate limit hits logged
- [ ] Error rates monitored
- [ ] Cost anomalies alerted

## Known Limitations

### Notification Listener
- Can read all notifications (user must trust app)
- No Android-level encryption for notifications

### Third-Party Services
- OpenAI: Data sent for processing
- WhatsApp: Phone numbers shared

### Mitigation
- Redaction before sending to OpenAI
- store: false to prevent OpenAI retention
- Clear user consent for WhatsApp

## Vulnerability Disclosure

Report security issues to: security@spendmanager.app

Response time:
- Acknowledge: 24 hours
- Initial assessment: 72 hours
- Resolution: Based on severity
