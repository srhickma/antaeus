package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import mu.KotlinLogging
import javax.inject.Inject

private val log = KotlinLogging.logger {}

/**
 * Configures the rest app along with basic exception handling and URL endpoints.
 */
class AntaeusRest @Inject constructor(
        private val invoiceService: InvoiceService,
        private val customerService: CustomerService
) : Runnable {
    override fun run() {
        app.start(7000)
    }

    private val app = Javalin
            .create()
            .apply {
                exception(EntityNotFoundException::class.java) { _, ctx ->
                    ctx.status(404)
                }
                exception(Exception::class.java) { e, _ ->
                    log.error(e) { "Internal server error" }
                }
                error(404) { ctx -> ctx.json("not found") }
            }

    init {
        app.routes {
            path("rest") {
                get("health") {
                    it.json("ok")
                }

                path("v1") {
                    path("invoices") {
                        get {
                            it.json(invoiceService.fetchAll())
                        }

                        get(":id") {
                            it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                        }
                    }

                    path("customers") {
                        get {
                            it.json(customerService.fetchAll())
                        }

                        get(":id") {
                            it.json(customerService.fetch(it.pathParam("id").toInt()))
                        }
                    }
                }
            }
        }
    }
}
