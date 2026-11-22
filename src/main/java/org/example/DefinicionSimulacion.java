package org.example;

import java.util.Map;

public final class DefinicionSimulacion {
    private final int[][] diseñoCiudad;
    private final ConfiguracionSimulacion configuracion;
    private final Map<CoordenadaMapa, String> configuracionesPorInterseccion;

    public DefinicionSimulacion(int[][] diseñoCiudad, ConfiguracionSimulacion configuracion) {
        this(diseñoCiudad, configuracion, Map.of());
    }

    public DefinicionSimulacion(
            int[][] diseñoCiudad,
            ConfiguracionSimulacion configuracion,
            Map<CoordenadaMapa, String> configuracionesPorInterseccion) {
        this.diseñoCiudad = copiarMatriz(diseñoCiudad);
        this.configuracion = configuracion.copiar();
        this.configuracionesPorInterseccion = Map.copyOf(configuracionesPorInterseccion);
    }

    public int[][] getDiseñoCiudad() {
        return copiarMatriz(diseñoCiudad);
    }

    public ConfiguracionSimulacion getConfiguracion() {
        return configuracion.copiar();
    }

    public Map<CoordenadaMapa, String> getConfiguracionesPorInterseccion() {
        return configuracionesPorInterseccion;
    }

    private static int[][] copiarMatriz(int[][] matriz) {
        int[][] copia = new int[matriz.length][];
        for (int i = 0; i < matriz.length; i++) {
            copia[i] = matriz[i].clone();
        }
        return copia;
    }
}
