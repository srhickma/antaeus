package io.pleo.antaeus.core.services

import com.github.shyiko.skedule.Schedule
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.time.Clock
import io.pleo.antaeus.core.time.CronJob
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private val log = KotlinLogging.logger {}
private val chargingSchedule = Schedule.parse("1 of month 10:00")
private val cronJobGuard: AtomicBoolean = AtomicBoolean(false)
private val cronJob: AtomicReference<CronJob?> = AtomicReference(null)

@Singleton
class BillingService @Inject constructor(
        private val paymentProvider: PaymentProvider,
        private val dal: AntaeusDal,
        private val clock: Clock
) {
    fun startCronCharger() {
        if (cronJobGuard.getAndSet(true)) {
            // This shouldn't happen in production, due to di, but it is expected in tests.
            log.warn { "Multiple billing service instances present" }
        }

        // Start a new job, and stop any existing job.
        cronJob.getAndSet(CronJob(chargingSchedule, clock) {
            chargeInvoices()
        })?.stop()
    }

    private fun chargeInvoices() {
        // Fetch in a separate transaction from payment charger api requests to
        // avoid transaction length proportional to # of invoices. This is especially
        // important since we are making a network request for every invoice.
        val invoices = dal.fetchOutstandingInvoices().map { it.id }.toMutableList()

        while (invoices.isNotEmpty()) {
            // Get next invoice to charge, or skip if it was deleted.
            val invoice = dal.fetchInvoice(invoices.popBack()!!) ?: continue
            try {
                tryChargeInvoice(invoice)
            } catch (t: Throwable) {
                log.error(t) { "Unexpected error when billing invoice ${invoice.id}" }
            }
        }
    }

    private fun tryChargeInvoice(invoice: Invoice) {
        log.info { "Attempting to bill invoice ${invoice.id}" }

        if (invoice.status != InvoiceStatus.PENDING) {
            log.info { "Invoice status changed since time of fetch, aborting payment of invoice ${invoice.id}" }
            return
        }

        val customer = dal.fetchCustomer(invoice.customerId)

        // Pre-check conditions to avoid exceptions during normal operation.
        if (customer == null) {
            log.info { "Removing orphaned invoice ${invoice.id}" }
            dal.deleteInvoice(invoice.id)
            return
        }
        if (customer.currency != invoice.amount.currency) {
            log.info { "Currency mismatch for invoice ${invoice.id}" }
            return
        }

        // Set status before making request, since request cannot be rolled back.
        // If the api request fails with an uncaught exception, it is better to
        // think the invoice is paid (even if it isn't), than to possibly
        // double-charge a customer.
        dal.setInvoiceStatus(invoice.id, InvoiceStatus.PAID)

        try {
            if (!paymentProvider.charge(invoice)) {
                log.info { "Insufficient funds to charge invoice ${invoice.id}" }
                dal.setInvoiceStatus(invoice.id, InvoiceStatus.PENDING)
            }

            return
        } catch (e: CustomerNotFoundException) {
            log.error(e) {
                "Inconsistent read from payment provider, aborting payment of invoice ${invoice.id}"
            }
        } catch (e: CurrencyMismatchException) {
            log.error(e) {
                "Inconsistent read from payment provider, aborting payment of invoice ${invoice.id}"
            }
        } catch (e: NetworkException) {
            // Invoice may or may not have been charged, this is VERY bad!
            // We will assume it has not been charged, as the error is most likely
            // that the api could not be reached.
            log.error(e) { "A network error occurred while charging invoice ${invoice.id}" }
        }

        // If an exception occurred, reset the invoice status as pending.
        dal.setInvoiceStatus(invoice.id, InvoiceStatus.PENDING)
    }
}

private fun <T> MutableList<T>.popBack(): T? {
    if (isEmpty()) {
        return null
    }
    return removeAt(size - 1)
}
