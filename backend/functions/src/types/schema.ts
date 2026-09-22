// Mirrors backend/docs/SCHEMA.md — keep both in sync.

export type Volume = "low" | "medium" | "high";

export type Role = "owner" | "member";

export interface Streak {
  current: number;
  longest: number;
  lastLogDateKey: string;
}

export interface UserDoc {
  displayName: string;
  photoURL: string | null;
  authProviders: string[];
  currentGroupIds: string[];
  timezone: string;
  streak: Streak;
  fcmTokens: Record<string, string>;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
}

export interface GroupSettings {
  dailyResetHour: number;
  weeklyResetWeekday: number;
}

export interface GroupDoc {
  name: string;
  inviteCode: string;
  ownerUid: string;
  memberCount: number;
  settings: GroupSettings;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
}

export interface GroupMemberDoc {
  displayName: string;
  photoURL: string | null;
  joinedAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  role: Role;
}

export interface LogDoc {
  uid: string;
  displayName: string;
  timestamp: FirebaseFirestore.Timestamp;
  durationSeconds: number;
  volume: Volume;
  dateKeyLocal: string;
  weekKeyLocal: string;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
}

export interface InviteCodeDoc {
  groupId: string;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
}

export interface LeaderboardEntry {
  count: number;
  totalDurationSeconds: number;
  displayName: string;
}

export interface LeaderboardDoc {
  entries: Record<string, LeaderboardEntry>;
  updatedAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  finalized?: boolean;
  finalizedAt?: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
}

export interface CreateGroupRequest {
  name: string;
}
export interface CreateGroupResponse {
  groupId: string;
  inviteCode: string;
}

export interface JoinGroupRequest {
  code: string;
}
export interface JoinGroupResponse {
  groupId: string;
  name: string;
}

export interface LeaveGroupRequest {
  groupId: string;
}
export interface LeaveGroupResponse {
  success: true;
}

export type BadgeType = "camel_of_day" | "camel_of_week" | "most_regular" | "streak_7" | "streak_30";

export interface BadgeDoc {
  type: BadgeType;
  periodKey: string;
  awardedToUid: string;
  awardedAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  meta: Record<string, unknown>;
}
