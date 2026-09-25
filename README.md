# ERP Reimbursement Backend

Spring Boot 4.1.1 + Java 25 + MySQL backend designed from the supplied Angular ERP Frontend.

## Workflow

### Reimbursement
Employee submits -> PENDING_MANAGER_APPROVAL -> Manager approves -> PENDING_FINANCE_VERIFICATION -> Finance approves -> PENDING_PAYMENT -> Finance pays -> PAID.

Manager or Finance rejection requires a remark and moves the claim to REJECTED. Every manager/finance action is stored in `approval_history`.

### Advance
Employee submits -> PENDING_MANAGER_APPROVAL -> Manager approves -> PENDING_FINANCE_PAYMENT -> Finance pays -> PAID.

## Demo accounts
All seeded demo accounts use password `Password123!`.
- Employee: user@example.com / Employee
- Manager: manager@example.com / Manager
- Finance: finance@example.com / Finance
- Admin: admin@example.com / Admin

For login, the frontend supplies the email/username and selected role.

## Setup
1. Create MySQL database: `CREATE DATABASE erp_reimbursement;`
2. Edit `src/main/resources/application.properties` and set `spring.datasource.password`.
3. Replace `app.jwt.secret` with a random secret of at least 32 characters.
4. Run with Java 25 and Maven: `mvn clean spring-boot:run`.

## Core API
- POST `/api/auth/login`
- POST `/api/auth/signup`
- POST `/api/auth/forgot-password/send-otp`
- POST `/api/auth/forgot-password/reset`
- GET `/api/users/me`
- POST `/api/reimbursements` multipart: JSON part `claim`, files `bills`, `evidence`
- GET `/api/reimbursements/mine`
- GET `/api/reimbursements/manager/claims`
- GET `/api/reimbursements/manager/pending`
- PUT `/api/reimbursements/{id}/manager/approve`
- PUT `/api/reimbursements/{id}/manager/reject` body `{ "remark": "..." }`
- GET `/api/reimbursements/finance/pending`
- PUT `/api/reimbursements/{id}/finance/approve`
- PUT `/api/reimbursements/{id}/finance/reject` body `{ "remark": "..." }`
- PUT `/api/reimbursements/{id}/pay` body `{ "paymentType": "Bank Transfer", "transactionReference": "TXN-..." }`
- GET `/api/reimbursements/attachments/{attachmentId}`
- POST `/api/advances`
- GET `/api/advances/mine`
- GET `/api/advances/manager/all`
- GET `/api/advances/manager/pending`
- PUT `/api/advances/{id}/manager/approve`
- PUT `/api/advances/{id}/manager/reject`
- GET `/api/advances/finance/pending`
- PUT `/api/advances/{id}/pay`
- GET `/api/admin/users`
- POST `/api/admin/users`
- PUT `/api/admin/users/{id}`
- DELETE `/api/admin/users/{id}`
- GET `/api/masters/public`

## Important frontend integration note
The supplied Angular project currently stores demo claims and advances in localStorage. This backend deliberately uses MySQL and REST instead. The Angular services must be changed from localStorage operations to these APIs. Do not keep both stores as sources of truth.
