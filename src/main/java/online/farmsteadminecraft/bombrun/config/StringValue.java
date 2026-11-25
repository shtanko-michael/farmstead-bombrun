package online.farmsteadminecraft.bombrun.config;

/** A free-text setting, written as a quoted TOML string. */
public final class StringValue extends ConfigValue<String> {
    StringValue(ConfigFile file, String section, String key, String def) {
        super(file, section, key, def);
    }

    public void set(String v) {
        value = v;
    }

    @Override
    public String setRaw(String raw) {
        String t = raw.trim();
        if (t.length() < 2 || !t.startsWith("\"") || !t.endsWith("\"")) {
            reset();
            return problem(raw, "expected a \"quoted\" string");
        }
        value = Toml.unquote(t);
        return null;
    }

    @Override
    public String text() {
        return quote(value);
    }

    @Override
    public String defaultText() {
        return quote(defaultValue());
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
