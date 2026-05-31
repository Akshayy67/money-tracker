package com.aimoneytracker.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.repository.PersonRepository
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.model.RelationshipType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {

    val needsReview: StateFlow<List<TransactionEntity>> =
        transactionRepository.observeNeedsReview()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<PersonEntity>> =
        personRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Business/Shop branch (§7): tag with a category; learns handle→category for silent future auto-tag. */
    fun tagBusiness(txn: TransactionEntity, category: String, note: String?) {
        viewModelScope.launch {
            transactionRepository.applyCorrection(
                transactionId = txn.id,
                category = category,
                note = note,
                markAsBusiness = true,
                handle = handleOf(txn),
            )
        }
    }

    /** Person branch (§7): link to a person (optionally with a category like "Rent split"/"Gift"). */
    fun tagPerson(txn: TransactionEntity, personId: Long, category: String?, note: String?) {
        viewModelScope.launch {
            transactionRepository.applyCorrection(
                transactionId = txn.id,
                category = category,
                personId = personId,
                note = note,
                markAsBusiness = false,
                handle = handleOf(txn),
            )
        }
    }

    fun createPersonAndTag(txn: TransactionEntity, name: String, relationship: RelationshipType, category: String?, note: String?) {
        viewModelScope.launch {
            val id = personRepository.add(PersonEntity(name = name, relationship = relationship))
            tagPerson(txn, id, category, note)
        }
    }

    fun tagRent(txn: TransactionEntity, note: String?) {
        viewModelScope.launch {
            transactionRepository.applyCorrection(txn.id, category = CategoryCatalog.RENT, note = note, markAsBusiness = true, handle = handleOf(txn))
        }
    }

    fun skip(txn: TransactionEntity) {
        viewModelScope.launch { transactionRepository.markReviewed(txn.id) }
    }

    fun ignore(txn: TransactionEntity) {
        viewModelScope.launch { transactionRepository.setIgnored(txn.id, true) }
    }

    /** Best handle to learn from: the UPI VPA in the raw message, else the merchant. */
    private fun handleOf(txn: TransactionEntity): String? {
        val vpa = Regex("""([a-zA-Z0-9._\-]{2,})@([a-zA-Z]{2,})""").find(txn.rawMessage ?: "")?.value
        return vpa?.lowercase()
    }
}
