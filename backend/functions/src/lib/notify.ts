import * as admin from "firebase-admin";
import { paths } from "./firestorePaths";

export function groupTopic(groupId: string): string {
  return `group_${groupId}`;
}

export async function sendToTopic(
  groupId: string,
  notification: { title: string; body: string },
  data?: Record<string, string>
): Promise<void> {
  try {
    await admin.messaging().send({
      topic: groupTopic(groupId),
      notification,
      data,
    });
  } catch (err) {
    // best-effort: a topic send failing (e.g. no subscribers yet) should never fail the
    // Firestore write that triggered it.
    console.error("sendToTopic failed", { groupId, err });
  }
}

export async function sendToUser(
  uid: string,
  notification: { title: string; body: string },
  data?: Record<string, string>
): Promise<void> {
  const db = admin.firestore();
  const userRef = db.doc(paths.user(uid));
  const snap = await userRef.get();
  const fcmTokens = (snap.data()?.fcmTokens ?? {}) as Record<string, string>;
  const tokens = Object.values(fcmTokens);
  if (tokens.length === 0) return;

  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    notification,
    data,
  });

  const deviceIds = Object.keys(fcmTokens);
  const staleDeviceIds: string[] = [];
  response.responses.forEach((r, i) => {
    if (
      !r.success &&
      (r.error?.code === "messaging/registration-token-not-registered" ||
        r.error?.code === "messaging/invalid-registration-token")
    ) {
      staleDeviceIds.push(deviceIds[i]);
    }
  });

  if (staleDeviceIds.length > 0) {
    const update: Record<string, FirebaseFirestore.FieldValue> = {};
    for (const deviceId of staleDeviceIds) {
      update[`fcmTokens.${deviceId}`] = admin.firestore.FieldValue.delete();
    }
    await userRef.update(update);
  }
}
