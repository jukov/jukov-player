package info.jukov.player

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class JukovTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(
        classLoader,
        TestJukovApplication::class.java.name,
        context,
    )
}
