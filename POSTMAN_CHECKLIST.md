# Postman test checklist

Start the database and the app first:

```bash
docker compose up -d      # PostgreSQL on localhost:5433
./mvnw spring-boot:run    # API on http://localhost:8080
```

On the first start the app inserts sample data: document types `1 ID Card`, `2 Salary Certificate`,
`3 Bank Statement` and products `1 Consumer Loan` (500–20000, 3–48 months), `2 Mortgage` (20000–500000, 60–240 months).

For every request with a JSON body set the header `Content-Type: application/json`
(Postman: Body → raw → JSON). Run the requests in this order, because later ones use ids created earlier.

## Customers

| # | Request | Body | Expected |
|---|---------|------|----------|
| 1 | `POST /api/customers` | `{"fullName":"Ana Beridze","email":"ana@mail.com","personalNumber":"01234567890","phone":"555123456"}` | 201, customer with `id` 1 |
| 2 | `POST /api/customers` | same body again | 409, "email ... already exists" |
| 3 | `POST /api/customers` | `{"fullName":"","email":"bad","personalNumber":"12"}` | 400, `fieldErrors` for fullName, email, personalNumber |
| 4 | `GET /api/customers/1` | – | 200 |
| 4a | `GET /api/customers` | – | 200, list with the customer from step 1 |
| 5 | `GET /api/customers/99` | – | 404 |
| 6 | `GET /api/customers/abc` | – | 400, invalid value for `id` |

## Products

| # | Request | Body | Expected |
|---|---------|------|----------|
| 7 | `GET /api/products` | – | 200, the 2 sample products |
| 8 | `POST /api/products` | `{"name":"Auto Loan","interestRate":11.5,"minAmount":1000,"maxAmount":50000,"minTermMonths":6,"maxTermMonths":60,"requiredDocumentTypeIds":[1]}` | 201, product with `id` 3 |
| 9 | `POST /api/products` | same body again | 409, name already exists |
| 10 | `POST /api/products` | same but `"name":"Bad","minAmount":9000,"maxAmount":500` | 400, minimum amount greater than maximum |
| 11 | `POST /api/products` | same but `"name":"Bad2","requiredDocumentTypeIds":[77]` | 404, document type not found |
| 12 | `PUT /api/products/3` | `{"name":"Auto Loan","interestRate":12,"minAmount":1000,"maxAmount":60000,"minTermMonths":6,"maxTermMonths":72,"requiredDocumentTypeIds":[1,2]}` | 200, updated values |
| 13 | `PUT /api/products/3` | same but `"name":"Mortgage"` | 409, name already exists |
| 14 | `DELETE /api/products/2` | – | 204 (Mortgage has no applications) |
| 15 | `DELETE /api/products/99` | – | 404 |

## Applications

| # | Request | Body | Expected |
|---|---------|------|----------|
| 16 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":5000,"termMonths":12}` | 201, `id` 1, status `SUBMITTED` |
| 17 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":7000,"termMonths":24}` | 201, `id` 2 |
| 18 | `POST /api/applications` | `{"customerId":1,"loanProductId":3,"amount":2000,"termMonths":12}` | 201, `id` 3 |
| 19 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":50,"termMonths":12}` | 400, amount outside 500–20000 (rule 1) |
| 20 | `POST /api/applications` | `{"customerId":1,"loanProductId":1,"amount":5000,"termMonths":100}` | 400, term outside 3–48 (rule 2) |
| 21 | `POST /api/applications` | `{"customerId":99,"loanProductId":1,"amount":5000,"termMonths":12}` | 404, customer not found |
| 22 | `POST /api/applications` | `{}` | 400, `fieldErrors` for all four fields |
| 23 | `GET /api/applications/1` | – | 200 |
| 24 | `GET /api/applications?page=0&size=2&sort=amount,desc` | – | 200, 2 items, highest amount first, `page.totalElements` 3 |
| 25 | `GET /api/applications?status=SUBMITTED` | – | 200, only SUBMITTED |
| 26 | `GET /api/applications?status=WRONG` | – | 400 |
| 27 | `GET /api/applications?sort=wrongField` | – | 400, unknown sort field |
| 28 | `DELETE /api/products/1` | – | 409, product has applications |

## Documents (file upload)

Postman: Body → form-data. Key `file` with type **File**, key `documentTypeId` with type Text.

| # | Request | form-data | Expected |
|---|---------|-----------|----------|
| 29 | `POST /api/applications/1/documents` | `file` = a PDF, `documentTypeId` = 1 | 201, document info |
| 30 | `POST /api/applications/1/documents` | `file` = a PNG or JPG, `documentTypeId` = 2 | 201 |
| 31 | `POST /api/applications/1/documents` | `file` = a .txt file, `documentTypeId` = 1 | 400, only PDF, JPG, PNG |
| 32 | `POST /api/applications/1/documents` | `file` = a file larger than 5MB | 400, file too large |
| 33 | `POST /api/applications/1/documents` | only `file`, no `documentTypeId` | 400, missing parameter |
| 34 | `POST /api/applications/99/documents` | valid file, `documentTypeId` = 1 | 404, application not found |
| 35 | `POST /api/applications/1/documents` | valid file, `documentTypeId` = 99 | 404, document type not found |
| 36 | `GET /api/applications/1` | – | 200, `documents` lists the 2 uploads; files exist in the `uploads/` folder |

## Decisions (approve / reject / cancel)

| # | Request | Body | Expected |
|---|---------|------|----------|
| 37 | `PUT /api/applications/2/approve` | – | 200, status `APPROVED` |
| 38 | `PUT /api/applications/2/approve` | – | 409, already APPROVED (rule 4) |
| 39 | `PUT /api/applications/3/reject` | `{"decisionComment":"  "}` | 400, reason required (rule 5) |
| 40 | `PUT /api/applications/3/reject` | `{"decisionComment":"Income too low"}` | 200, status `REJECTED`, comment saved |
| 41 | `DELETE /api/applications/3` | – | 409, REJECTED cannot be cancelled (rule 4) |
| 42 | `DELETE /api/applications/1` | – | 204, its files are removed from `uploads/` |
| 43 | `GET /api/applications/1` | – | 404 |
| 44 | `GET /api/applications?status=APPROVED` | – | 200, only application 2 |
