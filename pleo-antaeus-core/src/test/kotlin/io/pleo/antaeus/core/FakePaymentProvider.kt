package io.pleo.antaeus.core

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FakePaymentProvider @Inject constructor(
        private val dal: AntaeusDal
) : PaymentProvider {
    private val balances: HashMap<Int, BigDecimal> = HashMap()
    private var networkErrorNext = false

    @Synchronized
    override fun charge(invoice: Invoice): Boolean {
        if (networkErrorNext) {
            networkErrorNext = false
            throw NetworkException()
        }

        val customer = dal.fetchCustomer(invoice.customerId)
                ?: throw CustomerNotFoundException(invoice.customerId)

        if (customer.currency != invoice.amount.currency) {
            throw CurrencyMismatchException(
                    invoiceId = invoice.id,
                    customerId = customer.id
            )
        }

        val currentBalance = balances.getOrDefault(customer.id, BigDecimal(0))
        if (currentBalance < invoice.amount.value) {
            return false
        }

        balances[customer.id] = currentBalance.minus(invoice.amount.value)
        return true
    }

    @Synchronized
    fun addBalance(customer: Customer, balance: BigDecimal) {
        val newBalance = balances.getOrDefault(customer.id, BigDecimal(0)).add(balance)
        balances[customer.id] = newBalance
    }

    @Synchronized
    fun getBalance(customer: Customer): BigDecimal {
        return balances.getOrDefault(customer.id, BigDecimal(0))
    }

    @Synchronized
    fun setNetworkErrorNext(networkErrorNext: Boolean) {
        this.networkErrorNext = networkErrorNext
    }
}
