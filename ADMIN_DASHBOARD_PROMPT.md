# 🎯 AgriStock Admin Dashboard - Complete Development Prompt

## Project Overview
Create a comprehensive, modern web-based admin dashboard for **AgriStock** - a livestock marketplace app with bidding/auction functionality. The admin panel should provide complete control over users, content, bidding, transactions, and platform operations.

---

## 🔧 Technology Stack

### Frontend
- **Framework**: React 18+ with TypeScript
- **UI Library**: Material-UI (MUI) v5 or Ant Design v5
- **State Management**: Redux Toolkit or Zustand
- **Routing**: React Router v6
- **Charts**: Recharts or Chart.js
- **Tables**: React Table v8 or MUI DataGrid
- **Forms**: React Hook Form + Yup validation
- **Date Handling**: date-fns or Day.js
- **HTTP Client**: Axios
- **Real-time**: Firebase Realtime Database listeners

### Backend & Services
- **Backend**: Firebase (Firestore, Authentication, Storage, Cloud Functions)
- **Authentication**: Firebase Auth with custom claims for admin roles
- **Database**: Firestore
- **File Storage**: Firebase Storage
- **Cloud Functions**: For backend operations and scheduled tasks
- **Email**: SendGrid or Firebase Extensions
- **SMS**: Twilio (optional)

### Deployment
- **Hosting**: Firebase Hosting or Vercel
- **CI/CD**: GitHub Actions
- **Domain**: Custom domain with SSL

---

## 📊 Current App Context (AgriStock)

### Key Features
1. **User Management**: Registration, ID verification, profiles
2. **Post Types**: 
   - SELL posts (direct purchase)
   - BID posts (auction with live bidding)
3. **Bidding System**: 
   - Live auctions with countdown timers
   - Anonymous bidding with daily-refreshing codenames
   - Bid increments and minimum bids
   - Real-time bid updates
4. **Verification Systems**:
   - ID Verification (verificationStatus: pending/approved/rejected)
   - Bidding Approval (biddingApprovalStatus: pending/approved/rejected/banned)
5. **Messaging**: In-app chat between buyers and sellers
6. **Ratings**: 5-star rating system for sellers
7. **Reports**: User reporting system
8. **Support Tickets**: User support system
9. **Notifications**: Push, in-app, and email notifications
10. **Favorites**: Users can favorite posts
11. **Transactions**: Track completed sales/purchases

### Firestore Collections
```
users/
  - userId
    - username, email, phone
    - verificationStatus (pending/approved/rejected)
    - biddingApprovalStatus (pending/approved/rejected/banned)
    - rating, totalRatings
    - avatarUrl
    - role (user/admin)
    - isAdmin (boolean)
    - accountCreated, lastLogin
    - favorites/ (subcollection)

posts/
  - postId
    - title, description, price
    - type (SELL/BID)
    - category (livestock type)
    - imageUrls[]
    - userId (seller)
    - status (ACTIVE/SOLD/ENDED)
    - location, address
    - startingBid, currentBid, highestBid
    - bidIncrement
    - biddingEndTime
    - totalBidders
    - createdAt, updatedAt

bids/
  - bidId
    - postId
    - userId (bidder)
    - bidAmount
    - timestamp
    - codename (anonymous identifier)

chats/
  - chatId
    - participants[] (userIds)
    - itemId (related post)
    - lastMessage, lastMessageTime
    - unreadCount_userId
    - isHiddenFor (map)

messages/
  - messageId
    - chatId
    - senderId, receiverId
    - text, imageUrl
    - timestamp
    - isRead

notifications/
  - notificationId
    - userId
    - type, title, message
    - isRead
    - timestamp

reports/
  - reportId
    - reporterId
    - reportedUserId / reportedPostId
    - reason, description
    - status (pending/resolved/dismissed)
    - createdAt

supportTickets/
  - ticketId
    - userId
    - category, priority
    - subject, description
    - status (open/in_progress/resolved/closed)
    - assignedTo (adminId)
    - messages[]
    - createdAt, updatedAt

verification_requests/
  - requestId
    - userId
    - idType, idNumber
    - idImageUrl, selfieUrl
    - status (pending/approved/rejected)
    - reviewedBy (adminId)
    - reviewNotes
    - createdAt, reviewedAt

ratings/
  - ratingId
    - sellerId, buyerId
    - postId
    - rating (1-5)
    - review, images[]
    - timestamp

transactions/
  - transactionId
    - postId
    - sellerId, buyerId
    - amount
    - type (SELL/BID)
    - status (completed/pending/failed)
    - createdAt
```

---

## 🎨 Dashboard Requirements

### 1. **Authentication & Authorization**

#### Login Page
```tsx
Features:
- Email/password login
- "Remember me" checkbox
- Password reset link
- Two-factor authentication (optional)
- Brute force protection
- Session management

Firebase Setup:
- Admin users must have custom claim: { admin: true }
- Role-based access: super_admin, moderator, support_agent
```

#### User Roles & Permissions
```typescript
interface AdminUser {
  uid: string;
  email: string;
  role: 'super_admin' | 'moderator' | 'support_agent';
  permissions: {
    users: { view: boolean; edit: boolean; delete: boolean };
    posts: { view: boolean; edit: boolean; delete: boolean };
    bids: { view: boolean; manage: boolean };
    reports: { view: boolean; resolve: boolean };
    support: { view: boolean; respond: boolean };
    analytics: { view: boolean };
    settings: { view: boolean; edit: boolean };
  };
}
```

---

### 2. **Main Dashboard (Landing Page)**

#### Layout
```
┌──────────────────────────────────────────────────────┐
│  Header: Logo | Search | Notifications | Profile      │
├────────┬─────────────────────────────────────────────┤
│        │  📊 Dashboard Overview                       │
│ Side   │  ┌──────────┬──────────┬──────────┬────────┐│
│ bar    │  │👥 Users  │📝 Posts  │🔨 Auctions│💰 Rev ││
│        │  │ 1,234    │  456     │    89     │₱123K  ││
│ - Dash │  │ +52 ▲   │  +23 ▲  │   -5 ▼   │+₱12K ▲││
│ - Users│  └──────────┴──────────┴──────────┴────────┘│
│ - Posts│                                               │
│ - Bids │  📈 User Growth Chart (Last 30 Days)         │
│ - Reports│  [Line Chart]                              │
│ - Support│                                             │
│ - Analytics│ 📊 Transaction Volume (This Month)      │
│ - Settings │ [Bar Chart]                              │
│            │                                           │
│            │ ⚠️ Alerts & Pending Actions              │
│            │ • 15 Pending ID Verifications            │
│            │ • 8 Pending Bidding Approvals            │
│            │ • 5 Unresolved Reports                   │
│            │ • 12 Open Support Tickets                │
│            │ • 3 Auctions Ending in < 1 Hour          │
│            │                                           │
│            │ 🔥 Recent Activity Feed                  │
│            │ • User "John D." registered - 2 min ago  │
│            │ • Bid ₱50,000 on Post #1234 - 5 min ago │
│            │ • Ticket #456 resolved - 10 min ago     │
└────────┴──────────────────────────────────────────────┘
```

#### Dashboard Components
```tsx
// Quick Stats Cards
<Grid container spacing={3}>
  <StatCard
    title="Total Users"
    value={1234}
    change="+52 this week"
    trend="up"
    icon={<UsersIcon />}
    color="primary"
  />
  <StatCard title="Active Posts" value={456} ... />
  <StatCard title="Live Auctions" value={89} ... />
  <StatCard title="Revenue This Month" value="₱123,456" ... />
</Grid>

// Charts
<UserGrowthChart data={last30Days} />
<TransactionVolumeChart data={thisMonth} />
<CategoryDistributionPieChart data={categories} />

// Alerts Section
<AlertsList>
  <Alert severity="warning" action={<Button>Review</Button>}>
    15 Pending ID Verifications
  </Alert>
  <Alert severity="info" action={<Button>View</Button>}>
    8 Pending Bidding Approvals
  </Alert>
  ...
</AlertsList>

// Recent Activity Feed (Real-time)
<ActivityFeed>
  {activities.map(activity => (
    <ActivityItem
      user={activity.user}
      action={activity.action}
      timestamp={activity.timestamp}
      icon={getActivityIcon(activity.type)}
    />
  ))}
</ActivityFeed>
```

---

### 3. **User Management** 👥

#### User List Page
```tsx
Features:
- Searchable data table (username, email, phone)
- Filters:
  * Verification Status (all/pending/approved/rejected)
  * Bidding Approval (all/pending/approved/rejected/banned)
  * Role (all/user/admin)
  * Account Status (active/banned)
  * Join Date Range
  * Has Rating (yes/no)
- Sortable columns
- Pagination (25/50/100 per page)
- Bulk actions: 
  * Approve verification
  * Approve bidding
  * Ban users
  * Export to CSV
- Quick actions per row:
  * View Details
  * Edit
  * Ban/Unban
  * Send Notification

Table Columns:
┌──────────┬────────────┬──────────┬────────────┬──────────┬─────────┬─────────┐
│ Avatar   │ Name       │ Email    │ ID Verify  │ Bidding  │ Posts   │ Actions │
│          │            │          │ Status     │ Approval │ Created │         │
├──────────┼────────────┼──────────┼────────────┼──────────┼─────────┼─────────┤
│ [img]    │ John Doe   │ john@... │ ✅Approved │ ⏳Pending│ 5       │ [•••]   │
│ [img]    │ Jane Smith │ jane@... │ ⏳Pending  │ ❌None   │ 2       │ [•••]   │
│ [img]    │ Bob Wilson │ bob@...  │ ✅Approved │ ✅Approved│ 12      │ [•••]   │
└──────────┴────────────┴──────────┴────────────┴──────────┴─────────┴─────────┘

Implementation:
<UserTable
  users={users}
  onSearch={handleSearch}
  onFilter={handleFilter}
  onSort={handleSort}
  onBulkAction={handleBulkAction}
  onViewDetails={handleViewDetails}
  onEdit={handleEdit}
  onBan={handleBan}
/>
```

#### User Details Modal/Page
```tsx
Sections:
1. Profile Information
   - Avatar, Name, Email, Phone
   - Join Date, Last Login
   - Location
   - Edit button

2. Verification Status
   - ID Verification: 
     * Status badge
     * View submitted documents
     * Approve/Reject buttons
     * Rejection reason field
   - Bidding Approval:
     * Status badge
     * Application details
     * Approve/Reject/Ban buttons
     * Notes field

3. Activity Summary
   - Posts Created: 12 (8 SELL, 4 BID)
   - Total Bids Placed: 45
   - Purchases Made: 8
   - Items Sold: 5
   - Account Balance: ₱12,345

4. Ratings & Reviews
   - Seller Rating: 4.5 ⭐ (23 ratings)
   - View all ratings received
   - Flagged reviews

5. Posts
   - List of all posts created
   - Status, Views, Favorites
   - Quick edit/delete

6. Bidding History
   - All bids placed
   - Won/Lost status
   - Total spent

7. Reports
   - Reports filed by user: 3
   - Reports against user: 1
   - View details

8. Chats & Messages
   - Active chats: 5
   - Total messages sent: 234
   - Flagged conversations

9. Admin Actions
   - Ban User (with reason)
   - Reset Password
   - Send Notification
   - Delete Account
   - View Audit Log

<UserDetailsModal user={selectedUser}>
  <Tabs>
    <Tab label="Profile" />
    <Tab label="Verification" />
    <Tab label="Activity" />
    <Tab label="Ratings" />
    <Tab label="Posts" />
    <Tab label="Bids" />
    <Tab label="Reports" />
    <Tab label="Admin Actions" />
  </Tabs>
</UserDetailsModal>
```

