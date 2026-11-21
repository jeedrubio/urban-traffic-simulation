package org.example;

import javafx.scene.paint.Color;
import java.util.List;
import java.util.Random;

public class Vehiculo implements Runnable {
    private static final Color[] PALETA = {
            Color.CRIMSON, Color.ORANGE, Color.DEEPSKYBLUE, Color.DODGERBLUE,
            Color.MEDIUMSEAGREEN, Color.GOLD, Color.MEDIUMPURPLE, Color.CORAL, Color.HOTPINK
    };

    private final String id;
    private final List<Celda> ruta;
    private final int tiempoCruce;
    private final Color colorAuto;

    private volatile Celda celdaActual;
    private volatile String direccionMovimiento = "ESTE";
    private volatile int secFila = 0; // 0 = Arriba, 1 = Abajo
    private volatile int secCol = 0;  // 0 = Izquierda, 1 = Derecha
    private volatile int carrilUnSentido = 0;

    public Vehiculo(String id, List<Celda> ruta, int tiempoCruce) {
        this.id = id;
        this.ruta = ruta;
        this.tiempoCruce = tiempoCruce;
        this.colorAuto = PALETA[new Random().nextInt(PALETA.length)];
    }

    public String getId() { return id; }
    public Celda getCeldaActual() { return celdaActual; }
    public String getDireccionMovimiento() { return direccionMovimiento; }
    public int getSecFila() { return secFila; }
    public int getSecCol() { return secCol; }
    public Color getColorAuto() { return colorAuto; }

    public int getCarrilUnSentido() { return carrilUnSentido; }
    public void setCarrilUnSentido(int carrilUnSentido) { this.carrilUnSentido = carrilUnSentido; }

    public void setSeccionActual(int fila, int col) {
        this.secFila = fila;
        this.secCol = col;
    }

    private String calcularDireccion(Celda origen, Celda destino) {
        if (destino.x < origen.x) return "NORTE";
        if (destino.x > origen.x) return "SUR";
        if (destino.y > origen.y) return "ESTE";
        return "OESTE";
    }

    public boolean esDestino(Celda c) {
        return ruta != null && !ruta.isEmpty() && ruta.get(ruta.size() - 1) == c;
    }

    @Override
    public void run() {
        Celda ocupada = null;
        try {
            if (ruta == null || ruta.isEmpty()) return;

            // 1. Iniciar en la casilla de Spawn
            Celda inicio = ruta.get(0);
            if (ruta.size() > 1) {
                this.direccionMovimiento = calcularDireccion(inicio, ruta.get(1));
            }
            inicio.entrar(this);
            ocupada = inicio;
            this.celdaActual = inicio;
            Thread.sleep(tiempoCruce / 2);

            // 2. Recorrer las siguientes celdas avanzando sección por sección
            for (int i = 1; i < ruta.size(); i++) {
                Celda siguiente = ruta.get(i);

                // Mantiene dirección de entrada mientras espera semáforo o sección libre
                this.direccionMovimiento = calcularDireccion(ocupada, siguiente);

                // Entra a la primera sección de 'siguiente' antes de soltar 'ocupada'
                siguiente.entrar(this);

                ocupada.salir(this);
                ocupada = siguiente;
                this.celdaActual = siguiente;

                // Si es intersección, gira hacia el siguiente tramo ya estando dentro
                if (siguiente instanceof Interseccion && i + 1 < ruta.size()) {
                    this.direccionMovimiento = calcularDireccion(siguiente, ruta.get(i + 1));
                    Thread.sleep(tiempoCruce);
                } else {
                    // Primera mitad de la casilla (ej. inferior izquierda si va al ESTE)
                    Thread.sleep(tiempoCruce / 2);

                    // Avanza a la segunda mitad de la casilla (ej. inferior derecha si va al ESTE)
                    siguiente.avanzarDentroDeCelda(this);
                    Thread.sleep(tiempoCruce / 2);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (ocupada != null) {
                ocupada.salir(this);
            }
            this.celdaActual = null;
        }
    }
}