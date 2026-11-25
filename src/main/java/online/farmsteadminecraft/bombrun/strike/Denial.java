package online.farmsteadminecraft.bombrun.strike;

import java.util.Arrays;

/** Why a raid was refused: a translation key ({@code farmstead_bombrun.deny.*}) and its arguments. */
public record Denial(String key, Object... args) {
    @Override
    public boolean equals(Object o) {
        return o instanceof Denial d && d.key.equals(key) && Arrays.equals(d.args, args);
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 31 + Arrays.hashCode(args);
    }

    @Override
    public String toString() {
        return key + Arrays.toString(args);
    }
}
