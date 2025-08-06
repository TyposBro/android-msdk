package uz.click.mobilesdk.impl

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import uz.click.mobilesdk.R
import uz.click.mobilesdk.core.callbacks.ClickMerchantListener
import uz.click.mobilesdk.core.data.PaymentResponse
import uz.click.mobilesdk.core.errors.ArgumentEmptyException
import uz.click.mobilesdk.databinding.FragmentPaymentResultBinding
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.LOCALE
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.PAYMENT_AMOUNT
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.PAYMENT_RESULT
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.THEME_MODE
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions
import uz.click.mobilesdk.utils.*
import java.util.*

class PaymentResultFragment : AppCompatDialogFragment() {

    private var _binding: FragmentPaymentResultBinding? = null
    private val binding get() = _binding!!

    private var listener: ClickMerchantListener? = null
    private lateinit var locale: Locale
    private lateinit var themeMode: ThemeOptions

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
        _binding = FragmentPaymentResultBinding.inflate(inflater.cloneInContext(contextWrapper), container, false)
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
                binding.btnClose.setBackgroundResource(R.drawable.next_button_rounded)
            }
            ThemeOptions.NIGHT -> {
                binding.btnClose.setBackgroundResource(R.drawable.next_button_rounded_dark)
            }
        }

        listener = (parentFragment as MainDialogFragment?)?.getListener()
        if (arguments != null) {
            val result = requireArguments().getSerializable(PAYMENT_RESULT) as PaymentResponse
            locale = Locale(requireArguments().getString(LOCALE, "ru"))
            binding.tvPaymentTitle.text = result.paymentStatusDescription
            binding.tvPaymentAmount.text = requireArguments().getDouble(PAYMENT_AMOUNT).formatDecimals()

            binding.tvPaymentNumberTitle.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.payment_number, requireContext())
            binding.tvPaid.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.to_be_paid, requireContext())
            binding.tvAbbr.text = LanguageUtils.getLocaleStringResource(locale, R.string.abbr, requireContext())
            binding.tvClose.text = LanguageUtils.getLocaleStringResource(locale, R.string.close, requireContext())

            if (result.paymentId != null) {
                binding.llPaymentNumber.show()
                binding.tvPaymentNumber.text = "${result.paymentId}"
            } else binding.llPaymentNumber.hide()

            when {
                result.paymentStatus < 0 -> {
                    listener?.onFailure()
                    binding.ivPaymentStatus.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_failure
                        )
                    )
                    binding.tvPaymentSubtitle.text =
                        LanguageUtils.getLocaleStringResource(
                            locale,
                            R.string.failure_payment,
                            requireContext()
                        )
                    binding.tvPaymentTitle.text =
                        LanguageUtils.getLocaleStringResource(
                            locale,
                            R.string.payment_failed,
                            requireContext()
                        )
                }
                result.paymentStatus == 2 -> {
                    listener?.onSuccess(result.paymentId!!)
                    binding.ivPaymentStatus.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_success
                        )
                    )
                    binding.tvPaymentTitle.text =
                        LanguageUtils.getLocaleStringResource(
                            locale,
                            R.string.payment_success,
                            requireContext()
                        )
                    binding.tvPaymentSubtitle.text =
                        LanguageUtils.getLocaleStringResource(
                            locale,
                            R.string.success_payment,
                            requireContext()
                        )
                }
            }
        } else throw ArgumentEmptyException()

        binding.btnClose.setOnClickListener {
            parentFragment?.let {
                val parent = parentFragment as MainDialogFragment
                parent.close()
            }
        }
    }
}