package com.aimoneytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.aimoneytracker.data.local.dao.SubscriptionDao
import com.aimoneytracker.data.local.dao.SplitDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.local.entity.AuditLogEntity
import com.aimoneytracker.data.local.entity.BudgetEntity
import com.aimoneytracker.data.local.entity.CategoryEntity
import com.aimoneytracker.data.local.entity.DigestRecordEntity
import com.aimoneytracker.data.local.entity.ForecastSnapshotEntity
import com.aimoneytracker.data.local.entity.GoalContributionEntity
import com.aimoneytracker.data.local.entity.GoalEntity
import com.aimoneytracker.data.local.entity.GroupEntity
import com.aimoneytracker.data.local.entity.IncomeSourceEntity
import com.aimoneytracker.data.local.entity.MerchantMapEntity
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.PersonHandleEntity
import com.aimoneytracker.data.local.entity.RawMessageEntity
import com.aimoneytracker.data.local.entity.RuleEntity
import com.aimoneytracker.data.local.entity.SplitEntity
import com.aimoneytracker.data.local.entity.SplitParticipantEntity
import com.aimoneytracker.data.local.entity.SubscriptionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        PersonEntity::class,
        PersonHandleEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        MerchantMapEntity::class,
        RawMessageEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        SubscriptionEntity::class,
        SplitEntity::class,
        SplitParticipantEntity::class,
        GroupEntity::class,
        IncomeSourceEntity::class,
        ForecastSnapshotEntity::class,
        DigestRecordEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun merchantMapDao(): MerchantMapDao
    abstract fun rawMessageDao(): RawMessageDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun splitDao(): SplitDao
    abstract fun groupDao(): GroupDao
    abstract fun incomeSourceDao(): IncomeSourceDao
    abstract fun forecastSnapshotDao(): ForecastSnapshotDao
    abstract fun digestRecordDao(): DigestRecordDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val NAME = "aimoneytracker.db"
    }
}
