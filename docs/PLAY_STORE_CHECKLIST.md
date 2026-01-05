# Play Store Compliance Checklist

## Permission Strategy

### Play Store Flavor
The Play Store build uses **NotificationListenerService** instead of SMS permissions.

#### Permissions Used
- `INTERNET` - API communication
- `ACCESS_NETWORK_STATE` - Network availability check
- `BIND_NOTIFICATION_LISTENER_SERVICE` - Read notifications
- `RECEIVE_BOOT_COMPLETED` - Restart WorkManager after boot
- `WAKE_LOCK` - WorkManager background jobs

#### Permissions NOT Used (Play Flavor)
- ❌ `READ_SMS`
- ❌ `RECEIVE_SMS`
- ❌ `READ_CALL_LOG`
- ❌ `READ_CONTACTS`

### Sideload Flavor
SMS permissions are only available in the sideload flavor, which is NOT published to Play Store.

## NotificationListenerService Compliance

### User Enablement Flow
1. App requests user to enable notification access
2. User navigates to system Settings
3. User manually enables SpendManager
4. App verifies enablement before processing

### Best Practices Implemented
- ✅ Clear explanation of why access is needed
- ✅ Link directly to notification settings
- ✅ Check enablement status on each app start
- ✅ No functionality without explicit user action

## Data Safety Declaration

### Data Collected

| Data Type | Collected | Shared | Purpose |
|-----------|-----------|--------|---------|
| Phone number | Yes | No | Account identification |
| Financial transactions | Yes | No | Core app functionality |
| Device identifiers | Yes | No | Device management |

### Data Handling
- Encrypted in transit (HTTPS)
- Redacted by default
- User can delete all data
- User can export data

## Privacy Policy Requirements

### Must Include
- ✅ What data is collected
- ✅ How data is used
- ✅ Who data is shared with (nobody)
- ✅ How to delete data
- ✅ Contact information

### Template
See `/docs/PRIVACY_POLICY_DRAFT.md`

## Store Listing Requirements

### App Description
Must clearly state:
- App reads notifications
- Requires manual permission enablement
- Data stays on device by default
- Optional cloud features

### Screenshots
Should show:
- Permission request screen
- Privacy settings
- Consent toggles

## Restricted API Declaration

### Notification Listener
When submitting, select:
- **API**: NotificationListenerService
- **Use Case**: Financial tracking / expense management
- **Justification**: Required to capture transaction notifications for expense tracking. User must manually enable in Settings.

## Pre-Launch Checklist

- [ ] Remove all debugging code
- [ ] Verify no SMS permissions in manifest
- [ ] Test on multiple Android versions (8-14)
- [ ] Privacy policy published and linked
- [ ] Data safety form completed
- [ ] Restricted API declaration submitted
- [ ] Target SDK ≥ 34
- [ ] 64-bit support enabled
- [ ] ProGuard/R8 enabled for release

## Review Tips

1. **Be transparent**: Clearly explain notification access need
2. **Provide value**: Show how the app benefits users
3. **Demo video**: Consider including a demo showing the permission flow
4. **Support contact**: Provide responsive support email
