# Payment Gateway Challenge — Java

A Java 17 / Spring Boot payment gateway that validates card-payment requests,
submits them to a bank simulator, stores the result in memory, and retrieves
payments by ID. Merchant responses contain only the last four card digits and
never include the CVV.

This repository is based on the [Checkout.com recruitment challenge](https://github.com/cko-recruitment/).
Keep the supplied `imposters/` configuration and `.editorconfig` unchanged.

## Project structure

```text
src/main/java/com/checkout/payment/gateway/
  PaymentGatewayApplication.java    Spring Boot entry point
  controller/                      POST and GET payment endpoints
  service/                         Payment processing, outcomes, and retrieval
  bank/                            HTTP client for the bank simulator
  model/                           Merchant and bank request/response objects
  repository/                      In-memory payment storage
  exception/                       Application exception and HTTP error handler
  configuration/                   RestTemplate and connection/read timeouts
  enums/                           Payment statuses
src/main/resources/
  application.properties           Gateway port and bank URL
src/test/java/com/checkout/payment/gateway/
  controller/                      HTTP integration and validation tests
  service/                         Business behavior tests with mocked dependencies
  bank/                            Bank HTTP contract and response-mapping tests
  repository/                      Storage and duplicate-ID tests
scripts/integration-test.sh         Bash/curl/jq live HTTP tests
imposters/bank_simulator.ejs         Supplied simulator behavior
docker-compose.yml                 Bank simulator container setup
build.gradle                       Dependencies and test configuration
gradlew / gradlew.bat               Gradle wrappers; no separate Gradle install needed
```

## Run the service

Requirements:

- JDK 17, with `JAVA_HOME` pointing to that installation.
- Docker with Compose, running locally.
- Bash 3.2+, curl, and jq 1.6+ for the cross-platform integration script,
  or Postman for manual API checks.

Run all commands from the repository root.

1. Start the bank simulator:

   ```shell
   docker compose up -d
   docker compose ps
   ```

   Bank requests use `http://localhost:8080/payments`. The simulator management
   interface is on port `2525`.

2. Start the gateway in a separate terminal and leave it running:

   Windows PowerShell:

   ```powershell
   .\gradlew.bat bootRun
   ```

   macOS/Linux:

   ```shell
   ./gradlew bootRun
   ```

   Wait for the application to report that it started on port `8090`. The first
   Gradle run needs internet access to download dependencies.

3. Open [Swagger UI](http://localhost:8090/swagger-ui/index.html) to explore the API.

Configuration lives in `src/main/resources/application.properties`:

| Setting | Default |
| --- | --- |
| `server.port` | `8090` |
| `bank.simulator.payments-url` | `http://localhost:8080/payments` |

Use Ctrl+C in the gateway terminal to stop it. Stop the simulator when finished
with `docker compose down`. Restarting the gateway clears all stored payments.

## API behavior

| Request / outcome | Gateway response |
| --- | --- |
| `POST /api/payment`, bank authorizes | `201`, `Authorized`, generated payment ID |
| `POST /api/payment`, bank declines | `201`, `Declined`, generated payment ID |
| Exception during the bank call, including bank `503` | `201`, `Declined`, generated payment ID |
| Invalid payment input | `400`; no payment ID |
| Duplicate payment ID cannot be stored | `500`, support error message; no automatic retry |
| `GET /api/payment/{id}`, stored ID | `200`, stored payment |
| `GET /api/payment/{id}`, unknown UUID | `404` |
| `GET /api/payment/{id}`, malformed UUID | `400` |

Card numbers must contain 14–19 digits; CVVs must contain 3–4 digits. Supported
currencies are USD, EUR, and GBP. Amounts are positive integers in minor currency
units. The expiry date must be the current month or later.

The simulator authorizes cards ending in 1, 3, 5, 7, or 9; declines those ending
in 2, 4, 6, or 8; and returns HTTP `503` for those ending in 0. Under this MVP's
policy, the service stores a decline when the bank call throws an exception.
`BankPaymentResponse.authorized` is primitive `boolean`, so missing or null bank
authorization values default to `false` with the current JSON configuration.

This policy means a reported decline after a timeout does not prove the bank did
not authorize the payment. Durable storage, authentication, idempotency, and
payment reconciliation are outside the current MVP scope.

## Payment request flows

### POST `/api/payment`: create a payment

```text
Client → Controller → Service → Bank client → Bank simulator
                         ↓
                  In-memory repository
                         ↓
Client ← Controller ← Payment result
```

1. The client sends JSON containing the card number, expiry month/year, currency,
   amount, and CVV.
2. `PaymentGatewayController` validates the request. Invalid input returns `400`
   before the service calls the bank.
3. `PaymentGatewayService` generates a payment UUID and builds a bank request,
   combining the expiry month/year into the bank's expiry-date field.
4. `SimulatorBankClient` sends that request to the simulator's `/payments`
   endpoint. An authorized bank response becomes `Authorized`; a declined
   response or an exception during the bank call becomes `Declined`.
5. The service creates a result containing the ID, status, last four card
   digits, expiry month/year, currency, and amount. It stores this result in
   `PaymentsRepository`, without the full card number or CVV.
6. After storage succeeds, the controller returns HTTP `201` and the result.
   `201` means a payment result was created; check `status` to learn whether
   the bank authorized it. A duplicate-ID storage failure returns `500` instead.

### GET `/api/payment/{id}`: retrieve a payment

```text
Client → Controller → Service → In-memory repository
Client ← Controller ← Service ← Stored payment
```

1. The client puts the ID returned by POST into the URL.
2. The controller parses the ID as a UUID. A malformed UUID returns `400`.
3. The service looks up that UUID in `PaymentsRepository`.
4. If found, the controller returns HTTP `200` with the stored payment fields.
   If absent, the exception handler returns `404`.

GET does not call the bank or create another payment. Restarting the gateway
clears the repository, so IDs from a previous run will return `404`.

### Storage failures and production recovery

If the repository encounters a duplicate payment ID, it throws
`PaymentStorageException` carrying that ID. The HTTP exception handler logs the
failure and returns a controlled `500` response with a support message. The
current response body does not include the payment ID. Since the bank call
occurs before storage, a storage failure does not prove the bank declined the
payment.

Neither the bank request nor the save is automatically retried. The current
repository throws for duplicate IDs, which a repeated insert would not repair.
Clients must not automatically resubmit after this error: each new submission
generates another payment ID and calls the bank again. The API does not yet
support idempotency keys. This handling reports the failure; it does not recover
the payment or guarantee protection against duplicate submissions.

A production extension would commit a pending payment and stable operation key
in durable storage before requesting authorization, then store the provider's
reference and result. Temporary storage failures could retry the same update
with bounded backoff. Provider-supported idempotency, status queries, verified
webhooks, and reconciliation would resolve interrupted operations. A database
transaction alone cannot roll back a bank authorization. Never persist CVVs as
part of a recovery record.

The simulator has no capture, reversal, status-lookup, or idempotency contract.
These operations are therefore not implemented here. With a real provider,
manual capture should operate on the recorded authorization when appropriate
for the business, with its own idempotency and recovery handling. An abandoned
authorization may need cancellation/reversal; a captured payment may need a refund.

## Automated tests

Use JDK 17 and run the following from the repository root. Wait for Gradle to
finish; a successful run ends with `BUILD SUCCESSFUL`.

Windows:

```powershell
.\gradlew.bat test
```

macOS/Linux:

```shell
./gradlew test
```

These tests do not require Docker or a running gateway. The controller suite
loads the application with intercepted bank HTTP calls; the other suites test
the service, bank client, and repository directly. They cover validation,
masking, retrieval, bank outcomes and failures, JSON mapping, and duplicate IDs.

The HTML report is generated at `build/reports/tests/test/index.html`.

## Live integration tests

These tests use actual HTTP connections to the running gateway and Docker bank
simulator. They complement the automated tests above, which replace the bank.

### Integration test with the script

First follow **Run the service** above: start the Docker bank simulator and leave
the gateway running on port `8090`. Open a third terminal for the tests.

On Windows, open **Git Bash from the Start menu** and move to the repository
folder. For this checkout:

```bash
cd /d/Project/Payment/payment-gateway-challenge-java
```

On Linux/macOS, open Terminal at the repository root. Check the dependencies
in the same terminal that will run the script:

```bash
curl --version
jq --version
```

Both commands should print version information. Then run:

```bash
bash scripts/integration-test.sh
```

The script requires `curl` and `jq` on PATH and reports missing dependencies.
For installation options, see the [official jq downloads](https://jqlang.org/download/).
For example, install curl and jq on Ubuntu/Debian with
`sudo apt-get install curl jq`, or install jq on macOS with `brew install jq`.
On Windows, install Git Bash and make the Windows jq executable available on its
PATH; `winget install jqlang.jq` is one option. Open a new Git Bash terminal after
installation. WSL uses its own Linux packages and must be able to reach the
gateway and simulator at the chosen URLs.

If the script reports `Missing dependency: jq`, check `jq --version` in the
test terminal. After installing jq, close and reopen the terminal so it picks
up PATH changes. Installing Windows jq does not install the Linux package in
WSL. Opening Git Bash explicitly also avoids accidentally starting WSL through
the Windows `bash` command.

To use a different gateway port, edit `base_url` near the top of the script.
Calling it with `bash` avoids needing an executable permission bit.
Keep `.sh` files saved with LF line endings when sharing across operating systems.

The script uses synthetic card data and a future expiry year. It checks:

| Scenarios | Expected result |
| --- | --- |
| 1. Submit a card ending in 1 | `201`, `Authorized`, last four digits `0001`, and a payment ID |
| 2. Retrieve the ID from test 1 | `200`, with the same payment fields returned by POST |
| 3. Submit a card ending in 2 | `201`, `Declined` |
| 4. Submit an amount of zero | `400`, no payment ID |
| 5. Retrieve an unknown UUID | `404` |

### How to read the script

Each numbered test follows the same steps: prepare input, send an HTTP request
with `curl`, then compare the response with the expected result.

- `payment` contains a valid JSON request. Tests 3 and 4 use `jq` to change one
  field while keeping the other fields valid.
- `curl -o "$response_file"` saves the response body. `-w '%{http_code}'`
  returns the HTTP status separately into `http_status`.
- `jq` reads JSON fields. For example, `.status` reads the payment status and
  `.id` reads the ID needed for the next GET request.
- `assert_equal actual expected description` stops with a helpful failure
  message when the actual result differs from the expected one.

Test 1 exercises the gateway, bank client, simulator, and storage. Test 2 reads
the saved result back through the public API. Test 3 checks a bank decline;
HTTP `201` means a payment result was created, even though it was declined.
Tests 4 and 5 check validation and missing-payment behavior. Comments beside
each test explain its purpose and the command options.

GET retrieves from the in-memory repository, so a matching GET response verifies
that each created payment was stored. Invalid requests return no ID; this public
API cannot enumerate the repository to independently prove the absence of an
invalid record. Automated validation tests additionally verify that invalid
requests never reach the bank.

A successful run prints:

```text
All 5 end-to-end tests passed.
```

Any failed assertion or connection error stops the script with a nonzero exit
code. Each successful run creates two in-memory payments, which remain
retrievable until the gateway restarts. No repository-clearing operation is used.

## Integration test with Postman

Start the simulator and gateway using **Run the service** above. Keep both
running throughout these checks. In Postman, use **No Auth** for these local
requests. POST requests need `Content-Type: application/json`; selecting the
JSON body format normally sets this header. GET requests need no body.

1. Create a POST request to `http://localhost:8090/api/payment`, choose
   **Body → raw → JSON**, and send this synthetic request. Keep the expiry year
   current or in the future:

   ```json
   {
     "card_number": "4111111111110001",
     "expiry_month": 12,
     "expiry_year": 2030,
     "currency": "USD",
     "amount": 100,
     "cvv": "123"
   }
   ```

2. Expect HTTP `201`, status `Authorized`, and last four digits `0001`. Copy the
   returned `id`; send GET to `http://localhost:8090/api/payment/{id}` with that
   value. Expect HTTP `200` and matching payment fields.
3. Change the card number to `4111111111110002`. Expect `201`, `Declined`;
   retrieve its new ID and confirm the stored decline.
4. Change the card number to `4111111111110000`. The bank returns `503`, while
   the gateway should return `201`, `Declined`. Retrieve and verify this record too.
5. Restore the authorized card, then try one invalid change at a time: amount
   `0`, currency `JPY`, month `13`, card number `123`, or remove `cvv`. Each
   should return `400` without a payment ID.
6. GET a new random UUID that was never submitted; expect `404`.
7. GET `http://localhost:8090/api/payment/not-a-uuid`; expect `400` because the
   ID is not a valid UUID.

These checks exercise real HTTP requests through the running application.
For a successful payment, verify that the response includes the ID, status,
`cardNumberLastFour`, expiry month/year, currency, and amount, and excludes the
full card number and CVV. POST and GET should return matching payment fields.

Last verified on 2026-10-17: 75 automated tests passed.
