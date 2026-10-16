# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Current MVP

The current implementation supports submitting a card payment through the bank
simulator and retrieving its result by ID. Authorized and declined payments are
stored in memory; restarting the application clears them. Responses expose only
the last four card digits and never include the CVV.

- `POST /api/payment`: submit a payment; returns `201` with an `Authorized` or
  `Declined` result.
- `GET /api/payment/{id}`: retrieve a payment; returns `404` for an unknown ID.
- Invalid input returns `400` before contacting the bank. Supported currencies
  are USD, EUR, and GBP; amounts are positive integers in minor currency units.
- Bank unavailability returns `503`; a bank rejection of the gateway's request
  returns `500`.

### Run tests

Use JDK 17 and run `./gradlew test` (Windows: `.\gradlew.bat test`). The tests
replace the bank HTTP responses locally, so Docker is not required. They cover
submission and retrieval, authorized and declined outcomes, masking including
leading zeros, invalid and missing fields, expired cards, and bank errors.
The HTML report is generated at `build/reports/tests/test/index.html`.

### Run locally

1. Start Docker and run `docker compose up -d` to start the bank simulator.
2. Run `./gradlew bootRun` (Windows: `.\gradlew.bat bootRun`).
3. Open [Swagger UI](http://localhost:8090/swagger-ui/index.html).

Example request body (use a current or future expiry date):

```json
{
  "card_number": "4111111111111111",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "USD",
  "amount": 100,
  "cvv": "123"
}
```

The simulator authorizes cards ending in an odd digit, declines those ending in
2, 4, 6, or 8, and returns `503` for cards ending in 0.

This MVP uses one configured bank and in-memory storage. Durable storage,
authentication, idempotency, and payment reconciliation are outside its current
scope.
