package org.example;

import java.util.*;

public class Mapa {
    private Celda[][] cuadricula;
    private int filas;
    private int columnas;
    private final List<PuntoGeneracion> puntosSpawn = new ArrayList<>();
    private final List<PuntoGeneracion> puntosDestino = new ArrayList<>();
    private final ConfiguracionSimulacion config;
    private final Map<CoordenadaMapa, String> configuracionesPorInterseccion;
    private final ControlPausaSimulacion controlPausa;

    public Mapa(int[][] matrizDiseño) {
        this(matrizDiseño, new ConfiguracionSimulacion());
    }

    public Mapa(int[][] matrizDiseño, ConfiguracionSimulacion config) {
        this(matrizDiseño, config, null);
    }

    public Mapa(
            int[][] matrizDiseño,
            ConfiguracionSimulacion config,
            ControlPausaSimulacion controlPausa) {
        this(matrizDiseño, config, Map.of(), controlPausa);
    }

    public Mapa(DefinicionSimulacion definicion) {
        this(definicion, null);
    }

    public Mapa(DefinicionSimulacion definicion, ControlPausaSimulacion controlPausa) {
        this(
                definicion.getDiseñoCiudad(),
                definicion.getConfiguracion(),
                definicion.getConfiguracionesPorInterseccion(),
                controlPausa);
    }

    private Mapa(
            int[][] matrizDiseño,
            ConfiguracionSimulacion config,
            Map<CoordenadaMapa, String> configuracionesPorInterseccion,
            ControlPausaSimulacion controlPausa) {
        this.filas = matrizDiseño.length;
        this.columnas = matrizDiseño[0].length;
        this.cuadricula = new Celda[filas][columnas];
        this.config = config;
        this.configuracionesPorInterseccion = Map.copyOf(configuracionesPorInterseccion);
        this.controlPausa = controlPausa;
        construirDesdeMatriz(matrizDiseño);
        configurarSemaforos();
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
                                ? new Semaforo(config, controlPausa)
                                : new Semaforo(configSemaforo, controlPausa);
                        cuadricula[i][j] = new Interseccion(
                                i, j, sem, detectarLadosConectados(matriz, i, j));
                        break;
                    case 8: // SPAWN
                    case 9: // DESTINO
                    case 10: // SPAWN Y DESTINO
                        boolean esSpawn = tipo == 8 || tipo == 10;
                        boolean esDestino = tipo == 9 || tipo == 10;
                        PuntoGeneracion punto = new PuntoGeneracion(i, j, esSpawn, esDestino);
                        cuadricula[i][j] = punto;
                        if (esSpawn) puntosSpawn.add(punto);
                        if (esDestino) puntosDestino.add(punto);
                        break;
                    default: throw new IllegalArgumentException("Número no reconocido: " + tipo);
                }
            }
        }
    }

    private void configurarSemaforos() {
        for (int fila = 0; fila < filas; fila++) {
            for (int columna = 0; columna < columnas; columna++) {
                if (!(cuadricula[fila][columna] instanceof Interseccion interseccion)) {
                    continue;
                }
                Map<String, Celda> adyacentes = new HashMap<>();
                if (fila > 0) adyacentes.put("NORTE", cuadricula[fila - 1][columna]);
                if (fila < filas - 1) adyacentes.put("SUR", cuadricula[fila + 1][columna]);
                if (columna < columnas - 1) adyacentes.put("ESTE", cuadricula[fila][columna + 1]);
                if (columna > 0) adyacentes.put("OESTE", cuadricula[fila][columna - 1]);
                interseccion.configurarCeldasAdyacentes(adyacentes);
            }
        }
        for (Celda[] fila : cuadricula) {
            for (Celda celda : fila) {
                if (celda instanceof Interseccion interseccion) {
                    interseccion.iniciarSemaforo();
                }
            }
        }
    }

    public void detenerSemaforos() {
        forEachInterseccion(Interseccion::detenerSemaforo);
    }

    public void esperarDetencionSemaforos() throws InterruptedException {
        for (Celda[] fila : cuadricula) {
            for (Celda celda : fila) {
                if (celda instanceof Interseccion interseccion) {
                    interseccion.esperarDetencionSemaforo();
                }
            }
        }
    }

    private void forEachInterseccion(java.util.function.Consumer<Interseccion> accion) {
        for (Celda[] fila : cuadricula) {
            for (Celda celda : fila) {
                if (celda instanceof Interseccion interseccion) {
                    accion.accept(interseccion);
                }
            }
        }
    }

    private Set<Interseccion.Lado> detectarLadosConectados(int[][] matriz, int fila, int columna) {
        Set<Interseccion.Lado> lados = EnumSet.noneOf(Interseccion.Lado.class);
        if (fila > 0 && esCalleConectada(matriz[fila - 1][columna], Interseccion.Lado.NORTE)) {
            lados.add(Interseccion.Lado.NORTE);
        }
        if (fila < filas - 1 && esCalleConectada(matriz[fila + 1][columna], Interseccion.Lado.SUR)) {
            lados.add(Interseccion.Lado.SUR);
        }
        if (columna < columnas - 1 && esCalleConectada(matriz[fila][columna + 1], Interseccion.Lado.ESTE)) {
            lados.add(Interseccion.Lado.ESTE);
        }
        if (columna > 0 && esCalleConectada(matriz[fila][columna - 1], Interseccion.Lado.OESTE)) {
            lados.add(Interseccion.Lado.OESTE);
        }
        return lados;
    }

    private boolean esCalleConectada(int tipoCelda, Interseccion.Lado lado) {
        return switch (lado) {
            case NORTE, SUR -> tipoCelda == 1 || tipoCelda == 2 || tipoCelda == 6;
            case ESTE, OESTE -> tipoCelda == 3 || tipoCelda == 4 || tipoCelda == 5;
        };
    }

    public PuntoGeneracion buscarSpawnPorCoordenada(int fila, int col) {
        for (PuntoGeneracion spawn : puntosSpawn) {
            if (spawn.x == fila && spawn.y == col) {
                return spawn;
            }
        }
        return null;
    }

    public PuntoGeneracion buscarDestinoPorCoordenada(int fila, int col) {
        for (PuntoGeneracion destino : puntosDestino) {
            if (destino.x == fila && destino.y == col) {
                return destino;
            }
        }
        return null;
    }

    public List<PuntoGeneracion> getPuntosSpawn() { return List.copyOf(puntosSpawn); }
    public List<PuntoGeneracion> getPuntosDestino() { return List.copyOf(puntosDestino); }
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
