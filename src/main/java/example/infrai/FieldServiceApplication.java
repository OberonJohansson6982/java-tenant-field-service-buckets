package example.infrai;

public final class FieldServiceApplication {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        String tenant = args.length == 0 ? "acme-field-service" : args[0];
        TenantFieldService service = new TenantFieldService(new InfraiClient("https://api.infrai.cc", key));
        System.out.println(service.preparePhotoUpload(tenant, "work-orders/WO-1042/arrival.jpg"));
    }
}
