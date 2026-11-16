# Loan Platform

An online loan application system for a bank, built as a REST API with Spring Boot.
It also has a small website and an interactive API page (Swagger UI) to try everything in a browser.

- **Customers** apply for loan products and upload documents.
- **Loan officers** approve or reject applications.
- **Admins** manage loan products and document types.

What it can do:

- register, search, change and delete customers
- manage loan products and the document types a product requires
- calculate the monthly payment of a loan before applying
- submit, change, approve, reject and cancel applications, with a history of every status change
- upload, download and delete documents; an application cannot be approved while a required document is missing
- show the month-by-month repayment schedule of an approved application
- report totals per product and statistics about all applications

There is no login yet; roles and JWT security come in the next stage of the course.

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Spring Data JPA, Bean Validation, Thymeleaf) |
| Database | PostgreSQL 17 (Docker Compose) |
| Build | Maven (wrapper included, no Maven installation needed) |
| API documentation | springdoc-openapi (Swagger UI) |
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

3. Open one of these in a browser:

   | URL | What it is |
   |---|---|
   | `http://localhost:8080/ui` | The test website |
   | `http://localhost:8080/swagger-ui.html` | Swagger UI: every endpoint with a "Try it out" button |
   | `http://localhost:8080/api/products` | The raw JSON of the two sample products |

The tables are created automatically from the entities on startup (`spring.jpa.hibernate.ddl-auto=update`).
On the first start the application also inserts sample data:

| Document types | Loan products |
|---|---|
| 1 ID Card | 1 Consumer Loan: 14.5 %, 500–20 000, 3–48 months, requires the ID Card |
| | 2 Mortgage: 9.75 %, 20 000–500 000, 60–240 months, requires the ID Card |

The ID Card is the only document a loan needs. More document types can be added with
`POST /api/document-types` or on the website.

To stop the database: `docker compose down` (add `-v` to also delete the data).

### Database connection

| Host | Port | Database | User | Password |
|---|---|---|---|---|
| `localhost` | `5433` | `loan_platform` | `loan` | `loan` |

Port 5433 is used so the container does not clash with a PostgreSQL already installed on port 5432.
The settings are in `src/main/resources/application.properties`.

## Endpoints

**Customers**

| Method | URL | Description | Success |
|---|---|---|---|
| POST | `/api/customers` | Create a customer | 201 |
| GET | `/api/customers?search=&page=&size=&sort=` | List customers (paginated, sortable); `search` looks in name, email and personal number | 200 |
| GET | `/api/customers/{id}` | Get one customer | 200 |
| PUT | `/api/customers/{id}` | Update a customer | 200 |
| DELETE | `/api/customers/{id}` | Delete a customer that has no applications | 204 |
| GET | `/api/customers/{id}/applications?status=` | List one customer's applications with the given status | 200 |

**Document types**

| Method | URL | Description | Success |
|---|---|---|---|
| GET | `/api/document-types` | List all document types | 200 |
| POST | `/api/document-types` | Create a document type | 201 |
| DELETE | `/api/document-types/{id}` | Delete a document type that is not used | 204 |

**Loan products**

| Method | URL | Description | Success |
|---|---|---|---|
| GET | `/api/products` | List all loan products | 200 |
| GET | `/api/products/{id}` | Get one loan product | 200 |
| POST | `/api/products` | Create a loan product | 201 |
| PUT | `/api/products/{id}` | Update a loan product | 200 |
| DELETE | `/api/products/{id}` | Delete a loan product that has no applications | 204 |
| GET | `/api/products/{id}/calculate?amount=&termMonths=` | Payment calculator: monthly payment, total and interest | 200 |

**Applications**

