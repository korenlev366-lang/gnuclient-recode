package gnu.client.ui.clickgui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import gnu.client.module.Category;

import java.util.EnumMap;
import java.util.Map;

/**
 * Persisted ClickGUI category-column layout. Transient GUI state intentionally
 * does not belong in this DTO.
 */
public final class ClickGuiLayout {

    public static final int VERSION = 1;
    private static final int FIRST_X = 8;
    private static final int FIRST_Y = 8;
    private static final int COLUMN_STEP = 168;

    private final EnumMap<Category, Column> columns;

    public ClickGuiLayout() {
        columns = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            columns.put(category, defaultColumn(category));
        }
    }

    private ClickGuiLayout(Map<Category, Column> columns) {
        this();
        for (Map.Entry<Category, Column> entry : columns.entrySet()) {
            Column column = entry.getValue();
            if (entry.getKey() != null && column != null) {
                this.columns.put(entry.getKey(), column.copy());
            }
        }
    }

    public static ClickGuiLayout defaults() {
        return new ClickGuiLayout();
    }

    public Column get(Category category) {
        return columns.get(category);
    }

    public void set(Category category, int x, int y, boolean open) {
        if (category != null) {
            columns.put(category, new Column(x, y, open));
        }
    }

    public ClickGuiLayout copy() {
        return new ClickGuiLayout(columns);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject columnsJson = new JsonObject();
        for (Category category : Category.values()) {
            Column column = columns.get(category);
            if (column == null) {
                column = defaultColumn(category);
            }
            JsonObject columnJson = new JsonObject();
            columnJson.addProperty("x", column.getX());
            columnJson.addProperty("y", column.getY());
            columnJson.addProperty("open", column.isOpen());
            columnsJson.add(category.name(), columnJson);
        }
        root.add("columns", columnsJson);
        return root;
    }

    public static ClickGuiLayout fromJson(JsonObject root) {
        ClickGuiLayout layout = defaults();
        if (root == null || readInt(root.get("version"), -1) != VERSION) {
            return layout;
        }
        JsonElement columnsElement = root.get("columns");
        if (columnsElement == null || !columnsElement.isJsonObject()) {
            return layout;
        }

        JsonObject columnsJson = columnsElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : columnsJson.entrySet()) {
            Category category;
            try {
                category = Category.valueOf(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            Column fallback = defaultColumn(category);
            JsonObject columnJson = entry.getValue().getAsJsonObject();
            int x = readInt(columnJson.get("x"), fallback.getX());
            int y = readInt(columnJson.get("y"), fallback.getY());
            boolean open = readBoolean(columnJson.get("open"), fallback.isOpen());
            layout.set(category, x, y, open);
        }
        return layout;
    }

    private static Column defaultColumn(Category category) {
        return new Column(FIRST_X + category.ordinal() * COLUMN_STEP, FIRST_Y, true);
    }

    private static int readInt(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return fallback;
        }
        try {
            double value = primitive.getAsDouble();
            if (Double.isNaN(value) || Double.isInfinite(value)
                    || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                    || value != Math.rint(value)) {
                return fallback;
            }
            return (int) value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonElement element, boolean fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            return fallback;
        }
        return primitive.getAsBoolean();
    }

    public static final class Column {
        private final int x;
        private final int y;
        private final boolean open;

        public Column(int x, int y, boolean open) {
            this.x = x;
            this.y = y;
            this.open = open;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public boolean isOpen() {
            return open;
        }

        private Column copy() {
            return new Column(x, y, open);
        }
    }
}
