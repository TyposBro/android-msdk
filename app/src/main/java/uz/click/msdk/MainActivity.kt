package uz.click.msdk

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
// 1. Import the generated View Binding class for your layout
import uz.click.msdk.databinding.MainActivityBinding
import uz.click.mobilesdk.core.ClickMerchant
import uz.click.mobilesdk.core.ClickMerchantConfig
import uz.click.mobilesdk.core.ClickMerchantManager
import uz.click.mobilesdk.core.callbacks.ClickMerchantListener
import uz.click.mobilesdk.impl.paymentoptions.PaymentOptionEnum
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions

class MainActivity : AppCompatActivity() {

    private val productPrice = 1000.0
    private val transactionParam = "order_id_in_your_server"
    private val productName = "Супер ТВ"
    private val productDescription = "Подписка на сервис Супер ТВ"
    private lateinit var themeMode: ThemeOptions

    //fake in-memory user
    private val currentUser = UserDetail(0, "", null, false)

    // 2. Declare a variable for the binding object
    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 3. Inflate the layout using the binding class and set the content view to its root
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 4. Access all views through the 'binding' object
        binding.price.text = productPrice.toString()
        binding.goodName.text = productName
        binding.goodDescription.text = productDescription

        checkDarkThemeMode(this)

        binding.btnBuy.setOnClickListener {
            val config = ClickMerchantConfig.Builder()
                .serviceId(BuildConfig.SERVICE_ID)
                .merchantId(BuildConfig.MERCHANT_ID)
                .amount(productPrice)
                .transactionParam(transactionParam)
                .locale("RU")
                .option(PaymentOptionEnum.CLICK_EVOLUTION)
                .theme(themeMode)
                .productName(productName)
                .productDescription(productDescription)
                .merchantUserId(BuildConfig.MERCHANT_USER_ID)
                .requestId(currentUser.requestId)
                .build()

            ClickMerchantManager.logs = BuildConfig.DEBUG

            ClickMerchant.init(
                supportFragmentManager, config,
                object : ClickMerchantListener {
                    override fun onReceiveRequestId(id: String) {
                        currentUser.requestId = id
                    }

                    override fun onSuccess(paymentId: Long) {
                        currentUser.paymentId = paymentId
                        currentUser.paid = true
                    }

                    override fun onFailure() {
                        currentUser.requestId = ""
                    }

                    override fun onInvoiceCancelled() {
                        currentUser.requestId = ""
                    }

                    override fun closeDialog() {
                        ClickMerchant.dismiss()
                    }
                }
            )
        }
    }

    private fun checkDarkThemeMode(context: Context) {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        themeMode = when (mode) {
            Configuration.UI_MODE_NIGHT_NO -> ThemeOptions.LIGHT
            Configuration.UI_MODE_NIGHT_YES -> ThemeOptions.NIGHT
            else -> ThemeOptions.LIGHT
        }
    }
}