import { randomInt } from "crypto";
import * as admin from "firebase-admin";
import { paths } from "./firestorePaths";

const CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L — avoids visual ambiguity
// 8 chars over a 31-char alphabet is ~8.5e11 codes. The old length of 4 (~923k) was small
// enough to walk one `get` at a time, which firestore.rules permits for any signed-in user —
// denying `list` does not prevent that. Keep in sync with the Android generator.
const CODE_LENGTH = 8;
const MAX_ATTEMPTS = 10;

function randomSegment(): string {
  let out = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    // randomInt, not Math.random: a predictable code is as weak as a short one.
    out += CHARSET[randomInt(CHARSET.length)];
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