---

### 4. **ID Verification Management** ✅

#### Verification Requests Page
```tsx
Features:
- Queue of pending verifications
- Filter by status, submission date
- Sort by oldest first (priority)
- Quick approve/reject

Layout:
┌────────────────────────────────────────────────────────┐
│  Pending Verifications (15)                             │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ User: John Doe (john@example.com)                │  │
│ │ Submitted: 2 days ago                            │  │
│ │                                                   │  │
│ │ ID Type: Driver's License                        │  │
│ │ ID Number: A123-4567-8901                       │  │
│ │                                                   │  │
│ │ [ID Front Image]  [ID Back Image]  [Selfie]     │  │
│ │  Click to enlarge                                │  │
│ │                                                   │  │
│ │ Notes: _____________________________________     │  │
│ │                                                   │  │
│ │ [✅ Approve] [❌ Reject] [🔄 Request Resubmit]  │  │
│ └──────────────────────────────────────────────────┘  │
│                                                         │
│ [Next Request]                                          │
└────────────────────────────────────────────────────────┘

Image Viewer:
- Zoom in/out
- Rotate
- Side-by-side comparison
- Face verification check (optional AI)

Actions:
- Approve: Updates verificationStatus to "approved"
- Reject: Updates to "rejected", notify user with reason
- Request Resubmit: Send notification with specific requirements
```

---

### 5. **Bidding Approval Management** 🔨 (NEW)

#### Bidding Applications Page
```tsx
Features:
- Queue of pending bidding approvals
- View user qualifications
- Check user history
- Approve/Reject with notes

Layout:
┌────────────────────────────────────────────────────────┐
│  Pending Bidding Approvals (8)                         │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ User: Jane Smith                                 │  │
│ │ Applied: 1 day ago                               │  │
│ │                                                   │  │
│ │ User Qualifications:                             │  │
│ │ ✅ ID Verified                                   │  │
│ │ ✅ Account Age: 15 days                         │  │
│ │ ✅ Posts Created: 3                             │  │
│ │ ✅ Messages Sent: 12                            │  │
│ │ ✅ Rating: 4.2 ⭐ (5 reviews)                   │  │
│ │ ⚠️  Failed Bids: 0                              │  │
│ │ ⚠️  Reports Against: 0                          │  │
│ │                                                   │  │
│ │ Application Reason:                              │  │
│ │ "I want to participate in livestock auctions..." │  │
│ │                                                   │  │
│ │ Admin Notes: _________________________________   │  │
│ │                                                   │  │
│ │ [✅ Approve] [❌ Reject] [🚫 Ban from Bidding]  │  │
│ └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Approval Criteria Check:
- Auto-check eligibility using BiddingCriteriaChecker logic
- Show green/yellow/red indicators
- Recommended action based on criteria
```

---

### 6. **Post & Content Management** 📝

#### Posts List Page
```tsx
Features:
- View all posts (SELL & BID)
- Filters:
  * Type (ALL/SELL/BID)
  * Status (ACTIVE/SOLD/ENDED)
  * Category (Livestock types)
  * Date Range
  * Price Range
  * Flagged/Reported
- Search by title, description, ID
- Sort by date, price, views, favorites
- Grid or List view toggle

Grid View:
┌──────────┬──────────┬──────────┬──────────┐
│ [Image]  │ [Image]  │ [Image]  │ [Image]  │
│ Title    │ Title    │ Title    │ Title    │
│ ₱1,000   │ ₱2,500   │ ₱5,000   │ ₱750     │
│ SELL     │ BID      │ BID      │ SELL     │
│ Active   │ 2d left  │ Ended    │ Sold     │
│ [•••]    │ [•••]    │ [•••]    │ [•••]    │
└──────────┴──────────┴──────────┴──────────┘

Actions:
- View Details
- Edit Post
- Delete Post
- Feature Post
- Mark as Sold/Ended
```

#### Post Details Modal
```tsx
<PostDetailsModal post={selectedPost}>
  <Tabs>
    <Tab label="Details">
      - All post information
      - Images carousel
      - Seller information
      - Location
      - Description
      - Edit button
    </Tab>
    
    <Tab label="Bidding" if={post.type === 'BID'}>
      - Current highest bid
      - Total bids: 45
      - Total bidders: 12
      - Bid history table:
        ┌────────────┬────────┬──────────┬───────────┐
        │ Codename   │ Amount │ Time     │ Status    │
        ├────────────┼────────┼──────────┼───────────┤
        │ RedSwift123│ ₱5,200 │ 2 min ago│ Current   │
        │ BlueQuick45│ ₱5,100 │ 5 min ago│ Outbid    │
        │ GreenFast78│ ₱5,000 │ 8 min ago│ Outbid    │
        └────────────┴────────┴──────────┴───────────┘
      - Real user mapping (admin only):
        Codename → Real User
      - Countdown timer
      - Extend auction option
    </Tab>
    
    <Tab label="Analytics">
      - Views: 234
      - Favorites: 12
      - Messages: 8
      - Engagement chart
    </Tab>
    
    <Tab label="Reports" if={hasReports}>
      - Reports filed against this post
      - Reason, reporter, status
    </Tab>
  </Tabs>
  
  <AdminActions>
    <Button onClick={handleFeature}>Feature Post</Button>
    <Button onClick={handleEdit}>Edit</Button>
    <Button onClick={handleDelete} color="error">Delete</Button>
  </AdminActions>
</PostDetailsModal>
```

---

### 7. **Live Bidding Monitoring** 🔨

#### Active Auctions Dashboard
```tsx
Features:
- Real-time monitoring of all active auctions
- Auto-refresh every 5 seconds
- Highlight auctions ending soon
- Detect suspicious bidding patterns

Layout:
┌────────────────────────────────────────────────────────┐
│  🔴 LIVE Active Auctions (89)                          │
│  Filters: [Ending Soon] [High Value] [Suspicious]     │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ 🏆 Premium Bull - Brahman                        │  │
│ │ Current Bid: ₱125,000 (23 bids, 8 bidders)      │  │
│ │ Time Left: ⏰ 00:45:23 🔴                        │  │
│ │ Last Bid: RedSwift123 - 2 min ago               │  │
│ │ [View Details] [Extend Time] [End Now]          │  │
│ └──────────────────────────────────────────────────┘  │
│                                                         │
│ ┌──────────────────────────────────────────────────┐  │
│ │ ⚠️ Dairy Cow - Holstein                         │  │
│ │ Current Bid: ₱45,000 (67 bids, 3 bidders)       │  │
│ │ Time Left: ⏰ 2 days, 03:15:42                   │  │
│ │ ⚠️ Suspicious: Shill bidding detected           │  │
│ │ [Investigate] [Cancel Auction]                   │  │
│ └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Real-time Features:
- WebSocket or Firebase listeners for live updates
- Push notifications for critical events
- Bid velocity tracking
- Fraud detection algorithms
```

#### Bidding Analytics
```tsx
<BiddingAnalytics>
  <MetricCard title="Total Bids Today" value={1,234} />
  <MetricCard title="Avg Bid Amount" value="₱15,234" />
  <MetricCard title="Active Bidders" value={456} />
  <MetricCard title="Completion Rate" value="87%" />
  
  <Chart type="line" title="Bidding Activity (24h)" />
  <Chart type="bar" title="Top Bidders This Month" />
  <Chart type="pie" title="Bids by Category" />
</BiddingAnalytics>
```

---

### 8. **Reports & Safety** 🛡️

#### Reports Queue
```tsx
Features:
- Pending, resolved, dismissed filters
- Sort by priority, date
- Report types: User, Post, Message
- Severity levels: Low, Medium, High, Critical

Layout:
┌────────────────────────────────────────────────────────┐
│  Reports Queue (5 Pending)                              │
│  [All] [Pending] [In Review] [Resolved] [Dismissed]   │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ 🚨 HIGH Priority                                 │  │
│ │ Report #1234 - User Report                       │  │
│ │ Reporter: John Doe → Reported: Jane Smith       │  │
│ │ Reason: Harassment                               │  │
│ │ Details: "This user sent threatening messages..." │  │
│ │ Evidence: [Screenshot1.jpg] [Screenshot2.jpg]   │  │
│ │ Submitted: 3 hours ago                           │  │
│ │                                                   │  │
│ │ Similar Reports: 2 other users reported Jane    │  │
│ │                                                   │  │
│ │ Quick Actions:                                   │  │
│ │ [View User Profile] [View Chat History]         │  │
│ │ [Warn User] [Temporary Ban] [Permanent Ban]     │  │
│ │ [Dismiss Report] [Mark as Resolved]             │  │
│ └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Report Investigation Tools:
- View reported content in context
- Check user history
- See previous reports
- Recommended actions based on severity
- Template responses
```

---

### 9. **Support Tickets** 💬

#### Tickets Dashboard
```tsx
Features:
- Open, In Progress, Resolved, Closed tabs
- Priority levels (Low, Medium, High, Urgent)
- Category filters (Account, Bidding, Payment, Technical, Other)
- Assignment system
- SLA tracking

Ticket List:
┌──────┬───────────┬──────────┬─────────┬──────────┬────────┐
│ ID   │ User      │ Subject  │ Category│ Priority │ Status │
├──────┼───────────┼──────────┼─────────┼──────────┼────────┤
│ #4567│ John Doe  │ Can't bid│ Bidding │ 🔴 High  │ Open   │
│ #4566│ Jane S.   │ Payment..│ Payment │ 🟡 Med   │ In Prog│
│ #4565│ Bob W.    │ Account..│ Account │ 🟢 Low   │ Open   │
└──────┴───────────┴──────────┴─────────┴──────────┴────────┘

Ticket Details View:
┌────────────────────────────────────────────────────────┐
│  Ticket #4567 - Can't bid on items                     │
│  Status: Open | Priority: High | Category: Bidding    │
│  Created: 2 hours ago | SLA: 2h remaining             │
├────────────────────────────────────────────────────────┤
│  User: John Doe (john@example.com)                     │
│  [View Profile] [View Activity]                        │
├────────────────────────────────────────────────────────┤
│  Conversation:                                          │
│  ┌──────────────────────────────────────────────────┐ │
│  │ 👤 John (2h ago):                                │ │
│  │ "I'm trying to bid on livestock posts but I     │ │
│  │  keep getting an error message..."               │ │
│  │  [screenshot.jpg]                                │ │
│  └──────────────────────────────────────────────────┘ │
│                                                         │
│  ┌──────────────────────────────────────────────────┐ │
│  │ 👨‍💼 Admin Reply:                                  │ │
│  │ [Canned Response ▼] [Templates ▼]               │ │
│  │ ____________________________________________     │ │
│  │ ____________________________________________     │ │
│  │                                                   │ │
│  │ [📎 Attach File] [Send Reply]                   │ │
│  └──────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│  Actions:                                               │
│  Assign to: [Dropdown] | Priority: [Dropdown]         │
│  [Mark as Resolved] [Escalate] [Close Ticket]         │
└────────────────────────────────────────────────────────┘

Canned Responses Library:
- Bidding not approved
- ID verification required
- Account suspended
- Payment issue
- Technical troubleshooting steps
```

