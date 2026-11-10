// AC-16: explicit bootstrap command exits after its transaction commits.
package com.foodrescue.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FoodRescueApplication

fun main(args: Array<String>) {
    val context = runApplication<FoodRescueApplication>(*args)
    if (args.contains("--auth.bootstrap-admin=true")) context.close()
}
