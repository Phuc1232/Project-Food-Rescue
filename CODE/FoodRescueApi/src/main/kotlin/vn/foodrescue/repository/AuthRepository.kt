package vn.foodrescue.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import vn.foodrescue.dto.ProfileResponse
import java.sql.Timestamp
import java.time.Instant

data class Account(val id: Int, val email: String, val passwordHash: String, val name: String,
    val phone: String?, val role: String, val status: String) {
    fun profile() = ProfileResponse(id, name, email, phone, role)
}
data class Challenge(val id: String, val accountId: Int?, val email: String, val purpose: String,
    val codeHash: String, val expiresAt: Instant, val resendAt: Instant, val attempts: Int, val usedAt: Instant?)
data class LoginSession(val id: Int, val accountId: Int, val family: String, val expiresAt: Instant, val revokedAt: Instant?)
data class ResetGrant(val accountId: Int, val expiresAt: Instant, val usedAt: Instant?)

/** SQL remains in this layer. Account rows are locked before challenge/session/grant rows. */
@Repository
class AuthRepository(private val jdbc: JdbcTemplate) {
    private val accountMapper = RowMapper { r, _ -> Account(r.getInt("id"), r.getString("email"),
        r.getString("mat_khau_hash"), r.getString("ho_ten"), r.getString("so_dien_thoai"),
        r.getString("vai_tro"), r.getString("trang_thai")) }
    private val challengeMapper = RowMapper { r, _ -> Challenge(r.getString("id"),
        (r.getObject("tai_khoan_id") as? Number)?.toInt(), r.getString("email"), r.getString("muc_dich"),
        r.getString("ma_hash"), r.getTimestamp("het_han_luc").toInstant(), r.getTimestamp("gui_lai_luc").toInstant(),
        r.getInt("so_lan_sai"), r.getTimestamp("da_dung_luc")?.toInstant()) }
    private val sessionMapper = RowMapper { r, _ -> LoginSession(r.getInt("id"), r.getInt("tai_khoan_id"),
        r.getString("nhom_phien"), r.getTimestamp("het_han_luc").toInstant(), r.getTimestamp("thu_hoi_luc")?.toInstant()) }
    private val grantMapper = RowMapper { r, _ -> ResetGrant(r.getInt("tai_khoan_id"),
        r.getTimestamp("het_han_luc").toInstant(), r.getTimestamp("da_dung_luc")?.toInstant()) }

