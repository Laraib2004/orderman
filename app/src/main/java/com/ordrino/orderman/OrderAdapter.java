package com.ordrino.orderman;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private static final String TAG = "OrderAdapter";
    private List<Order> orderList;
    private Context context;

    // We'll need a way to access the document reference
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(DocumentReference documentReference, String currentStatus);
    }

    public OrderAdapter(Context context, OnItemClickListener listener) {
        this.orderList = new ArrayList<>();
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_preparer_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order model = orderList.get(position);

        // Set Table Number
        holder.tvTableNumber.setText("Table " + model.getTableNr());

        // Build the list of order items
        StringBuilder itemsList = new StringBuilder();
        if (model.getOrderedItems() != null) {
            for (OrderItem item : model.getOrderedItems()) {
                itemsList.append("• ")
                        .append(item.getQuantity())
                        .append("x ")
                        .append(item.getName())
                        .append("\n");
            }
        }
        holder.tvOrderItems.setText(itemsList.toString().trim());

        // Format the timestamp
        if (model.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            String formattedTime = sdf.format(model.getTimestamp());
            holder.tvOrderTimestamp.setText("Ordered at " + formattedTime);
        }

        // Set the status and background color
        holder.tvOrderStatus.setText(model.getStatus());
        int backgroundDrawable = R.drawable.status_new_background;
        int textColor = R.color.white;
        switch (model.getStatus()) {
            case "New":
                backgroundDrawable = R.drawable.status_new_background;
                break;
            case "Preparing":
                backgroundDrawable = R.drawable.status_preparing_background;
                break;
            case "Ready":
                backgroundDrawable = R.drawable.status_ready_background;
                textColor = R.color.black;
                break;
            default:
                break;
        }
        holder.tvOrderStatus.setBackgroundResource(backgroundDrawable);
        holder.tvOrderStatus.setTextColor(ContextCompat.getColor(context, textColor));

        // Set the click listener to update the status
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                // Pass the document reference and current status to the activity
                // The activity will handle the Firestore update
                listener.onItemClick(model.getDocumentReference(), model.getStatus());
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    public void updateData(List<Order> newOrders) {
        orderList.clear();
        orderList.addAll(newOrders);
        notifyDataSetChanged(); // Tell the adapter that the entire dataset has changed
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTableNumber;
        TextView tvOrderStatus;
        TextView tvOrderItems;
        TextView tvOrderTimestamp;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTableNumber = itemView.findViewById(R.id.tv_table_number);
            tvOrderStatus = itemView.findViewById(R.id.tv_order_status);
            tvOrderItems = itemView.findViewById(R.id.tv_order_items);
            tvOrderTimestamp = itemView.findViewById(R.id.tv_order_timestamp);
        }
    }
}