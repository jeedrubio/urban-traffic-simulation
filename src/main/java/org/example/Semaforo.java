package org.example;

public class Semaforo implements Runnable {
    private String estadoHorizontal = "ROJO";
    private String estadoVertical = "VERDE";

    private final long duracionVerdeMs;
    private final long duracionAmarilloMs;
    private final long duracionRojoMs;

    public Semaforo() {
        this(5, 2, 1);
    }

    public Semaforo(int verdeSeg, int amarilloSeg, int rojoSeg) {
        this.duracionVerdeMs = verdeSeg * 1000L;
        this.duracionAmarilloMs = amarilloSeg * 1000L;
        this.duracionRojoMs = rojoSeg * 1000L;
    }

    public synchronized String getEstadoHorizontal() { return estadoHorizontal; }
    public synchronized String getEstadoVertical() { return estadoVertical; }

    public synchronized String getEstadoPorDireccion(String direccionMovimiento) {
        if ("ESTE".equals(direccionMovimiento) || "OESTE".equals(direccionMovimiento)) {
            return estadoHorizontal;
        }
        return estadoVertical;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                cambiarEstados("ROJO", "VERDE");
                Thread.sleep(duracionVerdeMs);

                cambiarEstados("ROJO", "AMARILLO");
                Thread.sleep(duracionAmarilloMs);

                cambiarEstados("ROJO", "ROJO");
                Thread.sleep(duracionRojoMs);

                cambiarEstados("VERDE", "ROJO");
                Thread.sleep(duracionVerdeMs);

                cambiarEstados("AMARILLO", "ROJO");
                Thread.sleep(duracionAmarilloMs);

                cambiarEstados("ROJO", "ROJO");
                Thread.sleep(duracionRojoMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void cambiarEstados(String horiz, String vert) {
        this.estadoHorizontal = horiz;
        this.estadoVertical = vert;
    }
}