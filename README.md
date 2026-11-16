# Loan Platform

An online loan application system for a bank, built as a REST API with Spring Boot.

- **Customers** apply for loan products and upload documents.
- **Loan officers** approve or reject applications.
- **Admins** manage loan products.

There is no login yet; roles and JWT security come in the next stage of the course.

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Spring Data JPA, Bean Validation) |
| Database | PostgreSQL 17 (Docker Compose) |
| Build | Maven (wrapper included, no Maven installation needed) |
| Other | Lombok, JUnit 5, Mockito, MockMvc |

## Requirements

- JDK 21 or newer
- Docker (or Podman) for the PostgreSQL database
- Postman, to try the API

## How to run

1. Start the database (PostgreSQL on `localhost:5433`):

   ```bash
   docker compose up -d
   ```

2. Start the application (API on `http://localhost:8080`):

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows use `mvnw.cmd spring-boot:run`. The project can also be opened in IntelliJ IDEA and started
   from `LoanPlatformApplication`.

3. Open `http://localhost:8080/api/products` in a browser. It returns the two sample products.

The tables are created automatically from the entities on startup (`spring.jpa.hibernate.ddl-auto=update`).
On the first start the application also inserts sample data:

| Document types | Loan products |
|---|---|
| 1 ID Card | 1 Consumer Loan: 500–20 000, 3–48 months |
| 2 Salary Certificate | 2 Mortgage: 20 000–500 000, 60–240 months |
| 3 Bank Statement | |

To stop the database: `docker compose down` (add `-v` to also delete the data).

### Database connection

| Host | Port | Database | User | Password |
|---|---|---|---|---|
| `localhost` | `5433` | `loan_platform` | `loan` | `loan` |

Port 5433 is used so the container does not clash with a PostgreSQL already installed on port 5432.
The settings are in `src/main/resources/application.properties`.

## Endpoints

| Method | URL | Description | Success |
|---|---|---|---|
| POST | `/api/customers` | Create a customer | 201 |
| GET | `/api/customers` | List all customers | 200 |
| GET | `/api/customers/{id}` | Get one customer | 200 |
| GET | `/api/customers/{id}/applications?status=` | List one customer's applications with the given status | 200 |
| GET | `/api/products` | List all loan products | 200 |
| POST | `/api/products` | Create a loan product | 201 |
| PUT | `/api/products/{id}` | Update a loan product | 200 |
| DELETE | `/api/products/{id}` | Delete a loan product | 204 |
| POST | `/api/applications` | Create a loan application | 201 |
| GET | `/api/applications?status=&page=&size=&sort=` | List applications (paginated, sortable, optional status filter) | 200 |
| GET | `/api/applications/search?productName=&amountAbove=` | List applications of one product with an amount above a value | 200 |
| GET | `/api/applications/reports/approved-totals` | Report: total approved amount per product | 200 |
| GET | `/api/applications/{id}` | Get one application with its documents | 200 |
| PUT | `/api/applications/{id}/approve` | Approve an application | 200 |
| PUT | `/api/applications/{id}/reject` | Reject an application (body: `decisionComment`) | 200 |
| DELETE | `/api/applications/{id}` | Cancel an application | 204 |
| POST | `/api/applications/{id}/documents` | Upload a document (`multipart/form-data`: `file`, `documentTypeId`) | 201 |

Example of the paginated list: `GET /api/applications?status=SUBMITTED&page=0&size=10&sort=createdAt,desc`

## Business rules

1. The application amount must be within the product's `minAmount`..`maxAmount`.
2. The term must be within the product's `minTermMonths`..`maxTermMonths`.
3. New applications start as `SUBMITTED`.
4. Only `SUBMITTED` applications can be approved, rejected or cancelled.
5. Rejection requires a non-blank reason (`decisionComment`).
6. Uploaded files must be PDF, JPG or PNG and at most 5 MB.

## Error responses

Every error is returned as JSON in the same format by `GlobalExceptionHandler` (`@RestControllerAdvice`):

