package com.aimoneytracker.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.local.result.PersonBalance
import com.aimoneytracker.data.repository.PersonRepository
import com.aimoneytracker.data.repository.SplitRepository
import com.aimoneytracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonListItem(val person: PersonEntity, val net: Long)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val personRepository: PersonRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<PersonListItem>>(emptyList())
    val items: StateFlow<List<PersonListItem>> = _items.asStateFlow()

    val rawPeople: StateFlow<List<PersonEntity>> =
        personRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            personRepository.observeAll().collect { people ->
                _items.value = people.map { p ->
                    val bal = personRepository.balanceWith(p.id)
                    PersonListItem(p, bal.sent - bal.received)
                }
            }
        }
    }
}

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val transactionRepository: TransactionRepository,
    private val splitRepository: SplitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1

    val person: StateFlow<PersonEntity?> =
        personRepository.observeById(personId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val transactions: StateFlow<List<TransactionEntity>> =
        transactionRepository.observeByPerson(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _balance = MutableStateFlow(PersonBalance(personId, 0, 0, 0, 0))
    val balance: StateFlow<PersonBalance> = _balance.asStateFlow()

    init {
        viewModelScope.launch { _balance.value = personRepository.balanceWith(personId) }
    }
}
