package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// La intersección actúa como semáforo y zona crítica protegida por Mutex
public class Interseccion extends Celda {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Semaforo semaforo;

    // Métricas de congestión seguras para múltiples hilos
    private final AtomicInteger vehiculosEnEspera = new AtomicInteger(0);
    private final AtomicInteger maxCongestion = new AtomicInteger(0);
    private final AtomicInteger totalCruces = new AtomicInteger(0);

    public Interseccion(int x, int y, Semaforo semaforo) {
        super(x, y, true);
        this.semaforo = semaforo;
        GestorMetricas.getInstancia().registrarInterseccion(this);
    }

    public int getVehiculosEnEspera() { return vehiculosEnEspera.get(); }
    public int getMaxCongestion() { return maxCongestion.get(); }
    public int getTotalCruces() { return totalCruces.get(); }
    public String getCoordenadaTexto() { return "(" + x + ", " + y + ")"; }

    @Override
    public boolean puedeSalirHacia(String dir) { return true; }

    @Override
    public boolean puedeEntrarDesde(String dir) { return true; }

    @Override
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        return !lock.isLocked();
    }

    @Override
    public void entrar(Vehiculo v) throws InterruptedException {
        int actuales = vehiculosEnEspera.incrementAndGet();
        maxCongestion.accumulateAndGet(actuales, Math::max);
        boolean contandoEspera = true;

        long inicioEspera = System.currentTimeMillis();
        try {
            if (semaforo != null) {
                String dir = v.getDireccionMovimiento();
                while (!"VERDE".equals(semaforo.getEstadoPorDireccion(dir))) {
                    Thread.sleep(50);
                }
            }

            // Solicita el lock de forma interrumpible para garantizar limpieza del contador
            lock.lockInterruptibly();
            long tiempoDetenido = System.currentTimeMillis() - inicioEspera;
            if (tiempoDetenido > 0) {
                v.agregarTiempoEsperaSemaforo(tiempoDetenido);
            }

            vehiculosEnEspera.decrementAndGet();
            contandoEspera = false;
            totalCruces.incrementAndGet();
            v.setSeccionActual(0, 0);
        } finally {
            if (contandoEspera) {
                vehiculosEnEspera.decrementAndGet();
            }
        }
    }

    @Override
    public void salir(Vehiculo v) {
        lock.unlock();
    }

    @Override
    public void dibujar(GraphicsContext gc, double px, double py, double tamano) {
        gc.setFill(Color.web("#333742"));
        gc.fillRect(px, py, tamano, tamano);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeRect(px, py, tamano, tamano);

        if (semaforo != null) {
            double radio = tamano * 0.18;
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));

            // Semáforo Horizontal (arriba izquierda)
            gc.setFill(obtenerColor(semaforo.getEstadoHorizontal()));
            gc.fillOval(px + 8, py + 8, radio, radio);
            gc.setFill(Color.WHITE);
            gc.fillText("H", px + 8 + radio * 0.30, py + 8 + radio * 0.72);

            // Semáforo Vertical (abajo derecha)
            gc.setFill(obtenerColor(semaforo.getEstadoVertical()));
            gc.fillOval(px + tamano - radio - 8, py + tamano - radio - 8, radio, radio);
            gc.setFill(Color.WHITE);
            gc.fillText("V", px + tamano - radio - 8 + radio * 0.30, py + tamano - radio - 8 + radio * 0.72);
        }

        // Muestra la cantidad de autos esperando si hay congestión en el cruce
        int cola = vehiculosEnEspera.get();
        if (cola > 0) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            gc.fillText("Q:" + cola, px + tamano - 26, py + 16);
        }
    }

    private Color obtenerColor(String estado) {
        switch (estado) {
            case "VERDE": return Color.LIMEGREEN;
            case "AMARILLO": return Color.GOLD;
            default: return Color.CRIMSON;
        }
    }
}