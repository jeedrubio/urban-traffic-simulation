package org.example;


//Usando POO tenemos la clase principal que compone la matriz, todas las demás clases se extenderán de esta
// para poder tener los diferentes tipos de casillas que pide la actividad.
//Principalmente, las intersecciones (semaforos) puntos de inicio donde se generan los autos y a donde
// tienen que llegar, calles de un sentido y calles de doble sentido. Así como zonas muertas.

//Ahora también cada celda está dividida en 4 partes. Esto para poder tener hasta 4 vehiculos a la vez por casilla


import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public abstract class Celda {
    protected int x, y;
    protected boolean transitable;

    public Celda(int x, int y, boolean transitable) {
        this.x = x;
        this.y = y;
        this.transitable = transitable;
    }

    public abstract boolean puedeSalirHacia(String direccionSalida);
    public abstract boolean puedeEntrarDesde(String direccionOrigen);

    public abstract void entrar(Vehiculo v) throws InterruptedException;
    public abstract void salir(Vehiculo v);

    // Mueve el vehículo de la primera sección (entrada) a la segunda sección (salida) de la misma casilla
    public void avanzarDentroDeCelda(Vehiculo v) throws InterruptedException {
        // Por defecto no hace nada (Intersección y Spawn)
    }

    // Verifica si la sección de entrada para una dirección está libre sin bloquear el hilo
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        return true;
    }

    public abstract void dibujar(GraphicsContext gc, double px, double py, double tamano);

    protected void dibujarFlecha(GraphicsContext gc, double cx, double cy, String direccion, double largo) {
        gc.setStroke(Color.rgb(255, 255, 255, 0.75));
        gc.setLineWidth(2.0);
        double mitad = largo / 2.0;
        double punta = 5.0;

        switch (direccion) {
            case "NORTE":
                gc.strokeLine(cx, cy + mitad, cx, cy - mitad);
                gc.strokeLine(cx, cy - mitad, cx - punta, cy - mitad + punta);
                gc.strokeLine(cx, cy - mitad, cx + punta, cy - mitad + punta);
                break;
            case "SUR":
                gc.strokeLine(cx, cy - mitad, cx, cy + mitad);
                gc.strokeLine(cx, cy + mitad, cx - punta, cy + mitad - punta);
                gc.strokeLine(cx, cy + mitad, cx + punta, cy + mitad - punta);
                break;
            case "ESTE":
                gc.strokeLine(cx - mitad, cy, cx + mitad, cy);
                gc.strokeLine(cx + mitad, cy, cx + mitad - punta, cy - punta);
                gc.strokeLine(cx + mitad, cy, cx + mitad - punta, cy + punta);
                break;
            case "OESTE":
                gc.strokeLine(cx + mitad, cy, cx - mitad, cy);
                gc.strokeLine(cx - mitad, cy, cx - mitad + punta, cy - punta);
                gc.strokeLine(cx - mitad, cy, cx - mitad + punta, cy + punta);
                break;
        }
    }
}