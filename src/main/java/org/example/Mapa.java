package org.example;

import java.util.*;

public class Mapa {
    private Celda[][] cuadricula;
    private int filas;
    private int columnas;
    private List<PuntoGeneracion> puntosSpawn = new ArrayList<>();

    public Mapa(int[][] matrizDiseño) {
        this.filas = matrizDiseño.length;
        this.columnas = matrizDiseño[0].length;
        this.cuadricula = new Celda[filas][columnas];
        construirDesdeMatriz(matrizDiseño);
    }


    //Para construir el mapa de la ciudad, en una matriz se guardan los casos para cada tipo de

    private void construirDesdeMatriz(int[][] matriz) {
        for (int i = 0; i < filas; i++) {
            for (int j = 0; j < columnas; j++) {
                int tipo = matriz[i][j];
                switch (tipo) {
                    case 0: cuadricula[i][j] = new ZonaNoAccesible(i, j); break;
                    case 1: cuadricula[i][j] = new CalleUnSentido(i, j, "NORTE"); break;
                    case 2: cuadricula[i][j] = new CalleUnSentido(i, j, "SUR"); break;
                    case 3: cuadricula[i][j] = new CalleUnSentido(i, j, "ESTE"); break;
                    case 4: cuadricula[i][j] = new CalleUnSentido(i, j, "OESTE"); break;
                    case 5: cuadricula[i][j] = new CalleDobleSentido(i, j, "HORIZONTAL"); break;
                    case 6: cuadricula[i][j] = new CalleDobleSentido(i, j, "VERTICAL"); break;
                    case 7: //INTERSECCIÓN
                        Semaforo sem = new Semaforo();
                        new Thread(sem).start();
                        cuadricula[i][j] = new Interseccion(i, j, sem);
                        break;
                    case 8: // PUNTO DE GENERACIÓN
                        PuntoGeneracion spawn = new PuntoGeneracion(i, j);
                        cuadricula[i][j] = spawn;
                        puntosSpawn.add(spawn);
                        break;
                    default: throw new IllegalArgumentException("Número no reconocido: " + tipo);
                }
            }
        }
    }

    public List<PuntoGeneracion> getPuntosSpawn() { return puntosSpawn; }
    public Celda getCelda(int x, int y) { return cuadricula[x][y]; }
    public int getFilas() { return filas; }
    public int getColumnas() { return columnas; }

    // Aquí se implementa Dijkstra para calcular las rutas de los vehículos.
    public List<Celda> calcularRutaDijkstra(Celda origen, Celda destino) {
        Map<Celda, Integer> distancias = new HashMap<>();
        Map<Celda, Celda> previos = new HashMap<>();
        PriorityQueue<Celda> cola = new PriorityQueue<>(Comparator.comparingInt(c -> distancias.getOrDefault(c, Integer.MAX_VALUE)));

        for (int i = 0; i < filas; i++) {
            for (int j = 0; j < columnas; j++) {
                if (cuadricula[i][j].transitable) distancias.put(cuadricula[i][j], Integer.MAX_VALUE);
            }
        }
        distancias.put(origen, 0);
        cola.add(origen);

        while (!cola.isEmpty()) {
            Celda actual = cola.poll();
            if (actual == destino) break; // Aquí es cuando se llega al destino.

            for (Celda vecino : obtenerVecinosAccesibles(actual)) {
                int nuevaDistancia = distancias.get(actual) + 1; // 1 salto por cuadra
                if (nuevaDistancia < distancias.getOrDefault(vecino, Integer.MAX_VALUE)) {
                    distancias.put(vecino, nuevaDistancia);
                    previos.put(vecino, actual);
                    cola.add(vecino);
                }
            }
        }


        // Reconstruir la ruta hacia atrás
        List<Celda> ruta = new ArrayList<>();
        Celda actual = destino;
        if (!previos.containsKey(actual) && origen != destino) return null; // Manda null si es que no hay camino

        while (actual != null) {
            ruta.add(0, actual);
            actual = previos.get(actual);
        }
        return ruta;
    }



    //Como tenemos caminos que sólo se pueden entrar por un lado, para que el algoritmo no se meta en una
    //calle de reversa, se evalua si se puede entrar por ese lado de la celda, si no se puede retrocede.

    private List<Celda> obtenerVecinosAccesibles(Celda c) {
        List<Celda> vecinos = new ArrayList<>();
        int i = c.x; // Fila
        int j = c.y; // Columna

        // Evaluar movimiento hacia ARRIBA (NORTE)
        // El vehículo sale hacia el NORTE y entrará a la celda superior desde el SUR
        if (i > 0 && c.puedeSalirHacia("NORTE")) {
            Celda norte = cuadricula[i - 1][j];
            if (norte.transitable && norte.puedeEntrarDesde("SUR")) {
                vecinos.add(norte);
            }
        }

        // Evaluar movimiento hacia ABAJO (SUR)
        // El vehículo sale hacia el SUR y entrará a la celda inferior desde el NORTE
        if (i < filas - 1 && c.puedeSalirHacia("SUR")) {
            Celda sur = cuadricula[i + 1][j];
            if (sur.transitable && sur.puedeEntrarDesde("NORTE")) {
                vecinos.add(sur);
            }
        }

        // Evaluar movimiento hacia la IZQUIERDA (OESTE)
        // El vehículo sale hacia el OESTE y entrará a la celda izquierda desde el ESTE
        if (j > 0 && c.puedeSalirHacia("OESTE")) {
            Celda oeste = cuadricula[i][j - 1];
            if (oeste.transitable && oeste.puedeEntrarDesde("ESTE")) {
                vecinos.add(oeste);
            }
        }

        // Evaluar movimiento hacia la DERECHA (ESTE)
        // El vehículo sale hacia el ESTE y entrará a la celda derecha desde el OESTE
        if (j < columnas - 1 && c.puedeSalirHacia("ESTE")) {
            Celda este = cuadricula[i][j + 1];
            if (este.transitable && este.puedeEntrarDesde("OESTE")) {
                vecinos.add(este);
            }
        }

        return vecinos;
    }
}