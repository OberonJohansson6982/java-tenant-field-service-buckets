package example.infrai;

import java.io.IOException;

public final class TenantFieldService {
    private final InfraiClient infrai;

    public TenantFieldService(InfraiClient infrai) { this.infrai = infrai; }

    public String preparePhotoUpload(String tenant, String key) throws IOException, InterruptedException {
        String bucket = bucketName(tenant);
        // Provisioning is explicit: object operations follow bucket creation.
        infrai.call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + bucket + "\"}");
        // The same INFRAI_API_KEY is used for storage and account key control.
        // Capability marker: storage.object.presign.
        return infrai.call("POST", "/v1/storage/object/presign/" + bucket + "/" + key,
                "{\"op\":\"put\",\"expires_seconds\":600,\"content_type\":\"image/jpeg\",\"idempotency_key\":\"" + tenant + "-" + key + "\"}");
    }

    public String issueTenantCredential(String tenant) throws IOException, InterruptedException {
        return infrai.call("POST", "/v1/account/keys/create",
                "{\"name\":\"tenant-" + tenant + "\",\"scopes\":[\"storage.object.put\"],\"idempotency_key\":\"credential-" + tenant + "\"}");
    }

    public String offboardTenant(String tenant, String credentialId) throws IOException, InterruptedException {
        String bucket = bucketName(tenant);
        infrai.call("DELETE", "/v1/storage/bucket/delete/" + bucket, null);
        return infrai.call("DELETE", "/v1/account/keys/revoke/" + credentialId, null);
    }

    public static String bucketName(String tenant) {
        return "tenant-" + tenant.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
}
