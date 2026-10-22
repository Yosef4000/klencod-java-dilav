
package com.company.delivery;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int BLUE = Color.rgb(35, 94, 220);
    private final int DARK = Color.rgb(25, 35, 58);
    private final int BG = Color.rgb(244, 247, 252);
    private final int GREEN = Color.rgb(25, 150, 95);
    private final int ORANGE = Color.rgb(225, 130, 25);

    private LinearLayout root, listContainer, statsContainer;
    private EditText searchInput;
    private TextView countText;
    private DBHelper dbHelper;

    private final String[] statuses = {
            "جديد",
            "قيد التوصيل",
            "تم التسليم",
            "ملغى"
    };

    private final NumberFormat money =
            NumberFormat.getNumberInstance(Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbHelper = new DBHelper(this);
        buildScreen();
        refreshScreen();
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, int color,
                          boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tv.setTypeface(Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return tv;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private void buildScreen() {
        root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout content = column();
        scroll.addView(content);

        TextView title = text("وصل | إدارة التوصيل", 25,
                Color.WHITE, true);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView subtitle = text("نظام إدارة طلبات الشركة", 14,
                Color.rgb(225, 235, 255), false);

        LinearLayout header = column();
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(background(BLUE, 18));
        header.addView(title);
        header.addView(subtitle);

        content.addView(header, params(-1, -2));

        TextView section = text("ملخص العمليات", 19, DARK, true);
        LinearLayout.LayoutParams sectionParams =
                params(-1, dp(48));
        sectionParams.topMargin = dp(10);
        content.addView(section, sectionParams);

        statsContainer = row();
        statsContainer.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(statsContainer, params(-1, -2));

        Button addButton = new Button(this);
        addButton.setText("+  إضافة طلب توصيل");
        addButton.setTextSize(16);
        addButton.setTextColor(Color.WHITE);
        addButton.setAllCaps(false);
        addButton.setBackground(background(BLUE, 12));
        addButton.setOnClickListener(v -> showOrderForm(null));

        LinearLayout.LayoutParams addParams = params(-1, dp(54));
        addParams.topMargin = dp(16);
        content.addView(addButton, addParams);

        TextView listTitle = text("طلبات الشركة", 20, DARK, true);
        LinearLayout.LayoutParams listTitleParams =
                params(-1, dp(48));
        listTitleParams.topMargin = dp(8);
        content.addView(listTitle, listTitleParams);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setHint("ابحث بالاسم أو الهاتف أو رقم الطلب");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dp(12), 0, dp(12), 0);
        searchInput.setBackground(background(Color.WHITE, 12));
        searchInput.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        content.addView(searchInput, params(-1, dp(50)));

        countText = text("", 13, Color.GRAY, false);
        content.addView(countText, params(-1, dp(34)));

        listContainer = column();
        content.addView(listContainer, params(-1, -2));

        searchInput.addTextChangedListener(
                new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start,
                            int count, int after) { }

                    @Override
                    public void onTextChanged(
                            CharSequence s, int start,
                            int before, int count) {
                        refreshOrders(s.toString());
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable e) { }
                });

        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1f));

        setContentView(root);
    }

    private void refreshScreen() {
        refreshStats();
        String query = searchInput == null
                ? "" : searchInput.getText().toString();
        refreshOrders(query);
    }

    private void refreshStats() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        int total = 0;
        int newOrders = 0;
        int delivering = 0;
        int delivered = 0;
        double revenue = 0;

        Cursor c = db.rawQuery(
                "SELECT status, price FROM orders", null);

        while (c.moveToNext()) {
            String status = c.getString(0);
            double price = c.getDouble(1);
            total++;

            if ("جديد".equals(status)) newOrders++;
            if ("قيد التوصيل".equals(status)) delivering++;

            if ("تم التسليم".equals(status)) {
                delivered++;
                revenue += price;
            }
        }
        c.close();

        statsContainer.removeAllViews();

        addStat("كل الطلبات", String.valueOf(total), BLUE);
        addStat("جديدة", String.valueOf(newOrders), ORANGE);
        addStat("قيد التوصيل", String.valueOf(delivering), BLUE);
        addStat("تم التسليم", String.valueOf(delivered), GREEN);

        TextView income = text(
                "إجمالي قيمة الطلبات المسلّمة: "
                        + money.format(revenue) + " ل.س",
                15, GREEN, true);

        income.setPadding(dp(12), dp(8), dp(12), dp(8));
        income.setBackground(background(Color.WHITE, 12));

        LinearLayout.LayoutParams incomeParams =
                params(-1, dp(48));
        incomeParams.topMargin = dp(10);
        statsContainer.addView(income, incomeParams);
    }

    private void addStat(String label, String value, int color) {
        LinearLayout card = column();
        card.setPadding(dp(8), dp(10), dp(8), dp(10));
        card.setBackground(background(Color.WHITE, 12));

        TextView number = text(value, 21, color, true);
        number.setGravity(Gravity.CENTER);

        TextView caption = text(label, 11, DARK, false);
        caption.setGravity(Gravity.CENTER);

        card.addView(number, params(-1, dp(30)));
        card.addView(caption, params(-1, dp(30)));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        statsContainer.addView(card, lp);
    }

    private void refreshOrders(String query) {
        if (listContainer == null) return;

        listContainer.removeAllViews();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT id, customer, phone, address, details, " +
                "price, status FROM orders " +
                "WHERE customer LIKE ? OR phone LIKE ? " +
                "OR CAST(id AS TEXT) LIKE ? " +
                "ORDER BY id DESC",
                new String[]{
                        "%" + query + "%",
                        "%" + query + "%",
                        "%" + query + "%"
                });

        int found = 0;

        while (c.moveToNext()) {
            found++;

            int id = c.getInt(0);
            String customer = c.getString(1);
            String phone = c.getString(2);
            String address = c.getString(3);
            String details = c.getString(4);
            double price = c.getDouble(5);
            String status = c.getString(6);

            addOrderCard(id, customer, phone, address,
                    details, price, status);
        }

        c.close();
        countText.setText("عدد النتائج: " + found);

        if (found == 0) {
            TextView empty = text(
                    "لا توجد طلبات. أضف أول طلب للشركة.",
                    15, Color.GRAY, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            listContainer.addView(empty);
        }
    }

    private void addOrderCard(
            int id, String customer, String phone,
            String address, String details,
            double price, String status) {

        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(background(Color.WHITE, 14));

        LinearLayout.LayoutParams cardParams =
                params(-1, -2);
        cardParams.topMargin = dp(7);
        cardParams.bottomMargin = dp(5);
        listContainer.addView(card, cardParams);

        TextView orderTitle = text(
                "طلب رقم #" + id + " — " + customer,
                17, DARK, true);
        card.addView(orderTitle, params(-1, -2));

        TextView contact = text(
                "الهاتف: " + phone,
                14, Color.DKGRAY, false);
        card.addView(contact, params(-1, dp(30)));

        TextView location = text(
                "العنوان: " + address,
                14, Color.DKGRAY, false);
        card.addView(location, params(-1, -2));

        TextView description = text(
                "التفاصيل: " + details,
                14, Color.DKGRAY, false);
        card.addView(description, params(-1, -2));

        TextView priceView = text(
                "قيمة الطلب: " + money.format(price) + " ل.س",
                14, GREEN, true);
        card.addView(priceView, params(-1, dp(30)));

        TextView statusView = text(
                "الحالة: " + status,
                14, BLUE, true);
        card.addView(statusView, params(-1, dp(30)));

        LinearLayout actions = row();

        Button statusButton = smallButton("تغيير الحالة", BLUE);
        statusButton.setOnClickListener(
                v -> showStatusDialog(id, status));

        Button callButton = smallButton("اتصال", GREEN);
        callButton.setOnClickListener(v -> {
            if (phone.trim().isEmpty()) {
                Toast.makeText(this, "لا يوجد رقم هاتف",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + Uri.encode(phone)));
            startActivity(intent);
        });

        Button editButton = smallButton("تعديل", DARK);
        editButton.setOnClickListener(v ->
                showOrderForm(new Order(
                        id, customer, phone, address,
                        details, price, status)));

        Button deleteButton = smallButton("حذف",
                Color.rgb(190, 45, 55));
        deleteButton.setOnClickListener(v ->
                confirmDelete(id));

        actions.addView(statusButton,
                new LinearLayout.LayoutParams(0, dp(45), 1f));
        actions.addView(callButton,
                new LinearLayout.LayoutParams(0, dp(45), 1f));
        actions.addView(editButton,
                new LinearLayout.LayoutParams(0, dp(45), 1f));
        actions.addView(deleteButton,
                new LinearLayout.LayoutParams(0, dp(45), 1f));

        card.addView(actions, params(-1, dp(48)));
    }

    private Button smallButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(background(color, 9));
        return button;
    }

    private void showOrderForm(Order order) {
        boolean editing = order != null;

        LinearLayout form = column();
        form.setPadding(dp(8), dp(4), dp(8), dp(4));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        EditText customer = field("اسم العميل",
                editing ? order.customer : "", InputType.TYPE_CLASS_TEXT);
        EditText phone = field("رقم الهاتف",
                editing ? order.phone : "", InputType.TYPE_CLASS_PHONE);
        EditText address = field("عنوان التوصيل",
                editing ? order.address : "", InputType.TYPE_CLASS_TEXT);
        EditText details = field("تفاصيل الطلب",
                editing ? order.details : "", InputType.TYPE_CLASS_TEXT);
        EditText price = field("قيمة الطلب بالليرة السورية",
                editing ? String.valueOf(order.price) : "",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        form.addView(customer);
        form.addView(phone);
        form.addView(address);
        form.addView(details);
        form.addView(price);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, statuses);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (editing) {
            for (int i = 0; i < statuses.length; i++) {
                if (statuses[i].equals(order.status)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }

        form.addView(spinner, params(-1, dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "تعديل الطلب" : "طلب توصيل جديد")
                .setView(scroll)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton(editing ? "حفظ التعديلات" : "حفظ الطلب",
                        null)
                .create();

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE);

            save.setOnClickListener(v -> {
                String name = customer.getText().toString().trim();
                String number = phone.getText().toString().trim();
                String addr = address.getText().toString().trim();
                String desc = details.getText().toString().trim();
                String priceText = price.getText().toString().trim();

                if (name.isEmpty() || number.isEmpty()
                        || addr.isEmpty() || priceText.isEmpty()) {
                    Toast.makeText(this,
                            "أدخل اسم العميل والهاتف والعنوان والسعر",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(priceText);
                    if (Double.isNaN(amount)
                            || Double.isInfinite(amount)
                            || amount < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    price.setError("أدخل قيمة مالية صحيحة");
                    return;
                }

                ContentValues values = new ContentValues();
                values.put("customer", name);
                values.put("phone", number);
                values.put("address", addr);
                values.put("details", desc);
                values.put("price", amount);
                values.put("status",
                        spinner.getSelectedItem().toString());

                SQLiteDatabase db = dbHelper.getWritableDatabase();

                if (editing) {
                    db.update("orders", values,
                            "id = ?",
                            new String[]{String.valueOf(order.id)});
                } else {
                    db.insertOrThrow("orders", null, values);
                }

                dialog.dismiss();
                refreshScreen();

                Toast.makeText(this,
                        editing ? "تم تعديل الطلب" : "تم حفظ الطلب",
                        Toast.LENGTH_SHORT).show();
            });
        });

        dialog.show();
    }

    private EditText field(String hint, String value, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextSize(15);
        edit.setInputType(inputType);
        edit.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        edit.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        edit.setPadding(dp(10), 0, dp(10), 0);

        LinearLayout.LayoutParams lp = params(-1, dp(52));
        lp.bottomMargin = dp(5);
        edit.setLayoutParams(lp);

        return edit;
    }

    private void showStatusDialog(int id, String currentStatus) {
        int selected = 0;
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equals(currentStatus)) {
                selected = i;
                break;
            }
        }

        final int[] choice = {selected};

        new AlertDialog.Builder(this)
                .setTitle("تحديث حالة الطلب #" + id)
                .setSingleChoiceItems(statuses, selected,
                        (dialog, which) -> choice[0] = which)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    ContentValues values = new ContentValues();
                    values.put("status", statuses[choice[0]]);

                    dbHelper.getWritableDatabase().update(
                            "orders", values,
                            "id = ?",
                            new String[]{String.valueOf(id)});

                    refreshScreen();

                    Toast.makeText(this, "تم تحديث الحالة",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmDelete(int id) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الطلب")
                .setMessage("هل أنت متأكد من حذف الطلب رقم #" + id + "؟")
                .setNegativeButton("تراجع", null)
                .setPositiveButton("حذف", (dialog, which) -> {
                    dbHelper.getWritableDatabase().delete(
                            "orders", "id = ?",
                            new String[]{String.valueOf(id)});
                    refreshScreen();

                    Toast.makeText(this, "تم حذف الطلب",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static class Order {
        int id;
        String customer, phone, address, details, status;
        double price;

        Order(int id, String customer, String phone,
              String address, String details,
              double price, String status) {
            this.id = id;
            this.customer = customer;
            this.phone = phone;
            this.address = address;
            this.details = details;
            this.price = price;
            this.status = status;
        }
    }

    private static class DBHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "company_delivery.db";
        private static final int DB_VERSION = 1;

        DBHelper(Activity context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "customer TEXT NOT NULL," +
                    "phone TEXT NOT NULL," +
                    "address TEXT NOT NULL," +
                    "details TEXT NOT NULL DEFAULT ''," +
                    "price REAL NOT NULL DEFAULT 0," +
                    "status TEXT NOT NULL DEFAULT 'جديد'," +
                    "created_at INTEGER NOT NULL DEFAULT " +
                    "(strftime('%s','now'))" +
                    ")"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db,
                              int oldVersion, int newVersion) {
            // أضف تغييرات قاعدة البيانات هنا عند تحديث التطبيق.
        }
    }

    @Override
    protected void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }
}