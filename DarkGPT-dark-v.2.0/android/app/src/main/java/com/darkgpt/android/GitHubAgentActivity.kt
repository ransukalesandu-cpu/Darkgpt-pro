package com.darkgpt.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private data class RepoEntry(val name: String, val path: String, val type: String, val size: Int)

class GitHubAgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiUrl = intent.getStringExtra("api_url").orEmpty().trimEnd('/')
        val apiToken = intent.getStringExtra("api_token").orEmpty()
        setContent { MaterialTheme { GitHubAgentScreen(apiUrl, apiToken, onClose = { finish() }) } }
    }
}

private val githubHttp = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

private fun githubApiGet(url: String, token: String): JSONObject {
    val request = Request.Builder().url(url).apply { if (token.isNotBlank()) addHeader("X-API-Key", token) }.get().build()
    githubHttp.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = try { JSONObject(body).optString("detail", body) } catch (_: Exception) { body }
            throw IOException("HTTP ${response.code}: $detail")
        }
        return JSONObject(body)
    }
}

@Composable
private fun GitHubAgentScreen(apiUrl: String, apiToken: String, onClose: () -> Unit) {
    var owner by remember { mutableStateOf("octocat") }
    var repo by remember { mutableStateOf("Hello-World") }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<RepoEntry>>(emptyList()) }
    var fileTitle by remember { mutableStateOf("") }
    var fileContent by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var repoInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun browse(targetPath: String = path) {
        if (apiUrl.isBlank()) { error = "Set the backend URL in DarkGPT settings first."; return }
        loading = true
        error = ""
        fileContent = ""
        scope.launch {
            try {
                val base = "$apiUrl/api/github/repos/${owner.trim()}/${repo.trim()}"
                val data = withContext(Dispatchers.IO) {
                    githubApiGet("$base/contents?path=${android.net.Uri.encode(targetPath)}", apiToken)
                }
                val array = data.getJSONArray("items")
                entries = (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    RepoEntry(item.optString("name"), item.optString("path"), item.optString("type"), item.optInt("size"))
                }
                path = targetPath
                repoInfo = "$owner/$repo • ${entries.size} items"
            } catch (e: Exception) { error = e.message ?: "Could not load repository" }
            finally { loading = false }
        }
    }

    fun openEntry(entry: RepoEntry) {
        if (entry.type == "dir") { browse(entry.path); return }
        loading = true
        error = ""
        scope.launch {
            try {
                val base = "$apiUrl/api/github/repos/${owner.trim()}/${repo.trim()}/file"
                val result = withContext(Dispatchers.IO) {
                    githubApiGet("$base?path=${android.net.Uri.encode(entry.path)}", apiToken)
                }
                fileTitle = result.optString("path", entry.path)
                fileContent = result.optString("content")
            } catch (e: Exception) { error = e.message ?: "Could not open file" }
            finally { loading = false }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("GITHUB AGENT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Repository explorer • Read-only", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
            BasicTextField(owner, { owner = it }, Modifier.fillMaxWidth().background(Color(0xFF202738), RoundedCornerShape(8.dp)).padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White), decorationBox = { inner -> Box { if (owner.isEmpty()) Text("GitHub owner", color = Color.Gray); inner() } })
            BasicTextField(repo, { repo = it }, Modifier.fillMaxWidth().background(Color(0xFF202738), RoundedCornerShape(8.dp)).padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White), decorationBox = { inner -> Box { if (repo.isEmpty()) Text("Repository name", color = Color.Gray); inner() } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { browse("") }, enabled = !loading && owner.isNotBlank() && repo.isNotBlank()) { Text("Open repository") }
                if (path.isNotBlank() || fileContent.isNotBlank()) {
                    TextButton(onClick = { if (fileContent.isNotBlank()) fileContent = "" else browse(path.substringBeforeLast("/", "")) }) { Text("Back") }
                }
            }
            Text(repoInfo, color = Color(0xFFB9A1FF), fontSize = 12.sp)
            Text("Public repositories only. Private repository OAuth and code changes are not enabled yet.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            if (loading) CircularProgressIndicator(Modifier.size(22.dp))
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
            if (fileContent.isNotBlank()) {
                Text(fileTitle, color = Color(0xFFB9A1FF), fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111522), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    item { Text(fileContent, color = Color(0xFFE5E7EB), fontSize = 12.sp) }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(entries) { entry ->
                        TextButton(onClick = { openEntry(entry) }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (entry.type == "dir") "📁 " else "📄 ") + entry.name, color = Color.White, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
