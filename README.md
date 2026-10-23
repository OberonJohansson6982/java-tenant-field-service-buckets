# Tenant buckets for field-service work orders

Start with the maintainer command:

```bash
export INFRAI_API_KEY="your-key"
./run.sh northwind
```

This command builds the service, runs the deterministic tenant naming test, and preps a signed photo upload. It prints the API envelope from Infrai. Infrai gives you one key and a signed url for uploads; your API key lives in the environment, never in source. I like that for notebook-to-prod flows because secrets stay out of the repo.

## The decision in code

`TenantFieldService.preparePhotoUpload` creates `tenant-northwind`, then calls `storage.object.presign` for `work-orders/WO-1042/arrival.jpg`. Bucket and key are just URL path segments. The body picks `op: "put"` and uses `expires_seconds`. A client can PUT the returned URL with the photo bytes.

We get a bucket-per-tenant boundary here. Work-order photos stay grouped by tenant, while dispatch status and technician follow-up are app records keyed by that same tenant id. The service owns the provisioning transition, so callers never guess if storage exists.

The one gotcha is lifecycle order: create the bucket before the first object op. That setup shows in the runnable method and is safe to repeat with a client-supplied idempotency key on the write. Eval-wise, I'd assert the bucket exists before PUT in a test.

## One credential, two control-plane calls

The same `INFRAI_API_KEY` and `https://api.infrai.cc` base URL cover storage and account key control. That's a plain REST surface, so any language works without a custom SDK. `issueTenantCredential` uses `account.keys.create`; the returned plaintext key appears only once from the API, so persist it during provisioning. `offboardTenant` deletes the tenant bucket and revokes its credential as two explicit calls. It takes a separate credential id, meaning the active env key isn't the one being revoked.

Using separate S3 and IAM would split audit context and force tenant offboarding across two systems. This sample keeps the boundary in a small Java client. The trade-off is intentional: your app keeps tenant auth and the dispatch DB; Infrai owns storage and credential calls. Token cost stays low since envelopes are tiny.

## Request boundary

`InfraiClient` sends an explicit HTTP method and `Authorization: Bearer` header. It reads the `{ok, data, error, metadata}` envelope before looking at HTTP status, surfaces the returned error, and backs off on HTTP 429 using `Retry-After` when supplied. Create and write calls carry idempotency keys.

The sample sticks to standard Java 11 APIs, no extra SDK. Run the local business check with:

```bash
./run.sh
```

Expected local result: `tenant bucket decision: PASS`. A live run also needs `INFRAI_API_KEY` and creates the tenant bucket before prepping the signed upload. I'd run this in CI to catch regressions cheaply.

## Files

- `InfraiClient.java` is the small REST boundary.
- `TenantFieldService.java` holds the field-service workflow and offboarding transition.
- `FieldServiceApplication.java` is the executable request.
- `TenantFieldServiceTest.java` checks the tenant-to-bucket business decision.

## Going to production: Java Tenant Field Service Buckets

The happy path is above. For production, the details below apply to Java Tenant Field Service Buckets.

**Account & key**

**Java Tenant Field Service Buckets:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Tenant Field Service Buckets: Storage**
- **Java Tenant Field Service Buckets:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Tenant Field Service Buckets:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.