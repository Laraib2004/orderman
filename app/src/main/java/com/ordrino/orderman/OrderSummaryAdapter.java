package com.ordrino.orderman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class OrderSummaryAdapter extends FirestoreRecyclerAdapter<OrderItem, OrderSummaryAdapter.OrderItemHolder> {

    private OnItemActionListener listener;
    private Map<String, OrderItem> selectedItems = new HashMap<>();

    // REMOVE THIS CONSTRUCTOR: public OrderSummaryAdapter() { ... }
    // It's bad practice and can lead to bugs if used.

    public OrderSummaryAdapter(@NonNull FirestoreRecyclerOptions<OrderItem> options, OnItemActionListener listener) {
        super(options);
        this.listener = listener;
    }

    @Override
    protected void onBindViewHolder(@NonNull OrderItemHolder holder, int position, @NonNull OrderItem model) {
        holder.textViewName.setText(model.getName());
        holder.textViewQuantity.setText(String.valueOf(model.getQuantity()));
        holder.textViewPrice.setText(String.format("€%.2f", model.getPrice() * model.getQuantity()));

        // This is good practice. Clear the listener to prevent conflicts.
        holder.checkBox.setOnCheckedChangeListener(null);

        // Re-set the checkbox state based on the current selected items
        holder.checkBox.setChecked(selectedItems.containsKey(model.getId()));

        // Set the listener again
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedItems.put(model.getId(), model);
            } else {
                selectedItems.remove(model.getId());
            }
        });

        // ... your existing OnClickListener code for buttons
        holder.buttonIncrement.setOnClickListener(v -> {
            if (listener != null) listener.onIncrementClick(model);
        });
        holder.buttonDecrement.setOnClickListener(v -> {
            if (listener != null) listener.onDecrementClick(model);
        });
        holder.buttonRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveClick(model);
        });
    }

    @NonNull
    @Override
    public OrderItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_summary, parent, false);
        return new OrderItemHolder(view);
    }

    // This is the new, crucial method to add.
    @Override
    public void onDataChanged() {
        super.onDataChanged();

        // Create a new map for the selected items that still exist in the new data set
        Map<String, OrderItem> updatedSelectedItems = new HashMap<>();

        // Iterate through the current, live data in the adapter
        for (int i = 0; i < getItemCount(); i++) {
            DocumentSnapshot snapshot = getSnapshots().getSnapshot(i);
            String docId = snapshot.getId();

            // If the item was previously selected, and it still exists in the data, add it back.
            if (selectedItems.containsKey(docId)) {
                updatedSelectedItems.put(docId, selectedItems.get(docId));
            }
        }

        // Update the selectedItems map with the new, clean version
        selectedItems = updatedSelectedItems;

        // You may also want to notify the activity that the total has changed here
        // listener.onSelectedItemsChanged(selectedItems);
    }


    public Map<String, OrderItem> getSelectedItems() {
        return selectedItems;
    }

    public void clearSelectedItems() {
        selectedItems.clear();
        // REMOVE THE LINE: notifyDataSetChanged();
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.listener = listener;
    }


    public interface OnItemActionListener {
        void onIncrementClick(OrderItem orderItem);
        void onDecrementClick(OrderItem orderItem);
        void onRemoveClick(OrderItem orderItem);
    }

    class OrderItemHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewQuantity;
        TextView textViewPrice;
        ImageButton buttonIncrement;
        ImageButton buttonDecrement;
        ImageButton buttonRemove;
        CheckBox checkBox;

        public OrderItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_order_item_name);
            textViewQuantity = itemView.findViewById(R.id.text_view_order_item_quantity);
            textViewPrice = itemView.findViewById(R.id.text_view_order_item_price);
            buttonIncrement = itemView.findViewById(R.id.button_increment);
            buttonDecrement = itemView.findViewById(R.id.button_decrement);
            buttonRemove = itemView.findViewById(R.id.button_remove);
            checkBox = itemView.findViewById(R.id.checkbox_select_item);
        }
    }
}