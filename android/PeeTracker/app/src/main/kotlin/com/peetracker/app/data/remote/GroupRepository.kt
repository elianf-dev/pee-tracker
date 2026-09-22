package com.peetracker.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CreateGroupResult(val groupId: String, val inviteCode: String)

data class JoinGroupResult(val groupId: String, val name: String)

class InvalidInviteCodeException(message: String) : Exception(message)

private const val INVITE_CODE_CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private const val INVITE_CODE_LENGTH = 4
private const val MAX_CREATE_ATTEMPTS = 10

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
                // and groups can't be deleted by clients per the rules); with a ~1M-code space
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
        val currentCount = groupSnap.getLong("memberCount") ?: 0L
        val name = groupSnap.getString("name").orEmpty()

        val batch = firestore.batch()
        batch.update(groupRef, "memberCount", currentCount + 1)
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
        val groupSnap = groupRef.get().await()
        val currentCount = groupSnap.getLong("memberCount") ?: 1L

        val batch = firestore.batch()
        batch.update(groupRef, "memberCount", currentCount - 1)
        batch.delete(groupRef.collection("members").document(uid))
        batch.update(
            firestore.document(FirestorePaths.userDoc(uid)),
            "currentGroupIds",
            FieldValue.arrayRemove(groupId)
        )
        batch.commit().await()
    }

    private fun randomInviteCode(): String {
        val segment = (1..INVITE_CODE_LENGTH).map { INVITE_CODE_CHARSET.random() }.joinToString("")
        return "PEE-$segment"
    }
}
