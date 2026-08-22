package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tkbiswas.pilesclinic.databinding.ItemCredentialCardBinding

class PasswordCenterAdapter(
    private val context: Context,
    private var items: List<UserCredential>,
    private val onChange: (UserCredential) -> Unit
) : RecyclerView.Adapter<PasswordCenterAdapter.ViewHolder>() {

    fun updateItems(newItems: List<UserCredential>) {
        /* 🔴🔒 V509 (TK-রিপোর্ট ২১.০৮.২০২৬ — "স্ক্রিন কম্পন দিচ্ছে"):
           তালিকা আগে দুবার বসত (ফোনে জমানো, তারপর ক্লাউড)। হুবহু এক হলেও
           `notifyDataSetChanged()` প্রতিবার সব সারি নতুন করে আঁকত — চোখে
           সেটাই ঝিলিক। এখন হুবহু এক হলে আর বসানো হয় না।
           ⛔ এক চুল আলাদা হলেই আগের মতোই পুরো বসে। */
        if (items.size == newItems.size && items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemCredentialCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCredentialCardBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val acc = item.account

        // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে মোবাইল দুইবার দেখাত।
        b.tvName.text = acc.name.ifBlank { "UNKNOWN" }
        b.tvMeta.text = "${acc.role} · ${acc.branch.ifBlank { "-" }} · ${PasswordCenterModel.mob(acc.mobile)}"
        b.tvPassword.text = "Password: ${item.password}"
        b.btnChange.setOnClickListener { onChange(item) }
    
        // 🔴🔒 V449 — একই ফিক্স (দেখুন FollowUpAdapter.kt-এর মন্তব্য): তালিকার
        // সারির বাংলা যাতে বাংলা-বন্ধ স্টাফের ফোনে কখনো না দেখা যায়।
        try { NoBengali.sweep(holder.itemView) } catch (_: Throwable) { }
    }
}
