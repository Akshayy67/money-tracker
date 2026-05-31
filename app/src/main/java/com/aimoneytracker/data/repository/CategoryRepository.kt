package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.CategoryDao
import com.aimoneytracker.data.local.dao.MerchantMapDao
import com.aimoneytracker.data.local.dao.RuleDao
import com.aimoneytracker.data.local.entity.CategoryEntity
import com.aimoneytracker.data.local.entity.MerchantMapEntity
import com.aimoneytracker.data.local.entity.RuleEntity
import com.aimoneytracker.domain.categorize.CategoryCatalog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao,
    private val merchantMapDao: MerchantMapDao,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    fun observeTopLevel(): Flow<List<CategoryEntity>> = categoryDao.observeTopLevel()
    suspend fun getAll(): List<CategoryEntity> = categoryDao.getAll()
    suspend fun getByKey(key: String): CategoryEntity? = categoryDao.getByKey(key)
    suspend fun add(category: CategoryEntity) = categoryDao.insert(category)

    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) categoryDao.insertAll(CategoryCatalog.defaults)
    }

    // Rules editor (§6)
    fun observeRules(): Flow<List<RuleEntity>> = ruleDao.observeAll()
    suspend fun addRule(rule: RuleEntity): Long = ruleDao.insert(rule)
    suspend fun updateRule(rule: RuleEntity) = ruleDao.update(rule)
    suspend fun deleteRule(rule: RuleEntity) = ruleDao.delete(rule)

    // Merchant dictionary
    fun observeMerchantMap(): Flow<List<MerchantMapEntity>> = merchantMapDao.observeAll()
    suspend fun deleteMerchantMapping(key: String) = merchantMapDao.delete(key)
}
