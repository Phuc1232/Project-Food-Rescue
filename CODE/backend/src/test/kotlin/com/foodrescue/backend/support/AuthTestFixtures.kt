// AC-01..26: AuthTestFixtures responsibility.
package com.foodrescue.backend.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.*
import org.springframework.context.annotation.Import
import org.springframework.test.context.*
import org.springframework.jdbc.core.JdbcTemplate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode
import com.foodrescue.backend.auth.TokenPurpose
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64
import java.net.URI
import java.net.http.*
object TestAuthKeys {
    private val dir=Files.createTempDirectory("foodrescue-test-keys")
    private val pair=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun pem(name: String,type: String,bytes: ByteArray): String {
        val path=dir.resolve(name)
        Files.writeString(path,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,byteArrayOf(10)).encodeToString(bytes)+"\n-----END "+type+"-----\n")
        path.toFile().deleteOnExit(); return path.toUri().toString()
    }
    private val privateKey=pem("private.pem","PRIVATE KEY",pair.private.encoded)
    private val publicKey=pem("public.pem","PUBLIC KEY",pair.public.encoded)
    fun configure(r: DynamicPropertyRegistry) {
        r.add("auth.jwt.private-key-location") { privateKey }; r.add("auth.jwt.public-key-location") { publicKey }
        r.add("auth.jwt.issuer") { "foodrescue" }; r.add("auth.jwt.audience") { "foodrescue-api" }
        r.add("auth.action-base-url") { "https://example.test" }; r.add("auth.mail-from") { "test@example.test" }
        r.add("spring.mail.host") { "localhost" }; r.add("spring.mail.port") { 1025 }
        r.add("auth.jwt.key-id") { "test-key" }; r.add("auth.rate-limit-key-secret") { "test-only-secret-at-least-32-bytes-long" }
        r.add("files.storage-dir") { fileRoot.toString() }
        r.add("pickup.code.active-key-id") { "test-v1" }
        r.add("pickup.code.keys") { "test-v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
        // T5.3: tests call OrderExpiryService directly with a fixed instant; no background expiry job.
        r.add("finance.ledger-job-enabled") { "false" }
        r.add("notifications.dispatch-job-enabled") { "false" }
        r.add("orders.no-show-job-enabled") { "false" }; r.add("orders.no-show-compensation-job-enabled") { "false" }
        r.add("orders.expiry-job-enabled") { "false" }; r.add("payments.reconcile-job-enabled") { "false" }
        // T6.1: prod-profile tests build the payOS adapter; dummy credentials and an unreachable URL, never the real provider.
        r.add("payos.client-id") { "test-client" }; r.add("payos.api-key") { "test-api-key" }; r.add("payos.checksum-key") { "test-checksum-key" }
        r.add("payos.base-url") { "http://127.0.0.1:9" }
    }
    /** T3.1: uploaded test files live in a throwaway directory, never in the developer's real storage. */
    val fileRoot: java.nio.file.Path=Files.createTempDirectory("foodrescue-test-files")
}
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MailTestConfiguration::class, NotificationTestConfiguration::class)
abstract class AuthTestFixtures {
    companion object {
        /** Tables holding migration-seeded reference data; never truncated. */
        val REFERENCE_TABLES=setOf("permission","food_category")
        val FINANCIAL_POLICY_SEED: String = AuthTestFixtures::class.java.getResource("/db/migration/V12__financial_policy_seed.sql")!!.readText()
            .let { it.substring(it.indexOf("INSERT INTO financial_policy")).trimEnd().removeSuffix(";") }
        private val postgres=PostgreSQLContainer("postgres:16-alpine").apply { start() }
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword); TestAuthKeys.configure(r)
        }
    }
    @Value("\${local.server.port}") var port: Int=0
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var notificationSender: RecordingNotificationSender
    @Autowired lateinit var mail: RecordingAuthMailSender
    @Value("\${server.servlet.context-path:}") var contextPath: String=""
    private val client=HttpClient.newHttpClient()
    val password="Long-password-123!"
    @BeforeEach fun clearDatabase() {
        // Reference data seeded by migrations (T3.4+) survives between tests.
        val tables=jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename <> 'flyway_schema_history'",String::class.java).filterNotNull()
            .filter { it !in REFERENCE_TABLES }
        jdbc.execute("TRUNCATE "+tables.joinToString(",") { "\""+it+"\"" }+" CASCADE")
        // T4.5: financial_policy is insert-only, so it is truncated and re-seeded with the exact INSERT of migration V12.
        jdbc.execute(FINANCIAL_POLICY_SEED)
        mail.messages.clear(); mail.fail=false; notificationSender.reset()
    }
    fun call(path: String,method: String="GET",body: Any?=null,token: String?=null): HttpResponse<String> {
        val b=HttpRequest.newBuilder(URI("http://localhost:"+port+contextPath+path)).header("Content-Type","application/json")
        token?.let { b.header("Authorization","Bearer "+it) }
        val reqPayload=if(body==null) null else if(body is String) body else mapper.writeValueAsString(body)
        b.method(method,if(reqPayload==null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(reqPayload))
        val t0=System.nanoTime()
        val resp=client.send(b.build(),HttpResponse.BodyHandlers.ofString())
        val elapsed=(System.nanoTime()-t0)/1_000_000L
        TestEvidenceCollector.recordHttp(method,path,!token.isNullOrBlank(),reqPayload,resp.statusCode(),resp.body(),elapsed,resp.headers().firstValue("Content-Type").orElse(null))
        return resp
    }
    fun json(response: HttpResponse<String>): JsonNode = mapper.readTree(response.body())
    /** T3.1: multipart/form-data with one part; partName=null sends a multipart body without that part. */
    fun upload(content: ByteArray,token: String?,filename: String="file.bin",declaredType: String="application/octet-stream",partName: String?="file"): HttpResponse<String> {
        val boundary="----foodrescue"+java.util.UUID.randomUUID().toString().replace("-","")
        val body=java.io.ByteArrayOutputStream()
        val head=if(partName==null) "--$boundary\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nx\r\n"
            else "--$boundary\r\nContent-Disposition: form-data; name=\"$partName\"; filename=\"$filename\"\r\nContent-Type: $declaredType\r\n\r\n"
        body.write(head.toByteArray(Charsets.UTF_8)); if(partName!=null) { body.write(content); body.write("\r\n".toByteArray()) }
        body.write("--$boundary--\r\n".toByteArray())
        val b=HttpRequest.newBuilder(URI("http://localhost:"+port+contextPath+"/api/v1/files")).header("Content-Type","multipart/form-data; boundary=$boundary")
        token?.let { b.header("Authorization","Bearer "+it) }
        val t0=System.nanoTime()
        val resp=client.send(b.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString())
        val elapsed=(System.nanoTime()-t0)/1_000_000L
        TestEvidenceCollector.recordHttp("POST","/api/v1/files",!token.isNullOrBlank(),"multipart/form-data (part=$partName, filename=$filename, type=$declaredType, bytes=${content.size})",resp.statusCode(),resp.body(),elapsed,resp.headers().firstValue("Content-Type").orElse(null))
        return resp
    }
    fun download(path: String,token: String?): HttpResponse<ByteArray> {
        val b=HttpRequest.newBuilder(URI("http://localhost:"+port+contextPath+path)); token?.let { b.header("Authorization","Bearer "+it) }
        val t0=System.nanoTime()
        val resp=client.send(b.GET().build(),HttpResponse.BodyHandlers.ofByteArray())
        val elapsed=(System.nanoTime()-t0)/1_000_000L
        TestEvidenceCollector.recordBinaryHttp("GET",path,!token.isNullOrBlank(),null,resp.statusCode(),resp.body().size,resp.headers().firstValue("Content-Type").orElse(null),elapsed)
        return resp
    }
    fun storedFileCount(): Long = Files.walk(TestAuthKeys.fileRoot).use { s -> s.filter { Files.isRegularFile(it) }.count() }
    fun register(email: String="user@example.test",verify: Boolean=true) {
        assertEquals(201,call("/api/v1/auth/register","POST",mapOf("email" to email,"password" to password,"displayName" to "User")).statusCode())
        if(verify) assertEquals(204,call("/api/v1/auth/verify-email","POST",mapOf("token" to mail.token(email,TokenPurpose.VERIFY_EMAIL))).statusCode())
    }
    fun login(email: String="user@example.test",passwordValue: String=password): JsonNode {
        val r=call("/api/v1/auth/login","POST",mapOf("email" to email,"password" to passwordValue)); assertEquals(200,r.statusCode(),r.body()); return json(r)
    }
    fun refresh(token: String)=call("/api/v1/auth/refresh","POST",mapOf("refreshToken" to token))
}
