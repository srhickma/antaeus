package io.pleo.antaeus.core

import dagger.Module
import dagger.Provides
import io.pleo.antaeus.core.external.PaymentProvider
import org.jetbrains.exposed.sql.Database
import javax.inject.Singleton

@Module
internal class TestModule {
    @Singleton
    @Provides
    fun provideDb(): Database {
        return Database.connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
    }

    @Singleton
    @Provides
    fun providePaymentProvider(paymentProvider: FakePaymentProvider): PaymentProvider {
        return paymentProvider
    }
}
