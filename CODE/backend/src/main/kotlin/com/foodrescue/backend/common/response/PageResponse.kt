// AC-03: PageResponse responsibility.
package com.foodrescue.backend.common.response

data class PageResponse<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
