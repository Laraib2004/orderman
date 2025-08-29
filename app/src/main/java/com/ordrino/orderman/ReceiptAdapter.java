package com.ordrino.orderman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ReceiptAdapter extends FirestoreRecyclerAdapter<Receipt, ReceiptAdapter.ReceiptViewHolder> {

    public interface OnReceiptClickListener {
        void onReceiptClick(String receiptUrl);
    }

    private final OnReceiptClickListener listener;

    public ReceiptAdapter(@NonNull FirestoreRecyclerOptions<Receipt> options, OnReceiptClickListener listener) {
        super(options);
        this.listener = listener;
    }

    @Override
    protected void onBindViewHolder(@NonNull ReceiptViewHolder holder, int position, @NonNull Receipt model) {
        // Set the receipt info text (you could use the timestamp or a generated name)
        holder.textViewInfo.setText("Receipt " + (getItemCount() - position)); // Example: Receipt 1, Receipt 2, etc.

        // Format the timestamp for a human-readable date and time
        if (model.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.getDefault());
            String formattedDate = sdf.format(model.getTimestamp());
            holder.textViewDate.setText(formattedDate);
        } else {
            holder.textViewDate.setText("Date N/A");
        }

        // Set the click listener on the entire item view
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReceiptClick(model.getUrl());
            }
        });
    }

    @NonNull
    @Override
    public ReceiptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receipt, parent, false);
        return new ReceiptViewHolder(view);
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