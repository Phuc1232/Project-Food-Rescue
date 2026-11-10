// AC-13,17,24: AuthMailSender responsibility.
package com.foodrescue.backend.auth.mail

import com.foodrescue.backend.auth.TokenPurpose
interface AuthMailSender { fun send(email: String,purpose: TokenPurpose,token: String) }
