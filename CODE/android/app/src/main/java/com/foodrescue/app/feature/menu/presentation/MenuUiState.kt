package com.foodrescue.app.feature.menu.presentation

import com.foodrescue.app.feature.menu.domain.*

internal enum class MenuPage { LIST, DETAIL, EDIT, DOCUMENTS }
internal data class MenuUiState(
    val menu: StoreMenu = StoreMenu(), val loading: Boolean = true, val busy: Boolean = false,
    val page: MenuPage = MenuPage.LIST, val selectedId: String? = null, val draft: MenuDraft = MenuDraft(),
    val query: String = "", val category: MenuCategory? = null, val status: MenuStatus? = null, val sort: MenuSort = MenuSort.UPDATED,
    val errors: Set<MenuField> = emptySet(), val error: String? = null, val message: String? = null,
    val confirmExit: Boolean = false, val statusTarget: String? = null, val documentToRemove: String? = null,
) {
    val selected: MenuItem? get() = menu.items.firstOrNull { it.id == selectedId }
    val visibleItems: List<MenuItem> get() = menu.items.filterMenu(query, category, status, sort)
    val dirty: Boolean get() = draft != (menu.items.firstOrNull { it.id == draft.id }?.toDraft() ?: MenuDraft())
}

internal val MenuCategory.label: String get() = when (this) {
    MenuCategory.BAKERY -> "Bánh"; MenuCategory.MEAL -> "Món chính"; MenuCategory.DRINK -> "Đồ uống"
    MenuCategory.SNACK -> "Ăn nhẹ"; MenuCategory.GROCERY -> "Thực phẩm"; MenuCategory.OTHER -> "Khác"
}
internal val MenuUnit.label: String get() = when (this) {
    MenuUnit.PORTION -> "phần"; MenuUnit.PIECE -> "cái"; MenuUnit.CUP -> "ly"; MenuUnit.BOTTLE -> "chai"
    MenuUnit.BOX -> "hộp"; MenuUnit.PACK -> "gói"; MenuUnit.KILOGRAM -> "kg"
}
internal val MenuStatus.label: String get() = if (this == MenuStatus.ACTIVE) "Đang kinh doanh" else "Ngừng kinh doanh"
internal val MenuSort.label: String get() = when (this) {
    MenuSort.UPDATED -> "Mới cập nhật"; MenuSort.NAME -> "Tên A–Z"; MenuSort.PRICE_ASC -> "Giá tăng dần"; MenuSort.PRICE_DESC -> "Giá giảm dần"
}
internal val MenuAssetIssue.message: String get() = when (this) {
    MenuAssetIssue.UNSUPPORTED -> "Chọn ảnh JPG, PNG, WebP, HEIC hoặc tài liệu PDF hợp lệ."
    MenuAssetIssue.TOO_LARGE -> "Tệp vượt quá 10 MB. Vui lòng chọn tệp nhỏ hơn."
    MenuAssetIssue.UNREADABLE -> "Không đọc được tệp này. Hãy thử lại với ảnh hoặc PDF không khóa."
    MenuAssetIssue.LIMIT -> "Bạn có thể lưu tối đa 5 tài liệu. Gỡ một tài liệu trước khi thêm mới."
}
