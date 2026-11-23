package org.example;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PanelCiudadFX extends Pane {
    private final Mapa mapa;
    private final List<Vehiculo> vehiculos;
    private final double TAMANO_CELDA = 110.0;
    private final Canvas canvas;
    private volatile Set<PuntoGeneracion> spawnsResaltados = Set.of();
    private volatile Set<PuntoGeneracion> destinosResaltados = Set.of();
    private volatile PuntoGeneracion origenSeleccionado;
    private Consumer<PuntoGeneracion> alHacerClicEnSpawn;

    public PanelCiudadFX(Mapa mapa, List<Vehiculo> vehiculos) {
        this.mapa = mapa;
        this.vehiculos = vehiculos;

        double ancho = mapa.getColumnas() * TAMANO_CELDA;
        double alto = mapa.getFilas() * TAMANO_CELDA;

        this.canvas = new Canvas(ancho, alto);
        getChildren().add(canvas);
        canvas.setOnMouseClicked(event -> {
            int columna = (int) (event.getX() / TAMANO_CELDA);
            int fila = (int) (event.getY() / TAMANO_CELDA);
            if (fila >= 0 && fila < mapa.getFilas()
                    && columna >= 0 && columna < mapa.getColumnas()
                    && mapa.getCelda(fila, columna) instanceof PuntoGeneracion spawn
                    && alHacerClicEnSpawn != null) {
                alHacerClicEnSpawn.accept(spawn);
            }
        });

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
                if (celda instanceof PuntoGeneracion spawn) {
                    dibujarResaltadoSpawn(gc, spawn);
                }
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

    public void setAlHacerClicEnSpawn(Consumer<PuntoGeneracion> callback) {
        this.alHacerClicEnSpawn = callback;
    }

    public void mostrarSeleccionSpawns(
            Collection<PuntoGeneracion> spawns,
            Collection<PuntoGeneracion> destinos,
            PuntoGeneracion origen) {
        this.spawnsResaltados = Set.copyOf(spawns);
        this.destinosResaltados = Set.copyOf(destinos);
        this.origenSeleccionado = origen;
    }

    public void limpiarSeleccionSpawns() {
        spawnsResaltados = Set.of();
        destinosResaltados = Set.of();
        origenSeleccionado = null;
    }

    private void dibujarResaltadoSpawn(GraphicsContext gc, PuntoGeneracion spawn) {
        Color color;
        String etiqueta;
        if (spawn == origenSeleccionado) {
            color = Color.LIMEGREEN;
            etiqueta = "ORIGEN";
        } else if (destinosResaltados.contains(spawn)) {
            color = Color.GOLD;
            etiqueta = "DESTINO";
        } else if (spawnsResaltados.contains(spawn)) {
            color = Color.DEEPSKYBLUE;
            etiqueta = "ORIGEN";
        } else {
            return;
        }

        double px = spawn.y * TAMANO_CELDA;
        double py = spawn.x * TAMANO_CELDA;
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.24));
        gc.fillRect(px + 3, py + 3, TAMANO_CELDA - 6, TAMANO_CELDA - 6);
        gc.setStroke(color);
        gc.setLineWidth(4);
        gc.strokeRect(px + 4, py + 4, TAMANO_CELDA - 8, TAMANO_CELDA - 8);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.fillText(etiqueta, px + 8, py + 18);
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
