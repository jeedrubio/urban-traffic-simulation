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
        Mapa mapa = new Mapa(new int[][]{{8, 5, 8}}, new ConfiguracionSimulacion());
        List<Vehiculo> vehiculos = new CopyOnWriteArrayList<>();
        GestorVehiculos gestor = new GestorVehiculos(mapa, vehiculos, metricas);
        PuntoGeneracion origen = mapa.getPuntosSpawn().get(0);
        PuntoGeneracion destino = mapa.getPuntosSpawn().get(1);

        assertEquals(List.of(destino), gestor.obtenerDestinosAlcanzables(origen).get(5, TimeUnit.SECONDS));
        assertTrue(gestor.crearVehiculoEnRuta(origen, destino, true).get(5, TimeUnit.SECONDS));
        assertFalse(gestor.crearVehiculoEnRuta(origen, origen, true).get(5, TimeUnit.SECONDS));

        assertEquals(1, vehiculos.size());
        assertEquals("V1", vehiculos.get(0).getId());
        assertEquals(21, metricas.getTotalEsperados());
    }
}
