package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.concurrent.locks.ReentrantLock;


//La intersección actua como semáforo,

public class Interseccion extends Celda {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Semaforo semaforo;

    public Interseccion(int x, int y, Semaforo semaforo) {
        super(x, y, true);
        this.semaforo = semaforo;
    }


    // En la intersección se puede salir y entrar desde cualquier dirección, pero sólo si el semáforo está en verde,
    // en caso de que no, manda un sleep.

    //Cada intersección tiene dos semáforos, uno para vertical y otro para hhorizontal,
    //solo los carros pueden entrar de manera hhorizontal cuando esta en verde el semafotro
    //horizontal y viceversa

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
        if (semaforo != null) {
            String dir = v.getDireccionMovimiento();
            while (!"VERDE".equals(semaforo.getEstadoPorDireccion(dir))) {
                Thread.sleep(50);
            }
        }
        lock.lock();
        v.setSeccionActual(0, 0);
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
    }

    private Color obtenerColor(String estado) {
        switch (estado) {
            case "VERDE": return Color.LIMEGREEN;
            case "AMARILLO": return Color.GOLD;
            default: return Color.CRIMSON;
        }
    }
}