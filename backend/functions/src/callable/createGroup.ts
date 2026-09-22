import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { paths } from "../lib/firestorePaths";
import { generateUniqueInviteCode } from "../lib/inviteCode";
import {
  CreateGroupRequest,
  CreateGroupResponse,
  GroupDoc,
  GroupMemberDoc,
  InviteCodeDoc,
  UserDoc,
} from "../types/schema";

const MAX_TXN_ATTEMPTS = 3;

export const createGroup = onCall<CreateGroupRequest>(async (request): Promise<CreateGroupResponse> => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be signed in to create a group");
  }
  const uid = request.auth.uid;
  const name = (request.data?.name ?? "").trim();
  if (!name || name.length > 40) {
    throw new HttpsError("invalid-argument", "name must be 1-40 characters");
  }

  const db = admin.firestore();
  const userSnap = await db.doc(paths.user(uid)).get();
  const userData = userSnap.data() as UserDoc | undefined;
  const displayName = userData?.displayName ?? "Anonymous";
  const photoURL = userData?.photoURL ?? null;

  const groupRef = db.collection("groups").doc();
  const groupId = groupRef.id;

  for (let attempt = 0; attempt < MAX_TXN_ATTEMPTS; attempt++) {
    const code = await generateUniqueInviteCode();
    try {
      await db.runTransaction(async (txn) => {
        const codeRef = db.doc(paths.inviteCode(code));
        const codeSnap = await txn.get(codeRef);
        if (codeSnap.exists) {
          throw new Error("CODE_COLLISION");
        }
        const now = admin.firestore.FieldValue.serverTimestamp();

        const group: GroupDoc = {
          name,
          inviteCode: code,
          ownerUid: uid,
          memberCount: 1,
          settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
          createdAt: now,
        };
        const member: GroupMemberDoc = { displayName, photoURL, joinedAt: now, role: "owner" };
        const inviteCode: InviteCodeDoc = { groupId, createdAt: now };

        txn.set(groupRef, group);
        txn.set(db.doc(paths.groupMember(groupId, uid)), member);
        txn.set(codeRef, inviteCode);
        txn.update(db.doc(paths.user(uid)), {
          currentGroupIds: admin.firestore.FieldValue.arrayUnion(groupId),
        });
      });
      return { groupId, inviteCode: code };
    } catch (err) {
      if (err instanceof Error && err.message === "CODE_COLLISION") {
        continue;
      }
      throw err;
    }
  }

  throw new HttpsError("internal", "Could not create group, please try again");
});
