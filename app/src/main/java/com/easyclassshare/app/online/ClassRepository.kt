package com.easyclassshare.app.online

import android.content.Context
import com.easyclassshare.app.BuildConfig
import com.easyclassshare.app.data.ShareMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Everything the app stores online, in Firebase Realtime Database:
 *
 * ```
 * joinCodes/{code}                 -> { classId, role: "student" | "teacher" }
 * classSecrets/{classId}           -> { studentCode, teacherCode }        (teachers only)
 * classes/{classId}/info           -> { name, createdBy, createdAt }
 * classes/{classId}/teachers/{uid} -> { name, code, joinedAt }
 * classes/{classId}/devices/{uid}  -> { name, code, joinedAt, online, lastSeen }
 * classes/{classId}/sound          -> { all: Boolean, devices: { uid: Boolean } }
 * classes/{classId}/messages/{id}  -> { from, content, ts, by }
 * ```
 *
 * Every device signs in anonymously; its user id stands for "this tablet".
 * Who may read or write what is enforced by `database.rules.json`.
 */
object ClassRepository {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(OnlineConfig.firebaseApp(appContext)) }
    private val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(OnlineConfig.firebaseApp(appContext), BuildConfig.FIREBASE_DATABASE_URL)
    }
    private val root: DatabaseReference get() = db.reference
    private fun classRef(classId: String) = root.child("classes").child(classId)

    /** This device's id, signing in anonymously the first time. */
    suspend fun uid(): String {
        auth.currentUser?.let { return it.uid }
        val user = auth.signInAnonymously().await().user
        return user?.uid ?: throw IllegalStateException("Anonymous sign-in failed")
    }

    // ---------------------------------------------------------------- setup

    suspend fun createClass(className: String, teacherName: String): Membership {
        val uid = uid()
        val classId = root.child("classes").push().key ?: throw IllegalStateException("No class id")
        val codes = ClassCodes(
            studentCode = JoinCodes.generate(JoinCodes.STUDENT_LENGTH),
            teacherCode = JoinCodes.generate(JoinCodes.TEACHER_LENGTH),
        )
        val updates = hashMapOf<String, Any?>(
            "classes/$classId/info" to mapOf(
                "name" to className,
                "createdBy" to uid,
                "createdAt" to ServerValue.TIMESTAMP,
            ),
            "classes/$classId/teachers/$uid" to mapOf(
                "name" to teacherName,
                "code" to codes.teacherCode,
                "joinedAt" to ServerValue.TIMESTAMP,
            ),
            "classSecrets/$classId" to mapOf(
                "studentCode" to codes.studentCode,
                "teacherCode" to codes.teacherCode,
            ),
            "joinCodes/${codes.studentCode}" to mapOf("classId" to classId, "role" to "student"),
            "joinCodes/${codes.teacherCode}" to mapOf("classId" to classId, "role" to "teacher"),
        )
        root.updateChildren(updates).await()
        return Membership(classId, Role.TEACHER, teacherName)
    }

    /** Joins with a student or teacher code. [expectedRole] stops a student from joining with a teacher code by mistake. */
    suspend fun join(rawCode: String, name: String, expectedRole: Role): Membership {
        val code = JoinCodes.normalize(rawCode)
        if (!JoinCodes.isWellFormed(code)) throw JoinException(JoinException.Reason.CODE_NOT_FOUND)
        val uid = uid()
        val (classId, role) = lookUpCode(code) ?: throw JoinException(JoinException.Reason.CODE_NOT_FOUND)
        if (role != expectedRole) {
            throw JoinException(
                if (role == Role.STUDENT) {
                    JoinException.Reason.STUDENT_CODE_FOR_TEACHER
                } else {
                    JoinException.Reason.TEACHER_CODE_FOR_STUDENT
                },
            )
        }
        val node = memberRef(classId, role, uid)
        // Joining again (e.g. after the app was set up anew) replaces the old entry.
        if (node.get().await().exists()) node.removeValue().await()
        node.setValue(
            mapOf("name" to name, "code" to code, "joinedAt" to ServerValue.TIMESTAMP),
        ).await()
        return Membership(classId, role, name)
    }

    /** True if [rawCode] is the current teacher code of [classId]. Students need it to leave a class. */
    suspend fun isTeacherCode(classId: String, rawCode: String): Boolean {
        val code = JoinCodes.normalize(rawCode)
        if (!JoinCodes.isWellFormed(code)) return false
        uid()
        val found = lookUpCode(code) ?: return false
        return found.first == classId && found.second == Role.TEACHER
    }

    private suspend fun lookUpCode(code: String): Pair<String, Role>? {
        val snapshot = root.child("joinCodes").child(code).get().await()
        val classId = snapshot.child("classId").value as? String ?: return null
        val role = when (snapshot.child("role").value as? String) {
            "student" -> Role.STUDENT
            "teacher" -> Role.TEACHER
            else -> return null
        }
        return classId to role
    }

    suspend fun leave(membership: Membership) {
        val node = memberRef(membership.classId, membership.role, uid())
        if (membership.role == Role.STUDENT) node.onDisconnect().cancel().await()
        node.removeValue().await()
    }

    private fun memberRef(classId: String, role: Role, uid: String): DatabaseReference =
        classRef(classId).child(if (role == Role.TEACHER) "teachers" else "devices").child(uid)

    // ---------------------------------------------------------------- live data

    /** Emits false once this device is no longer part of the class (a teacher removed it). */
    fun isStillMember(membership: Membership): Flow<Boolean> = withUid { uid ->
        memberRef(membership.classId, membership.role, uid).values().map { it.exists() }
    }

    fun className(classId: String): Flow<String?> =
        classRef(classId).child("info").child("name").values().map { it.value as? String }

    /** The newest 50 messages, newest first. */
    fun messages(classId: String): Flow<List<ShareMessage>> =
        classRef(classId).child("messages").orderByKey().limitToLast(MESSAGE_LIMIT).values().map { snapshot ->
            snapshot.children.mapNotNull { child ->
                val id = child.key ?: return@mapNotNull null
                val content = child.child("content").value as? String ?: return@mapNotNull null
                ShareMessage(
                    id = id,
                    senderName = child.child("from").value as? String ?: "",
                    content = content,
                    timestamp = (child.child("ts").value as? Number)?.toLong() ?: 0L,
                )
            }.reversed()
        }

    /** For a student device: may it make sound right now? */
    fun soundAllowedForMe(classId: String): Flow<Boolean> = withUid { uid ->
        val sound = classRef(classId).child("sound")
        combine(
            sound.child("all").values(),
            sound.child("devices").child(uid).values(),
        ) { all, mine -> SoundPolicy.isAllowed(all.value as? Boolean, mine.value as? Boolean) }
    }

    // ---------------------------------------------------------------- teacher only

    fun soundSettings(classId: String): Flow<SoundSettings> =
        classRef(classId).child("sound").values().map { snapshot ->
            SoundSettings(
                classWide = snapshot.child("all").value as? Boolean,
                perDevice = snapshot.child("devices").children.mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    val allowed = child.value as? Boolean ?: return@mapNotNull null
                    uid to allowed
                }.toMap(),
            )
        }

    fun devices(classId: String): Flow<List<DeviceInfo>> =
        classRef(classId).child("devices").values().map { snapshot ->
            snapshot.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                val name = child.child("name").value as? String ?: return@mapNotNull null
                DeviceInfo(uid, name, online = child.child("online").value == true)
            }.sortedBy { it.name.lowercase() }
        }

    fun teachers(classId: String): Flow<List<TeacherInfo>> =
        classRef(classId).child("teachers").values().map { snapshot ->
            snapshot.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                TeacherInfo(uid, child.child("name").value as? String ?: "")
            }.sortedBy { it.name.lowercase() }
        }

    fun codes(classId: String): Flow<ClassCodes?> =
        root.child("classSecrets").child(classId).values().map { snapshot ->
            val student = snapshot.child("studentCode").value as? String
            val teacher = snapshot.child("teacherCode").value as? String
            if (student != null && teacher != null) ClassCodes(student, teacher) else null
        }

    suspend fun send(classId: String, from: String, content: String) {
        val uid = uid()
        val messages = classRef(classId).child("messages")
        val id = messages.push().key ?: throw IllegalStateException("No message id")
        messages.child(id).setValue(
            mapOf("from" to from, "content" to content, "ts" to ServerValue.TIMESTAMP, "by" to uid),
        ).await()
    }

    /** Sets sound for the whole class and clears every per-device switch. */
    suspend fun setClassSound(classId: String, allowed: Boolean) {
        uid()
        classRef(classId).child("sound").setValue(mapOf("all" to allowed)).await()
    }

    suspend fun setDeviceSound(classId: String, deviceUid: String, allowed: Boolean) {
        uid()
        classRef(classId).child("sound").child("devices").child(deviceUid).setValue(allowed).await()
    }

    suspend fun removeDevice(classId: String, deviceUid: String) {
        uid()
        root.updateChildren(
            hashMapOf<String, Any?>(
                "classes/$classId/devices/$deviceUid" to null,
                "classes/$classId/sound/devices/$deviceUid" to null,
            ),
        ).await()
    }

    suspend fun removeTeacher(classId: String, teacherUid: String) {
        uid()
        classRef(classId).child("teachers").child(teacherUid).removeValue().await()
    }

    /** Makes a new student or teacher code; the old one stops working. People already in the class stay. */
    suspend fun renewCode(classId: String, role: Role) {
        uid()
        val secretsRef = root.child("classSecrets").child(classId)
        val current = secretsRef.get().await()
        val oldStudent = current.child("studentCode").value as? String ?: return
        val oldTeacher = current.child("teacherCode").value as? String ?: return
        val updates = hashMapOf<String, Any?>()
        val codes = if (role == Role.STUDENT) {
            val fresh = JoinCodes.generate(JoinCodes.STUDENT_LENGTH)
            updates["joinCodes/$oldStudent"] = null
            updates["joinCodes/$fresh"] = mapOf("classId" to classId, "role" to "student")
            ClassCodes(fresh, oldTeacher)
        } else {
            val fresh = JoinCodes.generate(JoinCodes.TEACHER_LENGTH)
            updates["joinCodes/$oldTeacher"] = null
            updates["joinCodes/$fresh"] = mapOf("classId" to classId, "role" to "teacher")
            ClassCodes(oldStudent, fresh)
        }
        updates["classSecrets/$classId"] = mapOf("studentCode" to codes.studentCode, "teacherCode" to codes.teacherCode)
        root.updateChildren(updates).await()
    }

    // ---------------------------------------------------------------- presence

    /** While collected, marks this student device as online (and offline when the connection drops). */
    fun presence(classId: String): Flow<Unit> = withUid { uid ->
        val me = classRef(classId).child("devices").child(uid)
        db.getReference(".info/connected").values().map { snapshot ->
            if (snapshot.value == true) {
                me.onDisconnect().updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
                me.updateChildren(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
            }
        }
    }

    fun markOffline(classId: String) {
        if (auth.currentUser == null) return
        val me = classRef(classId).child("devices").child(auth.currentUser!!.uid)
        me.onDisconnect().cancel()
        me.updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    // ---------------------------------------------------------------- helpers

    private fun <T> withUid(block: (String) -> Flow<T>): Flow<T> = flow { emitAll(block(uid())) }

    private fun Query.values(): Flow<DataSnapshot> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }

    private const val MESSAGE_LIMIT = 50
}
