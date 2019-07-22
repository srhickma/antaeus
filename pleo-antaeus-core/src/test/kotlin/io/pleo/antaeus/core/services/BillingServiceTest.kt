package io.pleo.antaeus.core.services

import com.github.shyiko.skedule.Schedule
import dagger.Component
import io.pleo.antaeus.core.FakePaymentProvider
import io.pleo.antaeus.core.TestModule
import io.pleo.antaeus.core.TestUtils
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.time.Clock
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.InvoiceTable
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

internal class BillingServiceTest {
    @Inject
    lateinit var billingService: BillingService

    @Inject
    lateinit var testUtils: TestUtils

    @Inject
    lateinit var dal: AntaeusDal

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var paymentProvider: PaymentProvider
    lateinit var fakePaymentProvider: FakePaymentProvider

    private val chargingSchedule = Schedule.parse("1 of month 10:00")

    @BeforeEach
    fun setup() {
        DaggerTestComponent.create().inject(this)
        fakePaymentProvider = paymentProvider as FakePaymentProvider
        testUtils.setupInitialData()
        billingService.startCronCharger()
    }

    @Test
    fun `billing schedule`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)

        clock.advanceTo(chargingSchedule.next(clock.currentTime().minusDays(1)))

        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        clock.advance(Duration.ofDays(2).plusHours(1))
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice.id)!!.status)

        assertEquals(BigDecimal.ZERO, fakePaymentProvider.getBalance(customer))

        // Manually reset invoice status to test again.
        dal.setInvoiceStatus(invoice.id, InvoiceStatus.PENDING)
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        clock.advanceTo(chargingSchedule.next(clock.currentTime().minusDays(1)))

        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        clock.advance(Duration.ofDays(2).plusHours(1))
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice.id)!!.status)

        assertEquals(BigDecimal.ZERO, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `simple billing`() {
        val customer = dal.createCustomer(Currency.USD)!!

        val paid1 = dal.createInvoice(randomMoney(Currency.USD), customer, InvoiceStatus.PAID)!!
        val paid2 = dal.createInvoice(randomMoney(Currency.USD), customer, InvoiceStatus.PAID)!!
        val pending1 = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        val pending2 = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        val initialBalance = paid1.amount.value
                .plus(paid2.amount.value)
                .plus(pending1.amount.value)
                .plus(pending2.amount.value)

        // Add enough funds to pay for all invoices.
        fakePaymentProvider.addBalance(customer, initialBalance)

        clock.advanceToNextBilling()

        // All invoices are now paid.
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(paid1.id)!!.status)
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(paid2.id)!!.status)
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(pending1.id)!!.status)
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(pending2.id)!!.status)

        val expectedBalance = initialBalance
                .minus(pending1.amount.value)
                .minus(pending2.amount.value)

        // Check that the customer was only charged for pending invoices.
        assertEquals(expectedBalance, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `multi-customer billing`() {
        val customer1 = dal.createCustomer(Currency.USD)!!
        val customer2 = dal.createCustomer(Currency.GBP)!!

        val invoice1 = dal.createInvoice(randomMoney(Currency.USD), customer1)!!
        val invoice2 = dal.createInvoice(randomMoney(Currency.GBP), customer2)!!

        fakePaymentProvider.addBalance(customer1, invoice1.amount.value)
        fakePaymentProvider.addBalance(customer2, invoice2.amount.value)

        clock.advanceToNextBilling()

        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice1.id)!!.status)
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice2.id)!!.status)

        assertEquals(BigDecimal.ZERO, fakePaymentProvider.getBalance(customer1))
        assertEquals(BigDecimal.ZERO, fakePaymentProvider.getBalance(customer2))
    }

    @Test
    fun `many invoices billed`() {
        val customer = dal.createCustomer(Currency.USD)!!

        val invoices: MutableList<Invoice> = mutableListOf()

        for (i in 1..1000) {
            val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!
            invoices.add(invoice)
            fakePaymentProvider.addBalance(customer, invoice.amount.value)
        }

        clock.advanceToNextBilling()

        // All invoices have been paid.
        for (invoice in invoices) {
            assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice.id)!!.status)
        }

        // Remaining balance is correct.
        assertEquals(BigDecimal.ZERO, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `multiple services`() {
        // Start a second service.
        BillingService(
                paymentProvider = paymentProvider,
                dal = dal,
                clock = clock
        ).startCronCharger()

        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        // Add enough money to allow charging twice.
        fakePaymentProvider.addBalance(customer, invoice.amount.value)
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        clock.advanceToNextBilling()

        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice.id)!!.status)

        // Customer was only charged once.
        assertEquals(invoice.amount.value, fakePaymentProvider.getBalance(customer))

    }

    @Test
    fun `missing customer`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        // Remove customer before the billing service runs.
        dal.inTransaction { CustomerTable.deleteWhere { CustomerTable.id.eq(customer.id) } }

        var charged = false
        fakePaymentProvider.onCharge(invoice) {
            charged = true
        }

        clock.advanceToNextBilling()

        // Invoice was deleted without being charged.
        assertNull(dal.fetchInvoice(invoice.id))
        assertFalse(charged)
    }

    @Test
    fun `missing customer at payment provider`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        fakePaymentProvider.onCharge(invoice) {
            // Remove customer after billing service runs, but before payment is charged.
            dal.inTransaction { CustomerTable.deleteWhere { CustomerTable.id.eq(customer.id) } }
        }

        clock.advanceToNextBilling()

        // Invoice was not deleted, and not set as paid.
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
    }

    @Test
    fun `currency mismatch`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.GBP), customer)!!
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        var charged = false
        fakePaymentProvider.onCharge(invoice) {
            charged = true
        }

        clock.advanceToNextBilling()

        // Invoice was not charged.
        assertFalse(charged)
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        assertEquals(invoice.amount.value, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `currency mismatch at payment provider`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        fakePaymentProvider.onCharge(invoice) {
            // Update currency right before charging.
            dal.inTransaction {
                CustomerTable.update({ CustomerTable.id.eq(customer.id) }) {
                    it[this.currency] = Currency.GBP.toString()
                }
            }
        }

        clock.advanceToNextBilling()

        // Invoice was not charged.
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        assertEquals(invoice.amount.value, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `insufficient funds`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.GBP), customer)!!

        // Add to low of a balance.
        val initialBalance = invoice.amount.value.minus(BigDecimal.ONE)
        fakePaymentProvider.addBalance(customer, initialBalance)

        clock.advanceToNextBilling()

        // Invoice was not charged.
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        assertEquals(initialBalance, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `network error at payment provider`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        fakePaymentProvider.onCharge(invoice) {
            throw NetworkException()
        }

        clock.advanceToNextBilling()

        // Invoice was not charged.
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        assertEquals(invoice.amount.value, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `retry failure`() {
        val customer = dal.createCustomer(Currency.USD)!!

        // This invoice will fail due to currency mismatch.
        val invoice = dal.createInvoice(randomMoney(Currency.GBP), customer)!!

        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        clock.advanceToNextBilling()

        // Invoice was not charged.
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice.id)!!.status)
        assertEquals(invoice.amount.value, fakePaymentProvider.getBalance(customer))

        // Fix currency mismatch.
        dal.inTransaction {
            InvoiceTable.update({ InvoiceTable.id.eq(invoice.id) }) {
                it[this.currency] = Currency.USD.toString()
            }
        }

        clock.advanceToNextBilling()

        // Invoice was charged.
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice.id)!!.status)
        assertEquals(BigDecimal.ZERO, fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `single failure among many`() {
        val customer = dal.createCustomer(Currency.USD)!!

        // This invoice should succeed.
        val invoice1 = dal.createInvoice(randomMoney(Currency.USD), customer)!!

        // This invoice will fail due to currency mismatch.
        val invoice2 = dal.createInvoice(randomMoney(Currency.GBP), customer)!!

        val initialBalance = invoice1.amount.value.plus(invoice2.amount.value)

        fakePaymentProvider.addBalance(customer, initialBalance)

        clock.advanceToNextBilling()

        // Only first invoice is set as paid.
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice1.id)!!.status)
        assertEquals(InvoiceStatus.PENDING, dal.fetchInvoice(invoice2.id)!!.status)

        // Only amount of first invoice is withdrawn.
        assertEquals(initialBalance.minus(invoice1.amount.value), fakePaymentProvider.getBalance(customer))
    }

    @Test
    fun `failure with uncaught exception`() {
        val customer = dal.createCustomer(Currency.USD)!!
        val invoice = dal.createInvoice(randomMoney(Currency.USD), customer)!!
        fakePaymentProvider.addBalance(customer, invoice.amount.value)

        fakePaymentProvider.onCharge(invoice) {
            throw RuntimeException()
        }

        clock.advanceToNextBilling()

        // Invoice was not charged.
        assertEquals(invoice.amount.value, fakePaymentProvider.getBalance(customer))

        // Invoice was marked as paid, to avoid double-charging.
        assertEquals(InvoiceStatus.PAID, dal.fetchInvoice(invoice.id)!!.status)
    }

    private fun randomMoney(currency: Currency): Money {
        return Money(BigDecimal(Random.nextInt(1, 100000)), currency)
    }

    private fun Clock.advanceToNextBilling() {
        advanceTo(chargingSchedule.next(currentTime()).plusSeconds(1))
    }

    private fun Clock.advanceTo(dateTime: ZonedDateTime) {
        advance(Duration.ofSeconds(ChronoUnit.SECONDS.between(currentTime(), dateTime)))
    }
}

@Singleton
@Component(modules = [TestModule::class])
internal interface TestComponent {
    fun inject(test: BillingServiceTest)
}
