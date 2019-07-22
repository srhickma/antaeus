package io.pleo.antaeus.core.services

import dagger.Component
import io.pleo.antaeus.core.TestModule
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject
import javax.inject.Singleton

internal class CustomerServiceTest {
    @Inject
    lateinit var customerService: CustomerService

    @Inject
    lateinit var dal: AntaeusDal

    @BeforeEach
    fun setup() {
        DaggerCustomerServiceTestComponent.create().inject(this)
    }

    @Test
    fun `customer found`() {
        val customer1 = dal.createCustomer(Currency.USD)!!
        val customer2 = dal.createCustomer(Currency.USD)!!

        assertEquals(customer1, customerService.fetch(customer1.id))
        assertEquals(customer2, customerService.fetch(customer2.id))
    }

    @Test
    fun `customer not found`() {
        assertThrows<CustomerNotFoundException> {
            customerService.fetch(404)
        }
    }

    @Test
    fun `fetch all customers`() {
        assertTrue(customerService.fetchAll().isEmpty())

        val customer1 = dal.createCustomer(Currency.USD)!!
        val customer2 = dal.createCustomer(Currency.USD)!!

        val customers = customerService.fetchAll()
        assertEquals(2, customers.size)
        assertTrue(customers.contains(customer1))
        assertTrue(customers.contains(customer2))
    }
}

@Singleton
@Component(modules = [TestModule::class])
private interface CustomerServiceTestComponent {
    fun inject(test: CustomerServiceTest)
}
