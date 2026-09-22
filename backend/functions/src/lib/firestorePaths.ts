export const paths = {
  user: (uid: string) => `users/${uid}`,
  group: (groupId: string) => `groups/${groupId}`,
  groupMember: (groupId: string, uid: string) => `groups/${groupId}/members/${uid}`,
  groupLogs: (groupId: string) => `groups/${groupId}/logs`,
  inviteCode: (code: string) => `inviteCodes/${code}`,
  leaderboardDaily: (groupId: string, dateKey: string) =>
    `groups/${groupId}/leaderboardDaily/${dateKey}`,
  leaderboardWeekly: (groupId: string, weekKey: string) =>
    `groups/${groupId}/leaderboardWeekly/${weekKey}`,
  badge: (groupId: string, badgeId: string) => `groups/${groupId}/badges/${badgeId}`,
};