---

### 10. **Transactions & Financial** 💰

#### Transactions Page
```tsx
Features:
- All completed transactions
- Filter by date, amount, type (SELL/BID), status
- Export to CSV for accounting
- Revenue tracking

Table:
┌──────┬───────┬────────┬──────────┬────────┬────────┬────────┐
│ ID   │ Date  │ Type   │ Buyer    │ Seller │ Amount │ Status │
├──────┼───────┼────────┼──────────┼────────┼────────┼────────┤
│#T1234│Jan 15│ BID    │ John D.  │ Jane S.│₱50,000 │Complete│
│#T1233│Jan 15│ SELL   │ Bob W.   │ Alice M│₱12,500 │Complete│
│#T1232│Jan 14│ BID    │ Charlie B│ Dave K.│₱35,000 │Pending │
└──────┴───────┴────────┴──────────┴────────┴────────┴────────┘

Transaction Details:
- Post information
- Buyer and seller profiles
- Transaction timeline
- Payment method (if applicable)
- Platform fee calculation
- Refund option (if needed)

Financial Dashboard:
┌────────────────────────────────────────────────────────┐
│  💰 Financial Overview - January 2025                  │
├────────────────────────────────────────────────────────┤
│  Total Transaction Volume: ₱1,234,567                  │
│  Platform Fees Collected: ₱61,728 (5%)                │
│  Total Transactions: 456                                │
│  Average Transaction: ₱2,707                            │
├────────────────────────────────────────────────────────┤
│  📊 Charts:                                             │
│  - Daily Revenue Trend                                  │
│  - Revenue by Category                                  │
│  - Top Sellers/Buyers                                   │
│  - Payment Method Distribution                          │
└────────────────────────────────────────────────────────┘
```

---

### 11. **Analytics & Insights** 📊

#### Analytics Dashboard
```tsx
<AnalyticsDashboard>
  {/* Time Range Selector */}
  <DateRangePicker
    options={['Today', 'This Week', 'This Month', 'This Year', 'Custom']}
  />
  
  {/* KPI Overview */}
  <Grid container spacing={3}>
    <KPICard
      title="Daily Active Users"
      value={1,234}
      change="+12%"
      period="vs yesterday"
      chart={<SparklineChart data={last7Days} />}
    />
    <KPICard title="New Signups" value={52} change="+8%" />
    <KPICard title="Posts Created" value={23} change="-3%" />
    <KPICard title="Revenue" value="₱45,678" change="+15%" />
  </Grid>
  
  {/* Charts */}
  <Grid container spacing={3}>
    <Chart
      type="line"
      title="User Growth (Last 30 Days)"
      data={userGrowthData}
      yAxis="Users"
      xAxis="Date"
    />
    
    <Chart
      type="bar"
      title="Posts by Category"
      data={categoryData}
      yAxis="Count"
      xAxis="Category"
    />
    
    <Chart
      type="pie"
      title="Post Type Distribution"
      data={[
        { name: 'SELL', value: 234 },
        { name: 'BID', value: 222 }
      ]}
    />
    
    <Chart
      type="area"
      title="Transaction Volume"
      data={transactionData}
      yAxis="Amount (₱)"
      xAxis="Date"
    />
  </Grid>
  
  {/* Top Lists */}
  <Grid container spacing={3}>
    <TopList
      title="Top Sellers This Month"
      items={[
        { name: 'John Doe', value: '₱125,000', count: '12 sales' },
        { name: 'Jane Smith', value: '₱98,500', count: '8 sales' },
        ...
      ]}
    />
    
    <TopList
      title="Most Active Bidders"
      items={[
        { name: 'Bob Wilson', value: '145 bids', spent: '₱456,000' },
        ...
      ]}
    />
    
    <TopList
      title="Popular Categories"
      items={[
        { name: 'Cattle', value: '234 posts', percentage: '45%' },
        { name: 'Goat', value: '156 posts', percentage: '30%' },
        ...
      ]}
    />
  </Grid>
  
  {/* Custom Reports */}
  <CustomReportBuilder>
    <ReportFilters>
      - Date Range
      - User Segment
      - Post Type
      - Category
      - Location
    </ReportFilters>
    <ReportMetrics>
      - Select metrics to include
      - Choose visualization
      - Export format (CSV, PDF, Excel)
    </ReportMetrics>
    <Button onClick={generateReport}>Generate Report</Button>
  </CustomReportBuilder>
</AnalyticsDashboard>
```

---

### 12. **Settings & Configuration** ⚙️

#### Platform Settings Page
```tsx
<SettingsTabs>
  <Tab label="General">
    - App Name
    - Logo Upload
    - Contact Email
    - Support Phone
    - Business Hours
    - Timezone
  </Tab>
  
  <Tab label="Bidding Rules">
    - Min Auction Duration: [24] hours
    - Max Auction Duration: [7] days
    - Default Bid Increment: [₱10]
    - Auto-extend on last-minute bid: [Yes/No]
    - Auto-extend duration: [5] minutes
    - Bidding Eligibility:
      * Min Account Age: [7] days
      * Min Posts: [0]
      * Min Messages: [5]
      * ID Verification Required: [Yes/No]
      * Bidding Approval Required: [Yes/No]
  </Tab>
  
  <Tab label="Content Policies">
    - Prohibited Items (textarea)
    - Image Requirements:
      * Min Images: [1]
      * Max Images: [5]
      * Max File Size: [5] MB
      * Allowed Formats: JPG, PNG, WebP
    - Description Min Length: [50] chars
    - Auto-moderation: [Enabled/Disabled]
  </Tab>
  
  <Tab label="Categories">
    - Livestock Categories:
      * Cattle ✏️ 🗑️
      * Goat ✏️ 🗑️
      * Sheep ✏️ 🗑️
      * Carabao ✏️ 🗑️
      * Swine ✏️ 🗑️
      [+ Add Category]
  </Tab>
  
  <Tab label="Fees & Commission">
    - Platform Commission: [5]%
    - Payment Processing Fee: [2.5]%
    - Featured Post Fee: [₱500] per week
    - Premium Badge Fee: [₱1000] per month
  </Tab>
  
  <Tab label="Notifications">
    - Email Notifications: [Enabled/Disabled]
    - Push Notifications: [Enabled/Disabled]
    - SMS Notifications: [Enabled/Disabled]
    - Notification Templates:
      * New User Welcome
      * Verification Approved
      * Bidding Approved
      * Auction Ending Soon
      * Bid Won
      * Payment Received
      [Edit] [Preview]
  </Tab>
  
  <Tab label="Security">
    - Password Requirements:
      * Min Length: [8]
      * Require Uppercase: [Yes]
      * Require Numbers: [Yes]
      * Require Special Chars: [Yes]
    - Two-Factor Authentication: [Optional/Required]
    - Session Timeout: [30] minutes
    - Max Login Attempts: [5]
    - Lockout Duration: [15] minutes
  </Tab>
  
  <Tab label="Backup & Maintenance">
    - Automated Backups: [Daily at 2:00 AM]
    - Maintenance Mode: [Off]
    - Maintenance Message: (textarea)
    - Data Retention Period: [365] days
  </Tab>
</SettingsTabs>
```

---

### 13. **Admin Team Management** 👔

#### Admin Users Page
```tsx
Features:
- List of all admin users
- Role management
- Activity logs
- Add/remove admins

Table:
┌───────────┬──────────────┬────────────┬──────────┬────────────┐
│ Name      │ Email        │ Role       │ Last Login│ Actions   │
├───────────┼──────────────┼────────────┼──────────┼────────────┤
│ Admin One │ admin1@...   │ Super Admin│ 5 min ago│ [Edit] [•••]│
│ Mod Two   │ mod2@...     │ Moderator  │ 2 hrs ago│ [Edit] [🗑️] │
│ Support 3 │ support3@... │ Support    │ Yesterday│ [Edit] [🗑️] │
└───────────┴──────────────┴────────────┴──────────┴────────────┘

Add Admin Form:
- Email
- Role: Super Admin / Moderator / Support Agent
- Permissions Matrix:
  ┌──────────────┬──────┬──────┬────────┐
  │ Feature      │ View │ Edit │ Delete │
  ├──────────────┼──────┼──────┼────────┤
  │ Users        │ ✅   │ ✅   │ ✅     │
  │ Posts        │ ✅   │ ✅   │ ❌     │
  │ Bids         │ ✅   │ ✅   │ ❌     │
  │ Reports      │ ✅   │ ✅   │ ❌     │
  │ Support      │ ✅   │ ✅   │ ❌     │
  │ Analytics    │ ✅   │ ❌   │ ❌     │
  │ Settings     │ ✅   │ ❌   │ ❌     │
  └──────────────┴──────┴──────┴────────┘

Activity Logs:
┌────────────┬────────────┬────────────────────────────┐
│ Timestamp  │ Admin      │ Action                      │
├────────────┼────────────┼────────────────────────────┤
│ 2 min ago  │ Admin One  │ Approved user verification │
│ 5 min ago  │ Mod Two    │ Deleted post #1234         │
│ 10 min ago │ Support 3  │ Resolved ticket #4567      │
└────────────┴────────────┴────────────────────────────┘
```

---

## 🎨 UI/UX Design Guidelines

### Design System
```
Colors:
- Primary: #2196F3 (Blue - Trust, professionalism)
- Secondary: #FFD700 (Gold - Premium, livestock)
- Success: #4CAF50 (Green)
- Warning: #FF9800 (Orange)
- Error: #F44336 (Red)
- Info: #00BCD4 (Cyan)
- Background: #F5F5F5 (Light gray)
- Surface: #FFFFFF (White)
- Text Primary: #212121 (Dark gray)
- Text Secondary: #757575 (Gray)

Typography:
- Headings: Roboto Bold
- Body: Roboto Regular
- Monospace (for IDs): Roboto Mono

Spacing:
- Base unit: 8px
- Small: 8px
- Medium: 16px
- Large: 24px
- XLarge: 32px

Shadows:
- Card: 0 2px 4px rgba(0,0,0,0.1)
- Elevated: 0 4px 8px rgba(0,0,0,0.15)
- Modal: 0 8px 16px rgba(0,0,0,0.2)

Border Radius:
- Small: 4px
- Medium: 8px
- Large: 12px
- Round: 50%
```

### Responsive Design
```
Breakpoints:
- Mobile: < 600px
- Tablet: 600px - 960px
- Desktop: 960px - 1280px
- Large Desktop: > 1280px

Mobile Considerations:
- Collapsible sidebar
- Touch-friendly button sizes (min 44x44px)
- Simplified tables (card view)
- Bottom navigation for key actions
```

### Accessibility
```
Requirements:
- WCAG 2.1 AA compliance
- Keyboard navigation support
- Screen reader friendly
- Color contrast ratio: 4.5:1 minimum
- Focus indicators
- ARIA labels
- Alt text for images
```

---

## 🔐 Security Requirements

### Authentication
```typescript
// Firebase Auth with custom claims
const setAdminClaim = async (userId: string, role: string) => {
  await admin.auth().setCustomUserClaims(userId, {
    admin: true,
    role: role // 'super_admin', 'moderator', 'support_agent'
  });
};

// Protected routes
const ProtectedRoute = ({ children, requiredRole }) => {
  const { user } = useAuth();
  
  if (!user?.customClaims?.admin) {
    return <Navigate to="/login" />;
  }
  
  if (requiredRole && user.customClaims.role !== requiredRole) {
    return <Navigate to="/unauthorized" />;
  }
  
  return children;
};
```

