package com.ordrino.orderman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReceiptAdapter extends RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder> {

    private List<Receipt> receiptList;
    private OnReceiptClickListener listener;

    public interface OnReceiptClickListener {
        void onReceiptClick(String receiptUrl);
    }

    public ReceiptAdapter(OnReceiptClickListener listener) {
        this.receiptList = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReceiptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receipt, parent, false);
        return new ReceiptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReceiptViewHolder holder, int position) {
        Receipt currentReceipt = receiptList.get(position);

        // Use the correct IDs from your XML to set the text
        holder.textViewInfo.setText("Receipt " + (getItemCount() - position));

        // Format and set the timestamp
        Date timestamp = currentReceipt.getTimestamp();
        if (timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
            String formattedDate = sdf.format(timestamp);
            holder.textViewDate.setText(formattedDate);
        } else {
            holder.textViewDate.setText("Date N/A");
        }

        // Set the click listener on the entire item view
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReceiptClick(currentReceipt.getUrl());
            }
        });
    }

    @Override
    public int getItemCount() {
        return receiptList.size();
    }

    public void updateData(List<Receipt> newReceipts) {
        this.receiptList.clear();
        this.receiptList.addAll(newReceipts);
        notifyDataSetChanged();
    }

    public static class ReceiptViewHolder extends RecyclerView.ViewHolder {
        TextView textViewInfo;
        TextView textViewDate;

        public ReceiptViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewInfo = itemView.findViewById(R.id.text_view_receipt_info);
            textViewDate = itemView.findViewById(R.id.text_view_receipt_date);
        }
    }
}