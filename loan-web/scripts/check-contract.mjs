import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
const contract = JSON.parse(
  readFileSync("../.iam/loan-openapi.json", "utf8").replace(/^\uFEFF/, ""),
);
const required = {
  "/api/v1/products/current": ["get"],
  "/api/v1/drafts": ["post"],
  "/api/v1/drafts/{id}": ["put"],
  "/api/v1/loan-applications": ["get", "post"],
  "/api/v1/loan-applications/{id}": ["get", "put"],
  "/api/v1/loan-applications/{id}/submit": ["post"],
  "/api/v1/loan-applications/{id}/decisions": ["post"],
  "/api/v1/loan-applications/{id}/information-requests": ["get", "post"],
  "/api/v1/loan-applications/{id}/documents": ["get", "post"],
  "/api/v1/loan-applications/{id}/timeline": ["get"],
  "/api/v1/loan-applications/{id}/assessment": ["get"],
  "/api/v1/loans/{id}": ["get"],
  "/api/v1/loans/{id}/terms": ["get"],
  "/api/v1/loans/{id}/repayment-schedule": ["get"],
  "/api/v1/loans/{id}/balance": ["get"],
  "/api/v1/staff/summary": ["get"],
  "/api/v1/staff/configuration": ["get"],
  "/api/v1/operations/jobs": ["get"],
};
for (const [path, methods] of Object.entries(required))
  for (const method of methods)
    assert.ok(contract.paths[path]?.[method], `${method} ${path}`);
const schemas = contract.components.schemas;
assert.equal(
  schemas.ApplicationInput.properties.requestedAmount.type,
  "string",
);
assert.equal(schemas.Income.properties.netAmount.type, "string");
assert.equal(schemas.TermsView.properties.principal.type, "string");
for (const field of [
  "id",
  "version",
  "state",
  "revision",
  "application",
  "loanId",
  "decisionExplanation",
  "updatedAt",
])
  assert.ok(schemas.ApplicationView.properties[field], field);
for (const [path, method] of [
  ["/api/v1/drafts/{id}", "put"],
  ["/api/v1/loan-applications/{id}/submit", "post"],
  ["/api/v1/loan-applications/{id}/decisions", "post"],
])
  for (const name of ["If-Match", "Idempotency-Key"])
    assert.ok(
      contract.paths[path][method].parameters.some(
        (p) => p.name === name && p.required,
      ),
      `${path} ${name}`,
    );
console.log(
  `Verified ${Object.keys(required).length} real OpenAPI paths, immutable version headers and decimal-string contracts.`,
);
