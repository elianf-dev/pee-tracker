import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { paths } from "../lib/firestorePaths";
import { sendToTopic } from "../lib/notify";
import {
  GroupDoc,
  GroupMemberDoc,
  InviteCodeDoc,
  JoinGroupRequest,
  JoinGroupResponse,
  UserDoc,
} from "../types/schema";

export const joinGroup = onCall<JoinGroupRequest>(async (request): Promise<JoinGroupResponse> => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be signed in to join a group");
  }
  const uid = request.auth.uid;
  const code = (request.data?.code ?? "").trim().toUpperCase();
  if (!code) {
    throw new HttpsError("invalid-argument", "code is required");
  }

  const db = admin.firestore();
  const codeSnap = await db.doc(paths.inviteCode(code)).get();
  if (!codeSnap.exists) {
    throw new HttpsError("not-found", "Invalid invite code");
  }
  const { groupId } = codeSnap.data() as InviteCodeDoc;

  const groupRef = db.doc(paths.group(groupId));
  const memberRef = db.doc(paths.groupMember(groupId, uid));
  const userSnap = await db.doc(paths.user(uid)).get();
  const userData = userSnap.data() as UserDoc | undefined;

  const result = await db.runTransaction(async (txn) => {
    const [groupSnap, memberSnap] = await Promise.all([txn.get(groupRef), txn.get(memberRef)]);
    if (!groupSnap.exists) {
      throw new HttpsError("not-found", "Group no longer exists");
    }
    const groupData = groupSnap.data() as GroupDoc;

    if (memberSnap.exists) {
      // already a member — idempotent success rather than an error
      return { groupId, name: groupData.name, isNewMember: false };
    }

    const now = admin.firestore.FieldValue.serverTimestamp();
    const member: GroupMemberDoc = {
      displayName: userData?.displayName ?? "Anonymous",
      photoURL: userData?.photoURL ?? null,
      joinedAt: now,
      role: "member",
    };
    txn.set(memberRef, member);
    txn.update(groupRef, { memberCount: admin.firestore.FieldValue.increment(1) });
    txn.update(db.doc(paths.user(uid)), {
      currentGroupIds: admin.firestore.FieldValue.arrayUnion(groupId),
    });

    return { groupId, name: groupData.name, isNewMember: true };
  });

  if (result.isNewMember) {
    await sendToTopic(groupId, {
      title: "New member! 🎉",
      body: `${userData?.displayName ?? "Someone"} just joined ${result.name}.`,
    });
  }

  return { groupId: result.groupId, name: result.name };
});
