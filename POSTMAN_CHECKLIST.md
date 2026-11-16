# Postman test checklist

Start the database and the app first:

```bash
docker compose up -d      # PostgreSQL on localhost:5433
./mvnw spring-boot:run    # API on http://localhost:8080
```

On the first start the app inserts sample data: document type `1 ID Card` and products
`1 Consumer Loan` (500–20000, 3–48 months) and `2 Mortgage` (20000–500000, 60–240 months).
Both products require the ID Card.

For every request with a JSON body set the header `Content-Type: application/json`
(Postman: Body → raw → JSON). Run the requests in this order, because later ones use ids created earlier.
The ids in this list are the ones a fresh, empty database gives.

All endpoints can also be tried in the browser with Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Customers

| # | Request | Body | Expected |
|---|---------|------|----------|
| 1 | `POST /api/customers` | `{"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890","phone":"555123456"}` | 201, customer with `id` 1 |
| 2 | `POST /api/customers` | same body again | 409, "email ... already exists" |
| 3 | `POST /api/customers` | `{"fullName":"","email":"bad","personalNumber":"12"}` | 400, `fieldErrors` for fullName, email, personalNumber |
| 4 | `GET /api/customers/1` | – | 200 |
| 5 | `GET /api/customers?page=0&size=10&sort=fullName,asc` | – | 200, `content` has the customer, `page.totalElements` 1 |
| 6 | `GET /api/customers?search=ANA` | – | 200, the customer is found (name, email and personal number are searched, case is ignored) |
| 7 | `PUT /api/customers/1` | `{"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890","phone":"+995555000111"}` | 200, new phone |
| 8 | `GET /api/customers/99` | – | 404 |
| 9 | `GET /api/customers/abc` | – | 400, invalid value for `id` |
| 10 | `POST /api/customers` | `{"fullName":"Temp Customer","email":"temp@mail.com","personalNumber":"09876543210"}` | 201, `id` 2 |
| 11 | `DELETE /api/customers/2` | – | 204 (this customer has no applications) |

## Document types

| # | Request | Body | Expected |
|---|---------|------|----------|
| 12 | `GET /api/document-types` | – | 200, `ID Card` |
| 13 | `POST /api/document-types` | `{"name":"Passport"}` | 201, `id` 2 |
| 14 | `POST /api/document-types` | `{"name":"passport"}` | 409, already exists (case is ignored) |
| 15 | `DELETE /api/document-types/1` | – | 409, required by a loan product |

## Products

| # | Request | Body | Expected |
|---|---------|------|----------|
| 16 | `GET /api/products` | – | 200, the 2 sample products |
| 17 | `POST /api/products` | `{"name":"Auto Loan","interestRate":11.5,"minAmount":1000,"maxAmount":50000,"minTermMonths":6,"maxTermMonths":60,"requiredDocumentTypeIds":[1]}` | 201, product with `id` 3 |
| 18 | `POST /api/products` | same body again | 409, name already exists |
| 19 | `POST /api/products` | same but `"name":"Bad","minAmount":9000,"maxAmount":500` | 400, minimum amount greater than maximum |
| 20 | `POST /api/products` | same but `"name":"Bad2","requiredDocumentTypeIds":[77]` | 404, document type not found |
| 21 | `PUT /api/products/3` | `{"name":"Auto Loan","interestRate":12,"minAmount":1000,"maxAmount":60000,"minTermMonths":6,"maxTermMonths":72,"requiredDocumentTypeIds":[1]}` | 200, updated values |
| 22 | `PUT /api/products/3` | same but `"name":"Mortgage"` | 409, name already exists |
| 23 | `GET /api/products/3/calculate?amount=5000&termMonths=12` | – | 200, `monthlyPayment` 444.24, `totalPayment` 5330.93, `totalInterest` 330.93 |
| 24 | `GET /api/products/3/calculate?amount=50&termMonths=12` | – | 400, amount outside 1000–60000 (rule 1) |
| 25 | `DELETE /api/products/2` | – | 204 (Mortgage has no applications) |
| 26 | `DELETE /api/products/99` | – | 404 |

## Applications

