package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class ZonaNoAccesible extends Celda {
    public ZonaNoAccesible(int x, int y) {
        super(x, y, false);
    }

    @Override public boolean puedeSalirHacia(String dir) { return false; }
    @Override public boolean puedeEntrarDesde(String dir) { return false; }
    @Override public void entrar(Vehiculo v) {}
    @Override public void salir(Vehiculo v) {}

    @Override
    public void dibujar(GraphicsContext gc, double px, double py, double tamano) {
        gc.setFill(Color.web("#232730"));
        gc.fillRect(px, py, tamano, tamano);
        gc.setStroke(Color.web("#181b22"));
        gc.strokeRect(px, py, tamano, tamano);
    }
}