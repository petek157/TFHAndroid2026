package co.topflighthealth.app

import android.app.Application
import co.topflighthealth.app.api.ApiFactory
import co.topflighthealth.app.api.TfhApi
import co.topflighthealth.app.auth.AuthRepository
import co.topflighthealth.app.auth.TokenStore
import co.topflighthealth.app.data.UserRepository

/** Builds the app's long-lived objects once. Screens get them from [container]. */
class TfhApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val auth = AuthRepository(app, TokenStore(app))
    val api: TfhApi = ApiFactory.create(auth)
    val users = UserRepository(api)
}
