package uz.click.mobilesdk.impl

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import uz.click.mobilesdk.BuildConfig
import uz.click.mobilesdk.R
import uz.click.mobilesdk.core.ClickMerchantManager
import uz.click.mobilesdk.core.callbacks.ResponseListener
import uz.click.mobilesdk.core.data.CardPaymentResponse
import uz.click.mobilesdk.core.data.ConfirmPaymentByCardResponse
import uz.click.mobilesdk.core.data.PaymentResponse
import uz.click.mobilesdk.core.errors.ArgumentEmptyException
import uz.click.mobilesdk.databinding.FragmentConfirmPaymentBinding
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.LOCALE
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.REQUEST_ID
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.THEME_MODE
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions
import uz.click.mobilesdk.utils.*
import java.lang.IllegalStateException
import java.util.*

class PaymentConfirmationFragment : AppCompatDialogFragment() {

    private var _binding: FragmentConfirmPaymentBinding? = null
    private val binding get() = _binding!!

    private var payment: CardPaymentResponse? = null
    private var requestId: String? = ""
    private lateinit var locale: Locale
    private val clickMerchantManager = ClickMerchantManager()
    lateinit var themeMode: ThemeOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments == null) throw ArgumentEmptyException()
        themeMode = requireArguments().getSerializable(THEME_MODE) as ThemeOptions
        when (themeMode) {
            ThemeOptions.LIGHT -> {
                setStyle(STYLE_NO_FRAME, R.style.cl_FullscreenDialogTheme)
            }
            ThemeOptions.NIGHT -> {
                setStyle(STYLE_NO_FRAME, R.style.cl_FullscreenDialogThemeDark)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val contextWrapper = when (themeMode) {
            ThemeOptions.LIGHT -> ContextThemeWrapper(activity, R.style.Theme_App_Light)
            ThemeOptions.NIGHT -> ContextThemeWrapper(activity, R.style.Theme_App_Dark)
        }
        _binding = FragmentConfirmPaymentBinding.inflate(inflater.cloneInContext(contextWrapper), container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when (themeMode) {
            ThemeOptions.LIGHT -> {
                binding.btnNext.setBackgroundResource(R.drawable.next_button_rounded)
                binding.viewMobileNumberUnderline.setBackgroundResource(R.drawable.underline_background)
            }
            ThemeOptions.NIGHT -> {
                binding.btnNext.setBackgroundResource(R.drawable.next_button_rounded_dark)
                binding.viewMobileNumberUnderline.setBackgroundResource(R.drawable.underline_background_dark)
            }
        }
        if (arguments != null) {
            payment =
                requireArguments().getSerializable(MainDialogFragment.PAYMENT_RESULT) as CardPaymentResponse?
            requestId = requireArguments().getString(REQUEST_ID)
            locale = Locale(requireArguments().getString(LOCALE, "ru"))
            binding.tvTitle.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.confirm_with_sms, requireContext())
            binding.tvSubtitle.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.sms_code_sent, requireContext())
            binding.tvNext.text = LanguageUtils.getLocaleStringResource(locale, R.string.next, requireContext())
            binding.tvMobileNumberOwner.text =
                LanguageUtils.getLocaleStringResource(
                    locale,
                    R.string.owner_mobile_number,
                    requireContext()
                )

            binding.etCode.hint =
                LanguageUtils.getLocaleStringResource(locale, R.string.sms_code, requireContext())

            binding.tvMobileNumber.text = if (payment != null) "+" + payment?.phoneNumber else {
                val prefs =
                    context?.getSharedPreferences(BuildConfig.BASE_XML, Context.MODE_PRIVATE)
                prefs?.getString(requestId, "")
            }
        } else throw ArgumentEmptyException()

        binding.etCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirm()
                binding.etCode.hideKeyboard()
            }
            true
        }

        binding.btnNext.setOnClickListener {
            confirm()
            binding.btnNext.hideKeyboard()
        }

        binding.etCode.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            binding.viewMobileNumberUnderline.isEnabled = !hasFocus
        }
    }

    private fun confirm() {
        if (binding.etCode.text.toString().isEmpty()) {
            binding.etCode.error =
                LanguageUtils.getLocaleStringResource(locale, R.string.enter_code, requireContext())
        } else {
            showLoading()
            clickMerchantManager.confirmPaymentByCard(
                requestId!!,
                binding.etCode.text.toString(),
                object : ResponseListener<ConfirmPaymentByCardResponse> {
                    override fun onFailure(e: Exception) {
                        e.printStackTrace()
                        showError()
                        showErrorMessage(e)
                    }

                    override fun onSuccess(response: ConfirmPaymentByCardResponse) {
                        hideLoading()
                        (parentFragment as? MainDialogFragment)?.openPaymentResultPage(
                            PaymentResponse(
                                response.paymentStatusNote,
                                response.paymentId,
                                response.paymentStatus ?: throw IllegalStateException(),
                                0
                            )
                        )
                    }
                })
        }
    }

    private fun showError() {
        activity?.runOnUiThread {
            binding.pbLoading.hide()
            binding.llContainer.show()
            binding.tvError.show()
        }
    }

    private fun showErrorMessage(e: Exception) {
        e.printStackTrace()
        if (ErrorUtils.isApiError(e)) {
            activity?.runOnUiThread {
                binding.tvError.show()
                binding.tvError.text = ErrorUtils.getErrorMessage(
                    e,
                    locale,
                    requireContext()
                )
            }
        } else activity?.runOnUiThread {
            binding.tvError.show()
            binding.tvError.text = LanguageUtils.getLocaleStringResource(
                locale,
                R.string.network_connection_error,
                requireContext()
            )
        }
    }

    private fun showLoading() {
        binding.pbLoading.show()
        binding.llContainer.invisible()
        binding.tvError.hide()
    }

    private fun hideLoading() {
        activity?.runOnUiThread {
            _binding?.pbLoading?.hide()
            _binding?.llContainer?.show()
            _binding?.tvError?.hide()
        }
    }
}