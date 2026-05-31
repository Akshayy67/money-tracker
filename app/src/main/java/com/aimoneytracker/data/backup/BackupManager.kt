package com.aimoneytracker.data.backup

import android.content.Context
import android.util.Base64
import com.aimoneytracker.data.local.dao.AccountDao
import com.aimoneytracker.data.local.dao.CategoryDao
import com.aimoneytracker.data.local.dao.PersonDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.local.entity.CategoryEntity
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.security.DatabaseKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted backups & restore (§20, §21). Exports the core tables to a JSON bundle encrypted with
 * AES-256-GCM using the device key, and restores from such a bundle.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val txnDao: TransactionDao,
    private val accountDao: AccountDao,
    private val personDao: PersonDao,
    private val categoryDao: CategoryDao,
    private val keyProvider: DatabaseKeyProvider,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Serializable
    data class BackupBundle(
        val version: Int = 1,
        val createdAt: Long,
        val transactions: List<TransactionEntity>,
        val accounts: List<AccountEntity>,
        val people: List<PersonEntity>,
        val categories: List<CategoryEntity>,
    )

    suspend fun createBackup(encrypt: Boolean = true): File {
        val bundle = BackupBundle(
            createdAt = System.currentTimeMillis(),
            transactions = txnDao.queryRaw(
                androidx.sqlite.db.SimpleSQLiteQuery("SELECT * FROM transactions")
            ),
            accounts = accountDao.getAll(),
            people = personDao.getAll(),
            categories = categoryDao.getAll(),
        )
        val plain = json.encodeToString(BackupBundle.serializer(), bundle).toByteArray()
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        return if (encrypt) {
            val (iv, cipherText) = encryptAesGcm(plain)
            File(dir, "backup_$stamp.aimt").apply {
                writeText(Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipherText, Base64.NO_WRAP))
            }
        } else {
            File(dir, "backup_$stamp.json").apply { writeBytes(plain) }
        }
    }

    suspend fun restore(file: File): Int {
        val content = file.readText()
        val plain = if (file.extension == "aimt") {
            val (ivB64, ctB64) = content.split(":", limit = 2)
            decryptAesGcm(Base64.decode(ivB64, Base64.NO_WRAP), Base64.decode(ctB64, Base64.NO_WRAP))
        } else content.toByteArray()
        val bundle = json.decodeFromString(BackupBundle.serializer(), String(plain))
        categoryDao.insertAll(bundle.categories)
        bundle.accounts.forEach { accountDao.insert(it) }
        bundle.people.forEach { personDao.insert(it) }
        txnDao.insertAll(bundle.transactions)
        return bundle.transactions.size
    }

    fun listBackups(): List<File> =
        File(context.filesDir, "backups").listFiles()?.sortedDescending() ?: emptyList()

    private fun secretKey() = SecretKeySpec(keyProvider.databasePassphrase(), "AES")

    private fun encryptAesGcm(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return iv to cipher.doFinal(plain)
    }

    private fun decryptAesGcm(iv: ByteArray, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }
}
