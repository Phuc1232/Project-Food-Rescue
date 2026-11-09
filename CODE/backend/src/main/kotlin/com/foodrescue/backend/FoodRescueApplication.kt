package com.foodrescue.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FoodRescueApplication

fun main(args: Array<String>) {
    runApplication<FoodRescueApplication>(*args)
}
