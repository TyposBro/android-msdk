package uz.click.mobilesdk.core.data

import com.squareup.moshi.Json

/**
 * @author rahmatkhujaevs on 29/01/19
 * @constructor typosbro on 25/08/25
 * */
data class InitialResponse(
    @field:Json(name = "error_code")
    val errorCode: Int?, // Changed to nullable
    @field:Json(name = "error_note")
    val errorNote: String?, // Changed to nullable
    @field:Json(name = "request_id")
    val requestId: String?
)