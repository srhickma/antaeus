package io.pleo.antaeus.app

import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.InvoiceTable
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import java.math.BigDecimal
import kotlin.random.Random

/**
 * This will create all schemas and setup initial data
 */
internal fun setupInitialData(dal: AntaeusDal) {
    dal.inTransaction {
        val tables = arrayOf(InvoiceTable, CustomerTable)

        addLogger(StdOutSqlLogger)
        SchemaUtils.drop(*tables)
        SchemaUtils.create(*tables)
    }

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
