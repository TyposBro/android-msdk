package uz.click.mobilesdk.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory // Import the factory
import io.reactivex.rxjava3.core.Single
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import uz.click.mobilesdk.BuildConfig
import uz.click.mobilesdk.core.callbacks.ResponseListener
import uz.click.mobilesdk.core.data.*
import uz.click.mobilesdk.core.errors.ServerNotAvailableException
import uz.click.mobilesdk.utils.ErrorUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

class ClickMerchantManager {

    companion object {
        var logs = false
        private const val CONNECT_TIME_OUT: Long = 10 * 1000 // 10 second
        private const val READ_TIME_OUT: Long = 10 * 1000 // 10 second
        private const val WRITE_TIME_OUT: Long = 10 * 1000 // 10 second
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val INIT_URL = BuildConfig.API_ENDPOINT + "checkout/prepare"
        private const val CHECKOUT_URL = BuildConfig.API_ENDPOINT + "checkout/retrieve"
        private const val INVOICE_URL = BuildConfig.API_ENDPOINT + "checkout/invoice"
        private const val CARD_PAYMENT_URL = BuildConfig.API_ENDPOINT + "checkout/payment"
        private const val CARD_PAYMENT_CONFIRM_URL = BuildConfig.API_ENDPOINT + "checkout/verify"
    }

    private var okClient: OkHttpClient
    // Update the Moshi instance to include the KotlinJsonAdapterFactory
    private var moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    var invoiceCancelled = false

    init {
        val dispatcher = Dispatcher()
        dispatcher.maxRequests = 1
        val okhttpClientBuilder = OkHttpClient.Builder()
        okhttpClientBuilder.dispatcher(dispatcher)
        okhttpClientBuilder.addInterceptor(loggingInterceptor())
        okhttpClientBuilder
            .connectTimeout(CONNECT_TIME_OUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIME_OUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIME_OUT, TimeUnit.SECONDS)
        okClient = okhttpClientBuilder.build()
    }

    // ... rest of the file remains the same
    private fun loggingInterceptor(): Interceptor {
        val logging = HttpLoggingInterceptor()
        if (logs)
            logging.level = HttpLoggingInterceptor.Level.BODY
        else
            logging.level = HttpLoggingInterceptor.Level.NONE
        return logging
    }

    fun sendInitialRequest(
        serviceId: Long, merchantId: Long,
        amount: Double, transactionParam: String?, communalParam: String?,
        merchantUserId: Long, language: String, listener: ResponseListener<InitialResponse>
    ) {
        val initRequest = InitialRequest(
            serviceId,
            merchantId,
            amount,
            transactionParam,
            communalParam,
            "",
            merchantUserId,
            language,
            ""
        )
        val adapter = moshi.adapter(InitialRequest::class.java)
        val body = adapter.toJson(initRequest).toRequestBody(JSON)
        val request = Request.Builder()
            .url(INIT_URL)
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .post(body)
            .build()

        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onFailure(ServerNotAvailableException(response.code, response.message))
                        return
                    }

