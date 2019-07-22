## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|
|       Packages containing the main() application. 
|       This is where all the dependencies are instantiated.
|
├── pleo-antaeus-core
|
|       This is where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|
|       Module interfacing with the database. Contains the models, mappings and access layer.
|
├── pleo-antaeus-models
|
|       Definition of models used throughout the application.
|
├── pleo-antaeus-rest
|
|        Entry point for REST API. This is where the routes are defined.
└──
```

## Instructions
Fork this repo with your solution. We want to see your progression through commits (don’t commit the entire solution in 1 step) and don't forget to create a README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

Happy hacking 😁!

## How to run
```
./docker-start.sh
```

## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library

## Design Decisions
### Dependency Management
I chose to use dependency injection for this challenge (specifically [Dagger 2](https://github.com/google/dagger)) to manage dependencies between different services and resources. This makes initialization simpler, and the upfront setup cost pays off tenfold once more services are (inevitably) added down the line.

My choice to use DI also made integration testing much simpler, as it allowed me to inject different services as opposed to mocking them, which helps keep tests closer to the final application and overall less verbose. This also allowed me to inject fakes (e.g. `FakePaymentProvider`) to simulate external resources during testing, without having to use complex mocks. If DI had not been used, integration testing would be quite difficult, and the only feasible alternative would be to unit test services with all non-trivial dependencies mocked, which is clearly not ideal (doesn't catch integration errors, complex mocks, breaks encapsulation, etc.).

### Time Keeping
To manage the main goal of charging invoices, I decided to roll my own `CronJob` implementation, as well as a custom `Clock` implementation as a thin wrapper around the Java-Time clock. The main reason for this was to make testing simpler, by allowing a clock to be programmatically advanced by an arbitrary duration, thus making long running cron schedules easily testable. The custom `CronJob` implementation was needed in order to account for the fact that the current time could be arbitrarily advanced (so a simple waiting thread would not work). To handle the schedules themselves, I decided to use the [skedule](https://github.com/shyiko/skedule) library. This library fit the needs of the challenge perfectly, so there was no need to re-implement something similar.

### Billing Service
The billing service was implemented using a `CronJob` which triggers an operation to charge for all pending invoices.
#### Thread Safety
All (private) global variables used by the billing service are either immutable or atomic. An atomic guard is used to determine if multiple billing services are running (which DI + `@Singleton` should prevent anyways), and any existing charger cron-jobs are stopped whenever a new one is created. All of the actual charging code is self-contained, aside from accessing constant injected dependencies, so thread synchronization is not needed here (dependencies should synchronize internally if needed).

#### Transaction Safety
Each pending invoice is processes individually, and NOT inside a transaction. Since the api request to the `PaymentCharger` cannot be rolled back like changes to the database, making such requests in a transaction could lead to invalid state. For example, if an invoice is paid and an unchecked exception occurs after the api request, the money will still be withdrawn, however the change in state from `PENDING` to `PAID` would be rolled back, meaning the invoice would be charged again later.

On a similar note, I decided to preemptively set invoices as `PAID` before sending the charge request, and then setting them back to `UNPAID` afterwards if the charge failed. This ensures that in the event of unexpected errors, we always mark the invoice as `PAID`. It is _usually_ better to err on the side of not charging a customer than charging them twice, as errors like this should happen very rarely.

#### Multi-server Safety
For this exercise, I have assumed that there is only one app running at a time for a particular database. If there was a need to run multiple servers at once (e.g. behind a loadbalancer), then some method would be needed to synchronize the charging of invoices across all servers. A simple and flexible way to do this would be to have a configuration flag passed on server startup (or in a config file) determine whether the server should charge invoices, and then only pass this flag to one server.

### Testing
To test the `Clock` and `CronJob` I used simple unit tests, since these objects are fairly self contained. To test the three services I used integration tests based on injection (see DI section above). The two noteworthy bits of my tests are the usage of `Clock::advance` to thoroughly test the billing schedule, and my use of a fake `PaymentProvider` to test/observe the behaviour of the billing service under various conditions.
