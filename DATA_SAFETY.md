# Play Console Data safety: draft answers

Draft for both listings. **Read Google's current Data safety guidance before submitting**: the
answer depends on how Google classifies data sent between a user's own devices over a local network.

## Guest Experience TV (`com.guestexperience.tv`)

The TV app receives content from the host's own phone over the local network. It sends content only
back to that paired phone, on request. Nothing goes to the developer or to any third party.

| Question | Draft answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | Yes (the only network traffic is the encrypted local link to the paired phone) |
| Do you provide a way for users to request that their data is deleted? | Yes: "Clear for next guest", Clear storage, or uninstall |

Notes for the reviewer:
- Wi-Fi password: stored on the device only, encrypted with an Android Keystore key.
- Host PIN: stored only as a salted PBKDF2 hash.
- No analytics, ads, crash reporting or account SDKs.

## Guest Experience Host (`com.guestexperience.host`)

The phone app sends the guest's first name, house details and the Wi-Fi password **to the host's own
TV**, end-to-end encrypted, over the local network. Nothing is sent to the developer.

Google's guidance says data is "collected" when it is transmitted off the device. It also has
exemptions, e.g. for data sent end-to-end encrypted, or processed only on behalf of the user.
Two defensible options:

**Option A, matching the original brief (if the local, end-to-end encrypted transfer between the
user's own devices is treated as not collected):**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Encrypted in transit | Yes |

**Option B, conservative (recommended if unsure; it is never wrong to over-disclose):**

| Data type | Collected | Shared | Optional? | Purpose | Ephemeral? |
|---|---|---|---|---|---|
| Personal info › Name (guest first name, host name) | Yes | No | Optional | App functionality | No |
| Personal info › Phone number (host contact) | Yes | No | Optional | App functionality | No |
| Personal info › Other info (Wi-Fi network name and password) | Yes | No | Required | App functionality | No |

Plus: encrypted in transit **Yes**; users can request deletion **Yes**; and in the description
state "sent only to your own TV on your local network; never to us".

Whichever option you pick, keep the privacy policy (`PRIVACY_POLICY.md`) consistent with it.
