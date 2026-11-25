# Loan Platform

An online loan application system for a bank, built as a REST API with Spring Boot.
It also has a small website and an interactive API page (Swagger UI) to try everything in a browser.

- **Customers** apply for loan products and upload documents.
- **Loan officers** approve or reject applications.
- **Admins** manage loan products and document types.

What it can do:

- register, search, change and delete customers
- manage loan products and the document types a product requires
- calculate the monthly payment of a loan before applying, with equal or decreasing monthly payments
- submit, change, approve, reject and cancel applications, undo the last decision, with a history of every status change
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
(*Consumer Loan* with equal monthly payments and *Mortgage* with decreasing monthly payments).
Products saved before the repayment method existed get equal payments (`ANNUITY`). To stop the database: `docker compose down` (add `-v` to also delete the data).

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
  "code": "CONFLICT",
  "message": "Customer with email ana@mail.com already exists",
  "path": "/api/customers",
  "fieldErrors": null,
  "currentStatus": null,
  "allowedStatuses": null
}
```

Validation errors return 400 with `fieldErrors` (one message per invalid field), a missing record 404,
and a status that does not allow the action or a duplicate value 409.

`code` is a fixed, machine-readable name for the kind of error (the `ErrorCode` enum). The message is for people
and may be reworded; programs and tests can check the code instead:

| Code | HTTP | When |
|------|------|------|
| `NOT_FOUND` | 404 | no record with this id |
| `BUSINESS_RULE_VIOLATION` | 400 | a business rule is broken (amount or term outside the product range, ...) |
| `CONFLICT` | 409 | duplicate value, record still in use, required documents missing |
| `APPLICATION_NOT_MODIFIABLE` | 409 | modify, cancel or delete a document of an application that is already decided |
| `INVALID_STATUS_TRANSITION` | 409 | a decision the status does not allow (approve an APPROVED application) |
| `NO_DECISION_TO_UNDO` | 409 | undo on a SUBMITTED application |
| `NO_REPAYMENT_SCHEDULE` | 409 | schedule of an application that is not APPROVED |
| `VALIDATION_FAILED` | 400 | `@Valid` or a parameter constraint failed (see `fieldErrors`) |
| `MALFORMED_REQUEST_BODY` | 400 | the body is missing or not valid JSON |
| `INVALID_PARAMETER` | 400 | a path variable or parameter has the wrong type |
| `MISSING_PARAMETER` | 400 | a required parameter or file part is missing |
| `INVALID_SORT_FIELD` | 400 | `?sort=` names an unknown field |
| `FILE_TOO_LARGE` | 400 | the upload is above the size limit |
| `METHOD_NOT_ALLOWED` | 405 | the URL does not accept this HTTP method |
| `DATA_INTEGRITY_VIOLATION` | 409 | the database refused the change |

The four status codes come from `InvalidStatusException` (a `ConflictException`, so still 409). Only for them
`currentStatus` and `allowedStatuses` are filled, and the message ends with what the status still allows:

```json
{
  "status": 409,
  "code": "APPLICATION_NOT_MODIFIABLE",
  "message": "Application with id 5 is APPROVED and cannot be modified. An APPROVED application can only be rejected or have its decision undone.",
  "currentStatus": "APPROVED",
  "allowedStatuses": ["REJECTED"]
}
```

## File validation

Every upload is checked by `@ValidFile` (the website upload runs the same check); a failure is a 400
response with the reason.

| Check | Rule |
|---|---|
| Present | the file is not missing or empty |
| Size | at most 5 MB (also a global limit in `application.properties`) |
| Type | PDF, JPG or PNG; the extension and the Content-Type must match |
| Real image | a JPG/PNG must really be that format (only its header is read) |
| Pixel bomb | an image may have at most 25 million pixels |

Files are stored under a random generated name, never the original file name. Archives are not accepted,
so nothing is ever unpacked (no zip bombs).

## Swagger UI

`http://localhost:8080/swagger-ui.html` lists every `/api` endpoint, generated by springdoc-openapi from the
controllers; "Try it out" sends a real request. The JSON description is at `/v3/api-docs`.

## Test website (Thymeleaf)

`http://localhost:8080/ui` is a small website served by the controllers in `controller.web` (`@Controller`, one per area). It calls the same
services as the REST API, so the same validation and business rules apply. Invalid forms are shown again with
the typed values and a message under each field; a refused action shows the reason in a red message.

