import * as admin from "firebase-admin";

admin.initializeApp();

export { onUserCreate } from "./triggers/onUserCreate";
export { createGroup } from "./callable/createGroup";
export { joinGroup } from "./callable/joinGroup";
export { leaveGroup } from "./callable/leaveGroup";
export { onLogCreated } from "./triggers/onLogCreated";
export { onLogDeleted } from "./triggers/onLogDeleted";
export { dailyReset } from "./scheduled/dailyReset";
export { weeklyReset } from "./scheduled/weeklyReset";
export { streakSweep } from "./scheduled/streakSweep";
