package org.example;

import javafx.scene.paint.Color;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

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

    // Métricas individuales del vehículo
    private long tiempoInicioMs = 0;
    private long tiempoFinMs = 0;
    private final AtomicLong tiempoEsperaSemaforoMs = new AtomicLong(0);

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

    public void agregarTiempoEsperaSemaforo(long ms) {
        this.tiempoEsperaSemaforoMs.addAndGet(ms);
    }

    public long getTiempoTotalMs() {
        return Math.max(0, tiempoFinMs - tiempoInicioMs);
    }

    public long getTiempoEsperaSemaforoMs() {
        return tiempoEsperaSemaforoMs.get();
    }

    public double getPorcentajeEspera() {
        long total = getTiempoTotalMs();
        if (total <= 0) return 0.0;
        return Math.min(100.0, (tiempoEsperaSemaforoMs.get() * 100.0) / total);
    }

    public String getCoordenadaOrigen() {
        if (ruta == null || ruta.isEmpty()) return "(?,?)";
        Celda c = ruta.get(0);
        return "(" + c.x + "," + c.y + ")";
    }

    public String getCoordenadaDestino() {
        if (ruta == null || ruta.isEmpty()) return "(?,?)";
        Celda c = ruta.get(ruta.size() - 1);
        return "(" + c.x + "," + c.y + ")";
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
        this.tiempoInicioMs = System.currentTimeMillis();
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

                this.direccionMovimiento = calcularDireccion(ocupada, siguiente);
                siguiente.entrar(this);

                ocupada.salir(this);
                ocupada = siguiente;
                this.celdaActual = siguiente;

                if (siguiente instanceof Interseccion && i + 1 < ruta.size()) {
                    this.direccionMovimiento = calcularDireccion(siguiente, ruta.get(i + 1));
                    Thread.sleep(tiempoCruce);
                } else {
                    Thread.sleep(tiempoCruce / 2);
                    siguiente.avanzarDentroDeCelda(this);
                    Thread.sleep(tiempoCruce / 2);
                }
            }

            // 3. Registrar finalización exitosa y métricas de viaje
            this.tiempoFinMs = System.currentTimeMillis();
            GestorMetricas.getInstancia().registrarLlegada(this);

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