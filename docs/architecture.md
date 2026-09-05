# Placeholder Bank - Architecture

The root Maven parent retains Java 21, the wrapper and shared pinned versions. ledger-core remains independently deployable; loan-service adds no dependency on ledger entities, repositories or database credentials. Spring Boot 3.5.16 is retained. Spring AI 1.1.2, AWS SDK 2.31.78 and PDFBox 3.0.5 are pinned in the parent; other shared build versions remain unchanged.

~~~mermaid
flowchart LR
    Applicant -->|JWT| API[Loan REST API]
    Officer -->|Explicit review| API
    API --> DB[(Loan PostgreSQL)]
    API -->|Encrypted quarantine upload| S3[Private S3 store]
    DB --> Jobs[Durable jobs]
    Jobs --> Documents[Bounded document lane]
    Jobs --> Assessment[Bounded assessment lane]
    Jobs --> Provision[Bounded provisioning lane]
    Documents --> Scanner[ClamAV / unavailable state]
    Documents --> PDF[PDF text / OCR adapter / human transcription]
    Assessment --> Checks[KYC and bureau interfaces]
    Assessment --> Math[Deterministic BigDecimal arithmetic]
    Math --> AI[Restricted Spring AI advisory adapter]
    Provision -->|HTTP idempotent provision and lookup| Ledger[ledger-core API]
    Ledger --> LDB[(Ledger PostgreSQL)]
~~~

Ordinary state uses JPA repositories and immutable record DTOs. Explicit JDBC handles claims, audit metadata, queue admission and PostgreSQL row claiming. Controllers delegate to application, document, decision, compliance, loan and operations services. External adapters are meaningful interfaces; there is no generic CRUD framework.

Application drafts and immutable submitted revisions contain encrypted identity, employment, financial, KYC and privacy payloads. Every revision freezes document UUIDs and the product/policy configuration. Assessments preserve their revision, policy, documents, provider, model, schema/prompt version and execution duration. New evidence changes the editable draft and requires resubmission; old assessments remain unchanged.

Upload intent commits before object storage writes. Completion is verified before a document job is enqueued. This makes orphan candidates discoverable without listing other applicants' objects. Content uses generated UUID keys, AES-GCM encryption and private storage. No names occur in keys.

Submission and approval use short local transactions. Submission commits a frozen revision and job together. Approval commits the append-only decision, unique loan, immutable terms/schedule, audit and provisioning job together. No external call holds an application or decision lock. Worker snapshot and fenced result commits surround the external work.

~~~mermaid
sequenceDiagram
    participant O as Officer
    participant A as Loan API
    participant D as Loan database
    participant W as Provisioning worker
    participant L as Ledger HTTP API
    O->>A: Approve current revision + acknowledgements + version/key
    A->>D: Lock, gates, decision + loan + terms + job + audit
    D-->>A: Commit
    A-->>O: 202 approved, provisioning pending
    W->>D: Short claim transaction, lease + fence
    D-->>W: Commit and release locks
    W->>L: Lookup / provision stable external reference
    L-->>W: Same ASSET/DEBIT account
    W->>D: Fence check, unique mapping, awaiting agreement
~~~

The queue assumes at-least-once execution. Remote financial-account identity is protected by the ledger database, not process memory. Account creation never posts a journal. The ledger is the only authoritative balance source.

Performance controls include a global queue admission lock/bound, bounded HTTP/DB pools, independent worker lane limits, a bounded parser pool, AI rate limiting and a provider circuit breaker. Rate/circuit state is per replica; size aggregate provider quotas across replicas accordingly. No AI call is part of the ledger posting path.