### Data Access
```
Firestore Security Rules:
- Admin users identified by custom claim: request.auth.token.admin == true
- Read/write access to all collections for admins
- Audit logging for all admin actions
- IP whitelisting (optional)
- Rate limiting on API calls
```

### Best Practices
```
- HTTPS only
- Environment variables for sensitive data
- Regular security audits
- Password complexity requirements
- Session management
- CSRF protection
- XSS prevention
- SQL injection prevention (N/A for Firestore)
```

---

## 📱 Real-time Features

### Firebase Listeners
```typescript
// Real-time user count
useEffect(() => {
  const unsubscribe = firestore.collection('users')
    .onSnapshot(snapshot => {
      setUserCount(snapshot.size);
    });
  return () => unsubscribe();
}, []);

// Real-time bidding updates
useEffect(() => {
  const unsubscribe = firestore.collection('bids')
    .where('postId', '==', selectedPostId)
    .orderBy('timestamp', 'desc')
    .limit(50)
    .onSnapshot(snapshot => {
      const bids = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
      setBidHistory(bids);
    });
  return () => unsubscribe();
}, [selectedPostId]);

// Real-time notifications
useEffect(() => {
  const unsubscribe = firestore.collection('admin_notifications')
    .where('read', '==', false)
    .onSnapshot(snapshot => {
      setUnreadNotifications(snapshot.size);
    });
  return () => unsubscribe();
}, []);
```

---

## 🚀 Implementation Phases

### Phase 1: Core Setup (Week 1)
```
✅ Project scaffolding (React + TypeScript)
✅ Firebase integration
✅ Authentication system
✅ Basic layout (sidebar, header)
✅ Dashboard landing page
✅ User management (list, view, search)
```

### Phase 2: Verification & Approvals (Week 2)
```
✅ ID Verification page
✅ Bidding Approval page
✅ Image viewer component
✅ Approval/rejection workflows
✅ User notifications integration
```

### Phase 3: Content Management (Week 3)
```
✅ Posts management
✅ Post details modal
✅ Live bidding monitoring
✅ Bid history viewer
✅ Content moderation tools
```

### Phase 4: Safety & Support (Week 4)
```
✅ Reports queue
✅ Report investigation tools
✅ Support tickets system
✅ Canned responses
✅ User blocking/banning
```

### Phase 5: Analytics & Financials (Week 5)
```
✅ Analytics dashboard
✅ Charts and graphs
✅ Transaction history
✅ Financial reports
✅ Export functionality
```

### Phase 6: Settings & Admin (Week 6)
```
✅ Platform settings
✅ Admin user management
✅ Permissions system
✅ Activity logs
✅ Notification templates
```

### Phase 7: Polish & Optimization (Week 7-8)
```
✅ Performance optimization
✅ Mobile responsiveness
✅ Accessibility improvements
✅ Error handling
✅ Loading states
✅ Documentation
✅ Testing
```

---

## 📝 Code Structure

```
admin-dashboard/
├── public/
│   ├── index.html
│   └── favicon.ico
├── src/
│   ├── components/
│   │   ├── common/
│   │   │   ├── Button.tsx
│   │   │   ├── Card.tsx
│   │   │   ├── Table.tsx
│   │   │   ├── Modal.tsx
│   │   │   ├── Chart.tsx
│   │   │   └── ...
│   │   ├── layout/
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   ├── Footer.tsx
│   │   │   └── Layout.tsx
│   │   ├── dashboard/
│   │   │   ├── StatCard.tsx
│   │   │   ├── ActivityFeed.tsx
│   │   │   ├── AlertsList.tsx
│   │   │   └── QuickActions.tsx
│   │   ├── users/
│   │   │   ├── UserTable.tsx
│   │   │   ├── UserDetailsModal.tsx
│   │   │   ├── UserFilters.tsx
│   │   │   └── BulkActions.tsx
│   │   ├── verification/
│   │   │   ├── VerificationQueue.tsx
│   │   │   ├── VerificationCard.tsx
│   │   │   ├── ImageViewer.tsx
│   │   │   └── ApprovalActions.tsx
│   │   ├── bidding/
│   │   │   ├── BiddingApprovalQueue.tsx
│   │   │   ├── BiddingApprovalCard.tsx
│   │   │   └── QualificationChecker.tsx
│   │   ├── posts/
│   │   │   ├── PostsGrid.tsx
│   │   │   ├── PostsList.tsx
│   │   │   ├── PostDetailsModal.tsx
│   │   │   └── PostFilters.tsx
│   │   ├── auctions/
│   │   │   ├── LiveAuctionsMonitor.tsx
│   │   │   ├── AuctionCard.tsx
│   │   │   ├── BidHistory.tsx
│   │   │   └── CodenameMapper.tsx
│   │   ├── reports/
│   │   │   ├── ReportsQueue.tsx
│   │   │   ├── ReportCard.tsx
│   │   │   └── InvestigationTools.tsx
│   │   ├── support/
│   │   │   ├── TicketsList.tsx
│   │   │   ├── TicketDetails.tsx
│   │   │   ├── CannedResponses.tsx
│   │   │   └── ConversationView.tsx
│   │   ├── analytics/
│   │   │   ├── KPICard.tsx
│   │   │   ├── LineChart.tsx
│   │   │   ├── BarChart.tsx
│   │   │   ├── PieChart.tsx
│   │   │   └── CustomReport.tsx
│   │   └── settings/
│   │       ├── GeneralSettings.tsx
│   │       ├── BiddingRules.tsx
│   │       ├── ContentPolicies.tsx
│   │       └── AdminUsers.tsx
│   ├── pages/
│   │   ├── Login.tsx
│   │   ├── Dashboard.tsx
│   │   ├── Users.tsx
│   │   ├── Verification.tsx
│   │   ├── BiddingApproval.tsx
│   │   ├── Posts.tsx
│   │   ├── LiveAuctions.tsx
│   │   ├── Reports.tsx
│   │   ├── Support.tsx
│   │   ├── Transactions.tsx
│   │   ├── Analytics.tsx
│   │   ├── Settings.tsx
│   │   └── AdminManagement.tsx
│   ├── services/
│   │   ├── firebase.ts
│   │   ├── auth.service.ts
│   │   ├── users.service.ts
│   │   ├── posts.service.ts
│   │   ├── bids.service.ts
│   │   ├── reports.service.ts
│   │   ├── support.service.ts
│   │   ├── analytics.service.ts
│   │   └── notifications.service.ts
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useFirestore.ts
│   │   ├── useRealtime.ts
│   │   ├── usePagination.ts
│   │   └── useFilter.ts
│   ├── utils/
│   │   ├── formatters.ts
│   │   ├── validators.ts
│   │   ├── constants.ts
│   │   └── helpers.ts
│   ├── types/
│   │   ├── user.types.ts
│   │   ├── post.types.ts
│   │   ├── bid.types.ts
│   │   ├── report.types.ts
│   │   └── ...
│   ├── store/
│   │   ├── auth.slice.ts
│   │   ├── users.slice.ts
│   │   ├── posts.slice.ts
│   │   └── ...
│   ├── App.tsx
│   ├── index.tsx
│   └── routes.tsx
├── .env.example
├── .gitignore
├── package.json
├── tsconfig.json
└── README.md
```

---

## 🧪 Testing Requirements

```
Unit Tests:
- Component rendering
- Utility functions
- Form validation
- Data formatting

Integration Tests:
- Firebase operations
- Authentication flow
- CRUD operations
- Real-time listeners

E2E Tests (Cypress):
- Login flow
- User management
- Approval workflows
- Report handling
- Ticket responses

Coverage Target: 80%+
```

---

## 📚 Documentation Requirements

```
Technical Documentation:
- Architecture overview
- Component documentation
- API reference
- Firebase structure
- Security rules explanation

User Documentation:
- Admin user guide
- Feature walkthroughs
- Troubleshooting guide
- FAQ

Developer Documentation:
- Setup instructions
- Development workflow
- Contribution guidelines
- Deployment process
```

---

## 🚀 Deployment Checklist

```
✅ Environment variables configured
✅ Firebase project set up
✅ Firestore indexes created
✅ Security rules deployed
✅ Cloud Functions deployed
✅ Build optimized (minified, tree-shaken)
✅ Performance tested
✅ Security audit passed
✅ Cross-browser tested
✅ Mobile responsive verified
✅ Analytics integrated
✅ Error tracking (Sentry) set up
✅ Backups configured
✅ SSL certificate active
✅ Custom domain configured
✅ CDN set up (if needed)
```

---

## 💡 Nice-to-Have Features (Future)

```
1. Dark mode
2. Multi-language support
3. Advanced fraud detection AI
4. Automated content moderation (AI)
5. Bulk import/export tools
6. API for third-party integrations
7. Mobile admin app (React Native)
8. Advanced analytics (predictive)
9. A/B testing platform
10. Workflow automation
11. Custom role builder
12. Webhook integrations
13. Advanced reporting (custom SQL)
14. Data visualization dashboard builder
15. Admin chat/communication tool
```

---

## 🎯 Success Metrics

```
Performance:
- Page load time < 2 seconds
- Real-time updates < 500ms
- 99.9% uptime

Usability:
- Admin task completion time reduced by 50%
- User satisfaction score > 4.5/5
- Support ticket response time < 2 hours

Business:
- Platform transaction volume tracked
- Revenue insights accessible
- User growth monitored
- Fraud detection rate > 95%
```

---

## 📞 Support & Maintenance

```
Monitoring:
- Error tracking (Sentry, LogRocket)
- Performance monitoring (Lighthouse CI)
- Uptime monitoring (UptimeRobot)
- Analytics (Google Analytics, Mixpanel)

Maintenance Schedule:
- Daily: Automated backups
- Weekly: Security updates
- Monthly: Performance review
- Quarterly: Feature updates

Support Channels:
- Technical documentation
- Video tutorials
- Email support
- Slack/Discord community
```

---

## ✨ Final Notes

This admin dashboard should be:
- **Intuitive**: Easy to navigate and use
- **Powerful**: Comprehensive feature set
- **Fast**: Optimized performance
- **Secure**: Enterprise-grade security
- **Scalable**: Handle growth gracefully
- **Modern**: Contemporary design and tech
- **Accessible**: WCAG compliant
- **Reliable**: High uptime and stability

**Estimated Development Time**: 6-8 weeks (1-2 developers)
**Budget**: $15,000 - $30,000 (varies by region and team)
**Tech Stack Size**: ~50-80 npm packages
**Lines of Code**: ~15,000 - 25,000

---

## 🎓 Learning Resources

```
React:
- https://react.dev/
- https://react-typescript-cheatsheet.netlify.app/

Material-UI:
- https://mui.com/material-ui/

Firebase:
- https://firebase.google.com/docs
- https://firebase.google.com/docs/firestore
- https://firebase.google.com/docs/auth

State Management:
- https://redux-toolkit.js.org/
- https://zustand-demo.pmnd.rs/

Charts:
- https://recharts.org/
- https://www.chartjs.org/

Best Practices:
- https://github.com/goldbergyoni/nodebestpractices
- https://web.dev/
```

---

