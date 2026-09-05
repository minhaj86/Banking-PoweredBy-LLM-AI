# Placeholder Bank - AI assessment and limitations

Arithmetic, eligibility, product limits and policy thresholds belong to ordinary Java code. The model receives a financial projection and evidence references; it cannot modify rates, terms, arithmetic or decisions.

## Financial calculation

SEK money uses BigDecimal and exact PostgreSQL NUMERIC with explicit scale checks. API amounts are strings. Declared gross and net amounts are separate. Regular annual net income normalises by division by twelve; regular weekly income by 52/12, with HALF_EVEN rounding to cents. Variable/irregular and additional income, unknown payments, mixed currencies and conflicting evidence block approval pending correction.

Residual income = evidence-backed monthly net income − non-debt housing/living expenses − existing monthly debt repayments − proposed instalment. Expense input explicitly declares that debt repayments are excluded. Debt payments occur only in the obligations list. Missing is null, not zero. An empty declared debt list means an explicit declaration of no debts; verification and reviewer checks still apply.

The annuity uses monthly rate r = nominal annual rate / 12 with 34-digit HALF_EVEN intermediate precision. For r > 0, payment = P × r × (1+r)^n / ((1+r)^n − 1). For zero rate, payment = P/n. Scheduled cash amounts round HALF_EVEN to two decimals; each period rounds interest, allocates the remaining payment to principal, and the last period repays exactly the remaining principal plus that period's interest. Scheduled principal therefore sums exactly to approved principal and closes at zero. Period numbers are indicative; there are no invented contractual dates.

The fee-free demo uses an 8% nominal rate and 11% stress rate, minimum SEK 5,000 residual and maximum 0.40 debt-service/income ratio. Every value is configurable and frozen by policy version. These are demonstration thresholds, not validated legal standards or a credit-score/PD model. Nominal interest is not APR.

## Evidence and AI boundary

The built-in PDF parser extracts embedded text first. It accepts strict financial label lines (for example net_income=30000.00 SEK MONTHLY), records page/line provenance and excludes all other prose. Only those numeric financial snippets can reach the advisory model; names, identity numbers, nationality, PEP, addresses and unrelated KYC answers are absent. OCR has a replaceable adapter and defaults to explicitly unreliable/unavailable. Unsupported documents require reviewer transcription or replacement. Manual transcription creates a new version; it does not rewrite a prior assessment.

APPLICANT_DECLARED, DOCUMENT_EXTRACTED, REVIEWER_CONFIRMED and EXTERNALLY_VERIFIED are distinct provenance values. Extraction is not authentication. Current demo calculations are evidence-backed before human confirmation; an approval requires explicit reviewer confirmation of those facts, authenticity limitations, expenses and debts, plus separate external-verification gates. Synthetic checks are labelled and do not establish real verification.

Provider adapters: deterministic mock, Spring AI OpenAI and Spring AI Ollama. External model names are operator supplied. Model endpoint, timeout, key and data-handling approval are deployment configuration. Complete unredacted payslips/tax returns are never sent to an external provider by the built-in path. No RAG, cross-applicant retrieval, tools, SQL credentials, ledger client or payment capability is given to the model.

Prompt financial-advisory-v1 and schema advisory-v1 require a single structured result with outcome, finding codes/evidence references, inconsistencies, missing-information codes, limitations and reviewer questions. Enum-based material findings avoid free-text unsupported financial claims. Every source tuple must exactly match supplied document ID, page, line and financial excerpt. Unknown JSON fields, references, absent required collections, unsupported favourable outcomes, refusals, oversized outputs and truncation fail processing.

Advisory outcomes are SUPPORTS_REVIEW_FOR_APPROVAL, AFFORDABILITY_CONCERNS and INSUFFICIENT_EVIDENCE. None creates a final decision. Policy concerns cannot be overridden in this initial product. Missing evidence, expired verification and outdated terms cannot be waived. A provider's COMPLIANCE REVIEW_REQUIRED result can be resolved separately by a compliance reviewer; it never changes credit arithmetic. PEP is not an underwriting feature.

## Failure behavior and boundaries

Durable jobs bound retries with jitter and leases. External AI uses a per-instance rate limit and circuit breaker; retries occur at the job boundary, not an unbounded nested provider loop. A timeout or unavailable provider leaves no final credit decision. Exhaustion yields ASSESSMENT_FAILED. Model output is stored only after validation and a fenced current-revision check.

Hostile document instructions and URLs are excluded from the supported parser's output and are also described as untrusted data in the system prompt. The model has no tools. A labelled forged number still requires human source/authenticity review; neither a parser nor an LLM can establish document genuineness.

Production still requires evaluation against representative financial documents, provider-specific structured-output tests, bias/privacy evaluation, real verification providers and operational human-review procedures. No probability of repayment, calibrated confidence, document authenticity certification or lending readiness is claimed.

