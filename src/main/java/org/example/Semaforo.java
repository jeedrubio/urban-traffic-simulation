package org.example;

public class Semaforo implements Runnable {
    private String estadoHorizontal = "ROJO";
    private String estadoVertical = "VERDE";

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
                Thread.sleep(5000);

                cambiarEstados("ROJO", "AMARILLO");
                Thread.sleep(2000);

                cambiarEstados("ROJO", "ROJO");
                Thread.sleep(1000);

                cambiarEstados("VERDE", "ROJO");
                Thread.sleep(5000);

                cambiarEstados("AMARILLO", "ROJO");
                Thread.sleep(2000);

                cambiarEstados("ROJO", "ROJO");
                Thread.sleep(1000);
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