package io.pleo.antaeus.core.services

import dagger.Component
import io.pleo.antaeus.core.TestModule
import io.pleo.antaeus.core.TestUtils.Companion.randomMoney
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject
import javax.inject.Singleton

internal class InvoiceServiceTest {
    @Inject
    lateinit var invoiceService: InvoiceService

    @Inject
    lateinit var dal: AntaeusDal

    @BeforeEach
    fun setup() {
        DaggerInvoiceServiceTestComponent.create().inject(this)
    }

    @Test
    fun `customer found`() {
        val customer = dal.createCustomer(Currency.USD)!!

        val invoice1 = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        val invoice2 = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        Assertions.assertEquals(invoice1, invoiceService.fetch(invoice1.id))
        Assertions.assertEquals(invoice2, invoiceService.fetch(invoice2.id))
    }

    @Test
    fun `customer not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `fetch all customers`() {
        val customer = dal.createCustomer(Currency.USD)!!

        Assertions.assertTrue(invoiceService.fetchAll().isEmpty())

        val invoice1 = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        val invoice2 = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        val invoices = invoiceService.fetchAll()
        Assertions.assertEquals(2, invoices.size)
        Assertions.assertTrue(invoices.contains(invoice1))
        Assertions.assertTrue(invoices.contains(invoice2))
    }
}

@Singleton
@Component(modules = [TestModule::class])
private interface InvoiceServiceTestComponent {
    fun inject(test: InvoiceServiceTest)
}
