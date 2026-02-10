package com.ordrino.orderman;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import java.util.List;
import java.util.ArrayList;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class AddEditMenuItemActivity extends AppCompatActivity {

    private EditText editTextName;
    private EditText editTextDescription;
    private EditText editTextPrice;
    private Spinner spinnerTextCategory;
    private Spinner spinnerTextType;
    private Spinner spinnerTaxCode;
    private CheckBox checkBoxAvailable;
    private EditText editTextImageUrl;
    private Button buttonSave;
    private ProgressBar progressBar;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference menuItemsRef; // Now dynamic
    private String restaurantId; // To store the received restaurant ID

    private MenuItem currentMenuItem;
    private SpinnerItem selectedItemTaxCode;

    public class SpinnerItem {
        private String displayName;
        private String value;

        public SpinnerItem(String displayName, String value) {
            this.displayName = displayName;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return displayName; // This determines what shows in the spinner
        }
    }

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
        spinnerTaxCode = findViewById(R.id.spinner_tax_code);
        checkBoxAvailable = findViewById(R.id.checkbox_available);
        editTextImageUrl = findViewById(R.id.edit_text_image_url);
        buttonSave = findViewById(R.id.button_save_menu_item);
        progressBar = findViewById(R.id.progress_bar_loading);


        List<SpinnerItem> spinnerItemsTaxCode = new ArrayList<>();
        spinnerItemsTaxCode.add(new SpinnerItem("Food for Immediate Consumption",
                "txcd_40060003"));
        spinnerItemsTaxCode.add(new SpinnerItem("Carbonated Soft Drinks - 0% Fruit or Vegetable juice",
                "txcd_41040002"));
        spinnerItemsTaxCode.add(new SpinnerItem("Ice Cream, Packaged - Less Than One Pint Container",
                "txcd_40100004"));
        spinnerItemsTaxCode.add(new SpinnerItem("Bottled Water - Carbonated Naturally",
                "txcd_41030003"));
        spinnerItemsTaxCode.add(new SpinnerItem("Alcoholic Beverages - Spirits",
                "txcd_41020002"));
        spinnerItemsTaxCode.add(new SpinnerItem("Alcoholic Beverages - Wine",
                "txcd_41020003"));
        spinnerItemsTaxCode.add(new SpinnerItem("Alcoholic Beverages - Beer/Malt Beverage",
                "txcd_41020001"));
        spinnerItemsTaxCode.add(new SpinnerItem("Non-Alcoholic Beer/ Wine",
                "txcd_41052001"));
        spinnerItemsTaxCode.add(new SpinnerItem("Milk Coffee Tea Cocoa Beverages",
                "txcd_41060006"));
        spinnerItemsTaxCode.add(new SpinnerItem("Carbonated - Fruit or Vegetable juice",
                "txcd_41040011"));
        spinnerItemsTaxCode.add(new SpinnerItem("Non-Carbonated - Fruit or Vegetable juice",
                "txcd_41040024"));
        spinnerItemsTaxCode.add(new SpinnerItem("Snack Foods",
                "txcd_40070005"));

        ArrayAdapter<CharSequence> adapterCategory = ArrayAdapter.createFromResource(this,
                R.array.category_item, android.R.layout.simple_spinner_item);
        adapterCategory.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<CharSequence> adapterType = ArrayAdapter.createFromResource(this,
                R.array.type_item, android.R.layout.simple_spinner_item);
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<SpinnerItem> adapterTaxCode = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                spinnerItemsTaxCode
        );
        adapterTaxCode.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerTextCategory.setAdapter(adapterCategory);
        spinnerTextType.setAdapter(adapterType);
        spinnerTaxCode.setAdapter(adapterTaxCode);

        // Handle item selection
        spinnerTaxCode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedItemTaxCode = (SpinnerItem) parent.getItemAtPosition(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });


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
                // Set type spinner selection
                String taxCodeValue = currentMenuItem.getTaxCode();
                for (int i = 0; i < adapterTaxCode.getCount(); i++) {
                    if (adapterTaxCode.getItem(i).getValue().equals(taxCodeValue)) {
                        spinnerTaxCode.setSelection(i);
                        break;
                    }
                }
            }
        } else {
            setTitle("Add New Menu Item");
        }

        buttonSave.setOnClickListener(v -> saveMenuItem());
    }

    private void saveMenuItem() {
        showProgressBar();
        // ... (input validation logic) ...
        String name = editTextName.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();
        String priceStr = editTextPrice.getText().toString().trim();
        String category = spinnerTextCategory.getSelectedItem().toString();
        String type = spinnerTextType.getSelectedItem().toString();
        boolean available = checkBoxAvailable.isChecked();
        String imageUrl = editTextImageUrl.getText().toString().trim();
        String taxCode = selectedItemTaxCode.getValue();
        CustomConnectionTokenProvider conn = new CustomConnectionTokenProvider();

        if (name.isEmpty() || description.isEmpty() || priceStr.isEmpty() || category.isEmpty() || type.isEmpty() || taxCode.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            hideProgressBar();
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
            MenuItem newMenuItem = new MenuItem(name, description, price, category, type, available, imageUrl, taxCode, "");

            conn.createOrupdateProduct(restaurantId, newMenuItem, true, new CustomConnectionTokenProvider.CreateUpdateProductCallback() {
                @Override
                public void onSuccess(String prodId) {
                    newMenuItem.setProdId(prodId);
                    menuItemsRef.add(newMenuItem)
                            .addOnSuccessListener(documentReference -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Menu Item added", Toast.LENGTH_SHORT).show();
                        hideProgressBar();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Error adding menu item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        hideProgressBar();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(AddEditMenuItemActivity.this, "Menu Item NOT added", Toast.LENGTH_SHORT).show();
                    hideProgressBar();
                }
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
            currentMenuItem.setTaxCode(taxCode);

            // Use the existing ID to update the document

            conn.createOrupdateProduct(restaurantId, currentMenuItem, false, new CustomConnectionTokenProvider.CreateUpdateProductCallback() {
                @Override
                public void onSuccess(String prodId) {
                    currentMenuItem.setProdId(prodId);
                    menuItemsRef.document(currentMenuItem.getId()).set(currentMenuItem)
                            .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Menu Item updated", Toast.LENGTH_SHORT).show();
                        hideProgressBar();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddEditMenuItemActivity.this, "Error updating menu item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        hideProgressBar();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(AddEditMenuItemActivity.this, "Menu Item NOT updated", Toast.LENGTH_SHORT).show();
                    hideProgressBar();
                }
            });

        }
    }

    public void showProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            buttonSave.setEnabled(false);
        }
    }

    public void hideProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
            buttonSave.setEnabled(true);
        }
    }
}