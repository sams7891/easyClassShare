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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

/**
 * Everything the app stores online, in Firebase Realtime Database:
 *
 * ```
 * joinCodes/{code}                     -> { classId, role: "student" | "teacher" }
 * classSecrets/{classId}               -> { studentCode, teacherCode }        (teachers only)
 * classes/{classId}/info               -> { name, createdBy, createdAt }
 * classes/{classId}/teachers/{uid}     -> { name, code, joinedAt }
 * classes/{classId}/devices/{uid}      -> { name, code, joinedAt, online, lastSeen, battery, charging, canLock }
 * classes/{classId}/sound              -> { all: Boolean, until: Long, devices: { uid: Boolean } }
 * classes/{classId}/messages/{id}      -> { from, content, ts, by }            (whole class)
 * classes/{classId}/inbox/{uid}/{id}   -> { from, content, ts, by }            (one tablet)
 * classes/{classId}/opened/{id}/{uid}  -> ts                                   (who opened it)
 * classes/{classId}/attention          -> { text, from, ts }                   ("Eyes on me")
 * ```
 *
 * Every device signs in anonymously; its user id stands for "this tablet".
 * Who may read or write what is enforced by `database.rules.json`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
        return Membership(classId, Role.TEACHER, teacherName, className)
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
        val className = runCatching {
            classRef(classId).child("info").child("name").get().await().value as? String
        }.getOrNull().orEmpty()
        return Membership(classId, role, name, className)
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

    // ---------------------------------------------------------------- live data (everyone)

    /** Emits false once this device is no longer part of the class (a teacher removed it). */
    fun isStillMember(membership: Membership): Flow<Boolean> = withUid { uid ->
        memberRef(membership.classId, membership.role, uid).values().map { it.exists() }
    }

    fun className(classId: String): Flow<String?> =
        classRef(classId).child("info").child("name").values().map { it.value as? String }

    /** Difference between the server clock and this device's clock, in milliseconds. */
    fun serverTimeOffset(): Flow<Long> =
        db.getReference(".info/serverTimeOffset").values()
            .map { (it.value as? Number)?.toLong() ?: 0L }
            .onStart { emit(0L) }
            .distinctUntilChanged()

    // ---------------------------------------------------------------- student

    /** What this tablet received: everything sent to the whole class plus what was sent to it alone. Newest first. */
    fun messages(classId: String): Flow<List<ShareMessage>> = withUid { uid ->
        combine(
            classRef(classId).child("messages").orderByKey().limitToLast(MESSAGE_LIMIT).values(),
            classRef(classId).child("inbox").child(uid).orderByKey().limitToLast(MESSAGE_LIMIT).values(),
        ) { forClass, forMe ->
            (forClass.children + forMe.children)
                .mapNotNull { parseMessage(it) }
                .sortedByDescending { it.id }
                .take(MESSAGE_LIMIT)
                .map { ShareMessage(id = it.id, senderName = it.from, content = it.content, timestamp = it.timestamp) }
        }
    }

    /** The name a teacher gave this tablet (teachers can rename tablets). */
    fun myDeviceName(classId: String): Flow<String?> = withUid { uid ->
        classRef(classId).child("devices").child(uid).child("name").values().map { it.value as? String }
    }

    /** For a student device: may it make sound right now? Turns true by itself when a timed mute ends. */
    fun soundAllowedForMe(classId: String): Flow<Boolean> = withUid { uid ->
        val sound = classRef(classId).child("sound")
        combine(
            sound.child("all").values(),
            sound.child("until").values(),
            sound.child("devices").child(uid).values(),
            serverTimeOffset(),
        ) { all, until, mine, offset ->
            SoundInputs(all.value as? Boolean, (until.value as? Number)?.toLong(), mine.value as? Boolean, offset)
        }.flatMapLatest { s ->
            flow {
                val now = System.currentTimeMillis() + s.offset
                emit(SoundPolicy.isAllowed(s.all, s.mine, s.until, now))
                if (s.until != null && s.until > now) {
                    delay(s.until - now)
                    emit(SoundPolicy.isAllowed(s.all, s.mine, s.until, s.until))
                }
            }
        }.distinctUntilChanged()
    }

    private data class SoundInputs(val all: Boolean?, val until: Long?, val mine: Boolean?, val offset: Long)

    /** Tells the teachers this tablet opened a link (or saw a message). */
    suspend fun markOpened(classId: String, messageId: String) {
        val uid = uid()
        classRef(classId).child("opened").child(messageId).child(uid).setValue(ServerValue.TIMESTAMP).await()
    }

    /** Battery level, charging state and whether "Eyes on me" can cover the screen. */
    fun reportStatus(classId: String, values: Map<String, Any>) {
        val uid = auth.currentUser?.uid ?: return
        classRef(classId).child("devices").child(uid).updateChildren(values)
    }

    fun attention(classId: String): Flow<Attention?> =
        classRef(classId).child("attention").values().map { snapshot ->
            val text = snapshot.child("text").value as? String ?: return@map null
            Attention(text, snapshot.child("from").value as? String ?: "")
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
                mutedUntil = (snapshot.child("until").value as? Number)?.toLong(),
            )
        }

    fun devices(classId: String): Flow<List<DeviceInfo>> =
        classRef(classId).child("devices").values().map { snapshot ->
            snapshot.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                val name = child.child("name").value as? String ?: return@mapNotNull null
                DeviceInfo(
                    uid = uid,
                    name = name,
                    online = child.child("online").value == true,
                    lastSeen = (child.child("lastSeen").value as? Number)?.toLong(),
                    battery = (child.child("battery").value as? Number)?.toInt(),
                    charging = child.child("charging").value == true,
                    canLock = child.child("canLock").value as? Boolean,
                )
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

    /** Everything the teachers shared (to the class or to single tablets), newest first, with who opened it. */
    fun sentMessages(classId: String): Flow<List<SentMessage>> = combine(
        classRef(classId).child("messages").orderByKey().limitToLast(MESSAGE_LIMIT).values(),
        classRef(classId).child("inbox").values(),
        classRef(classId).child("opened").values(),
    ) { forClass, inboxes, opened ->
        val direct = HashMap<String, SentMessage>()
        for (inbox in inboxes.children) {
            val deviceUid = inbox.key ?: continue
            for (child in inbox.children) {
                val message = parseMessage(child) ?: continue
                val known = direct[message.id]
                direct[message.id] = (known ?: message.copy(targets = emptySet()))
                    .let { it.copy(targets = it.targets.orEmpty() + deviceUid) }
            }
        }
        val openedBy = opened.children.associate { m -> m.key.orEmpty() to m.children.mapNotNull { it.key }.toSet() }
        (forClass.children.mapNotNull { parseMessage(it) } + direct.values)
            .sortedByDescending { it.id }
            .take(MESSAGE_LIMIT)
            .map { it.copy(openedBy = openedBy[it.id].orEmpty()) }
    }

    /** Sends to the whole class ([targets] = null) or only to the given tablets. */
    suspend fun send(classId: String, from: String, content: String, targets: Set<String>?) {
        val uid = uid()
        val id = classRef(classId).child("messages").push().key ?: throw IllegalStateException("No message id")
        val value = mapOf("from" to from, "content" to content, "ts" to ServerValue.TIMESTAMP, "by" to uid)
        if (targets == null) {
            classRef(classId).child("messages").child(id).setValue(value).await()
        } else {
            val updates = HashMap<String, Any?>()
            for (target in targets) updates["classes/$classId/inbox/$target/$id"] = value
            root.updateChildren(updates).await()
        }
    }

    suspend fun deleteMessage(classId: String, message: SentMessage) {
        uid()
        val updates = hashMapOf<String, Any?>("classes/$classId/opened/${message.id}" to null)
        if (message.targets == null) {
            updates["classes/$classId/messages/${message.id}"] = null
        } else {
            for (target in message.targets) updates["classes/$classId/inbox/$target/${message.id}"] = null
        }
        root.updateChildren(updates).await()
    }

    /**
     * Sets sound for the whole class and clears every per-device switch.
     * With [mutedUntil] (server time) a mute ends by itself.
     */
    suspend fun setClassSound(classId: String, allowed: Boolean, mutedUntil: Long? = null) {
        uid()
        val value = if (!allowed && mutedUntil != null) mapOf("all" to false, "until" to mutedUntil) else mapOf("all" to allowed)
        classRef(classId).child("sound").setValue(value).await()
    }

    suspend fun setDeviceSound(classId: String, deviceUid: String, allowed: Boolean) {
        uid()
        classRef(classId).child("sound").child("devices").child(deviceUid).setValue(allowed).await()
    }

    suspend fun renameDevice(classId: String, deviceUid: String, name: String) {
        uid()
        classRef(classId).child("devices").child(deviceUid).child("name").setValue(name).await()
    }

    suspend fun setAttention(classId: String, attention: Attention?) {
        uid()
        val node = classRef(classId).child("attention")
        if (attention == null) {
            node.removeValue().await()
        } else {
            node.setValue(mapOf("text" to attention.text, "from" to attention.from, "ts" to ServerValue.TIMESTAMP)).await()
        }
    }

    suspend fun removeDevice(classId: String, deviceUid: String) {
        uid()
        root.updateChildren(
            hashMapOf<String, Any?>(
                "classes/$classId/devices/$deviceUid" to null,
                "classes/$classId/sound/devices/$deviceUid" to null,
                "classes/$classId/inbox/$deviceUid" to null,
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
        val uid = auth.currentUser?.uid ?: return
        val me = classRef(classId).child("devices").child(uid)
        me.onDisconnect().cancel()
        me.updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    // ---------------------------------------------------------------- helpers

    private fun parseMessage(child: DataSnapshot): SentMessage? {
        val id = child.key ?: return null
        val content = child.child("content").value as? String ?: return null
        return SentMessage(
            id = id,
            from = child.child("from").value as? String ?: "",
            content = content,
            timestamp = (child.child("ts").value as? Number)?.toLong() ?: 0L,
            targets = null,
        )
    }

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