Templates are in `src/main/resources/templates`, the stylesheet in `static/css/style.css` and a small
animation script in `static/js/motion.js`. The photos in `static/img/photos` are from
[Unsplash](https://unsplash.com) (free license) by Vitaly Gariev, Scott Graham, Towfiqu barbhuiya,
Tierra Mallorca and yahdi yasya.

## Testing with Postman

A ready collection is in `postman/Loan-Platform.postman_collection.json`: 108 requests with tests,
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

## Observer pattern: status change notifications

`LoanApplicationService` is the subject. When an application is created, approved or rejected, it publishes an
`ApplicationStatusChangedEvent` through Spring's `ApplicationEventPublisher`. It does not know who receives
the event. `CustomerNotificationListener` is an observer: its `@TransactionalEventListener` method runs after the
transaction commits, builds the message for the customer and hands it to a `NotificationSender` (an interface).
The only implementation, `LogNotificationSender`, writes it to the log because there is no mail server; an e-mail
sender could be added later without changing the listener. For example:
`To Ana Beridze <ana@example.com>: your loan application #5 was REJECTED. Reason: Income too low`.
To add a new reaction (for example an audit log), add another listener class. The service does not change.

## State pattern: what each status allows

Each `ApplicationStatus` constant is a state object that knows what an application in that status may do:
`canBeModified()` (modify, cancel, delete a document), `canMoveTo(target)` (rule 4: which decision is allowed),
`hasRepaymentSchedule()`, `hasDecisionToUndo()` and `describeAllowedActions()` (the sentence added to error
messages). The methods are `abstract` in the enum, so every constant must answer each of them, and a new status
cannot be added without deciding its rules. `allowedNextStatuses()` is written once for all statuses: it asks
`canMoveTo` for each one. The services ask the status (`if (!status.canBeModified())`) instead of comparing it
with a fixed value in many places, and refuse with an `InvalidStatusException`. The status is still stored as a
plain string (`@Enumerated(EnumType.STRING)`), so the database did not change.

| | SUBMITTED | APPROVED | REJECTED |
|---|---|---|---|
| modify, cancel, delete a document | yes | no | no |
| a decision can move it to | APPROVED, REJECTED | REJECTED | APPROVED |
| repayment schedule | no | yes | no |
| undo the last decision | no | yes | yes |

## Strategy pattern: repayment methods

A loan can be paid back in different ways, and each way is one class that implements `RepaymentStrategy`
(`buildSchedule(amount, monthlyRate, termMonths)` and `type()`):

| `repaymentType` | Strategy | Payments | Example: 12,000 at 12% for 12 months |
|---|---|---|---|
| `ANNUITY` | `AnnuityRepaymentStrategy` | the same every month | 1,066.19 every month, 794.23 interest |
| `DIFFERENTIATED` | `DifferentiatedRepaymentStrategy` | the same part of the loan every month plus interest on what is left, so they decrease | 1,120.00 down to 1,010.00, 780.00 interest |

Each `LoanProduct` has a `repaymentType` (optional in `POST`/`PUT /api/products`: a new product gets `ANNUITY`, an
update without it keeps the current one; an unknown value is a 400 `MALFORMED_REQUEST_BODY`). Spring injects every
strategy bean into `LoanCalculatorService` as a `List<RepaymentStrategy>`; the calculator turns the yearly rate into
a monthly one, picks the strategy whose `type()` matches the product, and adds up the totals. It does not know any
formula itself, so a new repayment method is a new `@Component` class and a new `RepaymentType` value.

The calculator result and the schedule summary contain `repaymentType`, `monthlyPayment` (the first payment) and
`lastPayment`. For equal payments the two are only a few cents apart (the last month closes the loan exactly);
for decreasing payments they show the range. The website shows one amount for equal payments and the first and
last payment for decreasing ones.

### Template method: how each strategy is built

Both strategies extend `AbstractRepaymentStrategy`. Its `buildSchedule` is the template method: it is `final`, so
the steps and their order are the same for every repayment method, and it holds the loop that was written twice
before. Every month it works out the interest on what is still owed, asks the subclass which part of the loan is
paid back, lets the last month pay exactly what is left, and adds a line. The subclasses fill in only the two steps
that differ:

| Step | `AnnuityRepaymentStrategy` | `DifferentiatedRepaymentStrategy` |
|---|---|---|
| `regularAmount(...)`, once before the loop | the fixed monthly payment (annuity formula) | the fixed part of the loan (amount / months) |
| `principalPart(regular, interest)`, every month | payment minus interest | always the same part |

Strategy and template method work together here: the strategy pattern decides *which* method a product uses, the
template method decides *how* every method is built. A new repayment method is a class with `type()` and these two
steps.

State and Strategy look alike in code (several classes behind one interface), but in State the application's own
status decides the behaviour and changes over time, while in Strategy the product chooses one algorithm that
stays the same.

Two interfaces already work the same way, each with one implementation today: `FileStorageService` (where uploaded
files are stored) and `NotificationSender` (how customers are told about a decision).

## Command pattern: officer decisions

Approve and reject are command objects that implement `DecisionCommand`: `ApproveCommand` carries the list of
problems the approval rules found (see below), and `RejectCommand` carries the rejection reason. Both decisions share
the same steps, which `LoanApplicationService.executeDecision` writes once: check rule 4 (the current status is asked: `canMoveTo`),
run `command.execute(...)`, add a history line, and publish the status change event. Only `execute` is different
for each decision. Commands are plain objects, not Spring beans, because a new one is made for every request.

## Chain of responsibility: approval rules

Every check an application must pass before approval is a class that implements `ApprovalRule`
(`String findProblem(LoanApplication)`: `null` = passed, otherwise the reason). Today there is one rule,
`RequiredDocumentsRule` (rule 7). Spring injects every `ApprovalRule` bean into `LoanApplicationService` as a
`List<ApprovalRule>`; approve (and undo back to APPROVED) asks each rule in turn and refuses with 409 when any
rule found a problem. A new check is a new `@Component` class; the service does not change. Which document types
are missing is worked out by `LoanApplication.findMissingDocumentTypes()`, also used by
`GET /api/applications/{id}/missing-documents`.

## Memento pattern: undo the last decision

`PUT /api/applications/{id}/undo-decision` (and "Undo the last decision" on the application page) returns a
decided application to the state it had before its last decision. `ApplicationStateMemento` is the snapshot
(status and rejection reason). `LoanApplication` is the originator: `createMemento()` takes a snapshot and
`restore(...)` goes back to one. The status history is the caretaker: every `StatusChange` row is a saved
snapshot (`toMemento()`). History is never deleted: the undo is added as a new line, so
SUBMITTED → APPROVED, then undo, gives SUBMITTED → APPROVED → SUBMITTED. A SUBMITTED application has nothing
to undo (409), and going back to APPROVED is refused while a required document is missing (rule 7).

## Static factory methods: creating applications and history lines

A new application is made with `LoanApplication.submit(customer, product, amount, termMonths)` instead of
`new LoanApplication()` and five setters. The name says what happens, and the rules of a new application are
written in one place: it starts as `SUBMITTED` (rule 3) and its history starts with that first line, so a caller
cannot forget either step. A history line is made with `StatusChange.from(application)`, which copies the
application's current snapshot (`createMemento()`); `LoanApplicationService` adds it to the history after every
decision and undo. The no-argument constructors stay public because JPA needs them and the tests use them.

These are static factory methods (a static method that returns a new object), not the GoF *Factory Method*
pattern, where a subclass decides which class is created.

## Project structure

```
src/main/java/ge/kiu/loanplatform
├── controller
│   ├── api          REST controllers (@RestController) under /api
│   └── web          the /ui website (@Controller), one per area: HomeWebController, CustomerWebController,
│                    LoanProductWebController, DocumentTypeWebController, LoanApplicationWebController
│                    (with the calculator), DocumentWebController; WebControllerAdvice (shared @InitBinder
│                    and @ExceptionHandler), WebForms and PreviousPage (static helpers)
├── service      business rules, transactions (@Service, @Transactional)
│   ├── customer     CustomerService
│   ├── product      LoanProductService
│   ├── application  LoanApplicationService
│   │   ├── command      DecisionCommand (interface), ApproveCommand, RejectCommand (command pattern)
│   │   ├── rule         ApprovalRule (interface), RequiredDocumentsRule (chain of responsibility)
│   │   └── event        ApplicationStatusChangedEvent, CustomerNotificationListener (observer pattern)
│   ├── document     DocumentService, DocumentTypeService
│   ├── calculation  LoanCalculatorService (rate conversion, picks the strategy, totals)
│   │   └── strategy     RepaymentStrategy (interface, strategy pattern), AbstractRepaymentStrategy
│   │                    (template method), AnnuityRepaymentStrategy, DifferentiatedRepaymentStrategy
│   ├── notification NotificationSender (interface), LogNotificationSender
│   └── storage      FileStorageService (interface), LocalFileStorageService (files on disk)
├── repository   Spring Data JPA repositories (method-name queries and @Query)
│   ├── customer     CustomerRepository
│   ├── product      LoanProductRepository
│   ├── application  LoanApplicationRepository, ProductTotalAmount (report row)
│   └── document     DocumentRepository, DocumentTypeRepository
├── entity       JPA entities
│   ├── customer     Customer
│   ├── product      LoanProduct, RepaymentType enum (which strategy a product uses)
│   ├── application  LoanApplication, StatusChange (static factory methods submit / from),
│   │                ApplicationStatus enum (state pattern), ApplicationStateMemento (memento pattern)
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
    ├── custom       ResourceNotFoundException, BusinessRuleException, ConflictException,
    │                InvalidStatusException (a ConflictException), ErrorCode (the "code" in error JSON)
    └── handler      GlobalExceptionHandler (@RestControllerAdvice)
```

The test classes in `src/test/java` use the same packages as the classes they test.

Uploaded files are saved in the folder set by `app.upload-dir` (default: `uploads/`).
