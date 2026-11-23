package org.example;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterseccionLucesTest {
    @Test
    void detectaLosCuatroLadosCuandoCadaUnoConectaConUnaCalle() {
        Mapa mapa = new Mapa(new int[][]{
                {0, 6, 0},
                {3, 7, 4},
                {0, 2, 0}
        });

        Interseccion interseccion = (Interseccion) mapa.getCelda(1, 1);

        assertEquals(Set.of(
                Interseccion.Lado.NORTE,
                Interseccion.Lado.SUR,
                Interseccion.Lado.ESTE,
                Interseccion.Lado.OESTE), interseccion.getLadosConectados());
    }

    @Test
    void noDetectaSpawnsNiCallesDeOrientacionIncompatibleComoConexiones() {
        Mapa mapa = new Mapa(new int[][]{
                {0, 5, 0},
                {5, 7, 8},
                {0, 0, 0}
        });

        Interseccion interseccion = (Interseccion) mapa.getCelda(1, 1);

        assertEquals(Set.of(Interseccion.Lado.OESTE), interseccion.getLadosConectados());
    }
}
