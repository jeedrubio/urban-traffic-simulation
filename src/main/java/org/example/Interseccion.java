package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// La intersección actúa como semáforo y zona crítica protegida por Mutex
public class Interseccion extends Celda {
    public enum Lado {
        NORTE, SUR, ESTE, OESTE
    }

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Semaforo semaforo;
    private final Set<Lado> ladosConectados;

    // Métricas de congestión seguras para múltiples hilos
    private final AtomicInteger vehiculosEnEspera = new AtomicInteger(0);
    private final AtomicInteger maxCongestion = new AtomicInteger(0);
    private final AtomicInteger totalCruces = new AtomicInteger(0);

    public Interseccion(int x, int y, Semaforo semaforo) {
        this(x, y, semaforo, Set.of());
    }

    public Interseccion(int x, int y, Semaforo semaforo, Set<Lado> ladosConectados) {
        super(x, y, true);
        this.semaforo = semaforo;
        this.ladosConectados = Set.copyOf(ladosConectados);
        GestorMetricas.getInstancia().registrarInterseccion(this);
    }

    public int getVehiculosEnEspera() { return vehiculosEnEspera.get(); }
    public int getMaxCongestion() { return maxCongestion.get(); }
    public int getTotalCruces() { return totalCruces.get(); }
    public String getCoordenadaTexto() { return "(" + x + ", " + y + ")"; }
    public Set<Lado> getLadosConectados() { return ladosConectados; }

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
            for (Lado lado : Lado.values()) {
                if (ladosConectados.contains(lado)) {
                    dibujarSemaforoLateral(gc, px, py, tamano, radio, lado);
                }
            }
        }

        // Muestra la cantidad de autos esperando si hay congestión en el cruce
        int cola = vehiculosEnEspera.get();
        if (cola > 0) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            gc.fillText("Q:" + cola, px + tamano - 26, py + 16);
        }
    }

    private void dibujarSemaforoLateral(
            GraphicsContext gc,
            double px,
            double py,
            double tamano,
            double diametro,
            Lado lado) {
        double margen = 4;
        double x;
        double y;
        switch (lado) {
            case NORTE -> {
                x = px + (tamano - diametro) / 2;
                y = py + margen;
            }
            case SUR -> {
                x = px + (tamano - diametro) / 2;
                y = py + tamano - diametro - margen;
            }
            case ESTE -> {
                x = px + tamano - diametro - margen;
                y = py + (tamano - diametro) / 2;
            }
            case OESTE -> {
                x = px + margen;
                y = py + (tamano - diametro) / 2;
            }
            default -> throw new IllegalStateException("Lado de intersección desconocido: " + lado);
        }

        gc.setFill(obtenerColor(semaforo.getEstadoPorDireccion(direccionDeEntrada(lado))));
        gc.fillOval(x, y, diametro, diametro);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeOval(x, y, diametro, diametro);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 9));
        gc.fillText(lado.name().substring(0, 1), x + diametro * 0.32, y + diametro * 0.69);
    }

    private String direccionDeEntrada(Lado lado) {
        return switch (lado) {
            case NORTE -> "SUR";
            case SUR -> "NORTE";
            case ESTE -> "OESTE";
            case OESTE -> "ESTE";
        };
    }

    private Color obtenerColor(String estado) {
        switch (estado) {
            case "VERDE": return Color.LIMEGREEN;
            case "AMARILLO": return Color.GOLD;
            default: return Color.CRIMSON;
        }
    }
}
