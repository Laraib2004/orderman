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

    // You can use both listeners if needed, or combine them
    public interface OnItemActionListener {
        void onIncrementClick(OrderItem orderItem);
        void onDecrementClick(OrderItem orderItem);
        void onRemoveClick(OrderItem orderItem);
        void onSelectionChange(OrderItem orderItem, int delta);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount, int totalCount);
    }

    private OnItemActionListener listener;

    private OnSelectionChangedListener selectionListener;
    private Map<String, OrderItem> selectedItems = new HashMap<>();

    private Map<String, Integer> itemsToPay = new HashMap<>();

    public OrderSummaryAdapter(@NonNull FirestoreRecyclerOptions<OrderItem> options, OnItemActionListener listener, OnSelectionChangedListener selectionListener) {
        super(options);
        this.listener = listener;
        this.selectionListener = selectionListener;
    }

    @Override
    protected void onBindViewHolder(@NonNull OrderItemHolder holder, int position, @NonNull OrderItem model) {
        holder.textViewName.setText(model.getName());
        holder.textViewQuantity.setText(String.valueOf(model.getQuantity()));
        holder.textViewPrice.setText(String.format("€%.2f", model.getPrice() * model.getQuantity()));

        // --- SELECTION LOGIC ---
        final int currentTotalQuantity = model.getQuantity();
        final int currentSelectedQuantity = itemsToPay.getOrDefault(model.getId(), 0);

        // Update the selection display: e.g., "2 / 5"
        holder.textViewSelectedQuantity.setText(currentSelectedQuantity + " / " + currentTotalQuantity);

        holder.buttonSelectionIncrement.setOnClickListener(v -> {
            // Only allow increment if selected quantity is less than total quantity
            if (currentSelectedQuantity < currentTotalQuantity) {
                if (listener != null) listener.onSelectionChange(model, 1);
            }
        });

        holder.buttonSelectionDecrement.setOnClickListener(v -> {
            // Only allow decrement if selected quantity is greater than zero
            if (currentSelectedQuantity > 0) {
                if (listener != null) listener.onSelectionChange(model, -1);
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

    // This method is critical for keeping selected items in sync
    // with database changes
    @Override
    public void onDataChanged() {
        super.onDataChanged();
        Map<String, OrderItem> updatedSelectedItems = new HashMap<>();
        for (int i = 0; i < getItemCount(); i++) {
            DocumentSnapshot snapshot = getSnapshots().getSnapshot(i);
            String docId = snapshot.getId();
            if (selectedItems.containsKey(docId)) {
                updatedSelectedItems.put(docId, selectedItems.get(docId));
            }
        }
        selectedItems = updatedSelectedItems;
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItems.size(), getItemCount());
        }
    }


    public Map<String, OrderItem> getSelectedItems() {
        return selectedItems;
    }

    public Map<String, Integer> getItemsToPay() {
        return itemsToPay;
    }

    // This method handles the logic for the "Select All" checkbox
    public void selectAll(boolean isChecked) {
        selectedItems.clear(); // First, clear the map
        if (isChecked) {
            // If checked, add all items from the current list to the map
            for (int i = 0; i < getItemCount(); i++) {
                OrderItem item = getItem(i);
                selectedItems.put(item.getId(), item);
            }
        }
        // Notify the adapter to refresh all views, updating the checkboxes
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItems.size(), getItemCount());
        }
    }

    // This is a crucial setter to connect the adapter to the Activity's checkbox
    public void setOnSelectionChangedListener(OnSelectionChangedListener selectionListener) {
        this.selectionListener = selectionListener;
    }

    public OnSelectionChangedListener getSelectionListener() {
        return selectionListener;
    }

    // --- End of New Methods ---

    public void clearSelectedItems() {
        selectedItems.clear();
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.listener = listener;
    }

    class OrderItemHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewQuantity;
        TextView textViewPrice;
        ImageButton buttonIncrement;
        ImageButton buttonDecrement;
        ImageButton buttonRemove;
        ImageButton buttonSelectionIncrement;
        ImageButton buttonSelectionDecrement;
        TextView textViewSelectedQuantity;

        public OrderItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_order_item_name);
            textViewQuantity = itemView.findViewById(R.id.text_view_order_item_quantity);
            textViewPrice = itemView.findViewById(R.id.text_view_order_item_price);
            buttonIncrement = itemView.findViewById(R.id.button_increment);
            buttonDecrement = itemView.findViewById(R.id.button_decrement);
            buttonRemove = itemView.findViewById(R.id.button_remove);
            buttonSelectionIncrement = itemView.findViewById(R.id.button_selection_increment);
            buttonSelectionDecrement = itemView.findViewById(R.id.button_selection_decrement);
            textViewSelectedQuantity = itemView.findViewById(R.id.text_view_selected_quantity);
        }
    }
}