**Ready to build? Start with Phase 1 and iterate! Good luck! 🚀**

## Project Overview
Create a comprehensive, modern web-based admin dashboard for **AgriStock** - a livestock marketplace app with bidding/auction functionality. The admin panel should provide complete control over users, content, bidding, transactions, and platform operations.

---

## 🔧 Technology Stack

### Frontend
- **Framework**: React 18+ with TypeScript
- **UI Library**: Material-UI (MUI) v5 or Ant Design v5
- **State Management**: Redux Toolkit or Zustand
- **Routing**: React Router v6
- **Charts**: Recharts or Chart.js
- **Tables**: React Table v8 or MUI DataGrid
- **Forms**: React Hook Form + Yup validation
- **Date Handling**: date-fns or Day.js
- **HTTP Client**: Axios
- **Real-time**: Firebase Realtime Database listeners

### Backend & Services
- **Backend**: Firebase (Firestore, Authentication, Storage, Cloud Functions)
- **Authentication**: Firebase Auth with custom claims for admin roles
- **Database**: Firestore
- **File Storage**: Firebase Storage
- **Cloud Functions**: For backend operations and scheduled tasks
- **Email**: SendGrid or Firebase Extensions
- **SMS**: Twilio (optional)

### Deployment
- **Hosting**: Firebase Hosting or Vercel
- **CI/CD**: GitHub Actions
- **Domain**: Custom domain with SSL

---

## 📊 Current App Context (AgriStock)

### Key Features
1. **User Management**: Registration, ID verification, profiles
2. **Post Types**: 
   - SELL posts (direct purchase)
   - BID posts (auction with live bidding)
3. **Bidding System**: 
   - Live auctions with countdown timers
   - Anonymous bidding with daily-refreshing codenames
   - Bid increments and minimum bids
   - Real-time bid updates
4. **Verification Systems**:
   - ID Verification (verificationStatus: pending/approved/rejected)
   - Bidding Approval (biddingApprovalStatus: pending/approved/rejected/banned)
5. **Messaging**: In-app chat between buyers and sellers
6. **Ratings**: 5-star rating system for sellers
7. **Reports**: User reporting system
8. **Support Tickets**: User support system
9. **Notifications**: Push, in-app, and email notifications
10. **Favorites**: Users can favorite posts
11. **Transactions**: Track completed sales/purchases

### Firestore Collections
```
users/
  - userId
    - username, email, phone
    - verificationStatus (pending/approved/rejected)
    - biddingApprovalStatus (pending/approved/rejected/banned)
    - rating, totalRatings
    - avatarUrl
    - role (user/admin)
    - isAdmin (boolean)
    - accountCreated, lastLogin
    - favorites/ (subcollection)

posts/
  - postId
    - title, description, price
    - type (SELL/BID)
    - category (livestock type)
    - imageUrls[]
    - userId (seller)
    - status (ACTIVE/SOLD/ENDED)
    - location, address
    - startingBid, currentBid, highestBid
    - bidIncrement
    - biddingEndTime
    - totalBidders
    - createdAt, updatedAt

bids/
  - bidId
    - postId
    - userId (bidder)
    - bidAmount
    - timestamp
    - codename (anonymous identifier)

chats/
  - chatId
    - participants[] (userIds)
    - itemId (related post)
    - lastMessage, lastMessageTime
    - unreadCount_userId
    - isHiddenFor (map)

messages/
  - messageId
    - chatId
    - senderId, receiverId
    - text, imageUrl
    - timestamp
    - isRead

notifications/
  - notificationId
    - userId
    - type, title, message
    - isRead
    - timestamp

reports/
  - reportId
    - reporterId
    - reportedUserId / reportedPostId
    - reason, description
    - status (pending/resolved/dismissed)
    - createdAt

supportTickets/
  - ticketId
    - userId
    - category, priority
    - subject, description
    - status (open/in_progress/resolved/closed)
    - assignedTo (adminId)
    - messages[]
    - createdAt, updatedAt

verification_requests/
  - requestId
    - userId
    - idType, idNumber
    - idImageUrl, selfieUrl
    - status (pending/approved/rejected)
    - reviewedBy (adminId)
    - reviewNotes
    - createdAt, reviewedAt

ratings/
  - ratingId
    - sellerId, buyerId
    - postId
    - rating (1-5)
    - review, images[]
    - timestamp

transactions/
  - transactionId
    - postId
    - sellerId, buyerId
    - amount
    - type (SELL/BID)
    - status (completed/pending/failed)
    - createdAt
```

---

## 🎨 Dashboard Requirements

### 1. **Authentication & Authorization**

#### Login Page
```tsx
Features:
- Email/password login
- "Remember me" checkbox
- Password reset link
- Two-factor authentication (optional)
- Brute force protection
- Session management

Firebase Setup:
- Admin users must have custom claim: { admin: true }
- Role-based access: super_admin, moderator, support_agent
```

#### User Roles & Permissions
```typescript
interface AdminUser {
  uid: string;
  email: string;
  role: 'super_admin' | 'moderator' | 'support_agent';
  permissions: {
    users: { view: boolean; edit: boolean; delete: boolean };
    posts: { view: boolean; edit: boolean; delete: boolean };
    bids: { view: boolean; manage: boolean };
    reports: { view: boolean; resolve: boolean };
    support: { view: boolean; respond: boolean };
    analytics: { view: boolean };
    settings: { view: boolean; edit: boolean };
  };
}
```

---

### 2. **Main Dashboard (Landing Page)**

#### Layout
```
┌──────────────────────────────────────────────────────┐
│  Header: Logo | Search | Notifications | Profile      │
├────────┬─────────────────────────────────────────────┤
│        │  📊 Dashboard Overview                       │
│ Side   │  ┌──────────┬──────────┬──────────┬────────┐│
│ bar    │  │👥 Users  │📝 Posts  │🔨 Auctions│💰 Rev ││
│        │  │ 1,234    │  456     │    89     │₱123K  ││
│ - Dash │  │ +52 ▲   │  +23 ▲  │   -5 ▼   │+₱12K ▲││
│ - Users│  └──────────┴──────────┴──────────┴────────┘│
│ - Posts│                                               │
│ - Bids │  📈 User Growth Chart (Last 30 Days)         │
│ - Reports│  [Line Chart]                              │
│ - Support│                                             │
│ - Analytics│ 📊 Transaction Volume (This Month)      │
│ - Settings │ [Bar Chart]                              │
│            │                                           │
│            │ ⚠️ Alerts & Pending Actions              │
│            │ • 15 Pending ID Verifications            │
│            │ • 8 Pending Bidding Approvals            │
│            │ • 5 Unresolved Reports                   │
│            │ • 12 Open Support Tickets                │
│            │ • 3 Auctions Ending in < 1 Hour          │
│            │                                           │
│            │ 🔥 Recent Activity Feed                  │
│            │ • User "John D." registered - 2 min ago  │
│            │ • Bid ₱50,000 on Post #1234 - 5 min ago │
│            │ • Ticket #456 resolved - 10 min ago     │
└────────┴──────────────────────────────────────────────┘
```

#### Dashboard Components
```tsx
// Quick Stats Cards
<Grid container spacing={3}>
  <StatCard
    title="Total Users"
    value={1234}
    change="+52 this week"
    trend="up"
    icon={<UsersIcon />}
    color="primary"
  />
  <StatCard title="Active Posts" value={456} ... />
  <StatCard title="Live Auctions" value={89} ... />
  <StatCard title="Revenue This Month" value="₱123,456" ... />
</Grid>

// Charts
<UserGrowthChart data={last30Days} />
<TransactionVolumeChart data={thisMonth} />
<CategoryDistributionPieChart data={categories} />

// Alerts Section
<AlertsList>
  <Alert severity="warning" action={<Button>Review</Button>}>
    15 Pending ID Verifications
  </Alert>
  <Alert severity="info" action={<Button>View</Button>}>
    8 Pending Bidding Approvals
  </Alert>
  ...
</AlertsList>

// Recent Activity Feed (Real-time)
<ActivityFeed>
  {activities.map(activity => (
    <ActivityItem
      user={activity.user}
      action={activity.action}
      timestamp={activity.timestamp}
      icon={getActivityIcon(activity.type)}
    />
  ))}
</ActivityFeed>
```

---

### 3. **User Management** 👥

#### User List Page
```tsx
Features:
- Searchable data table (username, email, phone)
- Filters:
  * Verification Status (all/pending/approved/rejected)
  * Bidding Approval (all/pending/approved/rejected/banned)
  * Role (all/user/admin)
  * Account Status (active/banned)
  * Join Date Range
  * Has Rating (yes/no)
- Sortable columns
- Pagination (25/50/100 per page)
- Bulk actions: 
  * Approve verification
  * Approve bidding
  * Ban users
  * Export to CSV
- Quick actions per row:
  * View Details
  * Edit
  * Ban/Unban
  * Send Notification

Table Columns:
┌──────────┬────────────┬──────────┬────────────┬──────────┬─────────┬─────────┐
│ Avatar   │ Name       │ Email    │ ID Verify  │ Bidding  │ Posts   │ Actions │
│          │            │          │ Status     │ Approval │ Created │         │
├──────────┼────────────┼──────────┼────────────┼──────────┼─────────┼─────────┤
│ [img]    │ John Doe   │ john@... │ ✅Approved │ ⏳Pending│ 5       │ [•••]   │
│ [img]    │ Jane Smith │ jane@... │ ⏳Pending  │ ❌None   │ 2       │ [•••]   │
│ [img]    │ Bob Wilson │ bob@...  │ ✅Approved │ ✅Approved│ 12      │ [•••]   │
└──────────┴────────────┴──────────┴────────────┴──────────┴─────────┴─────────┘

Implementation:
<UserTable
  users={users}
  onSearch={handleSearch}
  onFilter={handleFilter}
  onSort={handleSort}
  onBulkAction={handleBulkAction}
  onViewDetails={handleViewDetails}
  onEdit={handleEdit}
  onBan={handleBan}
/>
```

#### User Details Modal/Page
```tsx
Sections:
1. Profile Information
   - Avatar, Name, Email, Phone
   - Join Date, Last Login
   - Location
   - Edit button

2. Verification Status
   - ID Verification: 
     * Status badge
     * View submitted documents
     * Approve/Reject buttons
     * Rejection reason field
   - Bidding Approval:
     * Status badge
     * Application details
     * Approve/Reject/Ban buttons
     * Notes field

3. Activity Summary
   - Posts Created: 12 (8 SELL, 4 BID)
   - Total Bids Placed: 45
   - Purchases Made: 8
   - Items Sold: 5
   - Account Balance: ₱12,345

4. Ratings & Reviews
   - Seller Rating: 4.5 ⭐ (23 ratings)
   - View all ratings received
   - Flagged reviews

5. Posts
   - List of all posts created
   - Status, Views, Favorites
   - Quick edit/delete

6. Bidding History
   - All bids placed
   - Won/Lost status
   - Total spent

7. Reports
   - Reports filed by user: 3
   - Reports against user: 1
   - View details

8. Chats & Messages
   - Active chats: 5
   - Total messages sent: 234
   - Flagged conversations

9. Admin Actions
   - Ban User (with reason)
   - Reset Password
   - Send Notification
   - Delete Account
   - View Audit Log

<UserDetailsModal user={selectedUser}>
  <Tabs>
    <Tab label="Profile" />
    <Tab label="Verification" />
    <Tab label="Activity" />
    <Tab label="Ratings" />
    <Tab label="Posts" />
    <Tab label="Bids" />
    <Tab label="Reports" />
    <Tab label="Admin Actions" />
  </Tabs>
</UserDetailsModal>
```

