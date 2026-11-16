package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.surprisebox.domain.*

internal val BoxStorage.staffLabel: String get() = when (this) {
    BoxStorage.AMBIENT -> "Nhiệt độ thường"
    BoxStorage.CHILLED -> "Bảo quản mát"
    BoxStorage.FROZEN -> "Bảo quản đông"
}
internal val MenuUnit.staffLabel: String get() = when (this) {
    MenuUnit.PORTION -> "phần"; MenuUnit.PIECE -> "cái"; MenuUnit.CUP -> "ly"; MenuUnit.BOTTLE -> "chai"
    MenuUnit.BOX -> "hộp"; MenuUnit.PACK -> "gói"; MenuUnit.KILOGRAM -> "kg"
}
internal val PackingIssue.message: String get() = when (this) {
    PackingIssue.ACCESS -> "Bạn chưa có quyền đóng gói tại cửa hàng này."
    PackingIssue.BATCH -> "Đợt này không còn nhận đóng gói. Kiểm tra lại lịch nhận hàng."
    PackingIssue.CAPACITY -> "Đợt đã đủ số hộp cần chuẩn bị."
    PackingIssue.ITEMS -> "Chọn món và nhập số lượng hợp lệ cho từng món."
    PackingIssue.MENU_CHANGED -> "Menu đã thay đổi. Cập nhật món đã chọn rồi kiểm tra lại hộp."
    PackingIssue.VALUE -> "Giá trị các món chưa đạt mức tối thiểu của hộp."
    PackingIssue.EXPIRY -> "Hạn sử dụng phải còn hiệu lực và bao phủ hết khung giờ nhận."
    PackingIssue.STORAGE -> "Chọn cách bảo quản hộp."
    PackingIssue.INSPECTION -> "Hoàn tất cả ba mục kiểm tra trước khi xác nhận."
}
