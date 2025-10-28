# Firestore Composite Indexes Required

## Critical: Rating System Indexes

The rating system uses compound queries that require composite indexes to work properly.

### Required Composite Index #1
**Collection:** `ratings`
**Fields to index:**
- `buyerId` (Ascending)
- `sellerId` (Ascending)  
- `postId` (Ascending)

**Used in:** RatingDialog.kt - checking if user has already rated

### How to Create Indexes:

#### Option 1: Automatic (Recommended)
1. Run the app and try to submit a rating
2. Check Logcat for a clickable URL to create the index
3. Click the URL and Firebase will auto-create the index

#### Option 2: Manual via Firebase Console
1. Go to Firebase Console → Firestore Database
2. Click "Indexes" tab
3. Click "Add Index"
4. Collection ID: `ratings`
5. Add fields:
   - Field: `buyerId`, Order: Ascending
   - Field: `sellerId`, Order: Ascending
   - Field: `postId`, Order: Ascending
6. Query scope: Collection
7. Click "Create"

#### Option 3: Using firestore.indexes.json
Create/update `firestore.indexes.json`:

```json
{
  "indexes": [
    {
      "collectionGroup": "ratings",
      "queryScope": "COLLECTION",
      "fields": [
        {
          "fieldPath": "buyerId",
          "order": "ASCENDING"
        },
        {
          "fieldPath": "sellerId",
          "order": "ASCENDING"
        },
        {
          "fieldPath": "postId",
          "order": "ASCENDING"
        }
      ]
    }
  ]
}
```

Then deploy:
```bash
firebase deploy --only firestore:indexes
```

## Note
Single-field queries like `.whereEqualTo("sellerId", sellerId)` don't need composite indexes.