---

### 4. **ID Verification Management** ✅

#### Verification Requests Page
```tsx
Features:
- Queue of pending verifications
- Filter by status, submission date
- Sort by oldest first (priority)
- Quick approve/reject

Layout:
┌────────────────────────────────────────────────────────┐
│  Pending Verifications (15)                             │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ User: John Doe (john@example.com)                │  │
│ │ Submitted: 2 days ago                            │  │
│ │                                                   │  │
│ │ ID Type: Driver's License                        │  │
│ │ ID Number: A123-4567-8901                       │  │
│ │                                                   │  │
│ │ [ID Front Image]  [ID Back Image]  [Selfie]     │  │
│ │  Click to enlarge                                │  │
│ │                                                   │  │
│ │ Notes: _____________________________________     │  │
│ │                                                   │  │
│ │ [✅ Approve] [❌ Reject] [🔄 Request Resubmit]  │  │
│ └──────────────────────────────────────────────────┘  │
│                                                         │
│ [Next Request]                                          │
└────────────────────────────────────────────────────────┘

Image Viewer:
- Zoom in/out
- Rotate
- Side-by-side comparison
- Face verification check (optional AI)

Actions:
- Approve: Updates verificationStatus to "approved"
- Reject: Updates to "rejected", notify user with reason
- Request Resubmit: Send notification with specific requirements
```

---

### 5. **Bidding Approval Management** 🔨 (NEW)

#### Bidding Applications Page
```tsx
Features:
- Queue of pending bidding approvals
- View user qualifications
- Check user history
- Approve/Reject with notes

Layout:
┌────────────────────────────────────────────────────────┐
│  Pending Bidding Approvals (8)                         │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ User: Jane Smith                                 │  │
│ │ Applied: 1 day ago                               │  │
│ │                                                   │  │
│ │ User Qualifications:                             │  │
│ │ ✅ ID Verified                                   │  │
│ │ ✅ Account Age: 15 days                         │  │
│ │ ✅ Posts Created: 3                             │  │
│ │ ✅ Messages Sent: 12                            │  │
│ │ ✅ Rating: 4.2 ⭐ (5 reviews)                   │  │
│ │ ⚠️  Failed Bids: 0                              │  │
│ │ ⚠️  Reports Against: 0                          │  │
│ │                                                   │  │
│ │ Application Reason:                              │  │
│ │ "I want to participate in livestock auctions..." │  │
│ │                                                   │  │
│ │ Admin Notes: _________________________________   │  │
│ │                                                   │  │
│ │ [✅ Approve] [❌ Reject] [🚫 Ban from Bidding]  │  │
│ └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Approval Criteria Check:
- Auto-check eligibility using BiddingCriteriaChecker logic
- Show green/yellow/red indicators
- Recommended action based on criteria
```

---

### 6. **Post & Content Management** 📝

#### Posts List Page
```tsx
Features:
- View all posts (SELL & BID)
- Filters:
  * Type (ALL/SELL/BID)
  * Status (ACTIVE/SOLD/ENDED)
  * Category (Livestock types)
  * Date Range
  * Price Range
  * Flagged/Reported
- Search by title, description, ID
- Sort by date, price, views, favorites
- Grid or List view toggle

Grid View:
┌──────────┬──────────┬──────────┬──────────┐
│ [Image]  │ [Image]  │ [Image]  │ [Image]  │
│ Title    │ Title    │ Title    │ Title    │
│ ₱1,000   │ ₱2,500   │ ₱5,000   │ ₱750     │
│ SELL     │ BID      │ BID      │ SELL     │
│ Active   │ 2d left  │ Ended    │ Sold     │
│ [•••]    │ [•••]    │ [•••]    │ [•••]    │
└──────────┴──────────┴──────────┴──────────┘

Actions:
- View Details
- Edit Post
- Delete Post
- Feature Post
- Mark as Sold/Ended
```

#### Post Details Modal
```tsx
<PostDetailsModal post={selectedPost}>
  <Tabs>
    <Tab label="Details">
      - All post information
      - Images carousel
      - Seller information
      - Location
      - Description
      - Edit button
    </Tab>
    
    <Tab label="Bidding" if={post.type === 'BID'}>
      - Current highest bid
      - Total bids: 45
      - Total bidders: 12
      - Bid history table:
        ┌────────────┬────────┬──────────┬───────────┐
        │ Codename   │ Amount │ Time     │ Status    │
        ├────────────┼────────┼──────────┼───────────┤
        │ RedSwift123│ ₱5,200 │ 2 min ago│ Current   │
        │ BlueQuick45│ ₱5,100 │ 5 min ago│ Outbid    │
        │ GreenFast78│ ₱5,000 │ 8 min ago│ Outbid    │
        └────────────┴────────┴──────────┴───────────┘
      - Real user mapping (admin only):
        Codename → Real User
      - Countdown timer
      - Extend auction option
    </Tab>
    
    <Tab label="Analytics">
      - Views: 234
      - Favorites: 12
      - Messages: 8
      - Engagement chart
    </Tab>
    
    <Tab label="Reports" if={hasReports}>
      - Reports filed against this post
      - Reason, reporter, status
    </Tab>
  </Tabs>
  
  <AdminActions>
    <Button onClick={handleFeature}>Feature Post</Button>
    <Button onClick={handleEdit}>Edit</Button>
    <Button onClick={handleDelete} color="error">Delete</Button>
  </AdminActions>
</PostDetailsModal>
```

---

### 7. **Live Bidding Monitoring** 🔨

#### Active Auctions Dashboard
```tsx
Features:
- Real-time monitoring of all active auctions
- Auto-refresh every 5 seconds
- Highlight auctions ending soon
- Detect suspicious bidding patterns

Layout:
┌────────────────────────────────────────────────────────┐
│  🔴 LIVE Active Auctions (89)                          │
│  Filters: [Ending Soon] [High Value] [Suspicious]     │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ 🏆 Premium Bull - Brahman                        │  │
│ │ Current Bid: ₱125,000 (23 bids, 8 bidders)      │  │
│ │ Time Left: ⏰ 00:45:23 🔴                        │  │
│ │ Last Bid: RedSwift123 - 2 min ago               │  │
│ │ [View Details] [Extend Time] [End Now]          │  │
│ └──────────────────────────────────────────────────┘  │
│                                                         │
│ ┌──────────────────────────────────────────────────┐  │
│ │ ⚠️ Dairy Cow - Holstein                         │  │
│ │ Current Bid: ₱45,000 (67 bids, 3 bidders)       │  │
│ │ Time Left: ⏰ 2 days, 03:15:42                   │  │
│ │ ⚠️ Suspicious: Shill bidding detected           │  │
│ │ [Investigate] [Cancel Auction]                   │  │
│ └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Real-time Features:
- WebSocket or Firebase listeners for live updates
- Push notifications for critical events
- Bid velocity tracking
- Fraud detection algorithms
```

#### Bidding Analytics
```tsx
<BiddingAnalytics>
  <MetricCard title="Total Bids Today" value={1,234} />
  <MetricCard title="Avg Bid Amount" value="₱15,234" />
  <MetricCard title="Active Bidders" value={456} />
  <MetricCard title="Completion Rate" value="87%" />
  
  <Chart type="line" title="Bidding Activity (24h)" />
  <Chart type="bar" title="Top Bidders This Month" />
  <Chart type="pie" title="Bids by Category" />
</BiddingAnalytics>
```

---

### 8. **Reports & Safety** 🛡️

#### Reports Queue
```tsx
Features:
- Pending, resolved, dismissed filters
- Sort by priority, date
- Report types: User, Post, Message
- Severity levels: Low, Medium, High, Critical

Layout:
┌────────────────────────────────────────────────────────┐
│  Reports Queue (5 Pending)                              │
│  [All] [Pending] [In Review] [Resolved] [Dismissed]   │
├────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────┐  │
│ │ 🚨 HIGH Priority                                 │  │
│ │ Report #1234 - User Report                       │  │
│ │ Reporter: John Doe → Reported: Jane Smith       │  │
│ │ Reason: Harassment                               │  │
│ │ Details: "This user sent threatening messages..." │  │
│ │ Evidence: [Screenshot1.jpg] [Screenshot2.jpg]   │  │
│ │ Submitted: 3 hours ago                           │  │
│ │                                                   │  │
│ │ Similar Reports: 2 other users reported Jane    │  │
│ │                                                   │  │
│ │ Quick Actions:                                   │  │
│ │ [View User Profile] [View Chat History]         │  │
│ │ [Warn User] [Temporary Ban] [Permanent Ban]     │  │
│ │ [Dismiss Report] [Mark as Resolved]             │  │
│ └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Report Investigation Tools:
- View reported content in context
- Check user history
- See previous reports
- Recommended actions based on severity
- Template responses
```

---

### 9. **Support Tickets** 💬

#### Tickets Dashboard
```tsx
Features:
- Open, In Progress, Resolved, Closed tabs
- Priority levels (Low, Medium, High, Urgent)
- Category filters (Account, Bidding, Payment, Technical, Other)
- Assignment system
- SLA tracking

Ticket List:
┌──────┬───────────┬──────────┬─────────┬──────────┬────────┐
│ ID   │ User      │ Subject  │ Category│ Priority │ Status │
├──────┼───────────┼──────────┼─────────┼──────────┼────────┤
│ #4567│ John Doe  │ Can't bid│ Bidding │ 🔴 High  │ Open   │
│ #4566│ Jane S.   │ Payment..│ Payment │ 🟡 Med   │ In Prog│
│ #4565│ Bob W.    │ Account..│ Account │ 🟢 Low   │ Open   │
└──────┴───────────┴──────────┴─────────┴──────────┴────────┘

Ticket Details View:
┌────────────────────────────────────────────────────────┐
│  Ticket #4567 - Can't bid on items                     │
│  Status: Open | Priority: High | Category: Bidding    │
│  Created: 2 hours ago | SLA: 2h remaining             │
├────────────────────────────────────────────────────────┤
│  User: John Doe (john@example.com)                     │
│  [View Profile] [View Activity]                        │
├────────────────────────────────────────────────────────┤
│  Conversation:                                          │
│  ┌──────────────────────────────────────────────────┐ │
│  │ 👤 John (2h ago):                                │ │
│  │ "I'm trying to bid on livestock posts but I     │ │
│  │  keep getting an error message..."               │ │
│  │  [screenshot.jpg]                                │ │
│  └──────────────────────────────────────────────────┘ │
│                                                         │
│  ┌──────────────────────────────────────────────────┐ │
│  │ 👨‍💼 Admin Reply:                                  │ │
│  │ [Canned Response ▼] [Templates ▼]               │ │
│  │ ____________________________________________     │ │
│  │ ____________________________________________     │ │
│  │                                                   │ │
│  │ [📎 Attach File] [Send Reply]                   │ │
│  └──────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│  Actions:                                               │
│  Assign to: [Dropdown] | Priority: [Dropdown]         │
│  [Mark as Resolved] [Escalate] [Close Ticket]         │
└────────────────────────────────────────────────────────┘

Canned Responses Library:
- Bidding not approved
- ID verification required
- Account suspended
- Payment issue
- Technical troubleshooting steps
```