    fun accountByEmail(email: String, lock: Boolean = false): Account? = jdbc.query(
        "SELECT * FROM tai_khoan WHERE lower(email) = ?" + if (lock) " FOR UPDATE" else "", accountMapper, email).firstOrNull()
    fun account(id: Int, lock: Boolean = false): Account? = jdbc.query(
        "SELECT * FROM tai_khoan WHERE id = ?" + if (lock) " FOR UPDATE" else "", accountMapper, id).firstOrNull()
    fun insertAccount(email: String, name: String, hash: String, now: Instant): Account? = jdbc.query(
        """INSERT INTO tai_khoan(email, mat_khau_hash, ho_ten, vai_tro, trang_thai, tao_luc)
           VALUES (?, ?, ?, 'KHACH_HANG', 'CHO_XAC_THUC', ?) ON CONFLICT DO NOTHING RETURNING *""",
        accountMapper, email, hash, name, ts(now)).firstOrNull()
    fun activate(id: Int) { jdbc.update("UPDATE tai_khoan SET trang_thai='HOAT_DONG' WHERE id=? AND trang_thai='CHO_XAC_THUC'", id) }
    fun updatePassword(id: Int, hash: String) { jdbc.update("UPDATE tai_khoan SET mat_khau_hash=? WHERE id=?", hash, id) }
    fun updateProfile(id: Int, name: String, phone: String?) {
        jdbc.update("UPDATE tai_khoan SET ho_ten=?, so_dien_thoai=? WHERE id=?", name, phone, id)
    }
    fun challenge(id: String, lock: Boolean = false): Challenge? = jdbc.query(
        "SELECT * FROM ma_xac_minh WHERE id=?" + if (lock) " FOR UPDATE" else "", challengeMapper, id).firstOrNull()
    fun insertChallenge(id: String, accountId: Int?, email: String, purpose: String, hash: String, now: Instant) {
        jdbc.update("""INSERT INTO ma_xac_minh(id,tai_khoan_id,email,muc_dich,ma_hash,tao_luc,het_han_luc,gui_lai_luc)
            VALUES (?,?,?,?,?,?,?,?)""", id, accountId, email, purpose, hash, ts(now), ts(now.plusSeconds(600)), ts(now.plusSeconds(60)))
    }
    fun latestChallenge(email: String, purpose: String): Challenge? = jdbc.query(
        "SELECT * FROM ma_xac_minh WHERE email=? AND muc_dich=? AND da_dung_luc IS NULL ORDER BY tao_luc DESC LIMIT 1",
        challengeMapper, email, purpose).firstOrNull()
    fun invalidateChallenges(accountId: Int, purpose: String, now: Instant) {
        jdbc.update("UPDATE ma_xac_minh SET da_dung_luc=? WHERE tai_khoan_id=? AND muc_dich=? AND da_dung_luc IS NULL", ts(now), accountId, purpose)
    }
    fun useChallenge(id: String, now: Instant) { jdbc.update("UPDATE ma_xac_minh SET da_dung_luc=? WHERE id=?", ts(now), id) }
    fun wrongCode(id: String) { jdbc.update("UPDATE ma_xac_minh SET so_lan_sai=so_lan_sai+1 WHERE id=? AND so_lan_sai<5", id) }
    fun renewChallenge(id: String, hash: String, now: Instant) {
        jdbc.update("UPDATE ma_xac_minh SET ma_hash=?,tao_luc=?,het_han_luc=?,gui_lai_luc=?,so_lan_sai=0 WHERE id=?",
            hash, ts(now), ts(now.plusSeconds(600)), ts(now.plusSeconds(60)), id)
    }
    fun insertGrant(accountId: Int, hash: String, now: Instant) {
        jdbc.update("UPDATE quyen_dat_lai_mat_khau SET da_dung_luc=? WHERE tai_khoan_id=? AND da_dung_luc IS NULL", ts(now), accountId)
        jdbc.update("INSERT INTO quyen_dat_lai_mat_khau VALUES (?,?,?,?,NULL)", hash, accountId, ts(now), ts(now.plusSeconds(600)))
    }
    fun grant(hash: String, lock: Boolean = false): ResetGrant? = jdbc.query(
        "SELECT * FROM quyen_dat_lai_mat_khau WHERE token_hash=?" + if (lock) " FOR UPDATE" else "", grantMapper, hash).firstOrNull()
    fun invalidateGrants(accountId: Int, now: Instant) {
        jdbc.update("UPDATE quyen_dat_lai_mat_khau SET da_dung_luc=? WHERE tai_khoan_id=? AND da_dung_luc IS NULL", ts(now), accountId)
    }
    fun insertSession(accountId: Int, hash: String, family: String, now: Instant, expiry: Instant): Int = jdbc.queryForObject(
        """INSERT INTO phien_dang_nhap(tai_khoan_id,refresh_token_hash,tao_luc,het_han_luc,nhom_phien)
           VALUES (?,?,?,?,?) RETURNING id""", Int::class.java, accountId, hash, ts(now), ts(expiry), family)!!
    fun sessionByHash(hash: String, lock: Boolean = false): LoginSession? = jdbc.query(
        "SELECT * FROM phien_dang_nhap WHERE refresh_token_hash=?" + if (lock) " FOR UPDATE" else "", sessionMapper, hash).firstOrNull()
    fun session(id: Int): LoginSession? = jdbc.query("SELECT * FROM phien_dang_nhap WHERE id=?", sessionMapper, id).firstOrNull()
    fun revokeSession(id: Int, now: Instant) { jdbc.update("UPDATE phien_dang_nhap SET thu_hoi_luc=COALESCE(thu_hoi_luc,?) WHERE id=?", ts(now), id) }
    fun revokeFamily(family: String, now: Instant) { jdbc.update("UPDATE phien_dang_nhap SET thu_hoi_luc=COALESCE(thu_hoi_luc,?) WHERE nhom_phien=?", ts(now), family) }
    fun revokeAll(accountId: Int, now: Instant) { jdbc.update("UPDATE phien_dang_nhap SET thu_hoi_luc=COALESCE(thu_hoi_luc,?) WHERE tai_khoan_id=?", ts(now), accountId) }

    fun countRequest(key: String, now: Instant): Int = jdbc.queryForObject("""
        INSERT INTO gioi_han_xac_thuc(khoa_hash,so_lan,het_han_luc) VALUES (?,1,?)
        ON CONFLICT(khoa_hash) DO UPDATE SET
          so_lan=CASE WHEN gioi_han_xac_thuc.het_han_luc<=? THEN 1 ELSE gioi_han_xac_thuc.so_lan+1 END,
          het_han_luc=CASE WHEN gioi_han_xac_thuc.het_han_luc<=? THEN EXCLUDED.het_han_luc ELSE gioi_han_xac_thuc.het_han_luc END
        RETURNING so_lan""", Int::class.java, key, ts(now.plusSeconds(600)), ts(now), ts(now))!!
    private fun ts(now: Instant) = Timestamp.from(now)
}
