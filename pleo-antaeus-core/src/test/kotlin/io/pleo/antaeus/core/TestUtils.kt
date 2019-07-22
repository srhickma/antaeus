package io.pleo.antaeus.core

import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.random.Random

internal class TestUtils @Inject constructor(private val dal: AntaeusDal) {
    fun setupInitialData() {
        val customers = (1..100).mapNotNull {
            dal.createCustomer(
                    currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
            )
        }

        customers.forEach { customer ->
            (1..10).forEach {
                dal.createInvoice(
                        amount = Money(
                                value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                                currency = customer.currency
                        ),
                        customer = customer,
                        status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
                )
            }
        }
    }

}
