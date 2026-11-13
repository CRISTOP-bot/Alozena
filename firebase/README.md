# Firebase setup (manual, owner account)

No Firebase project is provisioned yet. Use the user's own Google account at Firebase Console.

1. Create a project named Estadía, add Android app `com.cristopbot.estadia`.
2. Enable Google provider under Authentication; add SHA fingerprints for the signing cert.
3. Create Cloud Firestore in a production region.
4. Deploy root rules and indexes: `firebase deploy --only firestore:rules,firestore:indexes`.
5. CI currently builds the app in disconnected/demo mode because the Firebase project doesn't exist yet. Once you create it, add `google-services.json` locally in `app/` (already ignored by git), and update CI to inject that downloaded config from a protected GitHub secret/file before building Firebase-connected releases. Never commit an admin/service-account credential.
6. Never use a service-account key in the Android app. For Firestore admin seeding use Firebase Console or trusted local Firebase CLI with the user's own session; don't publish sample inventory without verifying rights and rates.

Once credentials/configuration exist, use the Firebase Console download of `google-services.json` in local `app/` only (ignored by git), or configure CI as above. The Google web sign-in flow still needs its OAuth client ID and Android certificate fingerprints; the repository method accepts a Google ID token but UI login is not implemented yet.
