# Deploy Firestore Rules

## To deploy the updated Firestore security rules, run:

```bash
firebase deploy --only firestore:rules
```

## Or deploy from Firebase Console:

1. Go to Firebase Console
2. Navigate to Firestore Database
3. Click on "Rules" tab
4. Copy and paste the contents of `firestore.rules`
5. Click "Publish"

## Changes Made:

### Added Rules for:
- **Ratings Collection**: Allows buyers to rate sellers
- **Transactions Collection**: Records completed transactions
- **User Ratings**: Allows updating seller rating and totalRatings fields
- **Post Status**: Allows sellers to update status, soldAt, buyerId, and winnerId

### Security Features:
- Buyers can only create ratings for their own purchases
- Users can only read their own transactions
- Sellers can mark items as sold
- All collections have admin override permissions


