package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class PuntoGeneracion extends Celda {
    private Vehiculo ocupanteSalida = null;  // Carril de origen (Spawn)
    private Vehiculo ocupanteLlegada = null; // Carril de destino (Fin de ruta)
    private final boolean esSpawn;
    private final boolean esDestino;

    public PuntoGeneracion(int x, int y) {
        this(x, y, true, true);
    }

    public PuntoGeneracion(int x, int y, boolean esSpawn, boolean esDestino) {
        super(x, y, true);
        if (!esSpawn && !esDestino) {
            throw new IllegalArgumentException("Un punto debe ser spawn, destino o ambos.");
        }
        this.esSpawn = esSpawn;
        this.esDestino = esDestino;
    }

    public boolean esSpawn() {
        return esSpawn;
    }

    public boolean esDestino() {
        return esDestino;
    }

    @Override
    public boolean puedeSalirHacia(String dir) {
        return esSpawn;
    }

    @Override
    public boolean puedeEntrarDesde(String dir) {
        return esDestino;
    }

    public synchronized boolean estaOcupada() {
        return ocupanteSalida != null;
    }

    @Override
    public synchronized int contarVehiculosEnDireccion(String direccionMovimiento) {
        return ocupanteLlegada == null ? 0 : 1;
    }

    @Override
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        synchronized (this) {
            return esSpawn ? ocupanteSalida == null : ocupanteLlegada == null;
        }
    }

    @Override
    public synchronized void entrar(Vehiculo v) throws InterruptedException {
        if (v.esDestino(this)) {
            if (!esDestino) {
                throw new IllegalStateException("El vehículo no puede terminar en un punto que no es destino.");
            }
            // El auto terminó su ruta: entra sin bloquearse con el auto que está naciendo
            while (ocupanteLlegada != null && ocupanteLlegada != v) {
                wait();
            }
            ocupanteLlegada = v;
            v.setSeccionActual(0, 1);
        } else {
            if (!esSpawn) {
                throw new IllegalStateException("El vehículo no puede salir desde un punto que no es spawn.");
            }
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
        gc.setFill(esSpawn && esDestino
                ? Color.web("#5b2c6f")
                : esSpawn ? Color.web("#2874a6") : Color.web("#8e44ad"));
        gc.fillRect(px, py, tamano, tamano);
        gc.setStroke(Color.WHITE);
        gc.strokeRect(px, py, tamano, tamano);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        String etiqueta = esSpawn && esDestino
                ? "SPAWN/DEST."
                : esSpawn ? "SPAWN" : "DESTINO";
        gc.fillText(etiqueta, px + (tamano * 0.12), py + (tamano * 0.56));
    }
}
