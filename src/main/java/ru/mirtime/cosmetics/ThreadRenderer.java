package ru.mirtime.cosmetics;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class ThreadRenderer {

    private static final Map<UUID, List<BlockDisplay>> activeThreads = new HashMap<>();
    private static final float THICKNESS = 0.016f; // Толщина одной «ниточки» (1.6 см)

    public static void clear(UUID id) {
        List<BlockDisplay> list = activeThreads.remove(id);
        if (list != null) {
            for (BlockDisplay bd : list) {
                if (bd != null && !bd.isDead()) bd.remove();
            }
        }
    }

    public static void clearAll() {
        new ArrayList<>(activeThreads.keySet()).forEach(ThreadRenderer::clear);
    }

    public static void renderPlayer(Player p, String hatId, String backId, String petId, double tickTime) {
        if (!p.isOnline() || p.isDead() || p.isInvisible()) {
            clear(p.getUniqueId());
            return;
        }

        List<Segment> segments = new ArrayList<>();
        Location base = p.getLocation();
        double yawRad = Math.toRadians(-p.getBodyYaw());

        // 1. ШАПКИ ИЗ 3D-НИТОЧЕК
        double headY = p.isSneaking() ? 1.55 : 1.85;
        if ("halo".equals(hatId)) {
            double floatY = headY + 0.16 + Math.sin(tickTime * 2.5) * 0.03;
            addCircle(segments, 0, floatY, 0, 0.36, 16, Material.SEA_LANTERN, tickTime);
            addCircle(segments, 0, floatY + 0.03, 0, 0.28, 12, Material.LIGHT_BLUE_STAINED_GLASS, -tickTime * 1.5);
        } else if ("chinahat".equals(hatId)) {
            // Азиатская шляпа-конус из 16 лучевых нитей и обода
            int rays = 16;
            double r = 0.56;
            for (int i = 0; i < rays; i++) {
                double a1 = (2 * Math.PI * i / rays) + tickTime * 0.4;
                double a2 = (2 * Math.PI * (i + 1) / rays) + tickTime * 0.4;
                Vector3f p1 = new Vector3f((float) (Math.cos(a1) * r), (float) headY, (float) (Math.sin(a1) * r));
                Vector3f p2 = new Vector3f((float) (Math.cos(a2) * r), (float) headY, (float) (Math.sin(a2) * r));
                Vector3f top = new Vector3f(0, (float) (headY + 0.28), 0);
                segments.add(new Segment(p1, p2, Material.CYAN_CONCRETE));
                segments.add(new Segment(p1, top, i % 2 == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE));
            }
        } else if ("crown".equals(hatId)) {
            // Корона Императора из 8 золотых зубцов-нитей
            int pts = 8;
            double r = 0.33;
            for (int i = 0; i < pts; i++) {
                double a1 = (2 * Math.PI * i / pts) + tickTime * 0.8;
                double a2 = (2 * Math.PI * (i + 1) / pts) + tickTime * 0.8;
                double am = (a1 + a2) * 0.5;
                Vector3f p1 = new Vector3f((float) (Math.cos(a1) * r), (float) headY, (float) (Math.sin(a1) * r));
                Vector3f p2 = new Vector3f((float) (Math.cos(a2) * r), (float) headY, (float) (Math.sin(a2) * r));
                Vector3f tip = new Vector3f((float) (Math.cos(am) * (r + 0.04)), (float) (headY + 0.22), (float) (Math.sin(am) * (r + 0.04)));
                segments.add(new Segment(p1, p2, Material.GOLD_BLOCK));
                segments.add(new Segment(p1, tip, Material.GLOWSTONE));
                segments.add(new Segment(tip, p2, Material.GOLD_BLOCK));
            }
        } else if ("horns".equals(hatId)) {
            // Изогнутые Демон-Рога из сегментированных алых нитей
            for (int s : new int[]{-1, 1}) {
                Vector3f b = rotY(new Vector3f(0.22f * s, (float) (headY - 0.04), 0.05f), yawRad);
                Vector3f m = rotY(new Vector3f(0.42f * s, (float) (headY + 0.16), 0.12f), yawRad);
                Vector3f t = rotY(new Vector3f(0.32f * s, (float) (headY + 0.36), 0.22f), yawRad);
                segments.add(new Segment(b, m, Material.RED_CONCRETE));
                segments.add(new Segment(m, t, Material.SHROOMLIGHT));
            }
        }
        // 2. СПИНА ИЗ 3D-НИТОЧЕК (Крылья, Скрещённые Катаны, Физический Плащ)
        if ("wings".equals(backId)) {
            double flap = Math.sin(tickTime * 3.5) * 0.22;
            for (int side : new int[]{-1, 1}) {
                Vector3f root = rotY(new Vector3f(0.08f * side, 1.32f, -0.16f), yawRad);
                for (int feather = 0; feather < 7; feather++) {
                    double len = 0.72 + (feather % 3) * 0.16;
                    double spread = 0.45 + feather * 0.12;
                    Vector3f tipLocal = new Vector3f(
                            (float) (side * Math.sin(spread + flap) * len),
                            (float) (1.45 - feather * 0.14),
                            (float) (-0.25 - Math.cos(spread + flap) * (len * 0.55))
                    );
                    Vector3f tip = rotY(tipLocal, yawRad);
                    segments.add(new Segment(root, tip, feather % 2 == 0 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE));
                }
            }
        } else if ("katanas".equals(backId)) {
            for (int dir : new int[]{-1, 1}) {
                Vector3f bottom = rotY(new Vector3f(-0.42f * dir, 0.58f, -0.22f), yawRad);
                Vector3f top = rotY(new Vector3f(0.42f * dir, 1.72f, -0.22f), yawRad);
                Vector3f guard1 = rotY(new Vector3f(0.22f * dir, 1.54f, -0.22f), yawRad);
                Vector3f guard2 = rotY(new Vector3f(0.36f * dir, 1.44f, -0.22f), yawRad);
                segments.add(new Segment(bottom, top, Material.PURPLE_CONCRETE));
                segments.add(new Segment(guard1, guard2, Material.AMETHYST_BLOCK));
            }
        } else if ("cape".equals(backId)) {
            double sway = 0.22 + Math.sin(tickTime * 3.0) * 0.08;
            Vector3f tl = rotY(new Vector3f(-0.28f, 1.42f, -0.18f), yawRad);
            Vector3f tr = rotY(new Vector3f(0.28f, 1.42f, -0.18f), yawRad);
            Vector3f bl = rotY(new Vector3f(-0.34f, 0.35f, (float) (-0.25 - sway)), yawRad);
            Vector3f br = rotY(new Vector3f(0.34f, 0.35f, (float) (-0.25 - sway)), yawRad);
            segments.add(new Segment(tl, tr, Material.LIGHT_BLUE_CONCRETE));
            segments.add(new Segment(bl, br, Material.LIGHT_BLUE_CONCRETE));
            segments.add(new Segment(tl, bl, Material.SEA_LANTERN));
            segments.add(new Segment(tr, br, Material.SEA_LANTERN));
            segments.add(new Segment(tl, br, Material.BLUE_STAINED_GLASS));
            segments.add(new Segment(tr, bl, Material.BLUE_STAINED_GLASS));
        }

        // 3. ЛЕТАЮЩИЙ 3D-ПИТОМЕЦ ИЗ НИТОЧЕК (Кристалл или Куб Пустоты)
        if ("crystal".equals(petId)) {
            double orbit = tickTime * 1.6;
            float cx = (float) (Math.cos(orbit) * 0.92);
            float cz = (float) (Math.sin(orbit) * 0.92);
            float cy = (float) (1.35 + Math.sin(tickTime * 3.0) * 0.14);
            float r = 0.20f;
            Vector3f top = new Vector3f(cx, cy + r * 1.4f, cz);
            Vector3f bot = new Vector3f(cx, cy - r * 1.4f, cz);
            for (int i = 0; i < 4; i++) {
                double a1 = orbit * 2 + i * Math.PI / 2;
                double a2 = orbit * 2 + (i + 1) * Math.PI / 2;
                Vector3f p1 = new Vector3f((float) (cx + Math.cos(a1) * r), cy, (float) (cz + Math.sin(a1) * r));
                Vector3f p2 = new Vector3f((float) (cx + Math.cos(a2) * r), cy, (float) (cz + Math.sin(a2) * r));
                segments.add(new Segment(p1, p2, Material.MAGENTA_CONCRETE));
                segments.add(new Segment(p1, top, Material.SEA_LANTERN));
                segments.add(new Segment(p1, bot, Material.AMETHYST_BLOCK));
            }
            // Лёгкие световые частицы у кристалла
            p.getWorld().spawnParticle(Particle.DUST, base.clone().add(cx, cy, cz), 1, 0.05, 0.05, 0.05, new Particle.DustOptions(Color.fromRGB(0, 229, 255), 0.5f));
        }

        syncEntities(p, base, segments);
    }

    private static void syncEntities(Player p, Location base, List<Segment> segments) {
        List<BlockDisplay> pool = activeThreads.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());

        while (pool.size() > segments.size()) {
            BlockDisplay extra = pool.remove(pool.size() - 1);
            if (extra != null && !extra.isDead()) extra.remove();
        }
        while (pool.size()  {
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setInterpolationDelay(0);
                entity.setInterpolationDuration(2);
                entity.setTeleportDuration(2);
            });
            pool.add(bd);
        }

        Vector3f up = new Vector3f(0, 1, 0);
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            BlockDisplay bd = pool.get(i);
            if (bd.isDead()) continue;

            bd.teleport(base);
            if (bd.getBlock().getMaterial() != seg.mat) {
                bd.setBlock(seg.mat.createBlockData());
            }

            Vector3f diff = new Vector3f(seg.end).sub(seg.start);
            float len = diff.length();
            if (len < 0.001f) continue;

            Quaternionf rot = new Quaternionf().rotationTo(up, new Vector3f(diff).normalize());
            Vector3f offset = new Vector3f(seg.start).add(rot.transform(new Vector3f(-THICKNESS / 2f, 0, -THICKNESS / 2f)));

            bd.setTransformation(new Transformation(
                    offset,
                    rot,
                    new Vector3f(THICKNESS, len, THICKNESS),
                    new Quaternionf()
            ));
        }
    }

    private static void addCircle(List<Segment> list, double cx, double cy, double cz, double r, int segs, Material mat, double rotOffset) {
        for (int i = 0; i < segs; i++) {
            double a1 = (2 * Math.PI * i / segs) + rotOffset;
            double a2 = (2 * Math.PI * (i + 1) / segs) + rotOffset;
            list.add(new Segment(
                    new Vector3f((float) (cx + Math.cos(a1) * r), (float) cy, (float) (cz + Math.sin(a1) * r)),
                    new Vector3f((float) (cx + Math.cos(a2) * r), (float) cy, (float) (cz + Math.sin(a2) * r)),
                    mat
            ));
        }
    }

    private static Vector3f rotY(Vector3f v, double rad) {
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        return new Vector3f(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    private record Segment(Vector3f start, Vector3f end, Material mat) {}
}
