package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

//Se tiene una sola dirección, por lo que el auuto puede entrar desde el NORTE, SUR, ESTE u OESTE,
//si entra por una dirección, tiene que salir por la contraria, por lo que tenemos 4 tipos diferentes de calles
// de un solo sentido.


public class CalleUnSentido extends Celda {
    private final String direccion;
    private final Vehiculo[][] secciones = new Vehiculo[2][2];

    public CalleUnSentido(int x, int y, String direccion) {
        super(x, y, true);
        this.direccion = direccion;
    }

    public String getDireccion() {
        return direccion;
    }



    @Override
    public boolean puedeSalirHacia(String dir) {
        return this.direccion.equals(dir);
    }

    //Casos para que solo pueda entrar y salir por la dirección que le corresponde

    @Override
    public boolean puedeEntrarDesde(String dir) {
        if ("NORTE".equals(direccion) && "SUR".equals(dir)) return true;
        if ("SUR".equals(direccion) && "NORTE".equals(dir)) return true;
        if ("ESTE".equals(direccion) && "OESTE".equals(dir)) return true;
        if ("OESTE".equals(direccion) && "ESTE".equals(dir)) return true;
        return false;
    }

    private int[] obtenerSeccionEntrada(int carril) {
        switch (direccion) {
            case "ESTE":  return new int[]{carril, 0}; // Columna izquierda (fila 0 o 1)
            case "OESTE": return new int[]{carril, 1}; // Columna derecha (fila 0 o 1)
            case "SUR":   return new int[]{0, carril}; // Fila superior (col 0 o 1)
            case "NORTE": return new int[]{1, carril}; // Fila inferior (col 0 o 1)
            default:      return new int[]{0, 0};
        }
    }

    private int[] obtenerSeccionSalida(int carril) {
        switch (direccion) {
            case "ESTE":  return new int[]{carril, 1}; // Columna derecha
            case "OESTE": return new int[]{carril, 0}; // Columna izquierda
            case "SUR":   return new int[]{1, carril}; // Fila inferior
            case "NORTE": return new int[]{0, carril}; // Fila superior
            default:      return new int[]{0, 1};
        }
    }

    @Override
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        synchronized (secciones) {
            int[] ent0 = obtenerSeccionEntrada(0);
            int[] ent1 = obtenerSeccionEntrada(1);
            return secciones[ent0[0]][ent0[1]] == null || secciones[ent1[0]][ent1[1]] == null;
        }
    }

    @Override
    public void entrar(Vehiculo v) throws InterruptedException {
        synchronized (secciones) {
            while (true) {
                for (int carril = 0; carril < 2; carril++) {
                    int[] ent = obtenerSeccionEntrada(carril);
                    if (secciones[ent[0]][ent[1]] == null) {
                        secciones[ent[0]][ent[1]] = v;
                        v.setCarrilUnSentido(carril);
                        v.setSeccionActual(ent[0], ent[1]);
                        return;
                    }
                }
                secciones.wait();
            }
        }
    }

    @Override
    public void avanzarDentroDeCelda(Vehiculo v) throws InterruptedException {
        int carril = v.getCarrilUnSentido();
        int[] ent = obtenerSeccionEntrada(carril);
        int[] sal = obtenerSeccionSalida(carril);

        synchronized (secciones) {
            while (secciones[sal[0]][sal[1]] != null) {
                secciones.wait();
            }
            secciones[sal[0]][sal[1]] = v;
            if (secciones[ent[0]][ent[1]] == v) {
                secciones[ent[0]][ent[1]] = null;
            }
            v.setSeccionActual(sal[0], sal[1]);
            secciones.notifyAll();
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
            secciones.notifyAll();
        }
    }

    @Override
    public void dibujar(GraphicsContext gc, double px, double py, double tamano) {
        gc.setFill(Color.web("#3b3f4c"));
        gc.fillRect(px, py, tamano, tamano);

        // División sutil transversal de secciones
        gc.setStroke(Color.rgb(255, 255, 255, 0.12));
        gc.setLineWidth(1.0);
        gc.setLineDashes(3);
        if ("NORTE".equals(direccion) || "SUR".equals(direccion)) {
            gc.strokeLine(px, py + tamano / 2, px + tamano, py + tamano / 2);
        } else {
            gc.strokeLine(px + tamano / 2, py, px + tamano / 2, py + tamano);
        }

        // Línea divisoria blanca punteada entre los 2 carriles del mismo sentido
        gc.setStroke(Color.rgb(255, 255, 255, 0.45));
        gc.setLineWidth(1.5);
        gc.setLineDashes(6);

        if ("NORTE".equals(direccion) || "SUR".equals(direccion)) {
            gc.strokeLine(px + tamano / 2, py, px + tamano / 2, py + tamano);
            gc.setLineDashes(null);
            dibujarFlecha(gc, px + tamano * 0.25, py + tamano * 0.5, direccion, tamano * 0.35);
            dibujarFlecha(gc, px + tamano * 0.75, py + tamano * 0.5, direccion, tamano * 0.35);
        } else {
            gc.strokeLine(px, py + tamano / 2, px + tamano, py + tamano / 2);
            gc.setLineDashes(null);
            dibujarFlecha(gc, px + tamano * 0.5, py + tamano * 0.25, direccion, tamano * 0.35);
            dibujarFlecha(gc, px + tamano * 0.5, py + tamano * 0.75, direccion, tamano * 0.35);
        }
    }
}