package com.ordrino.orderman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

public class OrderSummaryAdapter extends FirestoreRecyclerAdapter<OrderItem, OrderSummaryAdapter.OrderItemHolder> {

    private OnItemActionListener listener;

    public OrderSummaryAdapter() {
        super(new FirestoreRecyclerOptions.Builder<OrderItem>().build());
    }


    public OrderSummaryAdapter(@NonNull FirestoreRecyclerOptions<OrderItem> options) {
        super(options);
    }

    @Override
    protected void onBindViewHolder(@NonNull OrderItemHolder holder, int position, @NonNull OrderItem model) {
        holder.textViewName.setText(model.getName());
        holder.textViewQuantity.setText(String.valueOf(model.getQuantity()));
        holder.textViewPrice.setText(String.format("€%.2f", model.getPrice() * model.getQuantity()));

        holder.buttonIncrement.setOnClickListener(v -> {
            if (listener != null) {
                listener.onIncrementClick(model);
            }
        });

        holder.buttonDecrement.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDecrementClick(model);
            }
        });

        holder.buttonRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveClick(model);
            }
        });
    }

    @NonNull
    @Override
    public OrderItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_summary, parent, false);
        return new OrderItemHolder(view);
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

        public OrderItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_order_item_name);
            textViewQuantity = itemView.findViewById(R.id.text_view_order_item_quantity);
            textViewPrice = itemView.findViewById(R.id.text_view_order_item_price);
            buttonIncrement = itemView.findViewById(R.id.button_increment_quantity);
            buttonDecrement = itemView.findViewById(R.id.button_decrement_quantity);
            buttonRemove = itemView.findViewById(R.id.button_remove_item);
        }
    }
}