package io.pleo.antaeus.core.services

import com.github.shyiko.skedule.Schedule
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private const val CHARGER_BATCH_SIZE = 100

class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private val dal: AntaeusDal,
        private val chargingSchedule: Schedule = Schedule.parse("1 of month 10:00")
) {
    init {
        // TODO(shane) start chron job using the charging schedule.
    }

    // TODO(shane) add appropriate locking (multi-thread? multi-server? etc.).
    private fun chargeInvoices() {
        // Fetch in a separate transaction from payment charger api requests to
        // avoid transaction length proportional to # of invoices. This is especially
        // important since we are making a network request for every invoice.
        val invoices = invoiceService.fetchOutstanding().map { it.id }.toMutableList()

        while (invoices.isNotEmpty()) {
            dal.inTransaction {
                for (i in 1..CHARGER_BATCH_SIZE) {
                    invoices.popBack()
                    val invoice = invoiceService.fetch(invoices.popBack() ?: break)

                    if (invoice.status != InvoiceStatus.PENDING) {
                        // Invoice status has changed since time of fetch, it is not safe to charge.
                        continue
                    }

                    tryChargeInvoice(invoice)
                }
            }
        }
    }

    private fun tryChargeInvoice(invoice: Invoice) {
        val customer = dal.fetchCustomer(invoice.customerId)

        // Pre-check conditions to avoid exceptions during normal operation.
        if (customer == null) {
            log.info { "Removing orphaned invoice ${invoice.id}" }
            invoiceService.deleteInvoice(invoice)
            return
        }
        if (customer.currency != invoice.amount.currency) {
            log.info { "Currency mismatch for invoice ${invoice.id}" }
            return
        }

        try {
            // Set status before making request, since request cannot be rolled back.
            invoiceService.setStatus(invoice, InvoiceStatus.PAID)

            if (!paymentProvider.charge(invoice)) {
                log.info { "Insufficient funds to charge invoice ${invoice.id}" }
                invoiceService.setStatus(invoice, InvoiceStatus.PENDING)
            }
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
            log.error(e) { "A network error occurred while charging invoice ${invoice.id}" }
        }
    }
}

fun <T> MutableList<T>.popBack(): T? {
    if (isEmpty()) {
        return null
    }
    return removeAt(size - 1)
}
