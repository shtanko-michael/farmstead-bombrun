package online.farmsteadminecraft.bombrun.strike;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A planned raid: every plane as a straight line in time, every bomb as a release tick, point and velocity
 * (PLAN.md §5.1). Planes are not entities: the server only drops bombs on their ticks, the client draws each
 * plane at {@link #position}(t). Ticks count from the raid's start tick. Plain Java, deterministic, serialised
 * for the network packet and the saved data.
 */
public final class FlightPlan {
    public static final int FORMAT = 1;

    public final double targetX, targetY, targetZ, radius;
    /** Unit flight direction, horizontal. */
    public final double dirX, dirZ;
    public final double speed;
    public final PayloadKind payload;

    // per plane
    final double[] startX, startY, startZ;
    final int[] startTick, endTick, firstDrop, lastDrop;

    // per bomb, sorted by tick
    final int[] dropTick;
    final short[] dropPlane;
    final double[] dropX, dropY, dropZ, dropVx, dropVy, dropVz;

    FlightPlan(double targetX, double targetY, double targetZ, double radius, double dirX, double dirZ, double speed,
               PayloadKind payload, double[] startX, double[] startY, double[] startZ, int[] startTick, int[] endTick,
               int[] dropTick, short[] dropPlane, double[] dropX, double[] dropY, double[] dropZ,
               double[] dropVx, double[] dropVy, double[] dropVz) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.radius = radius;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.speed = speed;
        this.payload = payload;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.startTick = startTick;
        this.endTick = endTick;
        this.dropTick = dropTick;
        this.dropPlane = dropPlane;
        this.dropX = dropX;
        this.dropY = dropY;
        this.dropZ = dropZ;
        this.dropVx = dropVx;
        this.dropVy = dropVy;
        this.dropVz = dropVz;
        int n = startX.length;
        this.firstDrop = new int[n];
        this.lastDrop = new int[n];
        java.util.Arrays.fill(firstDrop, -1);
        java.util.Arrays.fill(lastDrop, -1);
        for (int i = 0; i < dropTick.length; i++) {
            int p = dropPlane[i];
            if (firstDrop[p] < 0) firstDrop[p] = dropTick[i];
            lastDrop[p] = dropTick[i];
        }
    }

    public int planes() {
        return startX.length;
    }

    public int drops() {
        return dropTick.length;
    }

    /** Tick at which the last plane has flown off; the raid is over for the client after it. */
    public int duration() {
        int d = 0;
        for (int e : endTick) d = Math.max(d, e);
        return Math.max(d, lastDropTick() + 1);
    }

    public int lastDropTick() {
        return dropTick.length == 0 ? 0 : dropTick[dropTick.length - 1];
    }

    public int firstDropTick() {
        return dropTick.length == 0 ? 0 : dropTick[0];
    }

    /** Heading in Minecraft yaw degrees, back from the flight direction. */
    public double heading() {
        return Math.toDegrees(Math.atan2(-dirX, dirZ));
    }

    /** Whether plane {@code i} is in the sky at time {@code t} (ticks, fractional). */
    public boolean flying(int i, double t) {
        return t >= startTick[i] && t <= endTick[i];
    }

    /** Position of plane {@code i} at time {@code t} (ticks since start, fractional): {@code out = {x, y, z}}. */
    public double[] position(int i, double t, double[] out) {
        double d = speed * (t - startTick[i]);
        out[0] = startX[i] + dirX * d;
        out[1] = startY[i];
        out[2] = startZ[i] + dirZ * d;
        return out;
    }

    /** Whether plane {@code i}'s bomb bay is open at {@code t}: from 2 s before its first bomb to 0.5 s after its last. */
    public boolean bayOpen(int i, double t) {
        return firstDrop[i] >= 0 && t >= firstDrop[i] - 40 && t <= lastDrop[i] + 10;
    }

    public int dropTick(int k) {
        return dropTick[k];
    }

    public int dropPlane(int k) {
        return dropPlane[k];
    }

    /** Release point and velocity of bomb {@code k}: {@code {x, y, z, vx, vy, vz}}. */
    public double[] drop(int k) {
        return new double[] {dropX[k], dropY[k], dropZ[k], dropVx[k], dropVy[k], dropVz[k]};
    }

    public void write(DataOutput out) throws IOException {
        out.writeInt(FORMAT);
        out.writeDouble(targetX);
        out.writeDouble(targetY);
        out.writeDouble(targetZ);
        out.writeDouble(radius);
        out.writeDouble(dirX);
        out.writeDouble(dirZ);
        out.writeDouble(speed);
        out.writeByte(payload.ordinal());
        int n = planes();
        out.writeShort(n);
        for (int i = 0; i < n; i++) {
            out.writeDouble(startX[i]);
            out.writeDouble(startY[i]);
            out.writeDouble(startZ[i]);
            out.writeInt(startTick[i]);
            out.writeInt(endTick[i]);
        }
        int m = drops();
        out.writeInt(m);
        for (int k = 0; k < m; k++) {
            out.writeInt(dropTick[k]);
            out.writeShort(dropPlane[k]);
            out.writeDouble(dropX[k]);
            out.writeDouble(dropY[k]);
            out.writeDouble(dropZ[k]);
            out.writeDouble(dropVx[k]);
            out.writeDouble(dropVy[k]);
            out.writeDouble(dropVz[k]);
        }
    }

    public static FlightPlan read(DataInput in) throws IOException {
        int format = in.readInt();
        if (format != FORMAT) throw new IOException("unknown flight plan format " + format);
        double tx = in.readDouble(), ty = in.readDouble(), tz = in.readDouble(), r = in.readDouble();
        double dx = in.readDouble(), dz = in.readDouble(), speed = in.readDouble();
        int payload = in.readByte();
        PayloadKind[] kinds = PayloadKind.values();
        if (payload < 0 || payload >= kinds.length) throw new IOException("unknown payload " + payload);
        int n = in.readShort();
        if (n < 0 || n > 4096) throw new IOException("bad plane count " + n);
        double[] sx = new double[n], sy = new double[n], sz = new double[n];
        int[] st = new int[n], et = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = in.readDouble();
            sy[i] = in.readDouble();
            sz[i] = in.readDouble();
            st[i] = in.readInt();
            et[i] = in.readInt();
        }
        int m = in.readInt();
        if (m < 0 || m > 1_000_000) throw new IOException("bad drop count " + m);
        int[] t = new int[m];
        short[] p = new short[m];
        double[] x = new double[m], y = new double[m], z = new double[m], vx = new double[m], vy = new double[m], vz = new double[m];
        for (int k = 0; k < m; k++) {
            t[k] = in.readInt();
            p[k] = in.readShort();
            if (p[k] < 0 || p[k] >= n) throw new IOException("drop " + k + " names plane " + p[k]);
            x[k] = in.readDouble();
            y[k] = in.readDouble();
            z[k] = in.readDouble();
            vx[k] = in.readDouble();
            vy[k] = in.readDouble();
            vz[k] = in.readDouble();
        }
        return new FlightPlan(tx, ty, tz, r, dx, dz, speed, kinds[payload], sx, sy, sz, st, et, t, p, x, y, z, vx, vy, vz);
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static FlightPlan fromBytes(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return read(in);
        }
    }
}
