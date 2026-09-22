import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
} from "firebase/firestore";

const PROJECT_ID = "pee-tracker-rules-test";
const GROUP_ID = "group-1";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearFirestore();
});

const defaultUserDoc = {
  displayName: "Alice",
  photoURL: null,
  authProviders: ["password"],
  currentGroupIds: [],
  timezone: "UTC",
  streak: { current: 0, longest: 0, lastLogDateKey: "" },
  fcmTokens: {},
  createdAt: serverTimestamp(),
};

async function seedGroupWithOwner(ownerUid: string, memberCount = 1) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `groups/${GROUP_ID}`), {
      name: "Test Group",
      inviteCode: "PEE-TEST",
      ownerUid,
      memberCount,
      settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
    });
    await setDoc(doc(db, `groups/${GROUP_ID}/members/${ownerUid}`), {
      displayName: ownerUid,
      photoURL: null,
      role: "owner",
    });
  });
}

async function addMember(uid: string, role: "owner" | "member" = "member") {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `groups/${GROUP_ID}/members/${uid}`), {
      displayName: uid,
      photoURL: null,
      role,
    });
  });
}

describe("users/{uid}", () => {
  it("lets a user create their own doc with correct defaults", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(aliceDb, "users/alice"), defaultUserDoc));
  });

  it("denies creating another user's doc", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(aliceDb, "users/bob"), defaultUserDoc));
  });

  it("denies creating a doc with non-empty currentGroupIds", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, "users/alice"), { ...defaultUserDoc, currentGroupIds: [GROUP_ID] })
    );
  });

  it("denies creating a doc with a non-zero streak", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, "users/alice"), {
        ...defaultUserDoc,
        streak: { current: 1, longest: 1, lastLogDateKey: "2026-09-07" },
      })
    );
  });

  it("lets the owner read their own doc but not another user's", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users/alice"), defaultUserDoc);
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertSucceeds(getDoc(doc(aliceDb, "users/alice")));
    await assertFails(getDoc(doc(bobDb, "users/alice")));
  });

  it("lets the owner write their own streak and currentGroupIds directly (Spark mode)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users/alice"), defaultUserDoc);
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(aliceDb, "users/alice"), {
        streak: { current: 3, longest: 3, lastLogDateKey: "2026-09-07" },
        currentGroupIds: [GROUP_ID],
      })
    );
  });
});

