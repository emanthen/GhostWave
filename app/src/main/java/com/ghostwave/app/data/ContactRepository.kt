package com.ghostwave.app.data

import com.ghostwave.app.data.db.ContactDao
import com.ghostwave.app.data.model.Contact
import com.ghostwave.app.util.QrCodeUtil
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
) {
    fun observeAllContacts(): Flow<List<Contact>> = contactDao.observeAllContacts()

    fun observeContact(id: String): Flow<Contact?> = contactDao.observeContactById(id)

    suspend fun getContactById(id: String): Contact? = contactDao.getContactById(id)

    suspend fun getContactByGwId(gwId: String): Contact? = contactDao.getContactByGwId(gwId)

    /**
     * Adds a contact from a decoded QR payload.
     * Idempotent: if the GW-ID already exists, returns the existing contact ID.
     */
    suspend fun addContactFromQr(payload: QrCodeUtil.ContactPayload): String {
        val existing = contactDao.getContactByGwId(payload.ghostWaveId)
        if (existing != null) return existing.id

        val contact = Contact(
            id              = UUID.randomUUID().toString(),
            ghostWaveId     = payload.ghostWaveId,
            displayName     = payload.displayName,
            publicKeyBase64 = payload.publicKeyBase64,
            addedAt         = System.currentTimeMillis(),
        )
        contactDao.insertContact(contact)
        return contact.id
    }

    suspend fun addContactManual(gwId: String, displayName: String, publicKeyBase64: String): String {
        val existing = contactDao.getContactByGwId(gwId)
        if (existing != null) return existing.id
        val contact = Contact(
            id              = UUID.randomUUID().toString(),
            ghostWaveId     = gwId,
            displayName     = displayName,
            publicKeyBase64 = publicKeyBase64,
            addedAt         = System.currentTimeMillis(),
        )
        contactDao.insertContact(contact)
        return contact.id
    }

    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)

    suspend fun deleteContact(contact: Contact) = contactDao.deleteContact(contact)

    suspend fun setBlocked(id: String, blocked: Boolean) = contactDao.setBlocked(id, blocked)

    suspend fun setVerified(id: String, verified: Boolean) = contactDao.setVerified(id, verified)

    suspend fun updateFcmToken(gwId: String, token: String) = contactDao.updateFcmToken(gwId, token)

    suspend fun updateLastMessage(contactId: String, preview: String, timestamp: Long) =
        contactDao.updateLastMessage(contactId, preview, timestamp)

    suspend fun incrementUnread(contactId: String) = contactDao.incrementUnreadCount(contactId)

    suspend fun clearUnread(contactId: String) = contactDao.clearUnreadCount(contactId)

    suspend fun setDisappearingDuration(contactId: String, secs: Long?) =
        contactDao.setDisappearingDuration(contactId, secs)
}
