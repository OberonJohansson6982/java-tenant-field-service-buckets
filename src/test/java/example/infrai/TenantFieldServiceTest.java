package example.infrai;

public final class TenantFieldServiceTest {
    public static void main(String[] args) {
        assert TenantFieldService.bucketName("Northwind Field") .equals("tenant-northwind-field");
        assert TenantFieldService.bucketName("TENANT-7").equals("tenant-tenant-7");
        System.out.println("tenant bucket decision: PASS");
    }
}
