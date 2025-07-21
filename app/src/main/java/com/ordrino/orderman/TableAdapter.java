package com.ordrino.orderman;// In TableAdapter.java
// In TableAdapter.java

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

public class TableAdapter extends FirestoreRecyclerAdapter<Table, TableAdapter.TableHolder> {

    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;

    public TableAdapter(@NonNull FirestoreRecyclerOptions<Table> options) {
        super(options);
        setHasStableIds(true);
    }

    @Override
    protected void onBindViewHolder(@NonNull TableHolder holder, int position, @NonNull Table model) {
        holder.textViewNumber.setText("Table " + model.getNumber());
        holder.textViewCapacity.setText("Capacity: " + model.getCapacity());
        holder.textViewStatus.setText("Status: " + model.getStatus());
        holder.textViewSection.setText("Section: " + model.getSection());
        // Update to display total price, if you have a TextView for it in item_table.xml
        holder.textViewTotalPrice.setText("Order Total: €" + String.format("%.2f", model.getTotalPrice()));
        // Make sure you have a TextView with id `text_view_table_total_price` in `item_table.xml`
        if ("Available".equalsIgnoreCase(model.getStatus())) {
            holder.tableItemRootLayout.setBackgroundResource(R.drawable.table_available_background);
            // Text colors are now set in XML to status_text_light, so no need to change them here
            // unless you want dynamic text colors based on background
        } else {
            holder.tableItemRootLayout.setBackgroundResource(R.drawable.table_occupied_background);
            // Text colors are now set in XML to status_text_light
        }
    }

    @NonNull
    @Override
    public TableHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table,
                parent, false);
        return new TableHolder(v);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= getItemCount()) {
            return RecyclerView.NO_ID;
        }
        Table item = getItem(position);
        if (item != null && item.getId() != null) {
            return item.getId().hashCode();
        }
        return RecyclerView.NO_ID;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public interface OnItemClickListener {
        void onItemClick(Table table, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(Table table, int position);
    }

    class TableHolder extends RecyclerView.ViewHolder {
        TextView textViewNumber;
        TextView textViewCapacity;
        TextView textViewStatus;
        TextView textViewSection;
        TextView textViewTotalPrice;
        RelativeLayout tableItemRootLayout;


        public TableHolder(@NonNull View itemView) {
            super(itemView);
            textViewNumber = itemView.findViewById(R.id.text_view_table_number);
            textViewCapacity = itemView.findViewById(R.id.text_view_table_capacity);
            textViewStatus = itemView.findViewById(R.id.text_view_table_status);
            textViewSection = itemView.findViewById(R.id.text_view_table_section);
            textViewTotalPrice = itemView.findViewById(R.id.text_view_table_total_price);
            tableItemRootLayout = itemView.findViewById(R.id.table_item_status_background);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position), position);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (longClickListener != null && position != RecyclerView.NO_POSITION) {
                    return longClickListener.onItemLongClick(getItem(position), position);
                }
                return false;
            });
        }
    }
}