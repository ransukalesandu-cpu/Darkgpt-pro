package com.darkgpt.android

import android.content.Context
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("darkgpt_settings", Context.MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF8B5CF6),
                background = Color(0xFF090B12),
                surface = Color(0xFF151925)
            )) {
                DarkGPTScreen(
                    initialUrl = prefs.getString("api_url", "http://10.0.2.2:8000") ?: "",
                    initialToken = prefs.getString("api_token", "") ?: "",
                    onSaveSettings = { url, token ->
                        prefs.edit().putString("api_url", url.trim().trimEnd('/'))
                            .putString("api_token", token).apply()
                    }
                )
            }
        }
    }
}

private data class ChatMessage(val text: String, val fromUser: Boolean)
private data class ModelOption(val label: String, val id: String)

private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .build()
private val jsonType = "application/json; charset=utf-8".toMediaType()

@Composable
private fun DarkGPTScreen(
    initialUrl: String,
    initialToken: String,
    onSaveSettings: (String, String) -> Unit
) {
    var apiUrl by remember { mutableStateOf(initialUrl) }
    var apiToken by remember { mutableStateOf(initialToken) }
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadModels() {
        scope.launch {
            loading = true
            error = ""
            try {
                val result = withContext(Dispatchers.IO) { apiGet("$apiUrl/api/models", apiToken) }
                val array = result.getJSONArray("models")
                val parsed = (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    ModelOption(item.getString("label"), item.getString("id"))
                }
                models = parsed
                if (selectedModel.isBlank() || parsed.none { it.label == selectedModel }) {
                    selectedModel = parsed.firstOrNull()?.label.orEmpty()
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not connect to the API"
            } finally { loading = false }
        }
    }

    LaunchedEffect(apiUrl, apiToken) { if (apiUrl.isNotBlank()) loadModels() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF111522)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("DARKGPT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("AI CHAT ASSISTANT", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { messages = emptyList() }) { Text("New chat") }
                    TextButton(onClick = { startActivity(Intent(this@MainActivity, GitHubAgentActivity::class.java).apply { putExtra("api_url", prefs.getString("api_url", "http://10.0.2.2:8000")); putExtra("api_token", prefs.getString("api_token", "")) }) }) { Text("GitHub") }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MODEL", color = Color(0xFF9CA3AF), fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp))
                Box {
                    Button(onClick = { menuOpen = true }, enabled = models.isNotEmpty()) {
                        Text(selectedModel.ifBlank { "Select model" })
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.label) },
                                onClick = { selectedModel = model.label; menuOpen = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("◈", color = Color(0xFF8B5CF6), fontSize = 54.sp)
                        Text("How can I help you?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose a model and start a conversation.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                        ) {
                            Text(
                                message.text,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth(0.88f)
                                    .background(
                                        if (message.fromUser) Color(0xFF49318A) else Color(0xFF171D2A),
                                        RoundedCornerShape(16.dp)
                                    ).padding(14.dp)
                            )
                        }
                    }
                    if (loading) item { CircularProgressIndicator(modifier = Modifier.padding(12.dp)) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF111522)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).background(Color(0xFF202738), RoundedCornerShape(22.dp)).padding(14.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) Text("Message DarkGPT...", color = Color(0xFF8992A3))
                            inner()
                        }
                    }
                )
                IconButton(
                    enabled = input.isNotBlank() && !loading && selectedModel.isNotBlank(),
                    onClick = {
                        val prompt = input.trim()
                        input = ""
                        messages = messages + ChatMessage(prompt, true)
                        loading = true
                        error = ""
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    apiPost("$apiUrl/api/chat", apiToken, JSONObject()
                                        .put("message", prompt).put("model", selectedModel))
                                }
                                messages = messages + ChatMessage(result.getString("response"), false)
                            } catch (e: Exception) {
                                error = e.message ?: "Request failed"
                                messages = messages + ChatMessage("Request failed: $error", false)
                            } finally { loading = false }
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFB9A1FF))
                }
            }
        }
    }

    if (settingsOpen) {
        var urlDraft by remember { mutableStateOf(apiUrl) }
        var tokenDraft by remember { mutableStateOf(apiToken) }
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Connection settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backend URL", color = Color(0xFFB9A1FF))
                    BasicTextField(
                        value = urlDraft, onValueChange = { urlDraft = it },
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF202738), RoundedCornerShape(8.dp)).padding(10.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                    )
                    Text("API token (if configured on server)", color = Color(0xFFB9A1FF))
                    BasicTextField(
                        value = tokenDraft, onValueChange = { tokenDraft = it },
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF202738), RoundedCornerShape(8.dp)).padding(10.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                    )
                    Text("Use your computer's LAN IP on a real phone, or a deployed HTTPS URL.", color = Color.Gray, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    apiUrl = urlDraft.trim().trimEnd('/')
                    apiToken = tokenDraft
                    onSaveSettings(apiUrl, apiToken)
                    settingsOpen = false
                    loadModels()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { settingsOpen = false }) { Text("Cancel") } }
        )
    }
}

private fun apiGet(url: String, token: String): JSONObject = executeRequest(
    Request.Builder().url(url).apply { if (token.isNotBlank()) addHeader("X-API-Key", token) }.get().build()
)

private fun apiPost(url: String, token: String, payload: JSONObject): JSONObject = executeRequest(
    Request.Builder().url(url)
        .apply { if (token.isNotBlank()) addHeader("X-API-Key", token) }
        .post(payload.toString().toRequestBody(jsonType)).build()
)

private fun executeRequest(request: Request): JSONObject {
    http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = try { JSONObject(body).optString("detail", body) } catch (_: Exception) { body }
            throw IOException("HTTP ${response.code}: $detail")
        }
        return JSONObject(body)
    }
}
