package com.aimoneytracker.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.aimoneytracker.data.local.AppDatabase
import com.aimoneytracker.data.local.dao.AccountDao
import com.aimoneytracker.data.local.dao.AuditLogDao
import com.aimoneytracker.data.local.dao.BudgetDao
import com.aimoneytracker.data.local.dao.CategoryDao
import com.aimoneytracker.data.local.dao.DigestRecordDao
import com.aimoneytracker.data.local.dao.ForecastSnapshotDao
import com.aimoneytracker.data.local.dao.GoalDao
import com.aimoneytracker.data.local.dao.GroupDao
import com.aimoneytracker.data.local.dao.IncomeSourceDao
import com.aimoneytracker.data.local.dao.MerchantMapDao
import com.aimoneytracker.data.local.dao.PersonDao
import com.aimoneytracker.data.local.dao.RawMessageDao
import com.aimoneytracker.data.local.dao.RuleDao
import com.aimoneytracker.data.local.dao.SplitDao
import com.aimoneytracker.data.local.dao.SubscriptionDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): AppDatabase {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()

        // Encrypt the database with SQLCipher (§21). If the native layer is unavailable on a given
        // build/device, fall back to an unencrypted DB so the app still runs.
        runCatching {
            val factory = buildCipherFactory(keyProvider.databasePassphrase())
            if (factory != null) builder.openHelperFactory(factory)
        }
        return builder.build()
    }

    /**
     * Builds the SQLCipher [SupportSQLiteOpenHelper.Factory] via reflection so the project compiles
     * even if the SQLCipher artifact API shifts; returns null on any failure (→ unencrypted fallback).
     */
    private fun buildCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory? = runCatching {
        // Eagerly load the native library here (inside the guard) so a load failure becomes a clean
        // unencrypted fallback rather than a crash on first DB access.
        System.loadLibrary("sqlcipher")
        val clazz = Class.forName("net.zetetic.database.sqlcipher.SupportOpenHelperFactory")
        val ctor = clazz.getConstructor(ByteArray::class.java)
        ctor.newInstance(passphrase) as SupportSQLiteOpenHelper.Factory
    }.getOrNull()

    @Provides fun transactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun accountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun personDao(db: AppDatabase): PersonDao = db.personDao()
    @Provides fun categoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun ruleDao(db: AppDatabase): RuleDao = db.ruleDao()
    @Provides fun merchantMapDao(db: AppDatabase): MerchantMapDao = db.merchantMapDao()
    @Provides fun rawMessageDao(db: AppDatabase): RawMessageDao = db.rawMessageDao()
    @Provides fun budgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun goalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun subscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()
    @Provides fun splitDao(db: AppDatabase): SplitDao = db.splitDao()
    @Provides fun groupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun incomeSourceDao(db: AppDatabase): IncomeSourceDao = db.incomeSourceDao()
    @Provides fun forecastSnapshotDao(db: AppDatabase): ForecastSnapshotDao = db.forecastSnapshotDao()
    @Provides fun digestRecordDao(db: AppDatabase): DigestRecordDao = db.digestRecordDao()
    @Provides fun auditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()
}
