package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Implements the data access layer (DAL).
 * This file implements the database queries used to fetch and insert rows in our database tables.
 *
 * See the `mappings` module for the conversions between database rows and Kotlin objects.
 */
class AntaeusDal(private val db: Database) {
    fun <T> inTransaction(statement: Transaction.() -> T): T = transaction(db, statement)

    fun fetchInvoice(id: Int): Invoice? {
        return inTransaction {
            InvoiceTable
                    .select { InvoiceTable.id.eq(id) }
                    .firstOrNull()
                    ?.toInvoice()
        }
    }

    fun fetchOutstandingInvoices(): Iterable<Invoice> {
        return inTransaction {
            InvoiceTable
                    .select { InvoiceTable.status.eq(InvoiceStatus.PENDING.toString()) }
                    .map { it.toInvoice() }
        }
    }

    // TODO(shane) use iterables instead of lists.
    fun fetchInvoices(): List<Invoice> {
        return inTransaction {
            InvoiceTable
                    .selectAll()
                    .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        return inTransaction {
            InvoiceTable
                    .insert {
                        it[this.value] = amount.value
                        it[this.currency] = amount.currency.toString()
                        it[this.status] = status.toString()
                        it[this.customerId] = customer.id
                    } get InvoiceTable.id
        }?.let { fetchInvoice(it) }
    }

    fun setInvoiceStatus(id: Int, status: InvoiceStatus) {
        inTransaction {
            InvoiceTable
                    .update({ InvoiceTable.id.eq(id) }) {
                        it[this.status] = status.toString()
                    }
        }
    }

    fun deleteInvoice(id: Int) {
        inTransaction {
            InvoiceTable.deleteWhere { InvoiceTable.id.eq(id) }
        }
    }

    fun fetchCustomer(id: Int): Customer? {
        return inTransaction {
            CustomerTable
                    .select { CustomerTable.id.eq(id) }
                    .firstOrNull()
                    ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return inTransaction {
            CustomerTable
                    .selectAll()
                    .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        return inTransaction {
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }?.let { fetchCustomer(it) }
    }
}
