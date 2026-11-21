package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class PuntoGeneracion extends Celda {
    private Vehiculo ocupanteSalida = null;  // Carril de origen (Spawn)
    private Vehiculo ocupanteLlegada = null; // Carril de destino (Fin de ruta)

    public PuntoGeneracion(int x, int y) {
        super(x, y, true);
    }

    @Override public boolean puedeSalirHacia(String dir) { return true; }
    @Override public boolean puedeEntrarDesde(String dir) { return true; }

    public synchronized boolean estaOcupada() {
        return ocupanteSalida != null;
    }

    @Override
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        return !estaOcupada();
    }

    @Override
    public synchronized void entrar(Vehiculo v) throws InterruptedException {
        if (v.esDestino(this)) {
            // El auto terminó su ruta: entra sin bloquearse con el auto que está naciendo
            while (ocupanteLlegada != null && ocupanteLlegada != v) {
                wait();
            }
            ocupanteLlegada = v;
            v.setSeccionActual(0, 1);
        } else {
            // El auto está naciendo en el Spawn
            while (ocupanteSalida != null && ocupanteSalida != v) {
                wait();
            }
            ocupanteSalida = v;
            v.setSeccionActual(0, 0);
        }
    }

    @Override
    public synchronized void salir(Vehiculo v) {
        if (ocupanteSalida == v) {
            ocupanteSalida = null;
            notifyAll();
        }
        if (ocupanteLlegada == v) {
            ocupanteLlegada = null;
            notifyAll();
        }
    }

    @Override
    public void dibujar(GraphicsContext gc, double px, double py, double tamano) {
        gc.setFill(Color.web("#5b2c6f"));
        gc.fillRect(px, py, tamano, tamano);
        gc.setStroke(Color.WHITE);
        gc.strokeRect(px, py, tamano, tamano);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.fillText("SPAWN", px + (tamano * 0.16), py + (tamano * 0.56));
    }
}