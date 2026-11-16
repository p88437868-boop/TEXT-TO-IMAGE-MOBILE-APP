import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proves the phone-side check against a real server, using the same Java APIs
 * (SHA256withECDSA + X509EncodedKeySpec) and the public key compiled into the app.
 *
 *   java VerifyProd.java <baseUrl> <key> <deviceCode> <path-to-LicenseServerPublicKey.kt>
 */
public final class VerifyProd {
    public static void main(String[] args) throws Exception {
        String base = args[0];
        String key = args[1];
        String device = args[2];
        String kotlinSource = args[3];

        String kotlin = java.nio.file.Files.readString(java.nio.file.Path.of(kotlinSource));
        Matcher matcher = Pattern.compile("ENCODED: String = \"([^\"]+)\"").matcher(kotlin);
        if (!matcher.find()) throw new IllegalStateException("no ENCODED key in " + kotlinSource);
        byte[] spki = Base64.getDecoder().decode(matcher.group(1));

        String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
        String body = "{\"key\":\"" + key + "\",\"deviceCode\":\"" + device + "\","
            + "\"deviceModel\":\"Java verify\",\"androidVersion\":\"14\",\"appVersion\":\"1.1.2\","
            + "\"nonce\":\"" + nonce + "\",\"usageCount\":0,\"reportId\":null}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v1/verify"))
            .header("Content-Type", "application/json")
            .header("User-Agent", "TextPicStudio/1.1.2")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("HTTP " + response.statusCode());
        if (response.statusCode() != 200) {
            System.out.println(response.body());
            System.exit(1);
        }

        String payload = field(response.body(), "payload");
        String sig = field(response.body(), "sig");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payload);
        byte[] sigBytes = Base64.getUrlDecoder().decode(sig);

        PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(payloadBytes);
        boolean signatureOk = verifier.verify(sigBytes);

        String json = new String(payloadBytes, StandardCharsets.UTF_8);
        boolean nonceOk = json.contains("\"nonce\":\"" + nonce + "\"");

        System.out.println("signature valid: " + signatureOk);
        System.out.println("nonce matches:   " + nonceOk);
        System.out.println("payload:         " + json);
        if (!signatureOk || !nonceOk) System.exit(2);

        // A tampered payload must fail.
        byte[] tampered = json.replace("\"status\":\"ACTIVE\"", "\"status\":\"ACTIVE\",\"x\":1").getBytes(StandardCharsets.UTF_8);
        verifier.initVerify(publicKey);
        verifier.update(tampered);
        boolean tamperedOk = verifier.verify(sigBytes);
        System.out.println("tampered accepted: " + tamperedOk + " (must be false)");
        if (tamperedOk) System.exit(3);
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) throw new IllegalStateException("missing " + name);
        return matcher.group(1);
    }
}
