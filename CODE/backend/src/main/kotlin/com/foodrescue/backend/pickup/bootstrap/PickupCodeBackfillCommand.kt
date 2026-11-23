// AC-14 (T7.1): PickupCodeBackfillCommand runs explicitly requested legacy credential backfill.
package com.foodrescue.backend.pickup.bootstrap

import com.foodrescue.backend.pickup.service.PickupCodeBackfillService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["pickup.code.backfill-enabled"], havingValue = "true")
class PickupCodeBackfillCommand(private val service: PickupCodeBackfillService) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        var count = 0
        while (true) {
            val ids = service.candidates()
            if (ids.isEmpty()) break
            ids.forEach { if (service.issueOne(it)) count++ }
        }
        LoggerFactory.getLogger(javaClass).info("Pickup code backfill completed: {} orders", count)
    }
}
