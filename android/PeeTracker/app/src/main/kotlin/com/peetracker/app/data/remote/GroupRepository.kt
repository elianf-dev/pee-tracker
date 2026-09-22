package com.peetracker.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class CreateGroupResult(val groupId: String, val inviteCode: String)

data class JoinGroupResult(val groupId: String, val name: String)

class InvalidInviteCodeException(message: String) : Exception(message)

private const val INVITE_CODE_CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
// 8 chars over a 31-char alphabet is ~8.5e11 codes. The old length of 4 (~923k) was small
// enough to walk one `get` at a time, which firestore.rules permits for any signed-in user —
// denying `list` does not prevent that. Keep in sync with backend/functions inviteCode.ts.
private const val INVITE_CODE_LENGTH = 8
private const val MAX_CREATE_ATTEMPTS = 10

private val secureRandom = SecureRandom()

@Singleton
class GroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // Two sequential writes, not one batch: the members/{uid} create rule does a get() on
    // groups/{groupId} to check ownerUid, which only sees a doc that has already been committed
    // in a prior write — a batch commits all its writes together, so the group doc wouldn't be
    // visible to that in-batch rule check yet.
    suspend fun createGroup(name: String): CreateGroupResult {
        val user = auth.currentUser ?: error("Cannot create a group while signed out")
        val uid = user.uid

        var lastError: Exception? = null
        repeat(MAX_CREATE_ATTEMPTS) {
            val groupRef = firestore.collection("groups").document()
            val code = randomInviteCode()
            try {
                groupRef.set(
                    hashMapOf(
                        "name" to name,
                        "inviteCode" to code,
                        "ownerUid" to uid,
                        "memberCount" to 1L,
                        "settings" to hashMapOf("dailyResetHour" to 0L, "weeklyResetWeekday" to 0L),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val batch = firestore.batch()
                batch.set(
                    groupRef.collection("members").document(uid),
                    hashMapOf(
                        "displayName" to user.displayName.orEmpty(),
                        "photoURL" to user.photoUrl?.toString(),
                        "role" to "owner",
                        "joinedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    firestore.document("inviteCodes/$code"),
                    hashMapOf("groupId" to groupRef.id, "createdAt" to FieldValue.serverTimestamp())
                )
                batch.update(
                    firestore.document(FirestorePaths.userDoc(uid)),
                    "currentGroupIds",
                    FieldValue.arrayUnion(groupRef.id)
                )
                batch.commit().await()

                return CreateGroupResult(groupId = groupRef.id, inviteCode = code)
            } catch (e: Exception) {
                // The only realistic failure here is an inviteCodes/{code} collision (Firestore
                // treats a second write to an existing doc as an update, which the rules deny
                // for that collection) — retry with a fresh code AND a fresh groupId. The
                // groups/{groupId} doc from this attempt is left orphaned (nobody has its id,
                // and groups can't be deleted by clients per the rules); with a ~8.5e11-code space
                // this should essentially never happen.
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Failed to create group after $MAX_CREATE_ATTEMPTS attempts")
    }

    suspend fun joinGroup(code: String): JoinGroupResult {
        val user = auth.currentUser ?: error("Cannot join a group while signed out")
        val uid = user.uid

        val codeSnap = firestore.document("inviteCodes/$code").get().await()
        val groupId = if (codeSnap.exists()) codeSnap.getString("groupId") else null
        if (groupId == null) {
            throw InvalidInviteCodeException("That invite code doesn't match any group")
        }

        val groupRef = firestore.document(FirestorePaths.groupPath(groupId))
        val groupSnap = groupRef.get().await()
        if (!groupSnap.exists()) {
            throw InvalidInviteCodeException("That invite code doesn't match any group")
        }
        val name = groupSnap.getString("name").orEmpty()

        val batch = firestore.batch()
        // increment(), not a read-then-write of a client-side count: two friends opening the same
        // invite at once would both read the same value, both write value+1, and the second batch
        // would resolve to a delta of 0 against the server's actual value — failing the rules'
        // exactly-+1 check and rejecting that user's membership write along with it, with no
        // retry. The transform resolves server-side, so rules still see a genuine +1.
        batch.update(groupRef, "memberCount", FieldValue.increment(1))
        batch.set(
            groupRef.collection("members").document(uid),
            hashMapOf(
                "displayName" to user.displayName.orEmpty(),
                "photoURL" to user.photoUrl?.toString(),
                "role" to "member",
                "joinedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            firestore.document(FirestorePaths.userDoc(uid)),
            "currentGroupIds",
            FieldValue.arrayUnion(groupId)
        )
        batch.commit().await()

        return JoinGroupResult(groupId = groupId, name = name)
    }

    suspend fun leaveGroup(groupId: String) {
        val user = auth.currentUser ?: error("Cannot leave a group while signed out")
        val uid = user.uid

        val groupRef = firestore.document(FirestorePaths.groupPath(groupId))

        val batch = firestore.batch()
        // See joinGroup: increment() avoids the same stale-read race on the way out, and removes
        // the group read that existed only to compute the new count.
        batch.update(groupRef, "memberCount", FieldValue.increment(-1))
        batch.delete(groupRef.collection("members").document(uid))
        batch.update(
            firestore.document(FirestorePaths.userDoc(uid)),
            "currentGroupIds",
            FieldValue.arrayRemove(groupId)
        )
        batch.commit().await()
    }

    private fun randomInviteCode(): String {
        // SecureRandom, not Random.Default: a predictable code is as weak as a short one.
        val segment = (1..INVITE_CODE_LENGTH)
            .map { INVITE_CODE_CHARSET[secureRandom.nextInt(INVITE_CODE_CHARSET.length)] }
            .joinToString("")
        return "PEE-$segment"
    }
}