describe("groups/{groupId} create", () => {
  it("lets a signed-in user create a group they own with memberCount 1", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}`), {
        name: "Friends",
        inviteCode: "PEE-ABCD",
        ownerUid: "alice",
        memberCount: 1,
        settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
      })
    );
  });

  it("denies creating a group owned by someone else", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}`), {
        name: "Friends",
        inviteCode: "PEE-ABCD",
        ownerUid: "bob",
        memberCount: 1,
        settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
      })
    );
  });

  it("denies creating a group with memberCount != 1", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}`), {
        name: "Friends",
        inviteCode: "PEE-ABCD",
        ownerUid: "alice",
        memberCount: 5,
        settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
      })
    );
  });
});

describe("groups/{groupId} get/list", () => {
  // Regression test: joinGroup's client-side read of memberCount/name (to build the join batch)
  // happens before the joiner has a members/{uid} doc, so this must be gettable by any signed-in
  // user who already knows the groupId (e.g. from an invite-code lookup), not just existing
  // members.
  it("lets a non-member get a group doc by known id", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertSucceeds(getDoc(doc(bobDb, `groups/${GROUP_ID}`)));
  });

  it("denies listing the groups collection", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDocs(collection(bobDb, "groups")));
  });
});

describe("groups/{groupId}/members/{memberId} create", () => {
  it("lets the group's owner create their own membership doc with role owner", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `groups/${GROUP_ID}`), {
        name: "Friends",
        inviteCode: "PEE-ABCD",
        ownerUid: "alice",
        memberCount: 1,
        settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/members/alice`), {
        displayName: "Alice",
        photoURL: null,
        role: "owner",
      })
    );
  });

  it("denies the owner claiming role member (role must match ownership)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `groups/${GROUP_ID}`), {
        name: "Friends",
        inviteCode: "PEE-ABCD",
        ownerUid: "alice",
        memberCount: 1,
        settings: { dailyResetHour: 0, weeklyResetWeekday: 0 },
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/members/alice`), {
        displayName: "Alice",
        photoURL: null,
        role: "member",
      })
    );
  });

  it("lets a non-owner join with role member", async () => {
    await seedGroupWithOwner("alice");
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(doc(bobDb, `groups/${GROUP_ID}/members/bob`), {
        displayName: "Bob",
        photoURL: null,
        role: "member",
      })
    );
  });

  it("denies creating a membership doc for someone else", async () => {
    await seedGroupWithOwner("alice");
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(bobDb, `groups/${GROUP_ID}/members/carol`), {
        displayName: "Carol",
        photoURL: null,
        role: "member",
      })
    );
  });

  it("lets a member leave by deleting their own membership doc, denies deleting another's", async () => {
    await seedGroupWithOwner("alice");
    await addMember("bob");
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(deleteDoc(doc(aliceDb, `groups/${GROUP_ID}/members/bob`)));
    await assertSucceeds(deleteDoc(doc(bobDb, `groups/${GROUP_ID}/members/bob`)));
  });
});

describe("groups/{groupId} memberCount update (join/leave batches)", () => {
  it("lets a joining user increment memberCount in the same batch as creating their membership doc", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const batch = writeBatch(bobDb);
    batch.update(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 2 });
    batch.set(doc(bobDb, `groups/${GROUP_ID}/members/bob`), {
      displayName: "Bob",
      photoURL: null,
      role: "member",
    });
    await assertSucceeds(batch.commit());
  });

  it("denies incrementing memberCount without also creating the membership doc", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 2 }));
  });

  it("denies incrementing memberCount by more than one", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const batch = writeBatch(bobDb);
    batch.update(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 3 });
    batch.set(doc(bobDb, `groups/${GROUP_ID}/members/bob`), {
      displayName: "Bob",
      photoURL: null,
      role: "member",
    });
    await assertFails(batch.commit());
  });

  it("lets an existing member decrement memberCount while deleting their own membership doc", async () => {
    await seedGroupWithOwner("alice", 2);
    await addMember("bob");
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const batch = writeBatch(bobDb);
    batch.update(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 1 });
    batch.delete(doc(bobDb, `groups/${GROUP_ID}/members/bob`));
    await assertSucceeds(batch.commit());
  });

  it("denies a non-member decrementing memberCount", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 0 }));
  });

  // Regression: the rule used to check only existsAfter() for +1 and only exists() for -1,
  // both of which stay true for somebody who is already a member and stays one. That let any
  // member replay a bare +1 or -1 as often as they liked and drift memberCount off the real
  // roster (including negative). Each direction now has to cross the membership edge.
  it("denies an existing member incrementing memberCount without actually joining", async () => {
    await seedGroupWithOwner("alice", 1);
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(aliceDb, `groups/${GROUP_ID}`), { memberCount: 2 }));
  });

  it("denies an existing member decrementing memberCount without actually leaving", async () => {
    await seedGroupWithOwner("alice", 2);
    await addMember("bob");
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 1 }));
  });

  it("denies changing any other field alongside memberCount", async () => {
    await seedGroupWithOwner("alice", 1);
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const batch = writeBatch(bobDb);
    batch.update(doc(bobDb, `groups/${GROUP_ID}`), { memberCount: 2, name: "Hacked" });
    batch.set(doc(bobDb, `groups/${GROUP_ID}/members/bob`), {
      displayName: "Bob",
      photoURL: null,
      role: "member",
    });
    await assertFails(batch.commit());
  });
});

describe(`groups/{groupId}/logs/{logId}`, () => {
  const logPath = (id: string) => `groups/${GROUP_ID}/logs/${id}`;

  it("lets a group member create their own log", async () => {
    await seedGroupWithOwner("alice");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(aliceDb, logPath("log1")), {
        uid: "alice",
        displayName: "Alice",
        durationSeconds: 30,
        volume: "medium",
        dateKeyLocal: "2026-09-07",
        weekKeyLocal: "2026-W36",
        createdAt: serverTimestamp(),
      })
    );
  });

  it("denies a non-member creating a log", async () => {
    await seedGroupWithOwner("alice");
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(bobDb, logPath("log1")), {
        uid: "bob",
        displayName: "Bob",
        durationSeconds: 30,
        volume: "medium",
        dateKeyLocal: "2026-09-07",
        weekKeyLocal: "2026-W36",
        createdAt: serverTimestamp(),
      })
    );
  });

  it("denies creating a log with someone else's uid", async () => {
    await seedGroupWithOwner("alice");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, logPath("log1")), {
        uid: "bob",
        displayName: "Alice",
        durationSeconds: 30,
        volume: "medium",
        dateKeyLocal: "2026-09-07",
        weekKeyLocal: "2026-W36",
        createdAt: serverTimestamp(),
      })
    );
  });
});

describe("groups/{groupId}/leaderboardDaily/{dateKey}", () => {
  const dailyPath = `groups/${GROUP_ID}/leaderboardDaily/2026-09-07`;

  it("lets a member create the doc with only their own entry", async () => {
    await seedGroupWithOwner("alice");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(aliceDb, dailyPath), {
        entries: { alice: { count: 1, totalDurationSeconds: 30, displayName: "Alice" } },
        updatedAt: serverTimestamp(),
      })
    );
  });

  it("denies creating the doc with someone else's entry included", async () => {
    await seedGroupWithOwner("alice");
    await addMember("bob");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, dailyPath), {
        entries: {
          alice: { count: 1, totalDurationSeconds: 30, displayName: "Alice" },
          bob: { count: 1, totalDurationSeconds: 30, displayName: "Bob" },
        },
        updatedAt: serverTimestamp(),
      })
    );
  });

  it("lets a member update only their own key in an existing doc", async () => {
    await seedGroupWithOwner("alice");
    await addMember("bob");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), dailyPath), {
        entries: {
          alice: { count: 1, totalDurationSeconds: 30, displayName: "Alice" },
          bob: { count: 2, totalDurationSeconds: 60, displayName: "Bob" },
        },
        updatedAt: serverTimestamp(),
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(aliceDb, dailyPath), {
        "entries.alice": { count: 2, totalDurationSeconds: 60, displayName: "Alice" },
        updatedAt: serverTimestamp(),
      })
    );
  });

  it("denies updating another member's key", async () => {
    await seedGroupWithOwner("alice");
    await addMember("bob");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), dailyPath), {
        entries: {
          alice: { count: 1, totalDurationSeconds: 30, displayName: "Alice" },
          bob: { count: 2, totalDurationSeconds: 60, displayName: "Bob" },
        },
        updatedAt: serverTimestamp(),
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(aliceDb, dailyPath), {
        "entries.bob": { count: 999, totalDurationSeconds: 999, displayName: "Bob" },
      })
    );
  });

  it("lets any member set finalized without touching entries", async () => {
    await seedGroupWithOwner("alice");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), dailyPath), {
        entries: { alice: { count: 1, totalDurationSeconds: 30, displayName: "Alice" } },
        updatedAt: serverTimestamp(),
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(aliceDb, dailyPath), { finalized: true, finalizedAt: serverTimestamp() })
    );
  });

  it("denies a non-member reading or writing", async () => {
    await seedGroupWithOwner("alice");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), dailyPath), {
        entries: { alice: { count: 1, totalDurationSeconds: 30, displayName: "Alice" } },
        updatedAt: serverTimestamp(),
      });
    });
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(bobDb, dailyPath)));
    await assertFails(
      setDoc(doc(bobDb, `groups/${GROUP_ID}/leaderboardDaily/2026-09-08`), {
        entries: { bob: { count: 1, totalDurationSeconds: 30, displayName: "Bob" } },
        updatedAt: serverTimestamp(),
      })
    );
  });
});

describe("groups/{groupId}/badges/{badgeId}", () => {
  it("lets a user self-award a streak badge", async () => {
    await seedGroupWithOwner("alice");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/badges/alice_streak_7`), {
        type: "streak_7",
        periodKey: "2026-09-07",
        awardedToUid: "alice",
        awardedAt: serverTimestamp(),
        meta: { streakCurrent: 7 },
      })
    );
  });

  it("denies awarding a streak badge to someone else", async () => {
    await seedGroupWithOwner("alice");
    await addMember("bob");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/badges/bob_streak_7`), {
        type: "streak_7",
        periodKey: "2026-09-07",
        awardedToUid: "bob",
        awardedAt: serverTimestamp(),
        meta: {},
      })
    );
  });

  it("lets any member award a period badge naming a real member as winner", async () => {
    await seedGroupWithOwner("alice");
    await addMember("bob");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/badges/2026-09-07_camel_of_day`), {
        type: "camel_of_day",
        periodKey: "2026-09-07",
        awardedToUid: "bob",
        awardedAt: serverTimestamp(),
        meta: { count: 1 },
      })
    );
  });

  it("denies a period badge naming a non-member as winner", async () => {
    await seedGroupWithOwner("alice");
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/badges/2026-09-07_camel_of_day`), {
        type: "camel_of_day",
        periodKey: "2026-09-07",
        awardedToUid: "not-a-member",
        awardedAt: serverTimestamp(),
        meta: {},
      })
    );
  });

  it("denies overwriting an already-awarded badge (idempotency)", async () => {
    await seedGroupWithOwner("alice");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `groups/${GROUP_ID}/badges/2026-09-07_camel_of_day`), {
        type: "camel_of_day",
        periodKey: "2026-09-07",
        awardedToUid: "alice",
        awardedAt: serverTimestamp(),
        meta: {},
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(aliceDb, `groups/${GROUP_ID}/badges/2026-09-07_camel_of_day`), {
        type: "camel_of_day",
        periodKey: "2026-09-07",
        awardedToUid: "alice",
        awardedAt: serverTimestamp(),
        meta: { count: 999 },
      })
    );
  });
});

describe("inviteCodes/{code}", () => {
  it("lets any signed-in user get a code by exact id", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "inviteCodes/PEE-TEST"), { groupId: GROUP_ID });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(aliceDb, "inviteCodes/PEE-TEST")));
  });

  it("lets a signed-in user create a new code", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(aliceDb, "inviteCodes/PEE-NEW1"), { groupId: GROUP_ID }));
  });

  it("denies overwriting a code that already exists (collision)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "inviteCodes/PEE-TEST"), { groupId: GROUP_ID });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(aliceDb, "inviteCodes/PEE-TEST"), { groupId: "some-other-group" }));
  });

  it("denies an unauthenticated read or write", async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anonDb, "inviteCodes/PEE-TEST")));
    await assertFails(setDoc(doc(anonDb, "inviteCodes/PEE-TEST"), { groupId: GROUP_ID }));
  });
});
