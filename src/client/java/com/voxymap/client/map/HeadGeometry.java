package com.voxymap.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class HeadGeometry {
    private static final String HEAD = "head";
    private static final List<String> FACE_EXTRAS = List.of(
            "hat", "beak", "red_thing", "nose", "snout", "jaw", "mouth", "horns", "left_horn", "right_horn");
    private static final float FRONT_NORMAL = -0.5f;

    record RawVertex(float x, float y, float z, float u, float v) {
    }

    private HeadGeometry() {
    }

    static List<HeadIconComposer.Quad> capture(Model<?> model) {
        ModelPart root = model.root();
        if (!root.hasChild(HEAD)) {
            return List.of();
        }
        ModelPart head = root.getChild(HEAD);
        Set<ModelPart> headParts = Collections.newSetFromMap(new IdentityHashMap<>());
        headParts.addAll(head.getAllParts());
        List<ModelPart> parts = new ArrayList<>();
        parts.add(head);
        for (String name : FACE_EXTRAS) {
            if (root.hasChild(name)) {
                ModelPart extra = root.getChild(name);
                if (!headParts.contains(extra)) {
                    parts.add(extra);
                }
            }
        }

        List<RawVertex[]> faces = new ArrayList<>();
        int[] mainIndex = {-1};
        float[] mainArea = {0.0f};
        for (ModelPart part : parts) {
            List<ModelPart> all = part.getAllParts();
            List<PartPose> saved = new ArrayList<>(all.size());
            for (ModelPart child : all) {
                saved.add(child.storePose());
                child.resetPose();
            }
            try {
                boolean isHead = part == head;
                part.visit(new PoseStack(), (pose, path, index, cube) -> {
                    for (ModelPart.Polygon polygon : cube.polygons) {
                        Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
                        if (normal.z() > FRONT_NORMAL || polygon.vertices().length < 4) {
                            continue;
                        }
                        ModelPart.Vertex[] vertices = polygon.vertices();
                        RawVertex[] face = new RawVertex[vertices.length];
                        for (int i = 0; i < vertices.length; i++) {
                            Vector3f position = pose.pose().transformPosition(
                                    vertices[i].worldX(), vertices[i].worldY(), vertices[i].worldZ(), new Vector3f());
                            face[i] = new RawVertex(position.x(), position.y(), position.z(), vertices[i].u(), vertices[i].v());
                        }
                        float area = area(face);
                        if (isHead && path.isEmpty() && area > mainArea[0]) {
                            mainArea[0] = area;
                            mainIndex[0] = faces.size();
                        }
                        faces.add(face);
                    }
                });
            } finally {
                for (int i = 0; i < all.size(); i++) {
                    all.get(i).loadPose(saved.get(i));
                }
            }
        }
        return toQuads(faces, mainIndex[0]);
    }

    static List<HeadIconComposer.Quad> toQuads(List<RawVertex[]> faces, int mainIndex) {
        if (mainIndex < 0 || mainIndex >= faces.size()) {
            return List.of();
        }
        RawVertex[] main = faces.get(mainIndex);
        double[] gradient = textureGradient(main);
        boolean flipX = gradient[0] < 0.0;
        boolean flipY = gradient[1] < 0.0;
        List<HeadIconComposer.Quad> quads = new ArrayList<>(faces.size());
        quads.add(quad(main, flipX, flipY));
        for (int i = 0; i < faces.size(); i++) {
            if (i != mainIndex) {
                quads.add(quad(faces.get(i), flipX, flipY));
            }
        }
        return quads;
    }

    static double[] textureGradient(RawVertex[] face) {
        double e1x = face[1].x() - face[0].x();
        double e1y = face[1].y() - face[0].y();
        double e2x = face[3].x() - face[0].x();
        double e2y = face[3].y() - face[0].y();
        double determinant = e1x * e2y - e1y * e2x;
        if (Math.abs(determinant) < 1.0e-12) {
            return new double[]{1.0, 1.0};
        }
        double du1 = face[1].u() - face[0].u();
        double du2 = face[3].u() - face[0].u();
        double dv1 = face[1].v() - face[0].v();
        double dv2 = face[3].v() - face[0].v();
        double uPerX = (du1 * e2y - du2 * e1y) / determinant;
        double vPerY = (dv2 * e1x - dv1 * e2x) / determinant;
        return new double[]{uPerX, vPerY};
    }

    private static HeadIconComposer.Quad quad(RawVertex[] face, boolean flipX, boolean flipY) {
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] us = new float[4];
        float[] vs = new float[4];
        float depth = 0.0f;
        for (int i = 0; i < 4; i++) {
            RawVertex vertex = face[i];
            xs[i] = flipX ? -vertex.x() : vertex.x();
            ys[i] = flipY ? -vertex.y() : vertex.y();
            us[i] = vertex.u();
            vs[i] = vertex.v();
            depth += vertex.z();
        }
        return new HeadIconComposer.Quad(xs, ys, us, vs, depth / 4.0f);
    }

    private static float area(RawVertex[] face) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (RawVertex vertex : face) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y());
            maxY = Math.max(maxY, vertex.y());
        }
        return (maxX - minX) * (maxY - minY);
    }
}
