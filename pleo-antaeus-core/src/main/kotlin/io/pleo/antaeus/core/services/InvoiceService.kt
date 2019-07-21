package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

/**
 * Implements endpoints related to invoices.
 */
class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetchOutstanding(): Iterable<Invoice> = dal.fetchOutstandingInvoices()

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun setStatus(invoice: Invoice, status: InvoiceStatus): Invoice {
        dal.setInvoiceStatus(invoice.id, status)
        return dal.fetchInvoice(invoice.id)!!
    }

    fun deleteInvoice(invoice: Invoice) {
        dal.deleteInvoice(invoice.id)
    }
}