| Method | URL | Description | Success |
|---|---|---|---|
| POST | `/api/applications` | Create a loan application | 201 |
| GET | `/api/applications?status=&page=&size=&sort=` | List applications (paginated, sortable, optional status filter) | 200 |
| GET | `/api/applications/search?productName=&amountAbove=` | List applications of one product with an amount above a value | 200 |
| GET | `/api/applications/reports/approved-totals` | Report: total approved amount per product | 200 |
| GET | `/api/applications/reports/statistics` | Report: count per status, approval rate, average and approved amount | 200 |
| GET | `/api/applications/{id}` | Get one application with its documents | 200 |
| GET | `/api/applications/{id}/history` | Every status the application has had, with time and rejection reason | 200 |
| GET | `/api/applications/{id}/missing-documents` | Required documents that are not uploaded yet | 200 |
| GET | `/api/applications/{id}/schedule` | Repayment schedule of an approved application | 200 |
| PUT | `/api/applications/{id}` | Change the product, amount or term of a submitted application | 200 |
| PUT | `/api/applications/{id}/approve` | Approve an application | 200 |
| PUT | `/api/applications/{id}/reject` | Reject an application (body: `decisionComment`) | 200 |
| DELETE | `/api/applications/{id}` | Cancel a submitted application | 204 |

**Documents**

| Method | URL | Description | Success |
|---|---|---|---|
| POST | `/api/applications/{id}/documents` | Upload a document (`multipart/form-data`: `file`, `documentTypeId`) | 201 |
| GET | `/api/documents/{id}/download` | Download the stored file | 200 |
| DELETE | `/api/documents/{id}` | Delete a document of a submitted application | 204 |

Example of a paginated list: `GET /api/applications?status=SUBMITTED&page=0&size=10&sort=createdAt,desc`

### Payment calculator and repayment schedule

Loans are repaid with equal monthly payments (an annuity). With monthly rate `r` (yearly rate / 12 / 100)
and `n` months, the payment is `amount × r × (1 + r)^n / ((1 + r)^n − 1)`. Each month the interest part is
`r ×` the remaining balance and the rest of the payment reduces the balance. The last payment pays exactly
what is left, so rounding never leaves a debt. All amounts are `BigDecimal`, rounded to 2 decimals.

Example: `GET /api/products/1/calculate?amount=5000&termMonths=12` (Consumer Loan, 14.5 %)

```json
{
  "amount": 5000,
  "interestRate": 14.50,
  "termMonths": 12,
  "monthlyPayment": 450.11,
  "totalPayment": 5401.36,
  "totalInterest": 401.36
}
```

## Business rules

1. The application amount must be within the product's `minAmount`..`maxAmount`.
2. The term must be within the product's `minTermMonths`..`maxTermMonths`.
3. New applications start as `SUBMITTED`.
4. A decision can be changed (approved to rejected, or rejected to approved) but not repeated.
   Only `SUBMITTED` applications can be cancelled or modified.
5. Rejection requires a non-blank reason (`decisionComment`).
6. Uploaded files must be PDF, JPG or PNG and at most 5 MB.
7. An application can only be approved when every document type its product requires has been uploaded.

Other rules that protect the data:

- Email, personal number, product name and document type name are unique.
- A customer or product that has applications cannot be deleted.
- A document type cannot be deleted while a product requires it or a document was uploaded with it.
- A document can only be deleted while its application is `SUBMITTED`.
- Only an `APPROVED` application has a repayment schedule.
- Every status change (submitted, approved, rejected) is saved in the application's history.

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
| 404 Not Found | Customer, product, application, document or document type does not exist |
| 405 Method Not Allowed | The URL exists but not for this HTTP method |
| 409 Conflict | Duplicate email, personal number, product name or document type name; the application's status does not allow the action (rule 4: repeating a decision, cancelling or modifying a decided application); a required document is missing (rule 7); deleting something that is still used |

## Swagger UI

With the app running, open `http://localhost:8080/swagger-ui.html`.

The page is generated by springdoc-openapi from the `@RestController` classes: every endpoint is listed
with its parameters and JSON shapes, and "Try it out" sends a real request. The same description as JSON is
at `/v3/api-docs`. Only `/api/**` is documented (`springdoc.paths-to-match` in `application.properties`).

## Test website (Thymeleaf)

With the app running, open `http://localhost:8080/ui` in a browser.

The website is a set of Thymeleaf pages served by `WebController` (`@Controller`). It calls the same services
as the REST API, so the same validation and business rules apply. It does not go through the `/api` endpoints
(the only exception is the document download link).

