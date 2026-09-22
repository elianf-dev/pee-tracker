import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { paths } from "../lib/firestorePaths";
import { LeaveGroupRequest, LeaveGroupResponse } from "../types/schema";

export const leaveGroup = onCall<LeaveGroupRequest>(async (request): Promise<LeaveGroupResponse> => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be signed in to leave a group");
  }
  const uid = request.auth.uid;
  const groupId = request.data?.groupId;
  if (!groupId) {
    throw new HttpsError("invalid-argument", "groupId is required");
  }

  const db = admin.firestore();
  const memberRef = db.doc(paths.groupMember(groupId, uid));
  const groupRef = db.doc(paths.group(groupId));

  await db.runTransaction(async (txn) => {
    const memberSnap = await txn.get(memberRef);
    if (!memberSnap.exists) {
      return; // not a member — idempotent no-op
    }
    txn.delete(memberRef);
    txn.update(groupRef, { memberCount: admin.firestore.FieldValue.increment(-1) });
    txn.update(db.doc(paths.user(uid)), {
      currentGroupIds: admin.firestore.FieldValue.arrayRemove(groupId),
    });
  });

  return { success: true };
});
