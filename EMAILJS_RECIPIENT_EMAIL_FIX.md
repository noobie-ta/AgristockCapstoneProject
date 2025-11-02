# Fix: Email Sent to Connected Account Instead of User's Sign-Up Email

## Problem
Emails are being sent to your Gmail account (used to connect EmailJS) instead of the user's sign-up email address.

## Root Cause
The EmailJS template "To" field is hardcoded or not using the `{{to_email}}` variable correctly.

## ✅ Verification: Your Code is Correct!

From your Logcat, I can see the app is sending the correct data:
```json
{
  "to_email": "matabatchoy101@gmail.com",  ✅ Correct!
  "to_name": "reyj ball",                  ✅ Correct!
  "otp": "557792"                          ✅ Correct!
}
```

EmailJS API returns: `200 OK` ✅

**The problem is 100% in EmailJS template configuration, not your code.**

## Solution: Fix EmailJS Template "To" Field

### Step-by-Step Instructions

1. **Go to EmailJS Dashboard:**
   - Visit: https://dashboard.emailjs.com/admin
   - Log in to your account

2. **Navigate to Email Templates:**
   - Click **"Email Templates"** in the left sidebar
   - Find and click on your template (`template_hln4khb`)

3. **Find the "To" Field (CRITICAL):**
   - In the template editor, look for a field labeled:
     - **"Send email to"** OR
     - **"To"** OR
     - **"Recipient"** OR
     - **"To Email"**
   - This field is **ABOVE** or **BESIDE** the HTML content editor
   - It's usually in a form-like section at the top
   - It might be next to "Subject" and "From name" fields
   - **DO NOT** look in the HTML content area - this is separate!

4. **Check Current Value:**
   - If you see: `your-gmail@gmail.com` ❌ → This is WRONG
   - If you see: `{{to_email}}` ✅ → This should be correct, but verify it's saved

5. **Fix the "To" Field:**
   - **Delete** any hardcoded email address
   - **Type exactly:** `{{to_email}}` (with double curly braces, no spaces)
   - Make sure it's lowercase: `to_email` not `To_Email` or `TO_EMAIL`

6. **Save the Template:**
   - Click **"Save"** button
   - Wait for confirmation that template is saved

7. **Verify Settings:**
   - Double-check the "To" field still shows `{{to_email}}` after saving
   - Sometimes EmailJS resets fields if there's a validation error

## Visual Guide (What You Should See)

### In EmailJS Template Editor:

**Template Settings Section:**
```
┌─────────────────────────────────────┐
│ Template Settings                   │
├─────────────────────────────────────┤
│ Send email to: {{to_email}}  ✅     │
│ Subject: Your Agristock...          │
│ From name: Agristock                │
└─────────────────────────────────────┘
```

**NOT:**
```
┌─────────────────────────────────────┐
│ Template Settings                   │
├─────────────────────────────────────┤
│ Send email to: your@gmail.com  ❌   │
│ Subject: Your Agristock...          │
│ From name: Agristock                │
└─────────────────────────────────────┘
```

## Common Mistakes

1. **Hardcoded Email in "To" Field:**
   - ❌ `your-email@gmail.com`
   - ✅ `{{to_email}}`

2. **Variable in Wrong Place:**
   - Only the "To" field (in template settings) should have `{{to_email}}`
   - Don't put `{{to_email}}` in the email body/HTML content

3. **Spaces or Wrong Format:**
   - ❌ `{{ to_email }}` (has spaces)
   - ❌ `{to_email}` (single braces)
   - ❌ `{{TO_EMAIL}}` (wrong case)
   - ✅ `{{to_email}}` (correct)

4. **Template Not Saved:**
   - Make sure you click "Save" after making changes
   - Check that the change persisted after refresh

## Testing

After fixing:

1. **Test in EmailJS Dashboard:**
   - Go to your template
   - Click "Test" or "Preview"
   - Enter test values:
     - `to_email`: `test@example.com`
     - `to_name`: `Test User`
     - `otp`: `123456`
   - Send test email
   - Check if email goes to `test@example.com` (not your connected account)

2. **Test in App:**
   - Sign up with a test email (different from your connected account)
   - Check if OTP email goes to the test email
   - Check Logcat: Should show `"Sending OTP to recipient email: test@example.com"`

## Alternative: Check Gmail Service Settings

Sometimes Gmail service itself has a default recipient setting:

1. **Go to Email Services:**
   - Click **"Email Services"** in left sidebar
   - Click on your Gmail service (`service_ux45jvn`)

2. **Check for "Default Recipient" or "Always Send To":**
   - Some services have a setting that overrides the template "To" field
   - Look for any field that has your Gmail address hardcoded
   - Delete or clear that field
   - Save the service

## Still Not Working?

If emails still go to your connected account after fixing both template and service:

1. **Double-Check Template "To" Field:**
   - Go back to your template
   - Look VERY carefully at the "To" field
   - Make sure it says exactly: `{{to_email}}`
   - Try deleting it completely and retyping: `{{to_email}}`
   - Save again

2. **Check EmailJS Logs:**
   - Go to **Logs** in EmailJS Dashboard
   - Find the most recent email send
   - Click on it to see details
   - Check what "To" address is shown
   - If it shows your connected account, template "To" field is still wrong

3. **Test Template Directly:**
   - In template editor, click **"Test"** or **"Preview"**
   - Fill in test values:
     - `to_email`: `test123@example.com` (NOT your email!)
     - `to_name`: `Test User`
     - `otp`: `123456`
   - Click "Send Test"
   - Check which email receives it
   - If it goes to your connected account, the "To" field is definitely wrong

4. **Screenshot Verification:**
   - Take a screenshot of your template "To" field
   - It should show exactly: `{{to_email}}`
   - Make sure there are no invisible characters or spaces

5. **Re-create Template (Last Resort):**
   - Create a NEW template
   - Copy your HTML content
   - Set "To" field to `{{to_email}}` from the start
   - Save the template
   - Get the new Template ID
   - Update `EMAILJS_TEMPLATE_ID` in `EmailJSService.kt`

## Code Verification

The app code is correct. It sends:
```json
{
  "template_params": {
    "to_email": "user-signup-email@gmail.com",
    "to_name": "User Name",
    "otp": "123456",
    ...
  }
}
```

The issue is **100% in EmailJS template configuration**, not in the app code.