---

### 10. **Transactions & Financial** 💰

#### Transactions Page
```tsx
Features:
- All completed transactions
- Filter by date, amount, type (SELL/BID), status
- Export to CSV for accounting
- Revenue tracking

Table:
┌──────┬───────┬────────┬──────────┬────────┬────────┬────────┐
│ ID   │ Date  │ Type   │ Buyer    │ Seller │ Amount │ Status │
├──────┼───────┼────────┼──────────┼────────┼────────┼────────┤
│#T1234│Jan 15│ BID    │ John D.  │ Jane S.│₱50,000 │Complete│
│#T1233│Jan 15│ SELL   │ Bob W.   │ Alice M│₱12,500 │Complete│
│#T1232│Jan 14│ BID    │ Charlie B│ Dave K.│₱35,000 │Pending │
└──────┴───────┴────────┴──────────┴────────┴────────┴────────┘

Transaction Details:
- Post information
- Buyer and seller profiles
- Transaction timeline
- Payment method (if applicable)
- Platform fee calculation
- Refund option (if needed)

Financial Dashboard:
┌────────────────────────────────────────────────────────┐
│  💰 Financial Overview - January 2025                  │
├────────────────────────────────────────────────────────┤
│  Total Transaction Volume: ₱1,234,567                  │
│  Platform Fees Collected: ₱61,728 (5%)                │
│  Total Transactions: 456                                │
│  Average Transaction: ₱2,707                            │
├────────────────────────────────────────────────────────┤
│  📊 Charts:                                             │
│  - Daily Revenue Trend                                  │
│  - Revenue by Category                                  │
│  - Top Sellers/Buyers                                   │
│  - Payment Method Distribution                          │
└────────────────────────────────────────────────────────┘
```

---

### 11. **Analytics & Insights** 📊

#### Analytics Dashboard
```tsx
<AnalyticsDashboard>
  {/* Time Range Selector */}
  <DateRangePicker
    options={['Today', 'This Week', 'This Month', 'This Year', 'Custom']}
  />
  
  {/* KPI Overview */}
  <Grid container spacing={3}>
    <KPICard
      title="Daily Active Users"
      value={1,234}
      change="+12%"
      period="vs yesterday"
      chart={<SparklineChart data={last7Days} />}
    />
    <KPICard title="New Signups" value={52} change="+8%" />
    <KPICard title="Posts Created" value={23} change="-3%" />
    <KPICard title="Revenue" value="₱45,678" change="+15%" />
  </Grid>
  
  {/* Charts */}
  <Grid container spacing={3}>
    <Chart
      type="line"
      title="User Growth (Last 30 Days)"
      data={userGrowthData}
      yAxis="Users"
      xAxis="Date"
    />
    
    <Chart
      type="bar"
      title="Posts by Category"
      data={categoryData}
      yAxis="Count"
      xAxis="Category"
    />
    
    <Chart
      type="pie"
      title="Post Type Distribution"
      data={[
        { name: 'SELL', value: 234 },
        { name: 'BID', value: 222 }
      ]}
    />
    
    <Chart
      type="area"
      title="Transaction Volume"
      data={transactionData}
      yAxis="Amount (₱)"
      xAxis="Date"
    />
  </Grid>
  
  {/* Top Lists */}
  <Grid container spacing={3}>
    <TopList
      title="Top Sellers This Month"
      items={[
        { name: 'John Doe', value: '₱125,000', count: '12 sales' },
        { name: 'Jane Smith', value: '₱98,500', count: '8 sales' },
        ...
      ]}
    />
    
    <TopList
      title="Most Active Bidders"
      items={[
        { name: 'Bob Wilson', value: '145 bids', spent: '₱456,000' },
        ...
      ]}
    />
    
    <TopList
      title="Popular Categories"
      items={[
        { name: 'Cattle', value: '234 posts', percentage: '45%' },
        { name: 'Goat', value: '156 posts', percentage: '30%' },
        ...
      ]}
    />
  </Grid>
  
  {/* Custom Reports */}
  <CustomReportBuilder>
    <ReportFilters>
      - Date Range
      - User Segment
      - Post Type
      - Category
      - Location
    </ReportFilters>
    <ReportMetrics>
      - Select metrics to include
      - Choose visualization
      - Export format (CSV, PDF, Excel)
    </ReportMetrics>
    <Button onClick={generateReport}>Generate Report</Button>
  </CustomReportBuilder>
</AnalyticsDashboard>
```

---

### 12. **Settings & Configuration** ⚙️

#### Platform Settings Page
```tsx
<SettingsTabs>
  <Tab label="General">
    - App Name
    - Logo Upload
    - Contact Email
    - Support Phone
    - Business Hours
    - Timezone
  </Tab>
  
  <Tab label="Bidding Rules">
    - Min Auction Duration: [24] hours
    - Max Auction Duration: [7] days
    - Default Bid Increment: [₱10]
    - Auto-extend on last-minute bid: [Yes/No]
    - Auto-extend duration: [5] minutes
    - Bidding Eligibility:
      * Min Account Age: [7] days
      * Min Posts: [0]
      * Min Messages: [5]
      * ID Verification Required: [Yes/No]
      * Bidding Approval Required: [Yes/No]
  </Tab>
  
  <Tab label="Content Policies">
    - Prohibited Items (textarea)
    - Image Requirements:
      * Min Images: [1]
      * Max Images: [5]
      * Max File Size: [5] MB
      * Allowed Formats: JPG, PNG, WebP
    - Description Min Length: [50] chars
    - Auto-moderation: [Enabled/Disabled]
  </Tab>
  
  <Tab label="Categories">
    - Livestock Categories:
      * Cattle ✏️ 🗑️
      * Goat ✏️ 🗑️
      * Sheep ✏️ 🗑️
      * Carabao ✏️ 🗑️
      * Swine ✏️ 🗑️
      [+ Add Category]
  </Tab>
  
  <Tab label="Fees & Commission">
    - Platform Commission: [5]%
    - Payment Processing Fee: [2.5]%
    - Featured Post Fee: [₱500] per week
    - Premium Badge Fee: [₱1000] per month
  </Tab>
  
  <Tab label="Notifications">
    - Email Notifications: [Enabled/Disabled]
    - Push Notifications: [Enabled/Disabled]
    - SMS Notifications: [Enabled/Disabled]
    - Notification Templates:
      * New User Welcome
      * Verification Approved
      * Bidding Approved
      * Auction Ending Soon
      * Bid Won
      * Payment Received
      [Edit] [Preview]
  </Tab>
  
  <Tab label="Security">
    - Password Requirements:
      * Min Length: [8]
      * Require Uppercase: [Yes]
      * Require Numbers: [Yes]
      * Require Special Chars: [Yes]
    - Two-Factor Authentication: [Optional/Required]
    - Session Timeout: [30] minutes
    - Max Login Attempts: [5]
    - Lockout Duration: [15] minutes
  </Tab>
  
  <Tab label="Backup & Maintenance">
    - Automated Backups: [Daily at 2:00 AM]
    - Maintenance Mode: [Off]
    - Maintenance Message: (textarea)
    - Data Retention Period: [365] days
  </Tab>
</SettingsTabs>
```

---

### 13. **Admin Team Management** 👔

#### Admin Users Page
```tsx
Features:
- List of all admin users
- Role management
- Activity logs
- Add/remove admins

Table:
┌───────────┬──────────────┬────────────┬──────────┬────────────┐
│ Name      │ Email        │ Role       │ Last Login│ Actions   │
├───────────┼──────────────┼────────────┼──────────┼────────────┤
│ Admin One │ admin1@...   │ Super Admin│ 5 min ago│ [Edit] [•••]│
│ Mod Two   │ mod2@...     │ Moderator  │ 2 hrs ago│ [Edit] [🗑️] │
│ Support 3 │ support3@... │ Support    │ Yesterday│ [Edit] [🗑️] │
└───────────┴──────────────┴────────────┴──────────┴────────────┘

Add Admin Form:
- Email
- Role: Super Admin / Moderator / Support Agent
- Permissions Matrix:
  ┌──────────────┬──────┬──────┬────────┐
  │ Feature      │ View │ Edit │ Delete │
  ├──────────────┼──────┼──────┼────────┤
  │ Users        │ ✅   │ ✅   │ ✅     │
  │ Posts        │ ✅   │ ✅   │ ❌     │
  │ Bids         │ ✅   │ ✅   │ ❌     │
  │ Reports      │ ✅   │ ✅   │ ❌     │
  │ Support      │ ✅   │ ✅   │ ❌     │
  │ Analytics    │ ✅   │ ❌   │ ❌     │
  │ Settings     │ ✅   │ ❌   │ ❌     │
  └──────────────┴──────┴──────┴────────┘

Activity Logs:
┌────────────┬────────────┬────────────────────────────┐
│ Timestamp  │ Admin      │ Action                      │
├────────────┼────────────┼────────────────────────────┤
│ 2 min ago  │ Admin One  │ Approved user verification │
│ 5 min ago  │ Mod Two    │ Deleted post #1234         │
│ 10 min ago │ Support 3  │ Resolved ticket #4567      │
└────────────┴────────────┴────────────────────────────┘
```

---

## 🎨 UI/UX Design Guidelines

### Design System
```
Colors:
- Primary: #2196F3 (Blue - Trust, professionalism)
- Secondary: #FFD700 (Gold - Premium, livestock)
- Success: #4CAF50 (Green)
- Warning: #FF9800 (Orange)
- Error: #F44336 (Red)
- Info: #00BCD4 (Cyan)
- Background: #F5F5F5 (Light gray)
- Surface: #FFFFFF (White)
- Text Primary: #212121 (Dark gray)
- Text Secondary: #757575 (Gray)

Typography:
- Headings: Roboto Bold
- Body: Roboto Regular
- Monospace (for IDs): Roboto Mono

Spacing:
- Base unit: 8px
- Small: 8px
- Medium: 16px
- Large: 24px
- XLarge: 32px

Shadows:
- Card: 0 2px 4px rgba(0,0,0,0.1)
- Elevated: 0 4px 8px rgba(0,0,0,0.15)
- Modal: 0 8px 16px rgba(0,0,0,0.2)

Border Radius:
- Small: 4px
- Medium: 8px
- Large: 12px
- Round: 50%
```

### Responsive Design
```
Breakpoints:
- Mobile: < 600px
- Tablet: 600px - 960px
- Desktop: 960px - 1280px
- Large Desktop: > 1280px

Mobile Considerations:
- Collapsible sidebar
- Touch-friendly button sizes (min 44x44px)
- Simplified tables (card view)
- Bottom navigation for key actions
```

### Accessibility
```
Requirements:
- WCAG 2.1 AA compliance
- Keyboard navigation support
- Screen reader friendly
- Color contrast ratio: 4.5:1 minimum
- Focus indicators
- ARIA labels
- Alt text for images
```

---

## 🔐 Security Requirements

### Authentication
```typescript
// Firebase Auth with custom claims
const setAdminClaim = async (userId: string, role: string) => {
  await admin.auth().setCustomUserClaims(userId, {
    admin: true,
    role: role // 'super_admin', 'moderator', 'support_agent'
  });
};

// Protected routes
const ProtectedRoute = ({ children, requiredRole }) => {
  const { user } = useAuth();
  
  if (!user?.customClaims?.admin) {
    return <Navigate to="/login" />;
  }
  
  if (requiredRole && user.customClaims.role !== requiredRole) {
    return <Navigate to="/unauthorized" />;
  }
  
  return children;
};
```

