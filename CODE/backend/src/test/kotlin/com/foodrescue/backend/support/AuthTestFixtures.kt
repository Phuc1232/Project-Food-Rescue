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
    }
    /** T3.1: uploaded test files live in a throwaway directory, never in the developer's real storage. */
    val fileRoot: java.nio.file.Path=Files.createTempDirectory("foodrescue-test-files")
}
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MailTestConfiguration::class)
abstract class AuthTestFixtures {
    companion object {
        private val postgres=PostgreSQLContainer("postgres:16-alpine").apply { start() }
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword); TestAuthKeys.configure(r)
        }
    }
    @Value("\${local.server.port}") var port: Int=0
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var mail: RecordingAuthMailSender
    @Value("\${server.servlet.context-path:}") var contextPath: String=""
    private val client=HttpClient.newHttpClient()
    val password="Long-password-123!"
    @BeforeEach fun clearDatabase() {
        val tables=jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename <> 'flyway_schema_history'",String::class.java).filterNotNull()
        jdbc.execute("TRUNCATE "+tables.joinToString(",") { "\""+it+"\"" }+" CASCADE")
        mail.messages.clear(); mail.fail=false
    }
    fun call(path: String,method: String="GET",body: Any?=null,token: String?=null): HttpResponse<String> {
        val b=HttpRequest.newBuilder(URI("http://localhost:"+port+contextPath+path)).header("Content-Type","application/json")
        token?.let { b.header("Authorization","Bearer "+it) }
        b.method(method,if(body==null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(if(body is String) body else mapper.writeValueAsString(body)))
        return client.send(b.build(),HttpResponse.BodyHandlers.ofString())
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
        return client.send(b.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString())
    }
    fun download(path: String,token: String?): HttpResponse<ByteArray> {
        val b=HttpRequest.newBuilder(URI("http://localhost:"+port+contextPath+path)); token?.let { b.header("Authorization","Bearer "+it) }
        return client.send(b.GET().build(),HttpResponse.BodyHandlers.ofByteArray())
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
