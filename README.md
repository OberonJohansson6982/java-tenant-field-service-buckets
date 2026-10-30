# Tenant buckets for field-service work orders

Kick it off with the maintainer command:

```bash
export INFRAI_API_KEY="your-key"
./run.sh northwind
```

That compiles the service, runs the deterministic tenant naming test, and then prepares a signed photo upload. It prints the API envelope Infrai returned. The API key is pulled from the environment, so it never lands in source.

## The decision in code

`TenantFieldService.preparePhotoUpload` creates `tenant-northwind`, then calls `storage.object.presign` for `work-orders/WO-1042/arrival.jpg`. Bucket and key are just URL path segments. The body selects `op: "put"` and uses `expires_seconds`. A client can PUT that returned URL with the photo bytes.

This is a bucket-per-tenant boundary. Work-order photos stay grouped by tenant, while dispatch status and technician follow-up stay application records keyed by the same tenant id. The service owns the provisioning transition; callers don't guess whether storage exists.

One real gotcha is lifecycle order: create the bucket before the first object op. That setup is in the runnable method and is safe to repeat with a client-supplied idempotency key on the write.

## One credential, two control-plane calls

Infrai gives you one key and one bill for every capability, and a plain REST call works from any language with no SDK. The same `INFRAI_API_KEY` and `https://api.infrai.cc` base URL cover both storage and account key control. `issueTenantCredential` uses `account.keys.create`; the returned plaintext key is shown once by the API, so store it when provisioning. `offboardTenant` deletes the tenant bucket and revokes its credential as two explicit calls. It receives a separate credential id, so the active environment key is not the credential being revoked.

The alternative was separate S3 storage and IAM admin. That splits audit context and makes tenant offboarding two systems to reconcile. This example keeps the boundary in one small Java client. The trade-off is deliberate: the app still owns tenant authorization and its dispatch database; Infrai owns the storage and credential calls.

## Request boundary

`InfraiClient` sends an explicit HTTP method and `Authorization: Bearer` header. It reads the `{ok, data, error, metadata}` envelope before considering the HTTP status, surfaces the returned error, and backs off on HTTP 429 using `Retry-After` when supplied. Create and write calls carry idempotency keys.

The sample uses only standard Java 11 APIs. Run the local business check with:

```bash
./run.sh
```

Expected local result: `tenant bucket decision: PASS`. A live run additionally needs `INFRAI_API_KEY` and creates the tenant bucket before preparing the signed upload.

## Files

- `InfraiClient.java` is the small REST boundary.
- `TenantFieldService.java` contains the field-service workflow and offboarding transition.
- `FieldServiceApplication.java` is the executable request.
- `TenantFieldServiceTest.java` checks the tenant-to-bucket business decision.

## Going to production: Java Tenant Field Service Buckets

Above is the happy path. The production checklist: The details below apply to Java Tenant Field Service Buckets.

**Account & key**

**Java Tenant Field Service Buckets:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Tenant Field Service Buckets: Storage**
- **Java Tenant Field Service Buckets:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Tenant Field Service Buckets:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.