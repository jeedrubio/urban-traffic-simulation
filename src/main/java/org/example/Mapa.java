package org.example;

import java.util.*;

public class Mapa {
    private Celda[][] cuadricula;
    private int filas;
    private int columnas;
    private List<PuntoGeneracion> puntosSpawn = new ArrayList<>();
    private final ConfiguracionSimulacion config;
    private final Map<CoordenadaMapa, String> configuracionesPorInterseccion;

    public Mapa(int[][] matrizDiseño) {
        this(matrizDiseño, new ConfiguracionSimulacion());
    }

    public Mapa(int[][] matrizDiseño, ConfiguracionSimulacion config) {
        this(matrizDiseño, config, Map.of());
    }

    public Mapa(DefinicionSimulacion definicion) {
        this(
                definicion.getDiseñoCiudad(),
                definicion.getConfiguracion(),
                definicion.getConfiguracionesPorInterseccion());
    }

    private Mapa(
            int[][] matrizDiseño,
            ConfiguracionSimulacion config,
            Map<CoordenadaMapa, String> configuracionesPorInterseccion) {
        this.filas = matrizDiseño.length;
        this.columnas = matrizDiseño[0].length;
        this.cuadricula = new Celda[filas][columnas];
        this.config = config;
        this.configuracionesPorInterseccion = Map.copyOf(configuracionesPorInterseccion);
        construirDesdeMatriz(matrizDiseño);
    }

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
                    case 7: // INTERSECCIÓN
                        CoordenadaMapa coordenada = new CoordenadaMapa(i, j);
                        String idConfiguracion = configuracionesPorInterseccion.get(coordenada);
                        ConfiguracionSemaforo configSemaforo = idConfiguracion == null
                                ? null
                                : config.getConfiguracionesSemaforo().get(idConfiguracion);
                        if (idConfiguracion != null && configSemaforo == null) {
                            throw new IllegalArgumentException(
                                    "No existe la configuración de semáforo '" + idConfiguracion + "'.");
                        }
                        Semaforo sem = configSemaforo == null
                                ? new Semaforo(config)
                                : new Semaforo(configSemaforo);
                        Thread hiloSemaforo = new Thread(sem, "Semaforo-" + i + "-" + j);
                        hiloSemaforo.setDaemon(true);
                        hiloSemaforo.start();
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

    public PuntoGeneracion buscarSpawnPorCoordenada(int fila, int col) {
        for (PuntoGeneracion spawn : puntosSpawn) {
            if (spawn.x == fila && spawn.y == col) {
                return spawn;
            }
        }
        return null;
    }

    public List<PuntoGeneracion> getPuntosSpawn() { return puntosSpawn; }
    public Celda getCelda(int x, int y) { return cuadricula[x][y]; }
    public int getFilas() { return filas; }
    public int getColumnas() { return columnas; }

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
            if (actual == destino) break;

            for (Celda vecino : obtenerVecinosAccesibles(actual)) {
                int nuevaDistancia = distancias.get(actual) + 1;
                if (nuevaDistancia < distancias.getOrDefault(vecino, Integer.MAX_VALUE)) {
                    distancias.put(vecino, nuevaDistancia);
                    previos.put(vecino, actual);
                    cola.add(vecino);
                }
            }
        }

        List<Celda> ruta = new ArrayList<>();
        Celda actual = destino;
        if (!previos.containsKey(actual) && origen != destino) return null;

        while (actual != null) {
            ruta.add(0, actual);
            actual = previos.get(actual);
        }
        return ruta;
    }

    private List<Celda> obtenerVecinosAccesibles(Celda c) {
        List<Celda> vecinos = new ArrayList<>();
        int i = c.x;
        int j = c.y;

        if (i > 0 && c.puedeSalirHacia("NORTE")) {
            Celda norte = cuadricula[i - 1][j];
            if (norte.transitable && norte.puedeEntrarDesde("SUR")) {
                vecinos.add(norte);
            }
        }
        if (i < filas - 1 && c.puedeSalirHacia("SUR")) {
            Celda sur = cuadricula[i + 1][j];
            if (sur.transitable && sur.puedeEntrarDesde("NORTE")) {
                vecinos.add(sur);
            }
        }
        if (j > 0 && c.puedeSalirHacia("OESTE")) {
            Celda oeste = cuadricula[i][j - 1];
            if (oeste.transitable && oeste.puedeEntrarDesde("ESTE")) {
                vecinos.add(oeste);
            }
        }
        if (j < columnas - 1 && c.puedeSalirHacia("ESTE")) {
            Celda este = cuadricula[i][j + 1];
            if (este.transitable && este.puedeEntrarDesde("OESTE")) {
                vecinos.add(este);
            }
        }
        return vecinos;
    }
}
