@file:JvmName("Entrypoint")

package io.pleo.antaeus.app

import dagger.Component
import dagger.Module
import dagger.Provides
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.rest.AntaeusRest
import org.jetbrains.exposed.sql.Database
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

fun main() {
    AntaeusApp()
}

class AntaeusApp {
    @Inject
    internal lateinit var billingService: BillingService

    @Inject
    internal lateinit var rest: AntaeusRest

    init {
        DaggerAppComponent.create().inject(this)

        billingService.startCronCharger()
        rest.run()
    }
}

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun inject(app: AntaeusApp)
}

@Module
class AppModule {
    @Singleton
    @Provides
    fun provideDb(): Database {
        return Database.connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
    }

    @Singleton
    @Provides
    fun providePaymentProvider(): PaymentProvider {
        return object : PaymentProvider {
            override fun charge(invoice: Invoice): Boolean {
                return Random.nextBoolean()
            }
        }
    }
}
