-- Apply after the main schema when enforcing the original MVP scope.
-- Keep da_duoc_duyet true after a store is suspended/blocked.
CREATE UNIQUE INDEX uq_mvp_mot_cua_hang_duoc_duyet
ON cua_hang (chu_so_huu_id) WHERE da_duoc_duyet;
