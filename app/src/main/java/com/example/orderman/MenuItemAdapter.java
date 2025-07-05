package com.example.orderman;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // Import Button
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

import java.util.HashMap;
import java.util.Map;

public class MenuItemAdapter extends FirestoreRecyclerAdapter<MenuItem, MenuItemAdapter.MenuItemHolder> {

    // Change the listener interface
    private OnItemQuantityChangeListener listener;
    // Map to store quantities for each menu item, keyed by MenuItemId
    private Map<String, Integer> itemQuantities = new HashMap<>();

    public MenuItemAdapter(@NonNull FirestoreRecyclerOptions<MenuItem> options) {
        super(options);
        setHasStableIds(true); // Crucial for "Inconsistency detected" errors
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= getItemCount()) {
            return RecyclerView.NO_ID;
        }
        MenuItem item = getItem(position);
        if (item != null && item.getId() != null) {
            return item.getId().hashCode();
        }
        return RecyclerView.NO_ID;
    }

    @Override
    protected void onBindViewHolder(@NonNull MenuItemHolder holder, int position, @NonNull MenuItem model) {
        Log.d("MenuItemAdapter", "Binding item at position " + position +
                ", Name: " + model.getName() +
                ", ID: " + model.getId());

        holder.textViewName.setText(model.getName());
        holder.textViewDescription.setText(model.getDescription());
        holder.textViewPrice.setText(String.format("€%.2f", model.getPrice()));
        holder.textViewCategory.setText(model.getCategory());
        holder.textViewType.setText(model.getType());
        holder.textViewAvailable.setText(model.isAvailable() ? "Available" : "Not Available");

        // Set initial quantity from the map, or 0 if not present
        int quantity;
        if (itemQuantities.containsKey(model.getId())) {
            quantity = itemQuantities.get(model.getId());
        } else {
            quantity = 0;
        }
        holder.textViewItemQuantity.setText(String.valueOf(quantity));
        holder.textViewItemQuantity.setText(String.valueOf(quantity));

        // Disable minus button if quantity is 0
        holder.buttonMinus.setEnabled(quantity > 0);
    }

    @NonNull
    @Override
    public MenuItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu_item,
                parent, false);
        return new MenuItemHolder(view);
    }

    // New interface for quantity changes
    public interface OnItemQuantityChangeListener {
        void onPlusClick(MenuItem menuItem, int position, int currentQuantity);
        void onMinusClick(MenuItem menuItem, int position, int currentQuantity);
    }

    public void setOnItemQuantityChangeListener(OnItemQuantityChangeListener listener) {
        this.listener = listener;
    }

    // Method to update a specific item's quantity in the adapter's internal map
    public void updateItemQuantity(String menuItemId, int quantity) {
        itemQuantities.put(menuItemId, quantity);
        // Find the position of the item and notify adapter to re-bind it
        // This is not efficient for large lists, but acceptable for typical menu sizes.
        // For better performance, consider diffing utilities or more targeted updates.
        int position = -1;
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).getId().equals(menuItemId)) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    // Method to get all currently selected items and their quantities
    // This will be called when "Send Order" is clicked
    public Map<String, Integer> getSelectedItemsWithQuantities() {
        // Return a copy to prevent external modification
        return new HashMap<>(itemQuantities);
    }

    class MenuItemHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewDescription;
        TextView textViewPrice;
        TextView textViewCategory;
        TextView textViewType;
        TextView textViewAvailable;
        TextView textViewItemQuantity; // New
        Button buttonPlus;           // New
        Button buttonMinus;          // New

        public MenuItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_name);
            textViewDescription = itemView.findViewById(R.id.text_view_description);
            textViewPrice = itemView.findViewById(R.id.text_view_price);
            textViewCategory = itemView.findViewById(R.id.text_view_category);
            textViewType = itemView.findViewById(R.id.text_view_type);
            textViewAvailable = itemView.findViewById(R.id.text_view_available);
            textViewItemQuantity = itemView.findViewById(R.id.text_view_item_quantity);
            buttonPlus = itemView.findViewById(R.id.button_plus);
            buttonMinus = itemView.findViewById(R.id.button_minus);

            // Set up click listeners for the new buttons
            buttonPlus.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    MenuItem menuItem = getItem(position);
                    int currentQuantity = itemQuantities.containsKey(menuItem.getId())
                            ? itemQuantities.get(menuItem.getId())
                            : 0;
                    listener.onPlusClick(menuItem, position, currentQuantity);
                }
            });

            buttonMinus.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    MenuItem menuItem = getItem(position);
                    int currentQuantity = itemQuantities.containsKey(menuItem.getId())
                            ? itemQuantities.get(menuItem.getId())
                            : 0;
                    listener.onMinusClick(menuItem, position, currentQuantity);
                }
            });
            // Remove the old itemView.setOnClickListener if it was here, as it's no longer used for adding items.
        }
    }
}