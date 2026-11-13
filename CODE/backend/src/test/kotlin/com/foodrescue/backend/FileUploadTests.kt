// AC-01..12: FileUploadTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import com.foodrescue.backend.support.TestAuthKeys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

class FileUploadTests : AuthTestFixtures() {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun padded(header: ByteArray, size: Int) = header + ByteArray(size - header.size) { (it % 199).toByte() }
    private val png = padded(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), 2048)
    private val jpeg = padded(bytes(0xFF, 0xD8, 0xFF, 0xE0), 1500)
    private val webp = padded("RIFF".toByteArray() + bytes(0x10, 0, 0, 0) + "WEBPVP8 ".toByteArray(), 900)
    private val pdf = padded("%PDF-1.7\n".toByteArray(), 3000)
    private val max = 10L * 1024 * 1024

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun user(email: String = "user@example.test"): String { register(email); return login(email)["accessToken"].asString() }
    private fun rows() = jdbc.queryForObject("select count(*) from stored_file", Int::class.java)!!
    private fun fileError(r: java.net.http.HttpResponse<String>): String {
        assertEquals(400, r.statusCode(), r.body()); val body = json(r)
        assertEquals("VALIDATION_FAILED", body["code"].asString()); assertEquals("file", body["fieldErrors"][0]["field"].asString())
        return body["fieldErrors"][0]["message"].asString()
    }

    @Test fun `AC-01 AC-05 each allowed type uploads with detected mime checksum and downloads intact`() {
        val token = user(); val before = storedFileCount()
        for ((content, mime) in listOf(png to "image/png", jpeg to "image/jpeg", webp to "image/webp", pdf to "application/pdf")) {
            val r = upload(content, token, "Ảnh hóa đơn.dat", "application/octet-stream")
            assertEquals(201, r.statusCode(), r.body())
            val body = json(r); val id = body["id"].asString()
            assertEquals(mime, body["mimeType"].asString()); assertEquals(content.size.toLong(), body["sizeBytes"].asLong())
            assertEquals(sha256(content), body["checksum"].asString()); assertEquals("Ảnh hóa đơn.dat", body["originalName"].asString())
            assertFalse(body.has("storageKey")); assertFalse(r.body().contains(TestAuthKeys.fileRoot.toString()))
            assertTrue(r.headers().firstValue("Location").get().endsWith("/api/v1/files/$id"))
            assertEquals(200, call("/api/v1/files/$id", token = token).statusCode())
            val d = download("/api/v1/files/$id/content", token)
            assertEquals(200, d.statusCode()); assertArrayEquals(content, d.body())
            assertEquals(mime, d.headers().firstValue("Content-Type").get())
            assertTrue(d.headers().firstValue("Content-Disposition").get().startsWith("attachment;"))
            assertTrue(d.headers().firstValue("Content-Disposition").get().contains("filename*=UTF-8''"))
            assertEquals("nosniff", d.headers().firstValue("X-Content-Type-Options").get())
            val cache = d.headers().firstValue("Cache-Control").get(); assertTrue(cache.contains("no-store") && cache.contains("private"), cache)
        }
        assertEquals(before + 4, storedFileCount())
        val key = jdbc.queryForList("select storage_key from stored_file", String::class.java).first()!!
        assertTrue(key.matches(Regex("[a-f0-9]{64}"))); assertTrue(Files.isRegularFile(TestAuthKeys.fileRoot.resolve(key.substring(0, 2)).resolve(key.substring(2, 4)).resolve(key)))
        assertEquals(4, jdbc.queryForObject("select count(*) from stored_file f join account a on a.id=f.uploaded_by_id where a.login_identifier='user@example.test'", Int::class.java))
    }

    @Test fun `AC-02 content decides the type and invalid uploads leave nothing behind`() {
        val token = user(); val before = storedFileCount()
        assertEquals("Unsupported file type", fileError(upload("<svg xmlns='http://www.w3.org/2000/svg'><script>x</script></svg>".toByteArray(), token, "a.png", "image/png")))
        assertEquals("Unsupported file type", fileError(upload("<html><script>alert(1)</script></html>".toByteArray(), token, "a.jpg", "image/jpeg")))
        assertEquals("Unsupported file type", fileError(upload(padded("MZ".toByteArray(), 500), token, "photo.jpg", "image/jpeg")))
        assertEquals("Unsupported file type", fileError(upload(padded("GIF89a".toByteArray(), 500), token, "a.gif", "image/gif")))
        assertEquals("File is empty", fileError(upload(ByteArray(0), token, "empty.png", "image/png")))
        assertEquals("File is required", fileError(upload(png, token, partName = null)))
        assertEquals("File is required", fileError(upload(png, token, partName = "document")))
        assertEquals(400, call("/api/v1/files", "POST", mapOf("file" to "x"), token).statusCode())
        assertEquals(0, rows()); assertEquals(before, storedFileCount())
        // a real image with a misleading name/declared type is stored with the detected type
        assertEquals("image/png", json(upload(png, token, "report.pdf", "application/pdf"))["mimeType"].asString())
    }

    @Test fun `AC-03 AC-10 size limit is exact and uploads above 16 KB pass the middleware`() {
        val token = user()
        assertEquals(201, upload(padded(png.copyOf(8), 100_000), token, "big.png").statusCode())
        assertEquals(201, upload(padded(png.copyOf(8), max.toInt()), token, "max.png").statusCode())
        val before = storedFileCount(); val rowsBefore = rows()
        assertTrue(fileError(upload(padded(png.copyOf(8), max.toInt() + 1), token, "over.png")).startsWith("File exceeds"))
        assertEquals(before, storedFileCount()); assertEquals(rowsBefore, rows())
        // JSON endpoints keep the 16 KB cap
        assertEquals(413, call("/api/v1/auth/register", "POST", "x".repeat(17000)).statusCode())
    }

    @Test fun `AC-04 AC-08 unattached files are visible to the uploader only and ids cannot be probed`() {
        val owner = user(); val other = user("other@example.test")
        jdbc.update("insert into account_role(id,created_at,updated_at,version,account_id,role) select ?,now(),now(),0,id,'ADMIN' from account where login_identifier='other@example.test'", UUID.randomUUID())
        val id = json(upload(png, owner, "a.png"))["id"].asString()
        val missing = UUID.randomUUID()
        for (path in listOf("/api/v1/files/$id", "/api/v1/files/$id/content")) {
            val denied = call(path, token = other); val absent = call(path.replace(id, missing.toString()), token = other)
            assertEquals(404, denied.statusCode()); assertEquals(404, absent.statusCode())
            assertEquals(json(absent)["code"].asString(), json(denied)["code"].asString()); assertEquals("NOT_FOUND", json(denied)["code"].asString())
            assertEquals(json(absent)["message"].asString(), json(denied)["message"].asString())
            assertEquals(401, call(path).statusCode())
        }
        assertEquals(401, upload(png, null).statusCode()); assertEquals(401, upload(png, "not-a-jwt").statusCode())
        assertEquals(400, call("/api/v1/files/not-a-uuid", token = owner).statusCode())
        assertEquals(400, call("/api/v1/files/not-a-uuid/content", token = owner).statusCode())
    }

    @Test fun `AC-06 hostile file names never influence the storage path`() {
        val token = user(); val before = storedFileCount()
        val names = mapOf("../../../../etc/passwd" to "passwd", "..\\..\\win.ini" to "win.ini", "/abs/path.png" to "path.png", "..%2f..%2fx.png" to "..%2f..%2fx.png")
        for ((name, expected) in names) {
            val r = upload(png, token, name, "image/png"); assertEquals(201, r.statusCode(), name)
            assertEquals(expected, json(r)["originalName"].asString())
        }
        val long = upload(png, token, "a".repeat(400) + ".png", "image/png"); assertEquals(255, json(long)["originalName"].asString().length)
        assertEquals(before + 5, storedFileCount())
        Files.walk(TestAuthKeys.fileRoot).use { paths -> paths.filter { Files.isRegularFile(it) }.forEach {
            assertTrue(it.normalize().startsWith(TestAuthKeys.fileRoot)); assertTrue(it.fileName.toString().matches(Regex("[a-f0-9]{64}")))
        } }
    }

    @Test fun `AC-07 database failure removes the stored bytes`() {
        val token = user(); val before = storedFileCount()
        jdbc.execute("CREATE FUNCTION test_fail_file() RETURNS trigger LANGUAGE plpgsql AS \$\$ BEGIN RAISE EXCEPTION 'simulated' USING ERRCODE='23514'; END; \$\$")
        jdbc.execute("CREATE TRIGGER test_fail_file BEFORE INSERT ON stored_file FOR EACH ROW EXECUTE FUNCTION test_fail_file()")
        try {
            val r = upload(png, token, "a.png"); assertNotEquals(201, r.statusCode())
            assertEquals(0, rows()); assertEquals(before, storedFileCount())
        } finally { jdbc.execute("DROP TRIGGER test_fail_file ON stored_file"); jdbc.execute("DROP FUNCTION test_fail_file()") }
    }

    @Test fun `AC-09 upload quota per account returns 429 with retry after`() {
        val token = user()
        repeat(30) { assertEquals(201, upload(jpeg, token, "j$it.jpg").statusCode(), "upload $it") }
        val limited = upload(jpeg, token, "over.jpg")
        assertEquals(429, limited.statusCode()); assertEquals("RATE_LIMITED", json(limited)["code"].asString())
        assertTrue(limited.headers().firstValue("Retry-After").isPresent)
        assertEquals(201, upload(jpeg, user("other@example.test"), "o.jpg").statusCode(), "quota is per account")
    }

    @Test fun `AC-12 attached files fall back to uploader and admin until a policy exists`() {
        val owner = user(); val stranger = user("stranger@example.test"); val admin = user("admin@example.test")
        jdbc.update("insert into account_role(id,created_at,updated_at,version,account_id,role) select ?,now(),now(),0,id,'ADMIN' from account where login_identifier='admin@example.test'", UUID.randomUUID())
        val fileId = UUID.fromString(json(upload(pdf, owner, "license.pdf"))["id"].asString())
        val storeId = UUID.randomUUID(); val applicationId = UUID.randomUUID()
        jdbc.update("insert into store(id,created_at,updated_at,version,owner_id,name,address,latitude,longitude,timezone,status) select ?,now(),now(),0,id,'S','A',10.7,106.6,'Asia/Ho_Chi_Minh','PENDING_APPROVAL' from account where login_identifier='user@example.test'", storeId)
        jdbc.update("insert into partner_application(id,created_at,updated_at,version,store_id,applicant_id,revision,business_details,status,submitted_at) select ?,now(),now(),0,?,id,1,'{}','SUBMITTED',now() from account where login_identifier='user@example.test'", applicationId, storeId)
        jdbc.update("insert into attachment(id,created_at,updated_at,version,file_id,partner_application_id) values (?,now(),now(),0,?,?)", UUID.randomUUID(), fileId, applicationId)
        assertEquals(200, download("/api/v1/files/$fileId/content", owner).statusCode())
        assertEquals(200, download("/api/v1/files/$fileId/content", admin).statusCode())
        assertEquals(404, download("/api/v1/files/$fileId/content", stranger).statusCode())
    }
}
