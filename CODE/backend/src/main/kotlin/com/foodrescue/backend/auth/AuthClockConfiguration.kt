// AC-06,13,20,24: AuthClockConfiguration responsibility.
package com.foodrescue.backend.auth

import java.time.Clock
import org.springframework.context.annotation.*
@Configuration
class AuthClockConfiguration { @Bean fun authClock(): Clock = Clock.systemUTC() }
