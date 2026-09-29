package ci.agent.shield

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var listenerOn by mutableStateOf(false)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @Composable
    private fun Screen() {
        val events by AppDb.get(this).dao().recent().collectAsState(emptyList())
        val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }
        Column(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Agent Shield", style = MaterialTheme.typography.headlineMedium)
            Text(if (listenerOn) "✅ Surveillance active" else "❌ Accès aux notifications désactivé")
            if (!listenerOn) Button(onClick = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Activer l'accès aux notifications") }
            OutlinedButton(onClick = {
                AlertManager.fire(this@MainActivity, WaveRuleEngine.analyze("Test : retrait de 10 000 F"))
            }) { Text("Tester l'alerte sonore") }
            Text("Derniers événements (chiffrés localement)", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}
