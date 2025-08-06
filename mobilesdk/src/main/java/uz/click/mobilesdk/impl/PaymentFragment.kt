package uz.click.mobilesdk.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import uz.click.mobilesdk.BuildConfig
import uz.click.mobilesdk.R
import uz.click.mobilesdk.core.ClickMerchantConfig
import uz.click.mobilesdk.core.ClickMerchantManager
import uz.click.mobilesdk.core.callbacks.ClickMerchantListener
import uz.click.mobilesdk.core.callbacks.ResponseListener
import uz.click.mobilesdk.core.data.CardPaymentResponse
import uz.click.mobilesdk.core.data.CheckoutResponse
import uz.click.mobilesdk.core.data.InitialResponse
import uz.click.mobilesdk.core.data.InvoiceResponse
import uz.click.mobilesdk.core.errors.ArgumentEmptyException
import uz.click.mobilesdk.databinding.FragmentPaymentBinding
import uz.click.mobilesdk.impl.paymentoptions.PaymentOption
import uz.click.mobilesdk.impl.paymentoptions.PaymentOptionEnum
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions
import uz.click.mobilesdk.utils.*
import java.util.*

class PaymentFragment : AppCompatDialogFragment() {

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    private lateinit var config: ClickMerchantConfig
    private var listener: ClickMerchantListener? = null
    var requestId: String = ""
    private var mode = PaymentOptionEnum.CLICK_EVOLUTION
    private lateinit var locale: Locale
    private val APP_NAME = "air.com.ssdsoftwaresolutions.clickuz"

