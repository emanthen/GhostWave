package com.ghostwave.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ghostwave.app.data.model.CallDirection
import com.ghostwave.app.data.model.CallRecord
import com.ghostwave.app.data.model.CallStatus
import com.ghostwave.app.data.model.CallType
import com.ghostwave.app.data.model.Contact
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.data.model.MessageDirection
import com.ghostwave.app.data.model.MessageStatus
import com.ghostwave.app.data.model.MessageType
import com.ghostwave.app.data.model.SignalIdentityKey
import com.ghostwave.app.data.model.SignalPreKey
import com.ghostwave.app.data.model.SignalSession
import com.ghostwave.app.data.model.SignalSignedPreKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented tests for GhostWaveDatabase DAOs.
 *
 * Uses an in-memory Room database (no SQLCipher SupportFactory) for speed.
 * The SQLCipher encryption layer is tested implicitly in the production app
 * via DatabaseModule; here we verify the DAO logic independently.
 */
@RunWith(AndroidJUnit4::class)
class GhostWaveDatabaseTest {

    private lateinit var db: GhostWaveDatabase
    private lateinit var contactDao:  ContactDao
    private lateinit var messageDao:  MessageDao
    private lateinit var callRecordDao: CallRecordDao
    private lateinit var sessionDao:  SignalSessionDao
    private lateinit var preKeyDao:   SignalPreKeyDao
    private lateinit var spkDao:      SignalSignedPreKeyDao
    private lateinit var identityDao: SignalIdentityKeyDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GhostWaveDatabase::class.java,
        ).allowMainThreadQueries().build()

        contactDao   = db.contactDao()
        messageDao   = db.messageDao()
        callRecordDao = db.callRecordDao()
        sessionDao   = db.signalSessionDao()
        preKeyDao    = db.signalPreKeyDao()
        spkDao       = db.signalSignedPreKeyDao()
        identityDao  = db.signalIdentityKeyDao()
    }

    @After
    fun tearDown() = db.close()

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun contact(gwId: String = "GW-AAAA-BBBB") = Contact(
        id = UUID.randomUUID().toString(), ghostWaveId = gwId,
        displayName = "Alice", publicKeyBase64 = "key==", addedAt = 1L,
    )

    private fun message(contactId: String, body: String = "hi") = Message(
        id = UUID.randomUUID().toString(), contactId = contactId,
        senderGwId = "GW-AAAA-BBBB", body = body,
        direction = MessageDirection.OUTGOING, timestamp = System.currentTimeMillis(),
    )

    // ── Contact CRUD ──────────────────────────────────────────────────────

    @Test
    fun insertAndRetrieveContact() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        val loaded = contactDao.getContactById(c.id)
        assertNotNull(loaded)
        assertEquals(c.ghostWaveId, loaded!!.ghostWaveId)
        assertEquals(c.displayName, loaded.displayName)
    }

    @Test
    fun observeAllContacts_emitsUpdatesOnInsert() = runTest {
        val c = contact()
        var contacts = contactDao.observeAllContacts().first()
        assertTrue("Should start empty", contacts.isEmpty())
        contactDao.insertContact(c)
        contacts = contactDao.observeAllContacts().first()
        assertEquals(1, contacts.size)
    }

    @Test
    fun deleteContact_removesFromDb() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        contactDao.deleteContact(c)
        assertNull(contactDao.getContactById(c.id))
    }

    @Test
    fun updateLastMessage_persistsCorrectly() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        contactDao.updateLastMessage(c.id, "Hey there", 99_999L)
        val loaded = contactDao.getContactById(c.id)!!
        assertEquals("Hey there", loaded.lastMessagePreview)
        assertEquals(99_999L,     loaded.lastMessageAt)
    }

    @Test
    fun incrementAndClearUnreadCount() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        contactDao.incrementUnreadCount(c.id)
        contactDao.incrementUnreadCount(c.id)
        assertEquals(2, contactDao.getContactById(c.id)!!.unreadCount)
        contactDao.clearUnreadCount(c.id)
        assertEquals(0, contactDao.getContactById(c.id)!!.unreadCount)
    }

    @Test
    fun setBlocked_updatesFlag() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        contactDao.setBlocked(c.id, true)
        assertTrue(contactDao.getContactById(c.id)!!.isBlocked)
        // Blocked contacts excluded from observeAllContacts
        val list = contactDao.observeAllContacts().first()
        assertTrue("Blocked contacts must not appear in list", list.none { it.id == c.id })
    }

    // ── Message CRUD ──────────────────────────────────────────────────────

    @Test
    fun insertAndObserveMessages() = runTest {
        val c   = contact()
        contactDao.insertContact(c)
        val m   = message(c.id, "Hello")
        messageDao.insertMessage(m)
        val msgs = messageDao.observeMessagesForContact(c.id).first()
        assertEquals(1, msgs.size)
        assertEquals("Hello", msgs[0].body)
    }

    @Test
    fun deleteContact_cascadesMessages() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        val m = message(c.id)
        messageDao.insertMessage(m)
        contactDao.deleteContact(c)
        val msgs = messageDao.observeMessagesForContact(c.id).first()
        assertTrue("Messages should be cascade-deleted", msgs.isEmpty())
    }

    @Test
    fun markDelivered_updatesStatusAndTimestamp() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        val m = message(c.id)
        messageDao.insertMessage(m)
        val deliveredAt = System.currentTimeMillis()
        messageDao.markDelivered(m.id, deliveredAt = deliveredAt)
        val loaded = messageDao.getMessageById(m.id)!!
        assertEquals(MessageStatus.DELIVERED, loaded.status)
        assertEquals(deliveredAt, loaded.deliveredAt)
    }

    @Test
    fun getPendingOutboundMessages_returnsOnlyPendingAndFailed() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        val pending = message(c.id, "pending")
        val sent    = message(c.id, "sent").copy(status = MessageStatus.SENT)
        val failed  = message(c.id, "failed").copy(status = MessageStatus.FAILED)
        messageDao.insertMessage(pending)
        messageDao.insertMessage(sent)
        messageDao.insertMessage(failed)
        val queue = messageDao.getPendingOutboundMessages()
        assertEquals(2, queue.size)
        assertTrue(queue.all { it.status == MessageStatus.PENDING || it.status == MessageStatus.FAILED })
    }

    @Test
    fun searchMessages_matchesSubstring() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        messageDao.insertMessage(message(c.id, "Hello World"))
        messageDao.insertMessage(message(c.id, "Good Morning"))
        val results = messageDao.searchMessages("Morning")
        assertEquals(1, results.size)
        assertEquals("Good Morning", results[0].body)
    }

    @Test
    fun deleteExpiredMessages_removesOnlyExpired() = runTest {
        val c    = contact()
        contactDao.insertContact(c)
        val now  = System.currentTimeMillis()
        val live = message(c.id, "live").copy(expiresAt = now + 100_000L)
        val dead = message(c.id, "dead").copy(expiresAt = now - 1L)
        messageDao.insertMessage(live)
        messageDao.insertMessage(dead)
        val deletedCount = messageDao.deleteExpiredMessages(now)
        assertEquals(1, deletedCount)
        val remaining = messageDao.observeMessagesForContact(c.id).first()
        assertEquals(1, remaining.size)
        assertEquals("live", remaining[0].body)
    }

    // ── CallRecord CRUD ───────────────────────────────────────────────────

    @Test
    fun insertAndObserveCallRecords() = runTest {
        val c  = contact()
        contactDao.insertContact(c)
        val cr = CallRecord(
            id = UUID.randomUUID().toString(), contactId = c.id,
            callType = CallType.AUDIO, direction = CallDirection.OUTGOING,
            status = CallStatus.COMPLETED, startedAt = 1L, durationSeconds = 120,
        )
        callRecordDao.insertCallRecord(cr)
        val records = callRecordDao.observeCallRecordsForContact(c.id).first()
        assertEquals(1, records.size)
        assertEquals(CallStatus.COMPLETED, records[0].status)
    }

    @Test
    fun missedCallCount_tracksCorrectly() = runTest {
        val c = contact()
        contactDao.insertContact(c)
        callRecordDao.insertCallRecord(CallRecord(
            UUID.randomUUID().toString(), c.id, CallType.AUDIO,
            CallDirection.INCOMING, CallStatus.MISSED, 1L,
        ))
        val count = callRecordDao.observeMissedCallCount().first()
        assertEquals(1, count)
    }

    // ── Signal Session ────────────────────────────────────────────────────

    @Test
    fun storeAndLoadSession() {
        val session = SignalSession(
            compositeId      = "GW-AAAA-BBBB:1",
            gwId             = "GW-AAAA-BBBB",
            deviceId         = 1,
            serializedRecord = byteArrayOf(0x01, 0x02, 0x03),
            updatedAt        = System.currentTimeMillis(),
        )
        sessionDao.storeSession(session)
        val loaded = sessionDao.loadSession("GW-AAAA-BBBB:1")
        assertNotNull(loaded)
        assertTrue(loaded!!.serializedRecord.contentEquals(session.serializedRecord))
    }

    @Test
    fun containsSession_falseForUnknown() {
        assertFalse(sessionDao.containsSession("GW-ZZZZ-ZZZZ:1"))
    }

    @Test
    fun deleteSession_removesEntry() {
        val session = SignalSession("GW-TEST-XXXX:1", "GW-TEST-XXXX", 1, byteArrayOf(0xFF.toByte()), 1L)
        sessionDao.storeSession(session)
        sessionDao.deleteSession("GW-TEST-XXXX:1")
        assertNull(sessionDao.loadSession("GW-TEST-XXXX:1"))
    }

    // ── Signal PreKeys ────────────────────────────────────────────────────

    @Test
    fun storeAndLoadPreKey() {
        preKeyDao.storePreKey(SignalPreKey(42, byteArrayOf(0xAB.toByte())))
        assertNotNull(preKeyDao.loadPreKey(42))
        assertTrue(preKeyDao.containsPreKey(42))
    }

    @Test
    fun removePreKey_deletesEntry() {
        preKeyDao.storePreKey(SignalPreKey(99, byteArrayOf(0x01)))
        preKeyDao.removePreKey(99)
        assertFalse(preKeyDao.containsPreKey(99))
    }

    // ── Signal Identity Keys ──────────────────────────────────────────────

    @Test
    fun saveAndGetIdentityKey() {
        val key = SignalIdentityKey("GW-AAAA-BBBB", byteArrayOf(0x11), SignalIdentityKey.TRUST_DEFAULT, 1L)
        identityDao.saveIdentityKey(key)
        val loaded = identityDao.getIdentityKey("GW-AAAA-BBBB")
        assertNotNull(loaded)
        assertEquals(SignalIdentityKey.TRUST_DEFAULT, loaded!!.trustLevel)
    }

    @Test
    fun replaceIdentityKey_updatesOnConflict() {
        identityDao.saveIdentityKey(SignalIdentityKey("GW-AAAA-BBBB", byteArrayOf(0x11), SignalIdentityKey.TRUST_DEFAULT, 1L))
        identityDao.saveIdentityKey(SignalIdentityKey("GW-AAAA-BBBB", byteArrayOf(0x22), SignalIdentityKey.TRUST_UNVERIFIED, 2L))
        val loaded = identityDao.getIdentityKey("GW-AAAA-BBBB")!!
        assertEquals(SignalIdentityKey.TRUST_UNVERIFIED, loaded.trustLevel)
        assertTrue(loaded.serializedKey.contentEquals(byteArrayOf(0x22)))
    }
}