| Page | What you can do |
|------|-----------------|
| `/ui` | Overview: statistics, two charts, and the applications waiting for a decision |
| `/ui/applications` | Paginated list with status tabs; each row has an Actions menu (open, edit, approve, reject, cancel); search by product and amount |
| `/ui/applications/new`, `/ui/applications/{id}/edit` | Submit an application, or change one that is still submitted |
| `/ui/applications/{id}` | Details, required documents checklist, approve / reject / cancel, upload, download and delete documents, repayment schedule, history |
| `/ui/customers` | Search and page through customers, register, edit and delete a customer, open a customer's applications by status |
| `/ui/products` | List and delete loan products |
| `/ui/products/new`, `/ui/products/{id}/edit` | Create or edit a loan product (one shared form, required documents as checkboxes) |
| `/ui/document-types` | List, add and delete document types |
| `/ui/calculator` | Payment calculator; "Apply with these numbers" opens the application form already filled |

When a form is invalid, the same page is shown again with the typed values and one message under each
invalid field. When a service refuses an action (for example approving without the ID Card), the user is
sent back to the page they came from with the reason in a red message.

The charts on the overview are plain HTML and CSS, with no JavaScript library.

Templates are in `src/main/resources/templates`, the stylesheet in `src/main/resources/static/css/style.css`.

## Testing with Postman

A ready collection is in `postman/Loan-Platform.postman_collection.json`: 102 requests with tests,
covering every endpoint with success and error cases.

1. In Postman choose **Import** and select the collection file.
2. Start the database and the application.
3. Run the folders in order from top to bottom (or use **Run collection**). Later requests use ids
   that earlier requests save into collection variables, so "Create customer" must run first.
4. **File uploads** (three requests in folder 5 and "Upload ID card for the application to approve" in
   folder 6): Postman does not store files inside a collection. Open each upload request, go to
   Body → form-data, click the value of `file` and select the matching file from
   `postman/files/` (`sample.pdf`, `sample.png`, `sample.txt`).

The collection generates a unique email, personal number, product name and document type name on every
run, so it can be run repeatedly. It expects document type `1` to be the sample `ID Card`. The base URL is the collection variable `baseUrl` (`http://localhost:8080`).

`POSTMAN_CHECKLIST.md` contains the same scenarios as a manual step-by-step list.

## Automated tests

```bash
./mvnw test
```

| Test class | What it checks |
|---|---|
| `LoanApplicationServiceTest` | Business rules 1–4 and 7, history, calculator, schedule, statistics (Mockito, no database) |
| `LoanCalculatorServiceTest` | The loan maths: annuity formula, last payment, 0 % interest |
| `LoanProductServiceTest` | Product rules: duplicate name, min/max ranges, delete |
| `CustomerServiceTest` | Duplicate email and personal number, search, update, delete |
| `DocumentServiceTest`, `DocumentTypeServiceTest` | Download, delete rules for documents and document types |
| `FileStorageServiceTest` | Rule 6: file type, size, unique stored file name |
| `CustomerControllerTest`, `LoanProductControllerTest`, `LoanApplicationControllerTest`, `DocumentControllerTest`, `DocumentTypeControllerTest` | HTTP status codes, validation and JSON errors (MockMvc) |
| `WebControllerTest` | The website: pages render, forms keep typed values, errors are shown |
| `LoanPlatformApplicationTests` | The whole application starts |

The service and controller tests do not need the database. `LoanPlatformApplicationTests` starts the
whole application, so **the database must be running** when you run all tests.

## Project structure

```
src/main/java/ge/kiu/loanplatform
├── controller   REST controllers (@RestController) and WebController (@Controller) for the website
├── service      business rules, transactions (@Service, @Transactional)
├── repository   Spring Data JPA repositories (method-name queries and @Query)
├── entity       JPA entities (Customer, LoanProduct, DocumentType, LoanApplication, Document,
│                StatusChange) and the ApplicationStatus enum
├── dto          request DTOs with validation, response DTOs
├── config       AppProperties (app.upload-dir), DataInitializer (sample data), OpenApiConfig (Swagger title)
└── exception    custom exceptions and GlobalExceptionHandler
```

Uploaded files are saved in the folder set by `app.upload-dir` (default: `uploads/` in the project folder)
under a unique generated name; the path is stored in the `documents` table.
