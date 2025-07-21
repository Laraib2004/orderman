package com.example.orderman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for displaying category filter chips in a RecyclerView.
 * Manages the selected state of categories.
 */
public class CategoryFilterAdapter extends RecyclerView.Adapter<CategoryFilterAdapter.CategoryViewHolder> {

    private List<String> categories;
    private Set<String> selectedCategories; // Stores currently selected categories
    private OnCategoryClickListener listener;

    /**
     * Interface for handling category click events.
     */
    public interface OnCategoryClickListener {
        void onCategoryClick(String categoryName, boolean isSelected);
    }

    /**
     * Constructor for CategoryFilterAdapter.
     * @param categories A list of all available category names.
     * @param listener The listener for category click events.
     */
    public CategoryFilterAdapter(List<String> categories, OnCategoryClickListener listener) {
        this.categories = new ArrayList<>(categories);
        this.selectedCategories = new HashSet<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String category = categories.get(position);
        holder.textViewCategoryChip.setText(category);

        // Set selected state for the TextView based on whether it's in the selectedCategories set
        holder.textViewCategoryChip.setSelected(selectedCategories.contains(category));

        holder.itemView.setOnClickListener(v -> {
            boolean isSelected = selectedCategories.contains(category);
            if (isSelected) {
                selectedCategories.remove(category);
            } else {
                selectedCategories.add(category);
            }
            // Notify the adapter that this item's state has changed, so it can re-bind
            notifyItemChanged(position);
            // Notify the listener (MenuManagementActivity) about the click
            if (listener != null) {
                listener.onCategoryClick(category, !isSelected); // Pass the new selected state
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    /**
     * Returns the set of currently selected category names.
     * @return A Set of Strings representing selected categories.
     */
    public Set<String> getSelectedCategories() {
        return new HashSet<>(selectedCategories); // Return a copy to prevent external modification
    }

    /**
     * ViewHolder for CategoryFilterAdapter.
     */
    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView textViewCategoryChip;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewCategoryChip = itemView.findViewById(R.id.text_view_category_chip);
        }
    }
}