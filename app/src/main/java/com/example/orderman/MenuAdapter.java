package com.example.orderman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

// You might need a more generic adapter if you want to display menu items
// differently (e.g., in a waiter's ordering screen) vs. management.
// This one is good for simple display and admin functions.

public class MenuAdapter extends FirestoreRecyclerAdapter<MenuItem, MenuAdapter.MenuItemHolder> {

    private OnItemClickListener listener;

    public MenuAdapter(@NonNull FirestoreRecyclerOptions<MenuItem> options) {
        super(options);
    }

    @Override
    protected void onBindViewHolder(@NonNull MenuItemHolder holder, int position, @NonNull MenuItem model) {
        holder.textViewName.setText(model.getName());
        holder.textViewDescription.setText(model.getDescription());
        holder.textViewPrice.setText(String.format("€%.2f", model.getPrice()));
        holder.textViewCategory.setText(model.getCategory());
        holder.textViewType.setText(model.getType());
        holder.textViewAvailable.setText(model.isAvailable() ? "Available" : "Not Available");
    }

    @NonNull
    @Override
    public MenuItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu_item,
                parent, false);
        return new MenuItemHolder(view);
    }

    // Call this method to get the MenuItem object at a specific position
    public MenuItem getMenuItem(int position) {
        return getSnapshots().get(position);
    }

    class MenuItemHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewDescription;
        TextView textViewPrice;
        TextView textViewCategory;
        TextView textViewType;
        TextView textViewAvailable;

        public MenuItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_name);
            textViewDescription = itemView.findViewById(R.id.text_view_description);
            textViewPrice = itemView.findViewById(R.id.text_view_price);
            textViewCategory = itemView.findViewById(R.id.text_view_category);
            textViewType = itemView.findViewById(R.id.text_view_type);
            textViewAvailable = itemView.findViewById(R.id.text_view_available);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemClick(getSnapshots().get(position), position);
                    }
                }
            });
        }
    }

    public interface OnItemClickListener {
        void onItemClick(MenuItem menuItem, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}