| # | Request | Body | Expected |
|---|---------|------|----------|
| 27 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":5000,"termMonths":12}` | 201, `id` 1, status `SUBMITTED` |
| 28 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":7000,"termMonths":24}` | 201, `id` 2 |
| 29 | `POST /api/applications` | `{"customerId":1,"loanProductId":3,"amount":2000,"termMonths":12}` | 201, `id` 3 |
| 30 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":50,"termMonths":12}` | 400, amount outside 500–20000 (rule 1) |
| 31 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":5000,"termMonths":100}` | 400, term outside 3–48 (rule 2) |
| 32 | `POST /api/applications` | `{"customerId":99,"loanProductId":1,"amount":5000,"termMonths":12}` | 404, customer not found |
| 33 | `POST /api/applications` | `{}` | 400, `fieldErrors` for all four fields |
| 34 | `GET /api/applications/1` | – | 200 |
| 35 | `PUT /api/applications/1` | `{"loanProductId":1,"amount":5000,"termMonths":18}` | 200, `termMonths` 18, status still `SUBMITTED` |
| 36 | `PUT /api/applications/1` | `{"loanProductId":1,"amount":999999,"termMonths":18}` | 400, amount outside 500–20000 (rule 1) |
| 37 | `GET /api/applications?page=0&size=2&sort=amount,desc` | – | 200, 2 items, highest amount first, `page.totalElements` 3 |
| 38 | `GET /api/applications?status=SUBMITTED` | – | 200, only SUBMITTED |
| 39 | `GET /api/applications?status=WRONG` | – | 400 |
| 40 | `GET /api/applications?sort=wrongField` | – | 400, unknown sort field |
| 41 | `DELETE /api/products/1` | – | 409, product has applications |

## Documents (upload, download, delete)

Postman: Body → form-data. Key `file` with type **File**, key `documentTypeId` with type Text.

| # | Request | form-data | Expected |
|---|---------|-----------|----------|
| 42 | `POST /api/applications/1/documents` | `file` = a PDF, `documentTypeId` = 1 | 201, document `id` 1 |
| 43 | `POST /api/applications/1/documents` | `file` = a PNG or JPG, `documentTypeId` = 2 | 201, document `id` 2 |
| 44 | `POST /api/applications/1/documents` | `file` = a .txt file, `documentTypeId` = 1 | 400, only PDF, JPG, PNG (rule 6) |
| 45 | `POST /api/applications/1/documents` | `file` = a file larger than 5MB | 400, file too large (rule 6) |
| 46 | `POST /api/applications/1/documents` | only `file`, no `documentTypeId` | 400, missing parameter |
| 47 | `POST /api/applications/99/documents` | valid file, `documentTypeId` = 1 | 404, application not found |
| 48 | `POST /api/applications/1/documents` | valid file, `documentTypeId` = 99 | 404, document type not found |
| 49 | `GET /api/applications/1` | – | 200, `documents` lists the 2 uploads; files exist in the `uploads/` folder |
| 50 | `GET /api/documents/1/download` | – | 200, the PDF itself; header `Content-Disposition: attachment; filename="..."` (Postman: Send and Download) |
| 51 | `GET /api/applications/1/missing-documents` | – | 200, `[]` (the ID Card is uploaded) |

## Decisions (approve / reject / cancel)