                    responseBody.let {
                        val initialResponse = moshi.adapter(InitialResponse::class.java)
                            .fromJson(it.string())

                        when (initialResponse?.errorCode) {
                            0 -> {
                                if (initialResponse != null) {
                                    listener.onSuccess(initialResponse)
                                }
                            }
                            else -> {
                                listener.onFailure(
                                    ErrorUtils.getException(
                                        initialResponse?.errorCode,
                                        initialResponse?.errorNote
                                    )
                                )
                            }
                        }
                    }

                } else {
                    listener.onFailure(
                        ServerNotAvailableException(response.code, response.message)
                    )
                }
            }
        })
    }


    fun checkPaymentByRequestIdContinuously(requestId: String, listener: ResponseListener<CheckoutResponse>) {
        val request = Request.Builder()
            .url("$CHECKOUT_URL/$requestId")
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .get()
            .build()

        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onFailure(ServerNotAvailableException(response.code, response.message))
                        return
                    }

                    responseBody.let {
                        val checkoutResponse = moshi.adapter(CheckoutResponse::class.java)
                            .fromJson(it.string())
                        if (checkoutResponse?.payment != null) {
                            when {
                                checkoutResponse.payment.paymentStatus < 0 -> {
                                    listener.onSuccess(checkoutResponse)
                                }
                                checkoutResponse.payment.paymentStatus == 0 || checkoutResponse.payment.paymentStatus == 1 -> {
                                    if (!invoiceCancelled) {
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            call.clone().enqueue(this)
                                        }, 1000)
                                    } else {
                                        call.cancel()
                                        invoiceCancelled = false
                                    }
                                }
                                checkoutResponse.payment.paymentStatus == 2 -> {
                                    listener.onSuccess(checkoutResponse)
                                }
                            }
                        }
                    }

                } else listener.onFailure(
                    ServerNotAvailableException(response.code, response.message)
                )
            }
        })
    }

    fun checkPaymentByRequestId(requestId: String, listener: ResponseListener<CheckoutResponse>) {
        val request = Request.Builder()
            .url("$CHECKOUT_URL/$requestId")
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .get()
            .build()

        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body
                if (responseBody == null) {
                    listener.onFailure(ServerNotAvailableException(response.code, response.message))
                    return
                }

                responseBody.let {
                    if (response.isSuccessful) {
                        val checkoutResponse = moshi.adapter(CheckoutResponse::class.java)
                            .fromJson(it.string())
                        checkoutResponse?.let {
                            listener.onSuccess(checkoutResponse)
                        }
                    } else listener.onFailure(
                        ServerNotAvailableException(response.code, response.message)
                    )
                }
            }
        })
    }


    fun paymentByUSSD(requestId: String, phoneNumber: String, listener: ResponseListener<InvoiceResponse>) {

        val invoice = InvoiceRequest(requestId, phoneNumber)

        val adapter = moshi.adapter(InvoiceRequest::class.java)
        val body = adapter.toJson(invoice).toRequestBody(JSON)
        val request = Request.Builder()
            .url(INVOICE_URL)
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .post(body)
            .build()
        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onFailure(ServerNotAvailableException(response.code, response.message))
                        return
                    }

                    responseBody.let {

                        val invoiceResponse = moshi.adapter(InvoiceResponse::class.java)
                            .fromJson(it.string())

                        when (invoiceResponse?.errorCode) {
                            0 -> {
                                if (invoiceResponse != null) {
                                    listener.onSuccess(invoiceResponse)
                                }
                            }
                            else -> {
                                listener.onFailure(
                                    ErrorUtils.getException(
                                        invoiceResponse?.errorCode,
                                        invoiceResponse?.errorNote
                                    )
                                )
                            }
                        }
                    }

                } else {
                    listener.onFailure(
                        ServerNotAvailableException(response.code, response.message)
                    )
                }
            }
        })
    }

    fun paymentByCard(
        requestId: String,
        cardNumber: String,
        expireDate: String,
        listener: ResponseListener<CardPaymentResponse>
    ) {

        val payment = CardPaymentRequest(requestId, cardNumber, expireDate)

        val adapter = moshi.adapter(CardPaymentRequest::class.java)
        val body = adapter.toJson(payment).toRequestBody(JSON)
        val request = Request.Builder()
            .url(CARD_PAYMENT_URL)
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .post(body)
            .build()
        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onFailure(ServerNotAvailableException(response.code, response.message))
                        return
                    }

                    responseBody.let {

                        val cardPaymentResponse =
                            moshi.adapter(CardPaymentResponse::class.java)
                                .fromJson(it.string())
                        when (cardPaymentResponse?.errorCode) {
                            0 -> {
                                if (cardPaymentResponse != null) {
                                    listener.onSuccess(cardPaymentResponse)
                                }
                            }
                            else -> {
                                listener.onFailure(
                                    ErrorUtils.getException(
                                        cardPaymentResponse?.errorCode,
                                        cardPaymentResponse?.errorNote
                                    )
                                )
                            }
                        }
                    }

                } else listener.onFailure(
                    ServerNotAvailableException(response.code, response.message)
                )
            }
        })
    }

    fun confirmPaymentByCard(
        requestId: String,
        confirmCode: String,
        listener: ResponseListener<ConfirmPaymentByCardResponse>
    ) {
        val confirm = ConfirmPaymentByCardRequest(requestId, confirmCode)

        val adapter = moshi.adapter(ConfirmPaymentByCardRequest::class.java)
        val body = adapter.toJson(confirm).toRequestBody(JSON)
        val request = Request.Builder()
            .url(CARD_PAYMENT_CONFIRM_URL)
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .post(body)
            .build()
        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onFailure(ServerNotAvailableException(response.code, response.message))
                        return
                    }

                    responseBody.let {

                        val confirmResponse =
                            moshi.adapter(ConfirmPaymentByCardResponse::class.java)
                                .fromJson(it.string())
                        when (confirmResponse?.errorCode) {
                            0 -> {
                                if (confirmResponse != null) {
                                    listener.onSuccess(confirmResponse)
                                }
                            }
                            else -> {
                                listener.onFailure(
                                    ErrorUtils.getException(
                                        confirmResponse?.errorCode,
                                        confirmResponse?.errorNote
                                    )
                                )
                            }
                        }
                    }

                } else listener.onFailure(
                    ServerNotAvailableException(response.code, response.message)
                )
            }

        })
    }

    fun checkPayment(serviceId: String, paymentId: String, listener: ResponseListener<CheckPaymentResponse>) {
        val request = Request.Builder()
            .url("https://api.click.uz/v2/merchant/payment/status/$serviceId/$paymentId")
            .addHeader("Accept", "application/json")
            .addHeader("Content-type", "application/json")
            .get()
            .build()
        okClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                listener.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        listener.onFailure(ServerNotAvailableException(response.code, response.message))
                        return
                    }

                    responseBody.let {

                        val checkResponse =
                            moshi.adapter(CheckPaymentResponse::class.java)
                                .fromJson(it.string())
                        when (checkResponse?.errorCode) {
                            0 -> {
                                if (checkResponse != null) {
                                    listener.onSuccess(checkResponse)
                                }
                            }
                            else -> {
                                listener.onFailure(
                                    ErrorUtils.getException(checkResponse?.errorCode, checkResponse?.errorNote)
                                )
                            }
                        }
                    }

                } else listener.onFailure(
                    ServerNotAvailableException(response.code, response.message)
                )
            }
        })
    }

    fun sendInitialRequestRx(
        serviceId: Long, merchantId: Long,
        amount: Double, transactionParam: String?, communalParam: String?,
        merchantUserId: Long, language: String
    ): Single<InitialResponse> {
        return Single.create { emitter ->
            sendInitialRequest(serviceId, merchantId, amount, transactionParam, communalParam,
                merchantUserId, language,
                object : ResponseListener<InitialResponse> {
                    override fun onFailure(e: Exception) {
                        emitter.onError(e.cause!!)
                    }

                    override fun onSuccess(response: InitialResponse) {
                        emitter.onSuccess(response)
                    }
                }
            )
        }
    }

    fun checkPaymentByRequestIdRx(requestId: String): Single<CheckoutResponse> {
        return Single.create { emitter ->
            checkPaymentByRequestId(requestId,
                object : ResponseListener<CheckoutResponse> {
                    override fun onFailure(e: Exception) {
                        emitter.onError(e.cause!!)
                    }

                    override fun onSuccess(response: CheckoutResponse) {
                        emitter.onSuccess(response)
                    }
                }
            )
        }
    }

    fun paymentByUSSDRx(requestId: String, phoneNumber: String): Single<InvoiceResponse> {
        return Single.create { emitter ->
            paymentByUSSD(requestId, phoneNumber,
                object : ResponseListener<InvoiceResponse> {
                    override fun onFailure(e: Exception) {
                        emitter.onError(e.cause!!)
                    }

                    override fun onSuccess(response: InvoiceResponse) {
                        emitter.onSuccess(response)
                    }
                }
            )
        }
    }

    fun paymentByCardRx(
        requestId: String,
        cardNumber: String,
        expireDate: String
    ): Single<CardPaymentResponse> {
        return Single.create { emitter ->
            paymentByCard(requestId, cardNumber, expireDate,
                object : ResponseListener<CardPaymentResponse> {
                    override fun onFailure(e: Exception) {
                        emitter.onError(e.cause!!)
                    }

                    override fun onSuccess(response: CardPaymentResponse) {
                        emitter.onSuccess(response)
                    }
                }
            )
        }
    }

    fun confirmPaymentByCardRx(
        requestId: String,
        confirmCode: String
    ): Single<ConfirmPaymentByCardResponse> {
        return Single.create { emitter ->
            confirmPaymentByCard(requestId, confirmCode,
                object : ResponseListener<ConfirmPaymentByCardResponse> {
                    override fun onFailure(e: Exception) {
                        emitter.onError(e.cause!!)
                    }

                    override fun onSuccess(response: ConfirmPaymentByCardResponse) {
                        emitter.onSuccess(response)
                    }
                }
            )
        }
    }

    fun checkPaymentRx(serviceId: String, paymentId: String): Single<CheckPaymentResponse> {
        return Single.create { emitter ->
            checkPayment(serviceId, paymentId,
                object : ResponseListener<CheckPaymentResponse> {
                    override fun onFailure(e: Exception) {
                        emitter.onError(e.cause!!)
                    }

                    override fun onSuccess(response: CheckPaymentResponse) {
                        emitter.onSuccess(response)
                    }
                }
            )
        }
    }
}