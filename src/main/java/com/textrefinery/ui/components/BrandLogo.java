package com.textrefinery.ui.components;

import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Logo de Text Refinery: una "T" de trazos redondeados sobre un cuadrado rojo con una chispa
 * (el texto ya refinado). La geometría está en una rejilla de 64 unidades y se comparte entre
 * JavaFX (ventana) y AWT (bandeja); coincide con la pantalla «Logo» del diseño.
 */
public final class BrandLogo {

    private static final double GRID = 64;
    // Fondo
    private static final double[] BACK = {4, 4, 56, 56, 32};          // x, y, w, h, diámetro de esquina
    // T: barra y tallo
    private static final double[] BAR = {15, 17, 34, 9, 9};
    private static final double[] STEM = {27.5, 17, 9, 32, 9};
    // Chispa de cuatro puntas: centro y radio
    private static final double SPARK_X = 46;
    private static final double SPARK_Y = 46;
    private static final double SPARK_R = 8;
    private static final double SPARK_WAIST = 0.24; // grosor del centro respecto al radio

    private static final String RED_LIGHT = "#E8283F";
    private static final String RED_DARK = "#A00D25";
    private static final String SPARK = "#FFC2C9";

    private BrandLogo() {
    }

    /** Puntos de la chispa (8 vértices alternando punta y cintura). */
    private static double[][] sparkPoints() {
        double w = SPARK_R * SPARK_WAIST;
        return new double[][]{
                {SPARK_X, SPARK_Y - SPARK_R}, {SPARK_X + w, SPARK_Y - w},
                {SPARK_X + SPARK_R, SPARK_Y}, {SPARK_X + w, SPARK_Y + w},
                {SPARK_X, SPARK_Y + SPARK_R}, {SPARK_X - w, SPARK_Y + w},
                {SPARK_X - SPARK_R, SPARK_Y}, {SPARK_X - w, SPARK_Y - w}};
    }

    /** Imagen de JavaFX. Llamar desde el hilo de JavaFX. */
    public static Image fxImage(int px) {
        Canvas canvas = new Canvas(px, px);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(px / GRID, px / GRID);

        g.setFill(new LinearGradient(BACK[0], BACK[1], BACK[0] + BACK[2], BACK[1] + BACK[3], false,
                CycleMethod.NO_CYCLE, new Stop(0, Color.web(RED_LIGHT)), new Stop(1, Color.web(RED_DARK))));
        g.fillRoundRect(BACK[0], BACK[1], BACK[2], BACK[3], BACK[4], BACK[4]);

        g.setFill(Color.WHITE);
        g.fillRoundRect(BAR[0], BAR[1], BAR[2], BAR[3], BAR[4], BAR[4]);
        g.fillRoundRect(STEM[0], STEM[1], STEM[2], STEM[3], STEM[4], STEM[4]);

        g.setFill(Color.web(SPARK));
        double[][] p = sparkPoints();
        g.beginPath();
        g.moveTo(p[0][0], p[0][1]);
        for (int i = 1; i < p.length; i++) {
            g.lineTo(p[i][0], p[i][1]);
        }
        g.closePath();
        g.fill();

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    /** Imagen de AWT para la bandeja del sistema. */
    public static BufferedImage awtImage(int px) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(px / GRID, px / GRID);

        g.setPaint(new GradientPaint((float) BACK[0], (float) BACK[1], java.awt.Color.decode(RED_LIGHT),
                (float) (BACK[0] + BACK[2]), (float) (BACK[1] + BACK[3]), java.awt.Color.decode(RED_DARK)));
        g.fill(new RoundRectangle2D.Double(BACK[0], BACK[1], BACK[2], BACK[3], BACK[4], BACK[4]));

        g.setColor(java.awt.Color.WHITE);
        g.fill(new RoundRectangle2D.Double(BAR[0], BAR[1], BAR[2], BAR[3], BAR[4], BAR[4]));
        g.fill(new RoundRectangle2D.Double(STEM[0], STEM[1], STEM[2], STEM[3], STEM[4], STEM[4]));

        g.setColor(java.awt.Color.decode(SPARK));
        double[][] p = sparkPoints();
        Path2D.Double star = new Path2D.Double();
        star.moveTo(p[0][0], p[0][1]);
        for (int i = 1; i < p.length; i++) {
            star.lineTo(p[i][0], p[i][1]);
        }
        star.closePath();
        g.fill(star);

        g.dispose();
        return img;
    }
}
