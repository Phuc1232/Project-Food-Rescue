package com.foodrescue.app.dashboard.owner

import com.foodrescue.app.core.designsystem.component.FoodRescueIcon

internal enum class OwnerTab(val label: String, val icon: FoodRescueIcon) {
    OVERVIEW("Tổng quan", FoodRescueIcon.HOME),
    BOXES("Hộp bán", FoodRescueIcon.BAG),
    ORDERS("Đơn hàng", FoodRescueIcon.RECEIPT),
    REVENUE("Doanh thu", FoodRescueIcon.CHART),
    STORE("Cửa hàng", FoodRescueIcon.STORE),
}

internal enum class OwnerPage { ROOT, PROFILE, EDIT_PROFILE, PAYOUT, REVIEW, REGISTER, STAFF, LOCATION, MENU }
