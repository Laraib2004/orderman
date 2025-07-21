package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ordrino.orderman.models.Table;

import java.util.HashMap;
import java.util.Map;

public class AddEditTableActivity extends AppCompatActivity {

    private static final String TAG = "AddEditTableActivity";

    private EditText editTextNumber;
    private EditText editTextCapacity;
    private Spinner spinnerSection; // <--- ADD THIS LINE
    private Spinner spinnerStatus;
    private Button buttonSaveTable;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference tablesRef;
    private String restaurantId;
    private String tableId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_table);

        editTextNumber = findViewById(R.id.edit_text_table_number);
        editTextCapacity = findViewById(R.id.edit_text_table_capacity);
        spinnerSection = findViewById(R.id.spinner_table_section);
        spinnerStatus = findViewById(R.id.spinner_table_status);
        buttonSaveTable = findViewById(R.id.button_save_table);

        // Setup spinner for table status
        ArrayAdapter<CharSequence> adapterStatus = ArrayAdapter.createFromResource(this,
                R.array.table_statuses, android.R.layout.simple_spinner_item);
        adapterStatus.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(adapterStatus);

        // Setup spinner for table status
        ArrayAdapter<CharSequence> adapterSection = ArrayAdapter.createFromResource(this,
                R.array.table_section, android.R.layout.simple_spinner_item);
        adapterSection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSection.setAdapter(adapterSection);

        // Retrieve restaurantId
        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            tablesRef = db.collection("restaurants").document(restaurantId).collection("tables");
            Log.d(TAG, "Tables collection ref: " + tablesRef.getPath());
        } else {
            Toast.makeText(this, "Error: Restaurant ID not found.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Restaurant ID missing from Intent.");
            finish();
            return;
        }

        // PRIMARY CHECK FOR EDIT MODE: THE TABLE ID
        if (getIntent().hasExtra("tableId")) {
            tableId = getIntent().getStringExtra("tableId");
            Log.d(TAG, "Received tableId for editing: " + tableId);
            setTitle("Edit Table");

            Table currentTable = (Table) getIntent().getSerializableExtra("table");
            if (currentTable != null) {
                editTextNumber.setText(String.valueOf(currentTable.getNumber()));
                editTextCapacity.setText(String.valueOf(currentTable.getCapacity()));
                String section = currentTable.getSection();
                int spinnerPositionSection = adapterSection.getPosition(section);
                spinnerSection.setSelection(spinnerPositionSection);
                String status = currentTable.getStatus();
                int spinnerPositionStatus = adapterStatus.getPosition(status);
                spinnerStatus.setSelection(spinnerPositionStatus);
                Log.d(TAG, "Populated fields for table: " + currentTable.getNumber());
            } else {
                Log.w(TAG, "Table object was null despite receiving tableId. Fields not populated.");
                Toast.makeText(this, "Error loading table details. Please try again.", Toast.LENGTH_LONG).show();
            }
        } else {
            setTitle("Add Table");
            tableId = null;
            Log.d(TAG, "No tableId received. Assuming Add Table mode.");
        }

        buttonSaveTable.setOnClickListener(v -> saveTable());
    }

    private void saveTable() {
        String numberStr = editTextNumber.getText().toString().trim();
        String capacityStr = editTextCapacity.getText().toString().trim();
        String section = spinnerSection.getSelectedItem().toString();
        String status = spinnerStatus.getSelectedItem().toString();

        if (numberStr.isEmpty() || capacityStr.isEmpty() || section.isEmpty()) { // <--- ADD section check
            Toast.makeText(this, "Please enter table number, capacity, and section", Toast.LENGTH_SHORT).show();
            return;
        }

        int number = Integer.parseInt(numberStr);
        int capacity = Integer.parseInt(capacityStr);

        Map<String, Object> tableData = new HashMap<>();
        tableData.put("number", number);
        tableData.put("capacity", capacity);
        tableData.put("section", section); // <--- INCLUDE SECTION IN THE MAP
        tableData.put("status", status);

        // Retain currentOrderId when updating an existing table, if it exists
        // This is important because you're only updating fields, not setting the whole object.
        // If currentOrderId is *only* managed elsewhere and you don't want to touch it here,
        // then the update method is fine. But if it *can* be null or you want to ensure it's
        // explicitly set to null here, you'd need to add:
        // if (currentTable != null) {
        //    tableData.put("currentOrderId", currentTable.getCurrentOrderId()); // Or handle it as needed
        // }
        // For 'currentOrderId: null', it's usually fine as Firestore won't remove it on update
        // if it's not present in `tableData`.

        if (tableId != null && !tableId.isEmpty()) {
            Log.d(TAG, "Attempting to update table with ID: " + tableId);
            tablesRef.document(tableId)
                    .update(tableData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddEditTableActivity.this, "Table updated", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Table " + tableId + " updated successfully.");
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddEditTableActivity.this, "Error updating table: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error updating table " + tableId + ": " + e.getMessage(), e);
                    });
        } else {
            Log.d(TAG, "Attempting to add new table.");
            // When adding, currentOrderId should probably be null initially
            tableData.put("currentOrderId", null); // <--- Explicitly set currentOrderId to null for new tables

            tablesRef.add(tableData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(AddEditTableActivity.this, "Table added", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "New table added with ID: " + documentReference.getId());
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddEditTableActivity.this, "Error adding table: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error adding new table: " + e.getMessage(), e);
                    });
        }
    }
}