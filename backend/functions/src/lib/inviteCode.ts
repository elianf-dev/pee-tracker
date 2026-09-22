import * as admin from "firebase-admin";
import { paths } from "./firestorePaths";

const CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L — avoids visual ambiguity
const CODE_LENGTH = 4;
const MAX_ATTEMPTS = 10;

function randomSegment(): string {
  let out = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    out += CHARSET[Math.floor(Math.random() * CHARSET.length)];
  }
  return out;
}

export async function generateUniqueInviteCode(): Promise<string> {
  const db = admin.firestore();
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    const code = `PEE-${randomSegment()}`;
    const snap = await db.doc(paths.inviteCode(code)).get();
    if (!snap.exists) {
      return code;
    }
  }
  throw new Error("Could not generate a unique invite code after multiple attempts");
}