### Data Access
```
Firestore Security Rules:
- Admin users identified by custom claim: request.auth.token.admin == true
- Read/write access to all collections for admins
- Audit logging for all admin actions
- IP whitelisting (optional)
- Rate limiting on API calls
```

### Best Practices
```
- HTTPS only
- Environment variables for sensitive data
- Regular security audits
- Password complexity requirements
- Session management
- CSRF protection
- XSS prevention
- SQL injection prevention (N/A for Firestore)
```

---

## 📱 Real-time Features

### Firebase Listeners
```typescript
// Real-time user count
useEffect(() => {
  const unsubscribe = firestore.collection('users')
    .onSnapshot(snapshot => {
      setUserCount(snapshot.size);
    });
  return () => unsubscribe();
}, []);

// Real-time bidding updates
useEffect(() => {
  const unsubscribe = firestore.collection('bids')
    .where('postId', '==', selectedPostId)
    .orderBy('timestamp', 'desc')
    .limit(50)
    .onSnapshot(snapshot => {
      const bids = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
      setBidHistory(bids);
    });
  return () => unsubscribe();
}, [selectedPostId]);

// Real-time notifications
useEffect(() => {
  const unsubscribe = firestore.collection('admin_notifications')
    .where('read', '==', false)
    .onSnapshot(snapshot => {
      setUnreadNotifications(snapshot.size);
    });
  return () => unsubscribe();
}, []);
```

---

## 🚀 Implementation Phases

### Phase 1: Core Setup (Week 1)
```
✅ Project scaffolding (React + TypeScript)
✅ Firebase integration
✅ Authentication system
✅ Basic layout (sidebar, header)
✅ Dashboard landing page
✅ User management (list, view, search)
```

### Phase 2: Verification & Approvals (Week 2)
```
✅ ID Verification page
✅ Bidding Approval page
✅ Image viewer component
✅ Approval/rejection workflows
✅ User notifications integration
```

### Phase 3: Content Management (Week 3)
```
✅ Posts management
✅ Post details modal
✅ Live bidding monitoring
✅ Bid history viewer
✅ Content moderation tools
```

### Phase 4: Safety & Support (Week 4)
```
✅ Reports queue
✅ Report investigation tools
✅ Support tickets system
✅ Canned responses
✅ User blocking/banning
```

### Phase 5: Analytics & Financials (Week 5)
```
✅ Analytics dashboard
✅ Charts and graphs
✅ Transaction history
✅ Financial reports
✅ Export functionality
```

### Phase 6: Settings & Admin (Week 6)
```
✅ Platform settings
✅ Admin user management
✅ Permissions system
✅ Activity logs
✅ Notification templates
```

### Phase 7: Polish & Optimization (Week 7-8)
```
✅ Performance optimization
✅ Mobile responsiveness
✅ Accessibility improvements
✅ Error handling
✅ Loading states
✅ Documentation
✅ Testing
```

---

## 📝 Code Structure

```
admin-dashboard/
├── public/
│   ├── index.html
│   └── favicon.ico
├── src/
│   ├── components/
│   │   ├── common/
│   │   │   ├── Button.tsx
│   │   │   ├── Card.tsx
│   │   │   ├── Table.tsx
│   │   │   ├── Modal.tsx
│   │   │   ├── Chart.tsx
│   │   │   └── ...
│   │   ├── layout/
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   ├── Footer.tsx
│   │   │   └── Layout.tsx
│   │   ├── dashboard/
│   │   │   ├── StatCard.tsx
│   │   │   ├── ActivityFeed.tsx
│   │   │   ├── AlertsList.tsx
│   │   │   └── QuickActions.tsx
│   │   ├── users/
│   │   │   ├── UserTable.tsx
│   │   │   ├── UserDetailsModal.tsx
│   │   │   ├── UserFilters.tsx
│   │   │   └── BulkActions.tsx
│   │   ├── verification/
│   │   │   ├── VerificationQueue.tsx
│   │   │   ├── VerificationCard.tsx
│   │   │   ├── ImageViewer.tsx
│   │   │   └── ApprovalActions.tsx
│   │   ├── bidding/
│   │   │   ├── BiddingApprovalQueue.tsx
│   │   │   ├── BiddingApprovalCard.tsx
│   │   │   └── QualificationChecker.tsx
│   │   ├── posts/
│   │   │   ├── PostsGrid.tsx
│   │   │   ├── PostsList.tsx
│   │   │   ├── PostDetailsModal.tsx
│   │   │   └── PostFilters.tsx
│   │   ├── auctions/
│   │   │   ├── LiveAuctionsMonitor.tsx
│   │   │   ├── AuctionCard.tsx
│   │   │   ├── BidHistory.tsx
│   │   │   └── CodenameMapper.tsx
│   │   ├── reports/
│   │   │   ├── ReportsQueue.tsx
│   │   │   ├── ReportCard.tsx
│   │   │   └── InvestigationTools.tsx
│   │   ├── support/
│   │   │   ├── TicketsList.tsx
│   │   │   ├── TicketDetails.tsx
│   │   │   ├── CannedResponses.tsx
│   │   │   └── ConversationView.tsx
│   │   ├── analytics/
│   │   │   ├── KPICard.tsx
│   │   │   ├── LineChart.tsx
│   │   │   ├── BarChart.tsx
│   │   │   ├── PieChart.tsx
│   │   │   └── CustomReport.tsx
│   │   └── settings/
│   │       ├── GeneralSettings.tsx
│   │       ├── BiddingRules.tsx
│   │       ├── ContentPolicies.tsx
│   │       └── AdminUsers.tsx
│   ├── pages/
│   │   ├── Login.tsx
│   │   ├── Dashboard.tsx
│   │   ├── Users.tsx
│   │   ├── Verification.tsx
│   │   ├── BiddingApproval.tsx
│   │   ├── Posts.tsx
│   │   ├── LiveAuctions.tsx
│   │   ├── Reports.tsx
│   │   ├── Support.tsx
│   │   ├── Transactions.tsx
│   │   ├── Analytics.tsx
│   │   ├── Settings.tsx
│   │   └── AdminManagement.tsx
│   ├── services/
│   │   ├── firebase.ts
│   │   ├── auth.service.ts
│   │   ├── users.service.ts
│   │   ├── posts.service.ts
│   │   ├── bids.service.ts
│   │   ├── reports.service.ts
│   │   ├── support.service.ts
│   │   ├── analytics.service.ts
│   │   └── notifications.service.ts
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useFirestore.ts
│   │   ├── useRealtime.ts
│   │   ├── usePagination.ts
│   │   └── useFilter.ts
│   ├── utils/
│   │   ├── formatters.ts
│   │   ├── validators.ts
│   │   ├── constants.ts
│   │   └── helpers.ts
│   ├── types/
│   │   ├── user.types.ts
│   │   ├── post.types.ts
│   │   ├── bid.types.ts
│   │   ├── report.types.ts
│   │   └── ...
│   ├── store/
│   │   ├── auth.slice.ts
│   │   ├── users.slice.ts
│   │   ├── posts.slice.ts
│   │   └── ...
│   ├── App.tsx
│   ├── index.tsx
│   └── routes.tsx
├── .env.example
├── .gitignore
├── package.json
├── tsconfig.json
└── README.md
```

---

## 🧪 Testing Requirements

```
Unit Tests:
- Component rendering
- Utility functions
- Form validation
- Data formatting

Integration Tests:
- Firebase operations
- Authentication flow
- CRUD operations
- Real-time listeners

E2E Tests (Cypress):
- Login flow
- User management
- Approval workflows
- Report handling
- Ticket responses

Coverage Target: 80%+
```

---

## 📚 Documentation Requirements

```
Technical Documentation:
- Architecture overview
- Component documentation
- API reference
- Firebase structure
- Security rules explanation

User Documentation:
- Admin user guide
- Feature walkthroughs
- Troubleshooting guide
- FAQ

Developer Documentation:
- Setup instructions
- Development workflow
- Contribution guidelines
- Deployment process
```

---

## 🚀 Deployment Checklist

```
✅ Environment variables configured
✅ Firebase project set up
✅ Firestore indexes created
✅ Security rules deployed
✅ Cloud Functions deployed
✅ Build optimized (minified, tree-shaken)
✅ Performance tested
✅ Security audit passed
✅ Cross-browser tested
✅ Mobile responsive verified
✅ Analytics integrated
✅ Error tracking (Sentry) set up
✅ Backups configured
✅ SSL certificate active
✅ Custom domain configured
✅ CDN set up (if needed)
```

---

## 💡 Nice-to-Have Features (Future)

```
1. Dark mode
2. Multi-language support
3. Advanced fraud detection AI
4. Automated content moderation (AI)
5. Bulk import/export tools
6. API for third-party integrations
7. Mobile admin app (React Native)
8. Advanced analytics (predictive)
9. A/B testing platform
10. Workflow automation
11. Custom role builder
12. Webhook integrations
13. Advanced reporting (custom SQL)
14. Data visualization dashboard builder
15. Admin chat/communication tool
```

---

## 🎯 Success Metrics

```
Performance:
- Page load time < 2 seconds
- Real-time updates < 500ms
- 99.9% uptime

Usability:
- Admin task completion time reduced by 50%
- User satisfaction score > 4.5/5
- Support ticket response time < 2 hours

Business:
- Platform transaction volume tracked
- Revenue insights accessible
- User growth monitored
- Fraud detection rate > 95%
```

---

## 📞 Support & Maintenance

```
Monitoring:
- Error tracking (Sentry, LogRocket)
- Performance monitoring (Lighthouse CI)
- Uptime monitoring (UptimeRobot)
- Analytics (Google Analytics, Mixpanel)

Maintenance Schedule:
- Daily: Automated backups
- Weekly: Security updates
- Monthly: Performance review
- Quarterly: Feature updates

Support Channels:
- Technical documentation
- Video tutorials
- Email support
- Slack/Discord community
```

---

## ✨ Final Notes

This admin dashboard should be:
- **Intuitive**: Easy to navigate and use
- **Powerful**: Comprehensive feature set
- **Fast**: Optimized performance
- **Secure**: Enterprise-grade security
- **Scalable**: Handle growth gracefully
- **Modern**: Contemporary design and tech
- **Accessible**: WCAG compliant
- **Reliable**: High uptime and stability

**Estimated Development Time**: 6-8 weeks (1-2 developers)
**Budget**: $15,000 - $30,000 (varies by region and team)
**Tech Stack Size**: ~50-80 npm packages
**Lines of Code**: ~15,000 - 25,000

---

## 🎓 Learning Resources

```
React:
- https://react.dev/
- https://react-typescript-cheatsheet.netlify.app/

Material-UI:
- https://mui.com/material-ui/

Firebase:
- https://firebase.google.com/docs
- https://firebase.google.com/docs/firestore
- https://firebase.google.com/docs/auth

State Management:
- https://redux-toolkit.js.org/
- https://zustand-demo.pmnd.rs/

Charts:
- https://recharts.org/
- https://www.chartjs.org/

Best Practices:
- https://github.com/goldbergyoni/nodebestpractices
- https://web.dev/
```

---

**Ready to build? Start with Phase 1 and iterate! Good luck! 🚀**