```json
{
  "timestamp": "2026-11-16T14:30:00",
  "status": 409,
  "error": "Conflict",
  "message": "Customer with email ana@mail.com already exists",
  "path": "/api/customers",
  "fieldErrors": null
}
```

| Status | When |
|---|---|
| 400 Bad Request | Validation failed (`fieldErrors` lists each invalid field), malformed JSON, a broken business rule (rules 1, 2, 5, 6), invalid parameter |
| 404 Not Found | Customer, product, application or document type does not exist |
| 405 Method Not Allowed | The URL exists but not for this HTTP method |
| 409 Conflict | Duplicate email, personal number or product name; application is not `SUBMITTED` (rule 4); product still has applications |

## Test website (Thymeleaf)

With the app running, open `http://localhost:8080/ui` in a browser.

The website is a set of Thymeleaf pages served by `WebController` (`@Controller`). It calls the same services
as the REST API, so the same validation and business rules apply. It does not go through the `/api` endpoints.

| Page | What you can do |
|------|-----------------|
| `/ui` | Overview: applications waiting for a decision and the report "approved lending by product" |
| `/ui/customers` | List customers, register a customer, open a customer's applications by status |
| `/ui/products` | List and delete loan products |
| `/ui/products/new`, `/ui/products/{id}/edit` | Create or edit a loan product (one shared form) |
| `/ui/applications` | Paginated list with status tabs, search by product and amount |
| `/ui/applications/new` | Submit an application for a customer |
| `/ui/applications/{id}` | Details, approve / reject / cancel, upload documents |

When a form is invalid, the same page is shown again with the typed values and one message under each
invalid field.

Templates are in `src/main/resources/templates`, the stylesheet in `src/main/resources/static/css/style.css`.

## Testing with Postman

A ready collection is in `postman/Loan-Platform.postman_collection.json`: 55 requests with tests,
covering every endpoint with success and error cases.

1. In Postman choose **Import** and select the collection file.
2. Start the database and the application.
3. Run the folders in order from top to bottom (or use **Run collection**). Later requests use ids
   that earlier requests save into collection variables, so "Create customer" must run first.
4. **File uploads** (folder 4): Postman does not store files inside a collection. Open each upload
   request, go to Body → form-data, click the value of `file` and select the matching file from
   `postman/files/` (`sample.pdf`, `sample.png`, `sample.txt`).

The collection generates a unique email, personal number and product name on every run, so it can be
run repeatedly. The base URL is the collection variable `baseUrl` (`http://localhost:8080`).

`POSTMAN_CHECKLIST.md` contains the same scenarios as a manual step-by-step list.

## Automated tests

```bash
./mvnw test
```

| Test class | What it checks |
|---|---|
| `LoanApplicationServiceTest` | Business rules 1–4 (Mockito, no database) |
| `LoanProductServiceTest` | Product rules: duplicate name, min/max ranges, delete |
| `CustomerServiceTest` | Duplicate email and personal number, not found |
| `FileStorageServiceTest` | Rule 6: file type, size, unique stored file name |
| `CustomerControllerTest`, `LoanApplicationControllerTest` | HTTP status codes, validation and JSON errors (MockMvc) |
| `LoanPlatformApplicationTests` | The whole application starts |

The service and controller tests do not need the database. `LoanPlatformApplicationTests` starts the
whole application, so **the database must be running** when you run all tests.

## Project structure

```
src/main/java/ge/kiu/loanplatform
├── controller   REST controllers (@RestController)
├── service      business rules, transactions (@Service, @Transactional)
├── repository   Spring Data JPA repositories (method-name queries and @Query)
├── entity       JPA entities and the ApplicationStatus enum
├── dto          request DTOs with validation, response DTOs
├── config       AppProperties (app.upload-dir), DataInitializer (sample data)
└── exception    custom exceptions and GlobalExceptionHandler
```

Uploaded files are saved in the folder set by `app.upload-dir` (default: `uploads/` in the project folder)
under a unique generated name; the path is stored in the `documents` table.
