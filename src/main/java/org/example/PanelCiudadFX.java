package org.example;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.List;

public class PanelCiudadFX extends Pane {
    private final Mapa mapa;
    private final List<Vehiculo> vehiculos;
    private final double TAMANO_CELDA = 110.0;
    private final Canvas canvas;

    public PanelCiudadFX(Mapa mapa, List<Vehiculo> vehiculos) {
        this.mapa = mapa;
        this.vehiculos = vehiculos;

        double ancho = mapa.getColumnas() * TAMANO_CELDA;
        double alto = mapa.getFilas() * TAMANO_CELDA;

        this.canvas = new Canvas(ancho, alto);
        getChildren().add(canvas);

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                dibujarEscena();
            }
        };
        timer.start();
    }

    private void dibujarEscena() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int i = 0; i < mapa.getFilas(); i++) {
            for (int j = 0; j < mapa.getColumnas(); j++) {
                Celda celda = mapa.getCelda(i, j);
                celda.dibujar(gc, j * TAMANO_CELDA, i * TAMANO_CELDA, TAMANO_CELDA);
            }
        }

        for (Vehiculo v : vehiculos) {
            Celda ubicacion = v.getCeldaActual();
            if (ubicacion == null) continue;

            double baseX = ubicacion.y * TAMANO_CELDA;
            double baseY = ubicacion.x * TAMANO_CELDA;

            dibujarVehiculoDetallado(gc, v, baseX, baseY);
        }
    }

    private void dibujarVehiculoDetallado(GraphicsContext gc, Vehiculo v, double baseX, double baseY) {
        String dir = v.getDireccionMovimiento();
        int secFila = v.getSecFila(); // 0 = Superior, 1 = Inferior
        int secCol = v.getSecCol();   // 0 = Izquierda, 1 = Derecha

        double largoAuto = TAMANO_CELDA * 0.36;
        double anchoAuto = TAMANO_CELDA * 0.22;

        double posX, posY, w, h;

        if (v.getCeldaActual() instanceof Interseccion || v.getCeldaActual() instanceof PuntoGeneracion) {
            w = ("ESTE".equals(dir) || "OESTE".equals(dir)) ? largoAuto : anchoAuto;
            h = ("ESTE".equals(dir) || "OESTE".equals(dir)) ? anchoAuto : largoAuto;
            posX = baseX + (TAMANO_CELDA - w) / 2.0;
            posY = baseY + (TAMANO_CELDA - h) / 2.0;
        } else if ("ESTE".equals(dir) || "OESTE".equals(dir)) {
            w = largoAuto;
            h = anchoAuto;
            // Posición exacta en la sección [secFila][secCol]
            posX = baseX + (secCol == 0 ? TAMANO_CELDA * 0.08 : TAMANO_CELDA * 0.56);
            posY = baseY + (secFila == 0 ? TAMANO_CELDA * 0.14 : TAMANO_CELDA * 0.64);
        } else {
            w = anchoAuto;
            h = largoAuto;
            // Posición exacta en la sección [secFila][secCol]
            posX = baseX + (secCol == 0 ? TAMANO_CELDA * 0.14 : TAMANO_CELDA * 0.64);
            posY = baseY + (secFila == 0 ? TAMANO_CELDA * 0.08 : TAMANO_CELDA * 0.56);
        }

        gc.setFill(v.getColorAuto());
        gc.fillRoundRect(posX, posY, w, h, 8, 8);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(posX, posY, w, h, 8, 8);

        gc.setFill(Color.web("#1e2430"));
        if (w > h) {
            gc.fillRect(posX + w * 0.22, posY + 3, w * 0.18, h - 6);
            gc.fillRect(posX + w * 0.60, posY + 3, w * 0.18, h - 6);
        } else {
            gc.fillRect(posX + 3, posY + h * 0.22, w - 6, h * 0.18);
            gc.fillRect(posX + 3, posY + h * 0.60, w - 6, h * 0.18);
        }

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.fillText(v.getId(), posX + (w * 0.25), posY - 3);
    }
}