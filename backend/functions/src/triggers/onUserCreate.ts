import * as admin from "firebase-admin";
import { auth } from "firebase-functions/v1";
import { UserDoc } from "../types/schema";
import { paths } from "../lib/firestorePaths";

// Auth triggers (user lifecycle) are still v1-only in firebase-functions as of this writing;
// Firestore/HTTPS/scheduled functions elsewhere in this codebase use v2 (see index.ts).
export const onUserCreate = auth.user().onCreate(async (user) => {
  const db = admin.firestore();

  const authProviders = user.providerData.map((p) => {
    switch (p.providerId) {
      case "apple.com":
        return "apple";
      case "google.com":
        return "google";
      case "password":
        return "password";
      default:
        return p.providerId;
    }
  });

  const doc: UserDoc = {
    displayName: user.displayName ?? "Anonymous",
    photoURL: user.photoURL ?? null,
    authProviders,
    currentGroupIds: [],
    timezone: "UTC", // client updates this to the device's real IANA tz on first launch
    streak: { current: 0, longest: 0, lastLogDateKey: "" },
    fcmTokens: {},
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  await db.doc(paths.user(user.uid)).set(doc);
});
