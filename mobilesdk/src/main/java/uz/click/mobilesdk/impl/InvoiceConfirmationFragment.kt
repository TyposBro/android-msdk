package uz.click.mobilesdk.impl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import uz.click.mobilesdk.R
import uz.click.mobilesdk.core.ClickMerchantManager
import uz.click.mobilesdk.core.callbacks.ResponseListener
import uz.click.mobilesdk.core.data.CheckoutResponse
import uz.click.mobilesdk.core.errors.ArgumentEmptyException
import uz.click.mobilesdk.databinding.FragmentInvoiceConfirmationBinding
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.THEME_MODE
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions
import uz.click.mobilesdk.utils.ContextUtils.isAppAvailable
import uz.click.mobilesdk.utils.LanguageUtils
import uz.click.mobilesdk.utils.show
import java.util.*

class InvoiceConfirmationFragment : AppCompatDialogFragment() {

    private var _binding: FragmentInvoiceConfirmationBinding? = null
    private val binding get() = _binding!!

    private var requestId = ""
    private val clickMerchantManager = ClickMerchantManager()
    lateinit var themeMode: ThemeOptions

    companion object {
        private const val APP_NAME = "air.com.ssdsoftwaresolutions.clickuz"
        private const val TELEGRAM_BOT_NAME = "http://telegram.me/clickuz"
        private const val PLAY_STORE_ADDRESS = "http://play.google.com/store/apps/details?id="
        private const val CLICK_USSD = "*880#"
    }

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
        _binding = FragmentInvoiceConfirmationBinding.inflate(inflater.cloneInContext(contextWrapper), container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listener = (parentFragment as MainDialogFragment?)?.getListener()
        when (themeMode) {
            ThemeOptions.LIGHT -> {
                binding.btnBack.setBackgroundResource(R.drawable.next_button_rounded)
            }
            ThemeOptions.NIGHT -> {
                binding.btnBack.setBackgroundResource(R.drawable.next_button_rounded_dark)
            }
        }

        if (arguments != null) {
            requestId = requireArguments().getString(MainDialogFragment.REQUEST_ID, "")
            val locale = Locale(requireArguments().getString(MainDialogFragment.LOCALE, "ru"))
            binding.tvTitle.text = LanguageUtils.getLocaleStringResource(
                locale,
                R.string.waiting_confirmation,
                requireContext()
            )
            binding.tvSubtitle.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.invoice_placed, requireContext())
            binding.tvConfirmMethods.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.ways_to_confirm, requireContext())
            binding.tvBack.text = LanguageUtils.getLocaleStringResource(locale, R.string.cancel, requireContext())
            binding.tvBotInfo.text = LanguageUtils.getLocaleStringResource(
                locale,
                R.string.click_bot_sent_code,
                requireContext()
            )
            binding.tvUssdInfo.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.call_ussd, requireContext())
            binding.tvClickApp.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.click_app, requireContext())
            binding.tvClickAppInfo.text = LanguageUtils.getLocaleStringResource(
                locale,
                R.string.enter_invoices_list,
                requireContext()
            )
            checkConfirmation()
        } else throw ArgumentEmptyException()

        binding.llUSSD.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${Uri.encode(CLICK_USSD)}")
            startActivity(intent)
        }

        binding.llApp.setOnClickListener {
            if (isAppAvailable(requireContext(), APP_NAME)) {
                val intent = context?.packageManager?.getLaunchIntentForPackage(APP_NAME)
                if (intent != null) {
                    startActivity(intent)
                }
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$PLAY_STORE_ADDRESS$APP_NAME")))
            }
        }

        binding.llTelegram.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_BOT_NAME)))
        }

        binding.btnBack.setOnClickListener {
            clickMerchantManager.invoiceCancelled = true
            listener?.onInvoiceCancelled()
            (parentFragment as MainDialogFragment?)?.close()
        }
    }

    private fun checkConfirmation() {
        binding.progressIndicator.show()
        clickMerchantManager.checkPaymentByRequestIdContinuously(
            requestId,
            object : ResponseListener<CheckoutResponse> {
                override fun onFailure(e: Exception) {
                    e.printStackTrace()
                }

                override fun onSuccess(response: CheckoutResponse) {
                    parentFragment?.let {
                        val parent = it as MainDialogFragment
                        parent.openPaymentResultPage(response.payment)
                    }
                }

            })
    }
}