| # | Request | Body | Expected |
|---|---------|------|----------|
| 52 | `PUT /api/applications/2/approve` | – | 409, required documents are missing: ID Card (rule 7) |
| 53 | `GET /api/applications/2/missing-documents` | – | 200, `ID Card` |
| 54 | `POST /api/applications/2/documents` | form-data: `file` = a PDF, `documentTypeId` = 1 | 201, document `id` 3 |
| 55 | `PUT /api/applications/2/approve` | – | 200, status `APPROVED` |
| 56 | `PUT /api/applications/2/approve` | – | 409, already APPROVED (rule 4) |
| 57 | `GET /api/applications/2/schedule` | – | 200, 24 payments, the last `remainingBalance` is 0 |
| 58 | `DELETE /api/documents/3` | – | 409, the application is already decided |
| 59 | `PUT /api/applications/3/reject` | `{"decisionComment":"  "}` | 400, reason required (rule 5) |
| 60 | `PUT /api/applications/3/reject` | `{"decisionComment":"Income too low"}` | 200, status `REJECTED`, comment saved |
| 61 | `PUT /api/applications/3/reject` | `{"decisionComment":"Again"}` | 409, already REJECTED (rule 4) |
| 62 | `PUT /api/applications/2/reject` | `{"decisionComment":"Approved by mistake"}` | 200, the decision is changed to `REJECTED` (rule 4) |
| 63 | `PUT /api/applications/2/approve` | – | 200, changed back to `APPROVED`, `decisionComment` is null |
| 64 | `GET /api/applications/2/history` | – | 200, four lines: SUBMITTED, APPROVED, REJECTED (with the reason), APPROVED |
| 65 | `GET /api/applications/3/schedule` | – | 409, only an APPROVED application has a schedule |
| 66 | `DELETE /api/applications/3` | – | 409, REJECTED cannot be cancelled (rule 4) |
| 67 | `DELETE /api/documents/2` | – | 204 (application 1 is still SUBMITTED) |
| 68 | `DELETE /api/applications/1` | – | 204, its files are removed from `uploads/` |
| 69 | `GET /api/applications/1` | – | 404 |
| 70 | `GET /api/applications?status=APPROVED` | – | 200, only application 2 |

## Queries and reports

These endpoints use the repository query methods (method-name queries and `@Query` methods).

| # | Request | Body | Expected |
|---|---------|------|----------|
| 71 | `GET /api/customers/1/applications?status=APPROVED` | – | 200, only application 2 |
| 72 | `GET /api/customers/1/applications` | – | 400, missing parameter `status` |
| 73 | `GET /api/customers/1/applications?status=WRONG` | – | 400, invalid value for `status` |
| 74 | `GET /api/customers/99/applications?status=APPROVED` | – | 404, customer not found |
| 75 | `GET /api/applications/search?productName=Consumer Loan&amountAbove=1000` | – | 200, only application 2 (7000 on Consumer Loan) |
| 76 | `GET /api/applications/search?productName=Consumer Loan` | – | 400, missing parameter `amountAbove` |
| 77 | `GET /api/applications/search?productName=Consumer Loan&amountAbove=-1` | – | 400, `fieldErrors` for amountAbove |
| 78 | `GET /api/applications/reports/approved-totals` | – | 200, one row: `Consumer Loan`, `totalApprovedAmount` 7000.00 |
| 79 | `GET /api/applications/reports/statistics` | – | 200, `totalApplications` 2, `approved` 1, `rejected` 1, `approvalRate` 50.0, `averageAmount` 4500.00, `totalApprovedAmount` 7000.00 |
| 80 | `PUT /api/applications/2` | `{"loanProductId":1,"amount":5000,"termMonths":18}` | 409, APPROVED cannot be modified (rule 4) |
| 81 | `DELETE /api/customers/1` | – | 409, the customer has applications |

## Extra validation

| # | Request | Body | Expected |
|---|---------|------|----------|
| 82 | `GET /api/customers/-1` | – | 400, `fieldErrors.id` = "Id must be a positive number" |
| 83 | `POST /api/customers` | `{"fullName":"<script>alert(1)</script>","email":"safe@mail.com","personalNumber":"01234567891"}` | 400, `fieldErrors` for fullName |
| 84 | `POST /api/customers` | `{"fullName":"Ana Beridze","email":"ana@mail","personalNumber":"01234567891","phone":"call me"}` | 400, `fieldErrors` for email and phone |
| 85 | `POST /api/applications` | `{"customerId":-1,"loanProductId":0,"amount":5000.999,"termMonths":12}` | 400, `fieldErrors` for customerId, loanProductId, amount |
| 86 | `PUT /api/applications/3/reject` | `{"decisionComment":"<b>no</b>"}` | 400, `fieldErrors` for decisionComment |
| 87 | `GET /api/products/3/calculate?amount=0&termMonths=500` | – | 400, `fieldErrors` for amount and termMonths |
| 88 | `POST /api/document-types` | `{"name":"<script>"}` | 400, `fieldErrors` for name |
