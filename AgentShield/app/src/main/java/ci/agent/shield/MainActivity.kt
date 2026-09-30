package ci.agent.shield

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var listenerOn by mutableStateOf(false)
    private var a11yOn by mutableStateOf(false)
    private var modelOn by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var llmStatus by mutableStateOf("")

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        a11yOn = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains("ci.agent.shield.WaveScreenService")
        modelOn = LocalLlm.isInstalled(this)
    }

    private fun importModel(uri: Uri) {
        busy = true; llmStatus = "Copie du modèle en cours… (quelques minutes, reste dans l'app)"
        Thread {
            llmStatus = try {
                val mb = LocalLlm.importModel(this, uri) / 1_000_000
                "✅ Modèle importé ($mb Mo). Tu peux supprimer le fichier d'origine pour libérer de la place."
            } catch (e: Exception) { "❌ Échec de l'import : ${e.message ?: e.javaClass.simpleName}" }
            modelOn = LocalLlm.isInstalled(this); busy = false
        }.start()
    }

    private fun testModel() {
        busy = true; llmStatus = "Test en cours… (le premier chargement peut prendre 20 à 60 s)"
        Thread {
            val t0 = System.currentTimeMillis()
            val r = LocalLlm.judge(this, "Orange CI",
                "Gagnez 500000F ! Envoyez votre code OTP au 0700000000 pour recevoir votre lot.")
            val s = (System.currentTimeMillis() - t0) / 1000
            llmStatus = if (r == null) "❌ Le modèle n'a pas pu être chargé ou n'a pas répondu ($s s)."
            else "Résultat : $r (attendu : ARNAQUE) en $s s"
            busy = false
        }.start()
    }

    private fun launchableApps(): List<Pair<String, String>> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }

    @Composable
    private fun Screen() {
        val ctx = this@MainActivity
        val events by AppDb.get(ctx).dao().recent().collectAsState(emptyList())
        val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }
        val apps = remember { launchableApps() }
        var tts by remember { mutableStateOf(Prefs.ttsEnabled(ctx)) }
        var llmOn by remember { mutableStateOf(Prefs.llm(ctx)) }
        var vip by remember { mutableStateOf(Prefs.vipRaw(ctx)) }
        var diag by remember { mutableStateOf(Prefs.diag(ctx)) }
        var monitored by remember { mutableStateOf(Prefs.monitored(ctx)) }
        var showApps by remember { mutableStateOf(false) }
        val toggle = { pkg: String ->
            monitored = if (pkg in monitored) monitored - pkg else monitored + pkg
            Prefs.setMonitored(ctx, monitored)
        }

        if (showApps) AlertDialog(
            onDismissRequest = { showApps = false },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("OK") } },
            title = { Text("Applications surveillées") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps) { (label, pkg) ->
                        Row(Modifier.fillMaxWidth().clickable { toggle(pkg) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pkg in monitored, onCheckedChange = { toggle(pkg) })
                            Text(label)
                        }
                    }
                }
            })

        LazyColumn(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Agent Shield", style = MaterialTheme.typography.headlineMedium) }
            item { Text(if (listenerOn) "✅ Surveillance des notifications active" else "❌ Accès aux notifications désactivé") }
            if (!listenerOn) item {
                Button(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Activer l'accès aux notifications")
                }
            }
            item { Text(if (a11yOn) "✅ Vérification des transferts Wave active" else "❌ Vérification des transferts désactivée") }
            if (!a11yOn) item {
                Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Activer la vérification des transferts")
                }
            }
            item { Button(onClick = { showApps = true }) { Text("Choisir les applications surveillées (${monitored.size})") } }

            item { Text("IA locale (100 % sur le téléphone)", style = MaterialTheme.typography.titleMedium) }
            item { Text(if (modelOn) "✅ Modèle installé" else "❌ Aucun modèle installé") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { pickModel.launch(arrayOf("*/*")) }) { Text("Importer le modèle") }
                    if (modelOn) OutlinedButton(enabled = !busy, onClick = { testModel() }) { Text("Tester") }
                }
            }
            if (modelOn) item {
                OutlinedButton(enabled = !busy, onClick = {
                    LocalLlm.delete(ctx); modelOn = false; llmStatus = "Modèle supprimé."
                }) { Text("Supprimer le modèle") }
            }
            if (llmStatus.isNotEmpty()) item { Text(llmStatus) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = llmOn, onCheckedChange = { llmOn = it; Prefs.setLlm(ctx, it) })
                    Spacer(Modifier.width(8.dp)); Text("Analyse IA des messages (tri et arnaques)")
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = tts, onCheckedChange = { tts = it; Prefs.setTts(ctx, it) })
                    Spacer(Modifier.width(8.dp)); Text("Lecture vocale des messages importants")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = diag, onCheckedChange = { diag = it; Prefs.setDiag(ctx, it) })
                    Spacer(Modifier.width(8.dp)); Text("Mode diagnostic (texte des écrans Wave)")
                }
            }
            item {
                OutlinedTextField(value = vip, onValueChange = { vip = it; Prefs.setVipRaw(ctx, it) },
                    label = { Text("Expéditeurs prioritaires (séparés par des virgules)") },
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            AlertManager.fire(ctx, WaveRuleEngine.analyze("Test : retrait de 10 000 F"))
                        }, 5000)
                    }) { Text("Test alerte (5 s)") }
                    OutlinedButton(onClick = {
                        SpeechManager.speak(ctx, TransferParser.message("5000", "Paul Kouassi", "0700000000"))
                    }) { Text("Test voix") }
                }
            }
            item { Text("Derniers événements (chiffrés localement)", style = MaterialTheme.typography.titleMedium) }
            if (events.isEmpty()) item { Text("Aucun événement enregistré pour l'instant.") }
            items(events) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${fmt.format(Date(e.ts))} · ${e.source} · ${e.risk}", style = MaterialTheme.typography.labelMedium)
                        Text(e.text)
                    }
                }
            }
        }
    }
}