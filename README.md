# Product Information Aggregator

This project collects product information from several services and returns it in a single response. The aggregator gets product details, pricing, and availability. If the customer provides an ID, it also gets their profile. The project includes a `mock-server` for local use. It simulates responses from the upstream services, including their delays and occasional failures.

## 1. Running the application

### Docker Compose

If Docker with Docker Compose is installed, run this command from the project root:

```shell
docker compose up --build
```

Compose will build and start the mock server and the aggregator. The aggregator will be available at `http://localhost:8080`, and the mock server at `http://localhost:8081`. Press `Ctrl+C` to stop them. To remove the containers, run `docker compose down`.

### Locally, without Docker Compose

You do not need Docker. You only need JDK 21; the Maven Wrapper will download the required Maven version. Start each application in a separate terminal from the project root.

In the first terminal, start the mock server:

```shell
.\aggregator-service\mvnw.cmd -f pom.xml -pl mock-server spring-boot:run
```

In the second terminal, start the aggregator:

```shell
.\aggregator-service\mvnw.cmd -f pom.xml -pl aggregator-service spring-boot:run
```

By default, the aggregator connects to the mock server at `localhost:8081` and listens on port `8080`. The application runs with the `--enable-preview` flag, which is required by the Java 21 preview API `StructuredTaskScope`. The Maven and container configuration already set this flag.

## 2. Key design decisions

- **Fetching data in parallel:** `GetProductDetailsUseCaseImpl` starts requests to the services inside a `StructuredTaskScope`. The endpoint call is still synchronous from the client's point of view: it returns a response only after aggregation is complete. However, requests to the upstream services run in parallel. This means the total wait is closer to the response time of the slowest service than to the sum of all response times. The scope ties the tasks' lifetimes to the request and can cancel the remaining tasks if one of them fails with an unhandled error.
- **Required and optional sources:** The product cannot be returned without catalog data, so a Catalog failure stops the request. A Pricing or Availability failure does not block the full response: the aggregator returns the remaining data and indicates that the price is unavailable or the stock level is unknown. The customer profile is optional. If no customer ID is provided, the standard profile is created locally. The same standard profile is used if the Customer Service does not respond.
- **Statuses only when needed:** When Pricing or Availability returns data, the response includes the data without an extra status. If Pricing fails, the price fields are `null` and the status is `UNAVAILABLE`. If Availability fails, the stock fields are `null` and the status is `UNKNOWN`. A stock level of `0` is valid information that there are no items in stock; it is not a service failure.
- **Protecting calls to services:** Each client has timeouts and its own Circuit Breaker. When a circuit opens, calls to the failing service are temporarily stopped. For Catalog, the aggregator returns HTTP 503 Service Unavailable. For optional services, it uses a fallback.
- **Ports & Adapters architecture (hexagonal architecture):** This is a deliberate choice for structuring the application. Ports describe the data needed by a use case, REST adapters handle communication with upstream services, and `GetProductDetailsUseCaseImpl` coordinates the aggregation. This keeps the aggregation logic independent of a specific HTTP client. A separate REST response model also keeps the public API format separate from the domain models.

### Trade-offs

- In JDK 21, `StructuredTaskScope` is a preview feature. It requires the `--enable-preview` flag and depends on an API that may change in future Java versions. In return, it provides a structured way to handle parallel tasks while the code remains synchronous from the caller's point of view.
- `ShutdownOnFailure` does not know that Catalog is required and the other sources are optional. It cancels the remaining tasks if any task fails with an exception. The clients implement the required-versus-optional behavior: expected failures from optional services are converted into fallback values, while Catalog failures are propagated. An unexpected programming error in an optional task can also stop the aggregation.
- Adding a source does not require changing the existing clients, but the new call must still be added to the orchestration. Its data must also be included in the domain result and the API response. With four sources, this explicit structure is easy to follow. However, adding a class that implements an interface is not enough to make a new source work automatically.
- The mocks contain only a few example products, customers, and markets. This is intentional: they simulate service delays, reliability, and response formats; they do not replace the full service logic or data catalog.
- There is no retry, meaning failed calls are not sent again automatically. The requirements do not define how many times or for how long a request should be retried. Without these limits, retries could unnecessarily increase response times.

## 3. What I could improve with more time

- **Add limited retries for transient failures.** A retry means sending a failed request again. It can help, for example, when a service has a short interruption. Retries would only apply to GET calls, which are safe to repeat, and to errors that may go away on their own; there is no point retrying a 404 response. It would be useful to wait longer between each attempt (*backoff*) and add a small random delay (*jitter*). This helps prevent many requests from retrying at exactly the same time. All attempts would have to fit within one time limit for the full response, so a short failure does not turn into a long wait.
- **Consider gRPC for service-to-service communication.** If most clients and upstream services were managed internally, gRPC could provide typed contracts based on Protocol Buffers and efficient binary communication, which could reduce the overhead compared to REST/JSON.
- **Keep the option to change the concurrency mechanism.** If the project needed to run without a preview API, the orchestration could be isolated behind an interface. `StructuredTaskScope` could then be replaced with another mechanism without changing the endpoint contract.
- **Expand the tests when new requirements are added.** At that point, it would be useful to add tests for new markets and sources, and to check what happens when the total response time limit is exceeded.

## 4. Design question: adding five markets

The aggregator passes the market code to the upstream services and does not maintain separate, large language or currency maps. To add five markets, I would:

1. Extend the `market` validation in `ProductAggregatorController` and add tests for the new codes.
2. Add the localized product names and descriptions, currencies and exchange rates, warehouses, and estimated delivery times for the new markets to the mock server.
3. Check that all sources understand the same market codes and return data for the selected market.

The market data in the mock server is grouped in the `MARKETS` configuration, so adding a market does not require separate switch statements in several places. However, the list of accepted codes is also part of the controller validation and must be updated there. If markets needed to be added often or independently of application releases, this configuration could be moved outside the code.

## Example API requests

The aggregator endpoint has this format:

```text
GET /api/v1/products/{productId}?market={market}&customerId={customerId}
```

The mock server supports products `P-1` and `P-2`, markets `nl-NL`, `de-DE`, and `pl-PL`, and these customers:

- `TEST-CUSTOMER-100042` — B2B premium
- `TEST-CUSTOMER-200017` — B2C standard

The default market is `nl-NL`, and `customerId` can be omitted:

```shell
curl.exe "http://localhost:8080/api/v1/products/P-1?market=nl-NL&customerId=TEST-CUSTOMER-100042"
curl.exe "http://localhost:8080/api/v1/products/P-2?market=pl-PL&customerId=TEST-CUSTOMER-200017"
curl.exe "http://localhost:8080/api/v1/products/P-1"
```

If `customerId` is not provided, the aggregator returns the standard profile without calling the Customer Service. An unknown product results in a 404 response. If the customer profile is unknown or the Customer Service is unavailable, the aggregator returns the standard profile.
