package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;


//Estas calles son parecidas a las de un sentido, pero estas tienen que pueden ser horizontales o
//verticales, por lo que son horizontales o verticales, y permiten entrada desde ESTE U OESTE o de
// NORTE O SUR, dependiendo cual sea.


public class CalleDobleSentido extends Celda {
    private final String orientacion; // "HORIZONTAL" o "VERTICAL"
    // Cuadrícula 2x2 de secciones exclusivas: [fila: 0=Arriba, 1=Abajo][col: 0=Izq, 1=Der]
    private final Vehiculo[][] secciones = new Vehiculo[2][2];

    public CalleDobleSentido(int x, int y, String orientacion) {
        super(x, y, true);
        this.orientacion = orientacion;
    }

    public String getOrientacion() {
        return orientacion;
    }

    @Override
    public boolean puedeSalirHacia(String dir) {
        if ("HORIZONTAL".equals(orientacion)) return "ESTE".equals(dir) || "OESTE".equals(dir);
        if ("VERTICAL".equals(orientacion)) return "NORTE".equals(dir) || "SUR".equals(dir);
        return false;
    }

    @Override
    public boolean puedeEntrarDesde(String dir) {
        return puedeSalirHacia(dir);
    }

    // Devuelve [fila, col] de la sección de ENTRADA según la dirección
    private int[] obtenerSeccionEntrada(String dir) {
        switch (dir) {
            case "ESTE":  return new int[]{1, 0}; // Inferior Izquierda
            case "OESTE": return new int[]{0, 1}; // Superior Derecha
            case "SUR":   return new int[]{0, 0}; // Superior Izquierda
            case "NORTE": return new int[]{1, 1}; // Inferior Derecha
            default:      return new int[]{0, 0};
        }
    }

    // Devuelve [fila, col] de la sección de SALIDA según la dirección
    private int[] obtenerSeccionSalida(String dir) {
        switch (dir) {
            case "ESTE":  return new int[]{1, 1}; // Inferior Derecha
            case "OESTE": return new int[]{0, 0}; // Superior Izquierda
            case "SUR":   return new int[]{1, 0}; // Inferior Izquierda
            case "NORTE": return new int[]{0, 1}; // Superior Derecha
            default:      return new int[]{0, 1};
        }
    }

    @Override
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        int[] ent = obtenerSeccionEntrada(direccionMovimiento);
        synchronized (secciones) {
            // Confirmar que la sección de entrada esté libre y el carril no esté lleno (2 autos)
            int[] sal = obtenerSeccionSalida(direccionMovimiento);
            boolean llena = (secciones[ent[0]][ent[1]] != null && secciones[sal[0]][sal[1]] != null);
            return !llena && secciones[ent[0]][ent[1]] == null;
        }
    }

    @Override
    public void entrar(Vehiculo v) throws InterruptedException {
        String dir = v.getDireccionMovimiento();
        int[] ent = obtenerSeccionEntrada(dir);

        synchronized (secciones) {
            // Ningún carro puede entrar hasta que la sección de entrada esté libre
            while (secciones[ent[0]][ent[1]] != null) {
                secciones.wait();
            }
            secciones[ent[0]][ent[1]] = v;
            v.setSeccionActual(ent[0], ent[1]);
        }
    }

    @Override
    public void avanzarDentroDeCelda(Vehiculo v) throws InterruptedException {
        String dir = v.getDireccionMovimiento();
        int[] ent = obtenerSeccionEntrada(dir);
        int[] sal = obtenerSeccionSalida(dir);

        synchronized (secciones) {
            // Esperar si la segunda mitad del carril aún está ocupada por otro auto
            while (secciones[sal[0]][sal[1]] != null) {
                secciones.wait();
            }
            secciones[sal[0]][sal[1]] = v;
            if (secciones[ent[0]][ent[1]] == v) {
                secciones[ent[0]][ent[1]] = null;
            }
            v.setSeccionActual(sal[0], sal[1]);
            secciones.notifyAll(); // Libera la sección de entrada para el auto que viene detrás
        }
    }

    @Override
    public void salir(Vehiculo v) {
        synchronized (secciones) {
            for (int f = 0; f < 2; f++) {
                for (int c = 0; c < 2; c++) {
                    if (secciones[f][c] == v) {
                        secciones[f][c] = null;
                    }
                }
            }
            secciones.notifyAll(); // Notifica a los autos esperando avanzar o entrar
        }
    }

    @Override
    public void dibujar(GraphicsContext gc, double px, double py, double tamano) {
        gc.setFill(Color.web("#3b3f4c"));
        gc.fillRect(px, py, tamano, tamano);

        // División sutil entre las 2 secciones de cada carril
        gc.setStroke(Color.rgb(255, 255, 255, 0.12));
        gc.setLineWidth(1.0);
        gc.setLineDashes(3);
        if ("HORIZONTAL".equals(orientacion)) {
            gc.strokeLine(px + tamano / 2, py, px + tamano / 2, py + tamano);
        } else {
            gc.strokeLine(px, py + tamano / 2, px + tamano, py + tamano / 2);
        }

        // Línea central amarilla punteada que separa los dos sentidos
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(2.0);
        gc.setLineDashes(8);

        if ("HORIZONTAL".equals(orientacion)) {
            gc.strokeLine(px, py + tamano / 2, px + tamano, py + tamano / 2);
            gc.setLineDashes(null);
            dibujarFlecha(gc, px + tamano * 0.5, py + tamano * 0.25, "OESTE", tamano * 0.35);
            dibujarFlecha(gc, px + tamano * 0.5, py + tamano * 0.75, "ESTE", tamano * 0.35);
        } else {
            gc.strokeLine(px + tamano / 2, py, px + tamano / 2, py + tamano);
            gc.setLineDashes(null);
            dibujarFlecha(gc, px + tamano * 0.25, py + tamano * 0.5, "SUR", tamano * 0.35);
            dibujarFlecha(gc, px + tamano * 0.75, py + tamano * 0.5, "NORTE", tamano * 0.35);
        }
    }
}