package info.jukov.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import info.jukov.player.auth.data.DefaultAuthRepository
import info.jukov.player.auth.data.AuthStorageImpl
import info.jukov.player.auth.data.SubsonicAuthApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val repository = androidx.compose.runtime.remember {
                DefaultAuthRepository(
                    api = SubsonicAuthApi(HttpClient(OkHttp)),
                    storage = AuthStorageImpl(),
                )
            }
            App(repository)
        }
    }
}
