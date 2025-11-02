# EmailJS OTP Setup Guide

This guide will help you set up EmailJS to send OTP (One-Time Password) codes during user sign-up.

## Overview

The app now uses EmailJS to send 6-digit OTP codes via email for email verification during sign-up. This replaces the Firebase email link verification with a more user-friendly OTP system.

## Step 1: Sign up for EmailJS

1. Go to [https://www.emailjs.com/](https://www.emailjs.com/)
2. Create a free account (Free tier allows 200 emails/month)
3. Verify your email address

## Step 2: Connect Email Service

**Recommended:** Use **Outlook** or **SendGrid** instead of Gmail (fewer authentication issues)

1. In EmailJS Dashboard, go to **Email Services**
2. Click **"+ Add New Service"** or **"Create Service"**
3. Choose your email provider:
   - **Outlook/Office 365** (Recommended - Easy setup)
   - **SendGrid** (Best for production - 100 emails/day free)
   - **Gmail** (May require frequent re-authentication)
4. Follow the connection wizard to authorize EmailJS:
   - For **Outlook**: Sign in with Microsoft account, grant permissions
   - For **SendGrid**: Enter your SendGrid API key (get it from SendGrid dashboard)
   - For **Gmail**: Sign in with Google, grant all email permissions
5. **Note your Service ID** (e.g., `service_xyz123`) - This is shown after creating the service

## Step 3: Create Email Template

1. Go to **Email Templates** in EmailJS Dashboard
2. Click **Create New Template**
3. Use the following template structure:

**Template Name:** `OTP Verification`

**Subject:** `Your Agristock Verification Code`

**Content (HTML):**
```html
<div style="font-family: system-ui, sans-serif, Arial; font-size: 14px; color:#2c3e50; max-width: 500px; margin:auto; background:#f9fafb; padding:20px; border-radius:12px;">
  
  <div style="text-align:center; margin-bottom:20px;">
    <img src="https://i.postimg.cc/c1XrkVb1/Untitled-design.png" alt="Agristock" style="width:100px; margin-bottom:8px;">
    
    <h2 style="margin:0; font-size:20px;">Verify Your Agristock Account</h2>
    <p style="font-size:14px; color:#6c7a89;">Secure your account with the One-Time Password (OTP) below</p>
  </div>

  <div
    style="
      margin-top: 20px;
      padding: 20px;
      border:1px dashed #cbd5e1;
      background:white;
      border-radius:10px;
    "
  >
    <div style="text-align:center; font-size:38px; font-weight:bold; letter-spacing:8px; padding: 10px 0; background:#eef8ff; border-radius:8px; color:#FFC107;">
      {{otp}}
    </div>
    
    <p style="text-align:center; margin-top:10px; font-size:13px; color:#64748b;">
      Enter this code in the Agristock app to continue.
    </p>
  </div>

  <table role="presentation" style="width:100%; margin-top:25px;">
    <tr>
      <td style="vertical-align: top; width:45px;">
        <div
          style="
            padding: 8px 12px;
            background-color: #e8f3fe;
            border-radius: 8px;
            font-size: 24px;
          "
          role="img"
        >
          👤
        </div>
      </td>
      <td style="vertical-align: top;">
        <div style="font-size:15px; font-weight:500;">{{to_name}}</div>
        <div style="font-size:12px; color:#9aa1b1;">{{time}}</div>
        <p style="font-size:14px; margin-top:6px; color:#334155;">
          You're almost there! Use the OTP above to verify your account and start using Agristock.
        </p>
      </td>
    </tr>
  </table>

  <p style="font-size:12px; margin-top:20px; color:#64748b; line-height:1.5;">
    ⚠️ This OTP will expire in <strong>10 minutes</strong>. Do not share this code with anyone for your security.
  </p>

  <div style="text-align:center; margin-top:25px; font-size:12px; color:#94a3b8;">
    If you didn't request this, please ignore this email.
  </div>
</div>
```

**⚠️ IMPORTANT: Template Settings (Not in HTML!)**

In the EmailJS template editor, ABOVE the HTML content, you'll see form fields. Set:

- **"Send email to"** or **"To"** field: `{{to_email}}` ← **CRITICAL!**
- **Subject:** `Your Agristock Verification Code`
- **From name:** `Agristock`

The "To" field must use `{{to_email}}` variable, NOT a hardcoded email address!

**Template Variables Used in HTML Content:**
- `{{otp}}` - The 6-digit OTP code (e.g., "123456") - **REQUIRED**
- `{{to_name}}` - Recipient's name (e.g., "John Doe") - **REQUIRED** (also supports `{{name}}`)
- `{{time}}` - Timestamp when email was sent (e.g., "Nov 01, 2025 at 15:23") - **REQUIRED**

**Template Setting (NOT in HTML!):**
- **"Send email to" / "To" field:** `{{to_email}}` - **CRITICAL!** This determines recipient email address

**Note:** 
- The app sends both `to_name` and `name` variables, so either `{{to_name}}` or `{{name}}` will work in HTML
- The "To" field in template settings MUST use `{{to_email}}` - this is separate from HTML content

**⚠️ CRITICAL: EmailJS Template "To" Field Configuration**

**This is the #1 cause of emails going to the wrong address!**

### Step-by-Step Fix:

1. **Go to EmailJS Dashboard:**
   - Visit: https://dashboard.emailjs.com/admin/integration
   - Navigate to **"Email Templates"** (left sidebar)

2. **Open Your Template:**
   - Click on your template (`template_hln4khb`)
   - You'll see the template editor

3. **Find "To" Field (MOST IMPORTANT):**
   - Look at the **TOP** of the template editor
   - Find **"Send email to"** or **"To"** field
   - ⚠️ This is **NOT** in the HTML content below - it's a separate field at the top!

4. **Check Current Value:**
   - If you see: `your-gmail@gmail.com` or your connected account email ❌
   - This is the problem! Delete it immediately.

5. **Set Correct Value:**
   - **Type exactly:** `{{to_email}}`
   - No spaces: `{{to_email}}` ✅ NOT `{{ to_email }}` ❌
   - Lowercase: `to_email` ✅ NOT `To_Email` ❌
   - Double braces: `{{to_email}}` ✅ NOT `{to_email}` ❌

6. **Save Template:**
   - Click **"Save"** button
   - Verify the "To" field still shows `{{to_email}}` after saving

7. **Test:**
   - Click "Test" button in template editor
   - Enter `to_email`: `test@example.com`
   - Send test email
   - Email should go to `test@example.com`, NOT your connected account

### Visual Location:

The "To" field is at the **TOP** of the template editor, usually in a form like this:

```
┌─────────────────────────────────────────────┐
│ Template: OTP Verification                 │
├─────────────────────────────────────────────┤
│ Send email to: [{{to_email}}]  ← HERE! ✅  │
│ Subject: Your Agristock Verification Code   │
│ From name: Agristock                        │
│                                             │
│ [HTML Content Below...]                     │
└─────────────────────────────────────────────┘
```

2. **Template Variable Syntax:**
   - EmailJS uses double curly braces: `{{variable_name}}`
   - Make sure there are NO spaces: `{{name}}` ✅ NOT `{{ name }}` ❌
   - Variable names are case-sensitive: `{{name}}` ≠ `{{Name}}`

3. **Common Issues:**

   **Issue 1: Emails go to connected account instead of user**
   - The "To" field is hardcoded to your email
   - **Fix:** Change from `your-email@gmail.com` to `{{to_email}}`

   **Issue 2: Seeing `{{name}}` literally in email (not replaced)**
   - Variable name mismatch or syntax error
   - **Fix:** 
     1. Make sure you're using the exact variable name sent by the app: `{{name}}` or `{{to_name}}`
     2. Check for spaces: Use `{{name}}` NOT `{{ name }}`
     3. Check case sensitivity: Use lowercase `{{name}}` NOT `{{Name}}`
     4. Verify in EmailJS template editor that variables are showing correctly
     5. Try using `{{to_name}}` instead of `{{name}}` (the app sends both)

4. **Template "To" Field Should Look Like:**
   ```
   {{to_email}}
   ```
   NOT:
   ```
   your-email@gmail.com
   ```

5. **Template Content Variables Should Be:**
   ```
   {{otp}}      ✅ Correct
   {{name}}     ✅ Correct
   {{time}}     ✅ Correct
   {{to_email}} ✅ Correct (if used in content)
   
   {{ otp }}    ❌ Wrong (spaces)
   {{Name}}     ❌ Wrong (wrong case)
   {name}       ❌ Wrong (single braces)
   ```

**Note:** 
- This template matches your provided design exactly
- The template uses inline CSS and div-based layout (works with most email clients)
- The OTP code uses `{{otp}}` variable (not `{{otp_code}}`)
- Ensure the image URL (`https://i.postimg.cc/c1XrkVb1/Untitled-design.png`) is accessible

4. **Note your Template ID** (e.g., `template_abc123`)

## Step 4: Get Your Public Key

1. Go to **Account** > **General** in EmailJS Dashboard
2. Find **Public Key** (also called User ID)
3. **Copy the Public Key** (e.g., `abc123XYZ`)

## Step 5: Configure in Android App

1. Open `app/src/main/java/com/example/agristockcapstoneproject/utils/EmailJSService.kt`

2. Replace the placeholder values with your actual EmailJS credentials:

```kotlin
private const val EMAILJS_SERVICE_ID = "YOUR_SERVICE_ID"     // Replace with your Service ID
private const val EMAILJS_TEMPLATE_ID = "YOUR_TEMPLATE_ID"   // Replace with your Template ID
private const val EMAILJS_PUBLIC_KEY = "YOUR_PUBLIC_KEY"      // Replace with your Public Key
```

**Example:**
```kotlin
private const val EMAILJS_SERVICE_ID = "service_xyz123"
private const val EMAILJS_TEMPLATE_ID = "template_abc123"
private const val EMAILJS_PUBLIC_KEY = "abc123XYZ"
```

3. Save the file and rebuild the app

## Step 6: Test the Integration

1. Run the app on an Android device/emulator
2. Sign up with a new account
3. Check the email inbox (and spam folder) for the OTP code
4. Enter the 6-digit code in the verification screen
5. Verify that account creation proceeds successfully

## How It Works

1. **User signs up** → Account created in Firebase Auth
2. **OTP generated** → 6-digit random code created
3. **OTP stored** → Saved locally in SharedPreferences with 10-minute expiry
4. **OTP sent** → EmailJS sends email with OTP code
5. **User enters OTP** → Code verified against stored value
6. **Account verified** → User data saved to Firestore, navigates to MainActivity

## Features

✅ **6-digit OTP codes** - Secure random generation  
✅ **10-minute expiry** - Codes expire for security  
✅ **Auto-focus OTP fields** - Smooth UX with auto-advance  
✅ **Resend functionality** - 1-minute cooldown between resends  
✅ **Expiry timer** - Visual countdown showing remaining time  
✅ **Error handling** - Clear error messages for invalid/expired codes  

## Troubleshooting

### OTP not received (Email shows as sent but user doesn't receive it)
**This is the most common issue!** Follow these steps:

1. **Check EmailJS Dashboard Logs:**
   - Go to https://dashboard.emailjs.com/admin/logs
   - Check if emails show as "Sent" or "Failed"
   - Look for error messages in the logs

2. **Check Spam/Junk Folder:**
   - Most emails end up in spam initially
   - Mark as "Not Spam" if found
   - Check spam folder for emails from your EmailJS sender

3. **Verify Email Service Status:**
   - Go to EmailJS Dashboard → Email Services
   - Check if your service shows as "Connected" or "Active"
   - For Gmail: May need re-authorization if showing errors
   - For Outlook: Should show as connected

4. **Check EmailJS Quota:**
   - Free tier: 200 emails/month
   - Go to Dashboard → Usage to check quota
   - If exceeded, emails won't be sent (but API may return success)

5. **Verify Template Variables:**
   - Make sure template uses: `{{otp}}`, `{{name}}`, `{{time}}`
   - Check template in EmailJS dashboard for correct variable names
   - Test template manually in EmailJS dashboard

6. **Test Email Service Directly:**
   - In EmailJS Dashboard → Email Services
   - Click "Send Test Email" on your service
   - Check if test email is received
   - If test fails, the service needs re-configuration

7. **Check Email Address:**
   - Verify the email address is correct
   - Make sure there are no typos in the recipient email
   - Check Logcat logs for the exact email being sent

8. **Email Delivery Delay:**
   - Some providers delay emails by 5-10 minutes
   - Wait a few minutes before assuming failure
   - Check EmailJS logs to see actual delivery status

### "EmailJS not configured" error
- Verify all three credentials are set in `EmailJSService.kt`
- Make sure no placeholder values remain (YOUR_SERVICE_ID, etc.)
- Rebuild the app after making changes

### Invalid OTP error
- Ensure code is entered correctly (6 digits)
- Check if code has expired (10 minutes)
- Try resending a new code

### EmailJS quota exceeded
- Free tier: 200 emails/month
- Check usage in EmailJS dashboard
- Consider upgrading to paid plan if needed

### "403 - API calls are disabled for non-browser applications"
- ✅ **FIXED**: The code now includes browser-like headers automatically
- If you still see this error, check your EmailJS account settings

### "412 - Gmail_API: Request had insufficient authentication scopes"
This is a **Gmail service authentication issue**. Follow these steps:

#### Option 1: Re-create the Gmail Service (Recommended)

1. **Go to EmailJS Dashboard**: https://dashboard.emailjs.com/admin
2. **Click on "Email Services"** in the left sidebar
3. **Find your Gmail service** (service_mbpsw4i)
4. **Delete the existing service**:
   - Click the **trash/delete icon** or **three dots menu** next to your service
   - Confirm deletion
5. **Create a new Gmail service**:
   - Click **"+ Add New Service"** or **"Create Service"**
   - Select **"Gmail"** as the provider
   - Click **"Connect Account"** or **"Authorize"**
   - Sign in with your Gmail account
   - **Grant all requested permissions** (especially send email permissions)
   - **Note the new Service ID** (it will be different from `service_mbpsw4i`)
6. **Update your app code**:
   - Open `EmailJSService.kt`
   - Replace `EMAILJS_SERVICE_ID` with your new Service ID
   - Rebuild the app
7. **Test again** - the OTP should send successfully

#### Option 2: Use a Different Email Provider (Easier Alternative)

Gmail can be problematic. Consider using:

**Outlook/Office 365:**
1. In EmailJS Dashboard → Email Services
2. Click **"+ Add New Service"**
3. Select **"Outlook"** or **"Office 365"**
4. Authorize with your Microsoft account
5. Update `EMAILJS_SERVICE_ID` in `EmailJSService.kt`

**SendGrid (Recommended for Production):**
1. Sign up at https://sendgrid.com (free tier: 100 emails/day)
2. In EmailJS Dashboard → Email Services
3. Click **"+ Add New Service"**
4. Select **"SendGrid"** or **"SMTP"**
5. Enter your SendGrid API key
6. Update `EMAILJS_SERVICE_ID` in `EmailJSService.kt`

**Benefits of alternatives:**
- ✅ Simpler authentication
- ✅ More reliable for apps
- ✅ Higher sending limits
- ✅ Better for production use

## Security Notes

- OTPs are stored locally in SharedPreferences (encrypted on Android)
- OTPs expire after 10 minutes
- Each OTP can only be used once
- OTP is cleared after successful verification

## Support

For EmailJS support, visit: [https://www.emailjs.com/docs/](https://www.emailjs.com/docs/)

For app-related issues, check the Android logs using Logcat or contact the development team.





