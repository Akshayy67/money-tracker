package com.aimoneytracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.domain.ai.AiService
import com.aimoneytracker.domain.ai.ChatTurn
import com.aimoneytracker.domain.usecase.AnswerChatUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val fromUser: Boolean)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val answerChat: AnswerChatUseCase,
    private val aiService: AiService,
) : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(ChatMessage("Hi! Ask me anything about your money — e.g. \"How much did I spend on food this month?\"", false))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // Null when AI is working; otherwise a reason (no key / local-only / API error). Shown as a banner.
    private val _aiStatus = MutableStateFlow<String?>(null)
    val aiStatus: StateFlow<String?> = _aiStatus.asStateFlow()

    init { refreshStatus() }

    fun refreshStatus() {
        viewModelScope.launch { _aiStatus.value = aiService.statusReason() }
    }

    fun send(question: String) {
        if (question.isBlank()) return
        _messages.value = _messages.value + ChatMessage(question, true)
        _busy.value = true
        viewModelScope.launch {
            val history = _messages.value.takeLast(8).map {
                ChatTurn(if (it.fromUser) "user" else "assistant", it.text)
            }
            val answer = runCatching { answerChat(question, history) }.getOrNull()
            _messages.value = _messages.value + ChatMessage(answer?.text ?: "Sorry, I couldn't work that out.", false)
            _busy.value = false
            // Refresh status after each call so a fresh API error (bad key / no credit) surfaces.
            refreshStatus()
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // Banner explaining AI status. When AI is unavailable the assistant still answers from your
        // data deterministically — this just tells you why phrasing isn't AI-generated.
        aiStatus?.let { status ->
            Text(
                status,
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false) {
            items(messages) { msg -> ChatBubble(msg) }
            if (busy) item { Text("…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Ask a question…") }, maxLines = 3,
            )
            IconButton(onClick = { viewModel.send(input); input = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val align = if (msg.fromUser) Alignment.End else Alignment.Start
    val bg = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Box(
            Modifier.align(align).clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp),
        ) { Text(msg.text) }
    }
}
