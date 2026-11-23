package org.example;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GestorMetricasTest {
    @Test
    void lasReservasAdicionalesPosponenElCierreYSePuedenLiberar() {
        GestorMetricas gestor = GestorMetricas.getInstancia();
        gestor.reiniciar(1);
        AtomicInteger cierres = new AtomicInteger();
        gestor.setAlFinalizarSimulacion(cierres::incrementAndGet);

        assertTrue(gestor.registrarVehiculoAdicionalEsperado());
        assertEquals(2, gestor.getTotalEsperados());

        PuntoGeneracion origen = new PuntoGeneracion(0, 0);
        PuntoGeneracion destino = new PuntoGeneracion(0, 1);
        gestor.registrarLlegada(new Vehiculo("V1", List.of(origen, destino), 900));

        assertFalse(gestor.estaFinalizada());
        assertEquals(0, cierres.get());

        gestor.cancelarVehiculoAdicionalEsperado();

        assertTrue(gestor.estaFinalizada());
        assertEquals(1, gestor.getTotalEsperados());
        assertEquals(1, cierres.get());
        assertFalse(gestor.registrarVehiculoAdicionalEsperado());
    }

    @Test
    void finalizarSinReporteIgnoraLlegadasTardias() {
        GestorMetricas gestor = GestorMetricas.getInstancia();
        gestor.reiniciar(1);
        AtomicInteger cierres = new AtomicInteger();
        gestor.setAlFinalizarSimulacion(cierres::incrementAndGet);

        gestor.finalizarSinReporte();
        gestor.registrarLlegada(new Vehiculo(
                "Tardio",
                List.of(new PuntoGeneracion(0, 0), new PuntoGeneracion(0, 1)),
                900));

        assertTrue(gestor.estaFinalizada());
        assertEquals(0, gestor.getTotalLlegados());
        assertTrue(gestor.getRegistros().isEmpty());
        assertEquals(0, cierres.get());
        assertFalse(gestor.registrarVehiculoAdicionalEsperado());
    }
}
