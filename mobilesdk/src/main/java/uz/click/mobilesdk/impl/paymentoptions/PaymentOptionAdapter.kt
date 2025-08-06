package uz.click.mobilesdk.impl.paymentoptions

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import uz.click.mobilesdk.R
import uz.click.mobilesdk.databinding.ItemPaymentOptionBinding

class PaymentOptionAdapter(
    val context: Context,
    val themeMode: ThemeOptions = ThemeOptions.LIGHT,
    val items: ArrayList<PaymentOption>
) :
    RecyclerView.Adapter<PaymentOptionAdapter.PaymentOptionViewHolder>() {

    lateinit var callback: OnPaymentOptionSelected

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentOptionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemPaymentOptionBinding.inflate(inflater, parent, false)
        return PaymentOptionViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PaymentOptionViewHolder, position: Int) {
        holder.bind(items[position], position == items.size - 1)
        holder.itemView.setOnClickListener {
            callback.selected(position, items[position])
        }
    }

    class PaymentOptionViewHolder(private val binding: ItemPaymentOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PaymentOption, isLastItem: Boolean) {
            binding.ivOptionImage.setImageDrawable(ContextCompat.getDrawable(binding.root.context, item.image))
            binding.tvOptionTitle.text = item.title
            binding.tvOptionSubtitle.text = item.subtitle
            binding.divider.visibility = if (isLastItem) View.INVISIBLE else View.VISIBLE
        }
    }

    interface OnPaymentOptionSelected {
        fun selected(position: Int, item: PaymentOption)
    }
}