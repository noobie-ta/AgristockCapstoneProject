# FCM Token Storage Strategy Guide

## 📋 Current Structure (Same Document)
```
users/{userId} {
  // User Info
  firstName, lastName, email, phone
  username, displayName, bio, location
  
  // Status
  verificationStatus, biddingApprovalStatus
  isOnline, lastSeen
  
  // Profile
  avatarUrl, coverUrl, rating, totalRatings
  
  // FCM Token (Current)
  fcmToken: "token_string"
  fcmTokenUpdatedAt: Timestamp
  fcmTokenDeviceId: "device_id"
}
```

### ✅ Pros:
1. **Single Read** - Get all user data + token in one query
2. **Simple Queries** - Easy to send notifications: `users/{userId}.fcmToken`
3. **Atomic Updates** - Update token with other user fields atomically
4. **Lower Read Costs** - One document read vs multiple
5. **Simpler Security Rules** - Same rules apply
6. **Better Performance** - Fewer network calls

### ❌ Cons:
1. **Document Size** - Token adds ~200 bytes (negligible)
2. **Single Device Only** - Can only store one token (latest overwrites previous)
3. **Token Refresh Conflicts** - If user has 2 devices, last one wins

### 💰 Cost Impact:
- **Reads**: 1 read per user lookup ✅ Best
- **Writes**: Only when token changes ✅ Efficient

---

## Option 2: Subcollection (Multi-Device Support)
```
users/{userId} {
  // User info (same as above)
  ...
  
  // NO FCM token here
}

users/{userId}/devices/{deviceId} {
  fcmToken: "token_string"
  fcmTokenUpdatedAt: Timestamp
  deviceName: "Samsung Galaxy S21"
  deviceType: "android" | "ios"
  lastActive: Timestamp
  isActive: boolean
}
```

### ✅ Pros:
1. **Multiple Devices** - User can receive notifications on all devices
2. **Device Management** - Track which devices are active
3. **Clean Separation** - Device data separate from user data
4. **Scalability** - Unlimited devices per user

### ❌ Cons:
1. **Multiple Reads** - Need to query subcollection for tokens
2. **Complex Queries** - Harder to send to all devices
3. **Higher Costs** - More reads when sending notifications
4. **More Complex Code** - Need to manage device lifecycle

### 💰 Cost Impact:
- **Reads**: 1 read for user + N reads for devices (N = number of devices)
- **Writes**: One per device when token changes

---

## Option 3: Separate Collection (Not Recommended)
```
users/{userId} { ... }
devices/{deviceId} {
  userId: "user_id"
  fcmToken: "token_string"
  ...
}
```

### ❌ Cons:
1. **Query Overhead** - Need to query by userId
2. **No Referential Integrity** - Devices can exist without users
3. **Complex Cleanup** - When user deletes, need to clean devices
4. **Higher Costs** - Separate collection queries

---

## 🎯 Recommendation for Your App

### **Option 1: Same Document** ✅ BEST FOR YOUR USE CASE

**Why?**
1. Your app appears to be **single-device focused** (no multi-device UI)
2. Most users use **one device** for this type of app
3. **Lower costs** and **simpler code**
4. **Better performance** for common queries

### Implementation:
```kotlin
// Current (Good)
users/{userId} {
  fcmToken: "latest_token"
  fcmTokenUpdatedAt: Timestamp
  fcmTokenDeviceId: "device_id"
}
```

### When to Consider Option 2:
Only if you need:
- ✅ Users logging in on multiple phones simultaneously
- ✅ Send notifications to ALL user devices
- ✅ Device management UI (show/remove devices)
- ✅ Track active devices separately

---

## 🔧 Hybrid Approach (Advanced)

If you want to support multi-device but keep simplicity:

```javascript
users/{userId} {
  // Primary token (same document - fast access)
  fcmToken: "primary_device_token"
  fcmTokenUpdatedAt: Timestamp
  
  // Optional: Array for additional devices
  fcmTokens: [
    {
      token: "device1_token",
      deviceId: "device1_id",
      updatedAt: Timestamp
    },
    {
      token: "device2_token", 
      deviceId: "device2_id",
      updatedAt: Timestamp
    }
  ]
}
```

### Benefits:
- Fast access to primary device (same document)
- Support multiple devices (array)
- Still single document read
- Can send to all devices in array

### Use Case:
- Primary device gets priority (stored in `fcmToken`)
- Additional devices stored in `fcmTokens` array
- When sending critical notifications, use primary
- When sending to all, iterate array

---

## 📊 Comparison Table

| Factor | Same Document | Subcollection | Hybrid |
|--------|---------------|---------------|--------|
| **Read Cost** | 1 read | 1 + N reads | 1 read |
| **Write Cost** | Low | Medium | Low |
| **Query Complexity** | Simple | Complex | Simple |
| **Multi-Device** | ❌ | ✅ | ✅ |
| **Code Complexity** | Low | High | Medium |
| **Performance** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Scalability** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

---

## 🎯 Final Recommendation

**For your AgriStock app: Use Option 1 (Same Document)**

**Reasons:**
1. ✅ Agricultural marketplace typically single-device usage
2. ✅ Cost-effective (fewer reads)
3. ✅ Simple to maintain
4. ✅ Fast notification delivery
5. ✅ Easy to query for admin sending notifications

**Only switch to Option 2 if:**
- You have users requesting multi-device support
- You see users frequently switching devices
- You need device management features

---

## 📝 Code Example (Same Document)

```kotlin
// Save token
firestore.collection("users").document(userId)
    .update(mapOf(
        "fcmToken" to token,
        "fcmTokenUpdatedAt" to Timestamp.now(),
        "fcmTokenDeviceId" to deviceId
    ))

// Send notification (Admin function)
val userDoc = firestore.collection("users").document(userId).get().await()
val token = userDoc.getString("fcmToken")
// Send to token...
```

---

## 📝 Code Example (Subcollection - if needed)

```kotlin
// Save token
firestore.collection("users").document(userId)
    .collection("devices").document(deviceId)
    .set(deviceData)

// Send to all devices
val devices = firestore.collection("users").document(userId)
    .collection("devices").whereEqualTo("isActive", true)
    .get().await()
    
devices.forEach { device ->
    val token = device.getString("fcmToken")
    // Send to each token...
}
```

