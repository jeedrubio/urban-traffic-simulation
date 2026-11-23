package org.example;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GestorVehiculosTest {
    @Test
    void usaRutasAlcanzablesYActualizaElTotalSoloParaAutosAdicionales() throws Exception {
        GestorMetricas metricas = GestorMetricas.getInstancia();
        metricas.reiniciar(20);
        Mapa mapa = new Mapa(new int[][]{{8, 5, 9, 5, 8}}, new ConfiguracionSimulacion());
        List<Vehiculo> vehiculos = new CopyOnWriteArrayList<>();
        GestorVehiculos gestor = new GestorVehiculos(mapa, vehiculos, metricas);
        PuntoGeneracion origen = mapa.getPuntosSpawn().get(0);
        PuntoGeneracion destino = mapa.getPuntosDestino().get(0);

        assertEquals(2, mapa.getPuntosSpawn().size());
        assertEquals(1, mapa.getPuntosDestino().size());
        assertEquals(List.of(destino), gestor.obtenerDestinosAlcanzables(origen).get(5, TimeUnit.SECONDS));
        assertTrue(gestor.crearVehiculoEnRuta(origen, destino, true).get(5, TimeUnit.SECONDS));
        assertFalse(gestor.crearVehiculoEnRuta(origen, origen, true).get(5, TimeUnit.SECONDS));
        assertFalse(gestor.crearVehiculoEnRuta(destino, origen, true).get(5, TimeUnit.SECONDS));
        assertFalse(gestor.crearVehiculoEnRuta(origen, mapa.getPuntosSpawn().get(1), true)
                .get(5, TimeUnit.SECONDS));

        assertEquals(1, vehiculos.size());
        assertEquals("V1", vehiculos.get(0).getId());
        assertEquals(21, metricas.getTotalEsperados());
    }

    @Test
    void noOfreceNiCreaDestinosSinRutaAlcanzable() throws Exception {
        GestorMetricas metricas = GestorMetricas.getInstancia();
        metricas.reiniciar(0);
        Mapa mapa = new Mapa(new int[][]{{8, 0, 9}}, new ConfiguracionSimulacion());
        GestorVehiculos gestor = new GestorVehiculos(
                mapa, new CopyOnWriteArrayList<>(), metricas);
        PuntoGeneracion origen = mapa.getPuntosSpawn().get(0);
        PuntoGeneracion destino = mapa.getPuntosDestino().get(0);

        assertNull(mapa.calcularRutaDijkstra(origen, destino));
        assertTrue(gestor.obtenerDestinosAlcanzables(origen)
                .get(5, TimeUnit.SECONDS).isEmpty());
        assertFalse(gestor.crearVehiculoEnRuta(origen, destino, false)
                .get(5, TimeUnit.SECONDS));
    }
}
