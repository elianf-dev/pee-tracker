import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { applyLogDelta } from "../lib/leaderboard";
import { LogDoc } from "../types/schema";

export const onLogDeleted = onDocumentDeleted(
  "groups/{groupId}/logs/{logId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const log = snap.data() as LogDoc;
    await applyLogDelta(event.params.groupId, log, -1);
  }
);