    var shouldInitRequestId = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments == null) throw ArgumentEmptyException()

        config = requireArguments().getSerializable(
            MainDialogFragment.CLICK_MERCHANT_CONFIG
        ) as ClickMerchantConfig

        when (config.themeMode) {
            ThemeOptions.LIGHT -> {
                setStyle(STYLE_NO_FRAME, R.style.cl_FullscreenDialogTheme)
            }
            ThemeOptions.NIGHT -> {
                setStyle(STYLE_NO_FRAME, R.style.cl_FullscreenDialogThemeDark)
            }
        }
    }

    private val clickMerchantManager = ClickMerchantManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val contextWrapper = when (config.themeMode) {
            ThemeOptions.LIGHT -> ContextThemeWrapper(activity, R.style.Theme_App_Light)
            ThemeOptions.NIGHT -> ContextThemeWrapper(activity, R.style.Theme_App_Dark)
        }
        _binding = FragmentPaymentBinding.inflate(inflater.cloneInContext(contextWrapper), container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listener = (parentFragment as MainDialogFragment?)?.getListener()

        when (config.themeMode) {
            ThemeOptions.LIGHT -> {
                binding.btnNext.setBackgroundResource(R.drawable.next_button_rounded)
                binding.viewMobileNumberUnderline.setBackgroundResource(R.drawable.underline_background)
                binding.viewCardNumberUnderline.setBackgroundResource(R.drawable.underline_background)
                binding.viewCardDateUnderline.setBackgroundResource(R.drawable.underline_background)
            }
            ThemeOptions.NIGHT -> {
                binding.btnNext.setBackgroundResource(R.drawable.next_button_rounded_dark)
                binding.viewMobileNumberUnderline.setBackgroundResource(R.drawable.underline_background_dark)
                binding.viewCardNumberUnderline.setBackgroundResource(R.drawable.underline_background_dark)
                binding.viewCardDateUnderline.setBackgroundResource(R.drawable.underline_background_dark)
            }
        }

        if (arguments != null) {
            config = requireArguments().getSerializable(MainDialogFragment.CLICK_MERCHANT_CONFIG) as ClickMerchantConfig
            binding.tvTitle.text = config.productName
            binding.tvSubtitle.text = config.productDescription
            binding.tvSum.text = config.amount.formatDecimals()
            requestId = config.requestId

            mode = if (config.paymentOption != PaymentOptionEnum.CLICK_EVOLUTION) {
                config.paymentOption
            } else {
                val isClickEvolutionEnabled = !config.transactionParam.isNullOrEmpty() && appInstalledOrNot(APP_NAME)
                if (isClickEvolutionEnabled) config.paymentOption else PaymentOptionEnum.USSD
            }
            locale = Locale(config.locale.lowercase(Locale.getDefault()))

            binding.btnChange.text = LanguageUtils.getLocaleStringResource(locale, R.string.change, requireContext())
            binding.tvToPay.text = LanguageUtils.getLocaleStringResource(locale, R.string.payment, requireContext())
            binding.tvCommission.text = LanguageUtils.getLocaleStringResource(locale, R.string.commission, requireContext())
            binding.tvAbbrCommision.text = LanguageUtils.getLocaleStringResource(locale, R.string.abbr, requireContext())
            binding.tvNext.text = LanguageUtils.getLocaleStringResource(locale, R.string.next, requireContext())
            binding.tvAbbr.text = LanguageUtils.getLocaleStringResource(locale, R.string.abbr, requireContext())
            binding.tvRetry.text = LanguageUtils.getLocaleStringResource(locale, R.string.retry, requireContext())
            binding.tvErrorText.text = LanguageUtils.getLocaleStringResource(locale, R.string.connection_problem, requireContext())

            updateUiForPaymentMode()
        } else {
            throw ArgumentEmptyException()
        }

        binding.tvRetry.setOnClickListener {
            init()
        }

        binding.etCardNumber.addTextChangedListener(object : CardNumberFormatWatcher(binding.etCardNumber) {
            override fun afterTextWithoutPattern(cardNumber: String) {}
        })

        binding.etCardDate.addTextChangedListener(object : CardExpiryDateFormatWatcher(binding.etCardDate) {
            override fun afterTextWithoutPattern(expiredDate: String) {}
        })

        binding.etMobileNumber.addTextChangedListener(PhoneNumberTextWatcher(binding.etMobileNumber))

        binding.etMobileNumber.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            binding.viewMobileNumberUnderline.isEnabled = !hasFocus
        }

        binding.etCardNumber.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            binding.viewCardNumberUnderline.isEnabled = !hasFocus
        }

        binding.etCardDate.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            binding.viewCardDateUnderline.isEnabled = !hasFocus
        }

        binding.llChange.setOnClickListener {
            it.hideKeyboard()
            binding.tvError.hide()
            (parentFragment as? MainDialogFragment)?.onChange()
        }

        binding.etMobileNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                buyClick()
                binding.etMobileNumber.hideKeyboard()
            }
            true
        }

        binding.etCardDate.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                buyClick()
                binding.etCardDate.hideKeyboard()
            }
            true
        }

        binding.btnNext.setOnClickListener {
            buyClick()
            binding.btnNext.hideKeyboard()
        }

        binding.ivScanner.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), ScanFragment.REQUEST_CAMERA)
            } else {
                (parentFragment as? MainDialogFragment)?.scanCard()
            }
        }
    }

    private fun updateUiForPaymentMode() {
        when (mode) {
            PaymentOptionEnum.BANK_CARD -> {
                binding.llBankCard.show()
                binding.llUssd.hide()
                binding.ivPaymentType.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_cards))
                binding.tvPaymentTypeTitle.text = LanguageUtils.getLocaleStringResource(locale, R.string.bank_card, requireContext())
                binding.tvPaymentTypeSubtitle.text = LanguageUtils.getLocaleStringResource(locale, R.string.card_props, requireContext())
            }
            PaymentOptionEnum.USSD -> {
                binding.llBankCard.hide()
                binding.llUssd.show()
                binding.ivPaymentType.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_880))
                binding.tvPaymentTypeTitle.text = LanguageUtils.getLocaleStringResource(locale, R.string.invoicing, requireContext())
                binding.tvPaymentTypeSubtitle.text = LanguageUtils.getLocaleStringResource(locale, R.string.sms_confirmation, requireContext())
            }
            PaymentOptionEnum.CLICK_EVOLUTION -> {
                binding.llBankCard.hide()
                binding.llUssd.hide()
                binding.ivPaymentType.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_aevo))
                binding.tvPaymentTypeTitle.text = LanguageUtils.getLocaleStringResource(locale, R.string.click_evo_app, requireContext())
                binding.tvPaymentTypeSubtitle.text = LanguageUtils.getLocaleStringResource(locale, R.string.click_evo_app_description, requireContext())
            }
        }
        if (shouldInitRequestId) {
            init()
            shouldInitRequestId = false
        }
    }

    private fun appInstalledOrNot(uri: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(uri, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == ScanFragment.REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                (parentFragment as? MainDialogFragment)?.scanCard()
            }
        }
    }

    private fun init() {
        if (requestId.isEmpty()) {
            showLoading()
            clickMerchantManager.sendInitialRequest(
                config.serviceId, config.merchantId, config.amount,
                config.transactionParam, config.communalParam, config.merchantUserId, config.locale,
                object : ResponseListener<InitialResponse> {
                    override fun onFailure(e: Exception) {
                        e.printStackTrace()
                        showError()
                        if (ErrorUtils.isApiError(e)) {
                            activity?.runOnUiThread {
                                binding.tvErrorText.text = ErrorUtils.getErrorMessage(e, locale, requireContext())
                            }
                        }
                    }

                    override fun onSuccess(response: InitialResponse) {
                        // Safely handle the nullable requestId
                        val newRequestId = response.requestId
                        if (response.errorCode == 0 && newRequestId != null) {
                            requestId = newRequestId
                            listener?.onReceiveRequestId(requestId)
                            init() // Re-run init to check payment status with the new ID
                        } else {
                            // Handle the case where the API returned an error
                            activity?.runOnUiThread {
                                showError()
                                binding.tvErrorText.text = response.errorNote
                            }
                        }
                    }
                }
            )
        } else {
            showLoading()
            clickMerchantManager.checkPaymentByRequestId(requestId, object : ResponseListener<CheckoutResponse> {
                override fun onFailure(e: Exception) {
                    e.printStackTrace()
                    showError()
                }

                override fun onSuccess(response: CheckoutResponse) {
                    activity?.runOnUiThread {
                        binding.tvSum.text = response.amount.formatDecimals()
                        if ((response.commissionPercent ?: 0.0) > 0.0) {
                            val percent: Double = response.commissionPercent!!
                            val commissionAmount: Double = response.amount - response.amount / (1.0 + percent / 100.0)
                            binding.tvCommissionPercent.text = commissionAmount.formatDecimals()
                            binding.llCommission.show()
                        } else {
                            binding.tvCommissionPercent.text = ""
                            binding.llCommission.hide()
                        }
                    }

                    if (response.payment.paymentStatusDescription != null) {
                        val parent = parentFragment as? MainDialogFragment
                        when (config.paymentOption) {
                            PaymentOptionEnum.BANK_CARD -> {
                                when {
                                    response.payment.paymentStatus in 0..1 -> parent?.openPaymentConfirmation(null, requestId)
                                    response.payment.paymentStatus == 2 || response.payment.paymentStatus < 0 -> parent?.openPaymentResultPage(response.payment)
                                }
                            }
                            PaymentOptionEnum.USSD -> {
                                when {
                                    response.payment.paymentStatus in 0..1 -> parent?.openInvoiceConfirmationPage(requestId)
                                    response.payment.paymentStatus == 2 || response.payment.paymentStatus < 0 -> parent?.openPaymentResultPage(response.payment)
                                }
                            }
                            else -> {} // CLICK_EVOLUTION does not have this flow
                        }
                    } else {
                        showResult()
                    }
                }
            })
        }
    }

    private fun buyClick() {
        binding.tvError.hide()
        when (mode) {
            PaymentOptionEnum.BANK_CARD -> {
                when {
                    binding.etCardNumber.text.toString().isEmpty() -> binding.etCardNumber.error = LanguageUtils.getLocaleStringResource(locale, R.string.enter_card_number, requireContext())
                    binding.etCardDate.text.toString().isEmpty() -> binding.etCardDate.error = LanguageUtils.getLocaleStringResource(locale, R.string.enter_expire_date, requireContext())
                    else -> {
                        showLoading()
                        clickMerchantManager.paymentByCard(
                            requestId,
                            binding.etCardNumber.text.toString().replace(" ", ""),
                            binding.etCardDate.text.toString().replace("/", ""),
                            object : ResponseListener<CardPaymentResponse> {
                                override fun onFailure(e: Exception) {
                                    showResult()
                                    showErrorMessage(e)
                                }

                                override fun onSuccess(response: CardPaymentResponse) {
                                    showResult()
                                    val parent = parentFragment as? MainDialogFragment
                                    val prefs = context?.getSharedPreferences(BuildConfig.BASE_XML, Context.MODE_PRIVATE)
                                    prefs?.edit()?.putString(requestId, response.phoneNumber)?.apply()
                                    parent?.openPaymentConfirmation(response, requestId)
                                }
                            }
                        )
                    }
                }
            }
            PaymentOptionEnum.USSD -> {
                if (binding.etMobileNumber.text.toString().isNotEmpty()) {
                    showLoading()
                    clickMerchantManager.paymentByUSSD(
                        requestId,
                        unmaskMobileNumber(binding.etMobileNumber.text.toString()),
                        object : ResponseListener<InvoiceResponse> {
                            override fun onFailure(e: Exception) {
                                showResult()
                                e.printStackTrace()
                                showErrorMessage(e)
                            }

                            override fun onSuccess(response: InvoiceResponse) {
                                showResult()
                                (parentFragment as? MainDialogFragment)?.openInvoiceConfirmationPage(requestId)
                            }
                        })
                } else {
                    binding.etMobileNumber.error = LanguageUtils.getLocaleStringResource(locale, R.string.enter_valid_phone_number, requireContext())
                }
            }
            PaymentOptionEnum.CLICK_EVOLUTION -> {
                listener?.closeDialog()
                if (config.transactionParam == null) return

                val builder = Uri.Builder()
                    .scheme("https")
                    .authority("my.click.uz")
                    .appendPath("services")
                    .appendPath("pay")
                    .appendQueryParameter("service_id", "${config.serviceId}")
                    .appendQueryParameter("merchant_id", "${config.merchantId}")
                    .appendQueryParameter("amount", "${config.amount}")
                    .appendQueryParameter("transaction_param", "${config.transactionParam}")

                if (!config.returnUrl.isNullOrEmpty()) {
                    builder.appendQueryParameter("return_url", "${config.returnUrl}")
                }

                val i = Intent(Intent.ACTION_VIEW)
                i.data = builder.build()
                startActivity(i)
            }
        }
    }

    private fun unmaskMobileNumber(number: String): String {
        return "+998" + number.replace(" ", "")
    }

    private fun showErrorMessage(e: Exception) {
        e.printStackTrace()
        activity?.runOnUiThread {
            binding.tvError.show()
            if (ErrorUtils.isApiError(e)) {
                binding.tvError.text = ErrorUtils.getErrorMessage(e, locale, requireContext())
            } else {
                binding.tvError.text = LanguageUtils.getLocaleStringResource(locale, R.string.network_connection_error, requireContext())
            }
        }
    }

    private fun showLoading() {
        activity?.runOnUiThread {
            binding.llPaymentContainer.show()
            binding.pbLoading.show()
            binding.llBottomContainer.invisible()
            binding.llError.hide()
        }
    }

    private fun showResult() {
        activity?.runOnUiThread {
            binding.llPaymentContainer.show()
            binding.llBottomContainer.show()
            binding.pbLoading.invisible()
            binding.llError.hide()
        }
    }

    private fun showError() {
        activity?.runOnUiThread {
            binding.llPaymentContainer.hide()
            binding.llError.show()
            binding.pbLoading.hide()
        }
    }

    fun paymentOptionSelected(item: PaymentOption) {
        mode = item.type
        binding.ivPaymentType.setImageDrawable(ContextCompat.getDrawable(requireContext(), item.image))
        binding.tvPaymentTypeTitle.text = item.title
        binding.tvPaymentTypeSubtitle.text = item.subtitle
        updateUiForPaymentMode()
    }

    fun setScannedData(number: String, date: String) {
        binding.etCardDate.setText(date)
        binding.etCardNumber.setText(number)
    }
}