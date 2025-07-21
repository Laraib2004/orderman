package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ordrino.orderman.models.MenuItem;

public class AddEditMenuItemActivity extends AppCompatActivity {

    private EditText editTextName;
    private EditText editTextDescription;
    private EditText editTextPrice;
    private Spinner spinnerTextCategory;
    private Spinner spinnerTextType;
    private CheckBox checkBoxAvailable;
    private EditText editTextImageUrl;
    private Button buttonSave;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference menuItemsRef; // Now dynamic
    private String restaurantId; // To store the received restaurant ID

    private MenuItem currentMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_menu_item);

        // Get restaurantId from the Intent
        if (getIntent().hasExtra(LoginActivity.EXTRA_RESTAURANT_ID)) {
            restaurantId = getIntent().getStringExtra(LoginActivity.EXTRA_RESTAURANT_ID);
            // Construct the dynamic CollectionReference
            menuItemsRef = db.collection("restaurants").document(restaurantId).collection("menuItems");
        } else {
            Toast.makeText(this, "Error: Restaurant ID not passed to Add/Edit Menu Item.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        editTextName = findViewById(R.id.edit_text_name);
        editTextDescription = findViewById(R.id.edit_text_description);
        editTextPrice = findViewById(R.id.edit_text_price);
        spinnerTextCategory = findViewById(R.id.spinner_text_category);
        spinnerTextType = findViewById(R.id.spinner_text_type);
        checkBoxAvailable = findViewById(R.id.checkbox_available);
        editTextImageUrl = findViewById(R.id.edit_text_image_url);
        buttonSave = findViewById(R.id.button_save_menu_item);

        ArrayAdapter<CharSequence> adapterCategory = ArrayAdapter.createFromResource(this,
                R.array.category_item, android.R.layout.simple_spinner_item);
        adapterCategory.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<CharSequence> adapterType = ArrayAdapter.createFromResource(this,
                R.array.type_item, android.R.layout.simple_spinner_item);
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spinnerTextCategory.setAdapter(adapterCategory);
        spinnerTextType.setAdapter(adapterType);

        if (getIntent().hasExtra("menuItem")) {
            currentMenuItem = (MenuItem) getIntent().getSerializableExtra("menuItem");
            if (currentMenuItem != null) {
                setTitle("Edit Menu Item");
                editTextName.setText(currentMenuItem.getName());
                editTextDescription.setText(currentMenuItem.getDescription());
                editTextPrice.setText(String.valueOf(currentMenuItem.getPrice()));
                String category = currentMenuItem.getCategory();
                int spinnerPositionCategory = adapterCategory.getPosition(category);
                spinnerTextCategory.setSelection(spinnerPositionCategory);
                String type = currentMenuItem.getType();
                int spinnerPositionType = adapterType.getPosition(type);
                spinnerTextType.setSelection(spinnerPositionType);
                checkBoxAvailable.setChecked(currentMenuItem.isAvailable());
                editTextImageUrl.setText(currentMenuItem.getImageUrl());
            }
        } else {
            setTitle("Add New Menu Item");
        }

        buttonSave.setOnClickListener(v -> saveMenuItem());
    }

    private void saveMenuItem() {
        // ... (input validation logic) ...
        String name = editTextName.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();
        String priceStr = editTextPrice.getText().toString().trim();
        String category = spinnerTextCategory.getSelectedItem().toString();
        String type = spinnerTextType.getSelectedItem().toString();
        boolean available = checkBoxAvailable.isChecked();
        String imageUrl = editTextImageUrl.getText().toString().trim();

        if (name.isEmpty() || description.isEmpty() || priceStr.isEmpty() || category.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (menuItemsRef == null) {
            Toast.makeText(this, "Database reference not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentMenuItem == null) {
            // Add new item - Firestore will automatically generate a random ID
            MenuItem newMenuItem = new MenuItem(name, description, price, category, type, available, imageUrl);
            menuItemsRef.add(newMenuItem)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Menu Item added", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Error adding menu item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Update existing item
            currentMenuItem.setName(name);
            currentMenuItem.setDescription(description);
            currentMenuItem.setPrice(price);
            currentMenuItem.setCategory(category);
            currentMenuItem.setType(type);
            currentMenuItem.setAvailable(available);
            currentMenuItem.setImageUrl(imageUrl);

            // Use the existing ID to update the document
            menuItemsRef.document(currentMenuItem.getId()).set(currentMenuItem)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Menu Item updated", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Error updating menu item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}