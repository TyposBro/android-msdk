package uz.click.mobilesdk.impl.paymentoptions

import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import uz.click.mobilesdk.R
import uz.click.mobilesdk.core.errors.ArgumentEmptyException
import uz.click.mobilesdk.databinding.FragmentPaymentOptionBinding
import uz.click.mobilesdk.impl.MainDialogFragment
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.IS_CLICK_EVOLUTION_ENABLED
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.LOCALE
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.THEME_MODE
import uz.click.mobilesdk.utils.LanguageUtils
import java.util.*
import kotlin.collections.ArrayList

class PaymentOptionListFragment : AppCompatDialogFragment() {

    private var _binding: FragmentPaymentOptionBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentPaymentOptionBinding.inflate(inflater.cloneInContext(contextWrapper), container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parent = parentFragment as MainDialogFragment

        if (arguments != null) {
            val locale = Locale(requireArguments().getString(LOCALE, "ru"))
            binding.tvTitle.text =
                LanguageUtils.getLocaleStringResource(locale, R.string.payment_types, requireContext())
            val items = ArrayList<PaymentOption>()

            val isClickEvolutionEnabled = requireArguments().getBoolean(IS_CLICK_EVOLUTION_ENABLED, false)

            if (isClickEvolutionEnabled) {
                items.add(
                    PaymentOption(
                        R.drawable.ic_aevo,
                        LanguageUtils.getLocaleStringResource(
                            locale,
                            R.string.click_evo_app,
                            requireContext()
                        ),
                        LanguageUtils.getLocaleStringResource(
                            locale,
                            R.string.click_evo_app_description,
                            requireContext()
                        ),
                        PaymentOptionEnum.CLICK_EVOLUTION
                    )
                )
            }

            items.add(
                PaymentOption(
                    R.drawable.ic_880,
                    LanguageUtils.getLocaleStringResource(locale, R.string.invoicing, requireContext()),
                    LanguageUtils.getLocaleStringResource(
                        locale,
                        R.string.sms_confirmation,
                        requireContext()
                    ),
                    PaymentOptionEnum.USSD
                )
            )
            items.add(
                PaymentOption(
                    R.drawable.ic_cards,
                    LanguageUtils.getLocaleStringResource(locale, R.string.bank_card, requireContext()),
                    LanguageUtils.getLocaleStringResource(locale, R.string.card_props, requireContext()),
                    PaymentOptionEnum.BANK_CARD
                )
            )
            val adapter = PaymentOptionAdapter(requireContext(), themeMode, items)

            adapter.callback = object : PaymentOptionAdapter.OnPaymentOptionSelected {
                override fun selected(position: Int, item: PaymentOption) {
                    parent.paymentOptionSelected(item)
                }
            }

            binding.rvPaymentTypes.layoutManager = LinearLayoutManager(context)
            binding.rvPaymentTypes.adapter = adapter
        } else throw ArgumentEmptyException()
    }
}