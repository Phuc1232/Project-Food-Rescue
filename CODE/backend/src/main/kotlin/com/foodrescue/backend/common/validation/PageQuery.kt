// AC-03: PageQuery responsibility.
package com.foodrescue.backend.common.validation

import com.foodrescue.backend.common.exception.ApiException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
class PageQuery {
    fun pageable(page: Int, size: Int, sort: String): PageRequest {
        if (page < 0 || size !in 1..100 || sort !in setOf("createdAt","expiresAt"))
            throw ApiException(400,"INVALID_PAGINATION","Invalid page, size or sort")
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,sort).and(Sort.by("id")))
    }
}
