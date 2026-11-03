-- V1 is already shared. Authentication changes are additive.
ALTER TABLE phien_dang_nhap ADD COLUMN nhom_phien varchar(36);
UPDATE phien_dang_nhap SET nhom_phien = 'legacy-' || id;
ALTER TABLE phien_dang_nhap ALTER COLUMN nhom_phien SET NOT NULL;
CREATE INDEX ix_phien_nhom ON phien_dang_nhap(nhom_phien);

CREATE TABLE ma_xac_minh (
    id varchar(36) PRIMARY KEY,
    tai_khoan_id int REFERENCES tai_khoan(id),
    email varchar(254) NOT NULL,
    muc_dich varchar(24) NOT NULL CHECK (muc_dich IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    ma_hash varchar(64) NOT NULL,
    tao_luc timestamptz NOT NULL,
    het_han_luc timestamptz NOT NULL,
    gui_lai_luc timestamptz NOT NULL,
    so_lan_sai int NOT NULL DEFAULT 0 CHECK (so_lan_sai BETWEEN 0 AND 5),
    da_dung_luc timestamptz,
    CHECK (het_han_luc > tao_luc)
);
CREATE INDEX ix_xac_minh_tai_khoan ON ma_xac_minh(tai_khoan_id, muc_dich);
CREATE INDEX ix_xac_minh_het_han ON ma_xac_minh(het_han_luc);
CREATE INDEX ix_xac_minh_email ON ma_xac_minh(email, muc_dich, tao_luc DESC);

CREATE TABLE quyen_dat_lai_mat_khau (
    token_hash varchar(64) PRIMARY KEY,
    tai_khoan_id int NOT NULL REFERENCES tai_khoan(id),
    tao_luc timestamptz NOT NULL,
    het_han_luc timestamptz NOT NULL,
    da_dung_luc timestamptz,
    CHECK (het_han_luc > tao_luc)
);
CREATE INDEX ix_dat_lai_tai_khoan ON quyen_dat_lai_mat_khau(tai_khoan_id);

-- Shared by all backend instances; no raw email or IP address is stored here.
CREATE TABLE gioi_han_xac_thuc (
    khoa_hash varchar(64) PRIMARY KEY,
    so_lan int NOT NULL CHECK (so_lan > 0),
    het_han_luc timestamptz NOT NULL
);
CREATE INDEX ix_gioi_han_het_han ON gioi_han_xac_thuc(het_han_luc);
