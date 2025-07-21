package com.ordrino.orderman;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.ordrino.orderman.models.MenuItem;

/**
 * Adapter for displaying MenuItems in the MenuManagementActivity.
 * This adapter is designed for viewing/editing items, not for order taking with quantities.
 */
public class MenuManagementItemAdapter extends FirestoreRecyclerAdapter<MenuItem, MenuManagementItemAdapter.MenuManagementItemHolder> {

    private OnItemClickListener listener;

    /**
     * Constructs a new FirestoreRecyclerAdapter.
     * @param options The options for the adapter.
     */
    public MenuManagementItemAdapter(@NonNull FirestoreRecyclerOptions<MenuItem> options) {
        super(options);
        // Crucial for "Inconsistency detected" errors when data changes
        setHasStableIds(true);
    }

    /**
     * Returns the stable ID for the item at the given position.
     * Uses the MenuItem's ID hashCode for a stable ID.
     * @param position The position of the item.
     * @return The stable ID.
     */
    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= getItemCount()) {
            return RecyclerView.NO_ID; // Return a default invalid ID for out of bounds
        }
        MenuItem item = getItem(position); // Safely get the MenuItem model
        if (item != null && item.getId() != null) {
            return item.getId().hashCode();
        }
        return RecyclerView.NO_ID; // If ID is null, return invalid ID
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the ViewHolder to reflect the item at the given position.
     * @param holder The ViewHolder which should be updated to represent the contents of the item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     * @param model The MenuItem object at the given position.
     */
    @Override
    protected void onBindViewHolder(@NonNull MenuManagementItemHolder holder, int position, @NonNull MenuItem model) {
        Log.d("MenuManagementAdapter", "Binding item at position " + position +
                ", Name: " + model.getName() +
                ", ID: " + model.getId());

        holder.textViewName.setText(model.getName());
        holder.textViewDescription.setText(model.getDescription());
        holder.textViewPrice.setText(String.format("€%.2f", model.getPrice()));
        holder.textViewCategory.setText(model.getCategory());
        holder.textViewType.setText(model.getType());
        holder.textViewAvailable.setText(model.isAvailable() ? "Available" : "Not Available");
    }

    /**
     * Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new MenuManagementItemHolder that holds a View of the given view type.
     */
    @NonNull
    @Override
    public MenuManagementItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // This line references the item_menu_management.xml layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu_management,
                parent, false);
        return new MenuManagementItemHolder(view);
    }

    /**
     * Interface for click events on menu items.
     */
    public interface OnItemClickListener {
        void onItemClick(MenuItem menuItem, int position);
    }

    /**
     * Sets the click listener for items in the RecyclerView.
     * @param listener The listener to set.
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * ViewHolder for MenuManagementItemAdapter. Holds the views for each menu item.
     */
    class MenuManagementItemHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewDescription;
        TextView textViewPrice;
        TextView textViewCategory;
        TextView textViewType;
        TextView textViewAvailable;

        /**
         * Constructor for MenuManagementItemHolder.
         * @param itemView The view for a single menu item.
         */
        public MenuManagementItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_name);
            textViewDescription = itemView.findViewById(R.id.text_view_description);
            textViewPrice = itemView.findViewById(R.id.text_view_price);
            textViewCategory = itemView.findViewById(R.id.text_view_category);
            textViewType = itemView.findViewById(R.id.text_view_type);
            textViewAvailable = itemView.findViewById(R.id.text_view_available);

            // Set up the click listener for the entire item view
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    // Ensure valid position and listener is set
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemClick(getSnapshots().get(position), position);
                    }
                }
            });
        }
    }
}