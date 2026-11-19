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

3. Open `http://localhost:8080/ui` (website) or `http://localhost:8080/swagger-ui.html` (every endpoint
   with a "Try it out" button).

On the first start the application inserts sample data: the document type *ID Card* and two loan products
(*Consumer Loan* and *Mortgage*). To stop the database: `docker compose down` (add `-v` to also delete the data).

Database connection: `localhost:5433`, database `loan_platform`, user `loan`, password `loan`
(port 5433, so it does not clash with a local PostgreSQL on 5432).

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

Validation errors return 400 with `fieldErrors` (one message per invalid field), a missing record 404,
and a status that does not allow the action or a duplicate value 409.

## File uploads

Documents are uploaded as `multipart/form-data` (`file` + `documentTypeId`).

- `@ValidFile` on the `file` parameter checks that the file is present and not empty, at most 5 MB, and
  declared as PDF, JPG or PNG (the extension and the Content-Type must match). A failure is a clean 400
  with the reason under `fieldErrors.file`. `FileStorageService` runs the same check again, so the website
  upload is protected too.
- The original file name is never used as the storage path: the stored name is a random UUID plus an
  extension chosen from the checked type (e.g. `3f2a...e9.pdf`). The original name is only kept in the
  database (without any folder part) as the download name.
- Global limits in `application.properties`: `max-file-size=5MB` and `max-request-size=6MB` stop a bigger
  upload while it is read (400), and `server.tomcat.max-swallow-size` caps how much of a refused request
  is still read.
- Pixel bombs (a tiny image whose header claims e.g. 100 000 x 100 000 pixels): for a JPG or PNG only the
  header is read with `javax.imageio`, never the pixels. An image over 25 million pixels, or bytes that are
  not really the declared format, are refused with 400.
- Archives (ZIP etc.) are not accepted and nothing is ever unpacked, so zip bombs cannot reach the server.

## Swagger UI

`http://localhost:8080/swagger-ui.html` lists every `/api` endpoint, generated by springdoc-openapi from the
controllers; "Try it out" sends a real request. The JSON description is at `/v3/api-docs`.

## Test website (Thymeleaf)

`http://localhost:8080/ui` is a small website served by `WebController` (`@Controller`). It calls the same
services as the REST API, so the same validation and business rules apply. Invalid forms are shown again with
the typed values and a message under each field; a refused action shows the reason in a red message.

Templates are in `src/main/resources/templates`, the stylesheet in `static/css/style.css` and a small
animation script in `static/js/motion.js`. The photos in `static/img/photos` are from
[Unsplash](https://unsplash.com) (free license) by Vitaly Gariev, Scott Graham, Towfiqu barbhuiya,
Tierra Mallorca and yahdi yasya.

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

Service tests use Mockito, controller tests use MockMvc; both run without the database.
`LoanPlatformApplicationTests` starts the whole application, so **the database must be running** when
you run all tests.

## Project structure

```
src/main/java/ge/kiu/loanplatform
├── controller
│   ├── api          REST controllers (@RestController) under /api
│   └── web          WebController (@Controller) for the /ui website
├── service      business rules, transactions (@Service, @Transactional)
│   ├── customer     CustomerService
│   ├── product      LoanProductService
│   ├── application  LoanApplicationService, LoanCalculatorService
│   └── document     DocumentService, DocumentTypeService, FileStorageService
├── repository   Spring Data JPA repositories (method-name queries and @Query)
│   ├── customer     CustomerRepository
│   ├── product      LoanProductRepository
│   ├── application  LoanApplicationRepository, ProductTotalAmount (report row)
│   └── document     DocumentRepository, DocumentTypeRepository
├── entity       JPA entities
│   ├── customer     Customer
│   ├── product      LoanProduct
│   ├── application  LoanApplication, StatusChange, ApplicationStatus enum
│   └── document     Document, DocumentType
├── dto          request DTOs with validation, response DTOs
│   ├── customer     CustomerRequest, CustomerResponse
│   ├── product      LoanProductRequest, LoanProductResponse
│   ├── application  create/update/reject requests, LoanApplicationResponse, StatusChangeResponse
│   ├── document     document and document type DTOs, DocumentFile (download)
│   ├── calculation  payment calculator result and repayment schedule
│   ├── report       approved totals per product, statistics
│   ├── error        ErrorResponse (JSON error body)
│   └── validation   reusable constraints @ValidAmount, @ValidTerm, @ValidFile with their validators
├── config
│   ├── properties   AppProperties (app.upload-dir)
│   ├── data         DataInitializer (sample data)
│   └── openapi      OpenApiConfig (Swagger title)
└── exception
    ├── custom       ResourceNotFoundException, BusinessRuleException, ConflictException
    └── handler      GlobalExceptionHandler (@RestControllerAdvice)
```

The test classes in `src/test/java` use the same packages as the classes they test.

Uploaded files are saved in the folder set by `app.upload-dir` (default: `uploads/`).
