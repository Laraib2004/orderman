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

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PreparerOrderAdapter extends FirestoreRecyclerAdapter<Order, PreparerOrderAdapter.PreparerOrderViewHolder> {

    private static final String TAG = "PreparerOrderAdapter";
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Context context;

    public PreparerOrderAdapter(@NonNull FirestoreRecyclerOptions<Order> options, Context context) {
        super(options);
        this.context = context;
    }

    @Override
    protected void onBindViewHolder(@NonNull PreparerOrderViewHolder holder, int position, @NonNull Order model) {
        // Set Table Number
        holder.tvTableNumber.setText("Table " + model.getTableNr());

        // Build the list of order items from the list within the Order object
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
        int backgroundDrawable = R.drawable.status_new_background; // Default to new
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
            DocumentSnapshot snapshot = getSnapshots().getSnapshot(holder.getAdapterPosition());
            updateOrderStatus(snapshot, model.getStatus());
        });
    }

    private void updateOrderStatus(DocumentSnapshot snapshot, String currentStatus) {
        String newStatus;
        switch (currentStatus) {
            case "New":
                newStatus = "Preparing";
                break;
            case "Preparing":
                newStatus = "Ready";
                break;
            default:
                // No more status updates for this item
                Toast.makeText(context, "Order is already ready!", Toast.LENGTH_SHORT).show();
                return;
        }

        snapshot.getReference().update("status", newStatus)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "DocumentSnapshot successfully updated!"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating document", e));
    }

    @NonNull
    @Override
    public PreparerOrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_preparer_order, parent, false);
        return new PreparerOrderViewHolder(view);
    }

    public static class PreparerOrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTableNumber;
        TextView tvOrderStatus;
        TextView tvOrderItems;
        TextView tvOrderTimestamp;

        public PreparerOrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTableNumber = itemView.findViewById(R.id.tv_table_number);
            tvOrderStatus = itemView.findViewById(R.id.tv_order_status);
            tvOrderItems = itemView.findViewById(R.id.tv_order_items);
            tvOrderTimestamp = itemView.findViewById(R.id.tv_order_timestamp);
        }
    }
}