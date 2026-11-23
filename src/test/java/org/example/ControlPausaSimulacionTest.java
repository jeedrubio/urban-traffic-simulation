package org.example;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class ControlPausaSimulacionTest {
    @Test
    void pausaCongelaElRelojYConservaElTiempoRestante() throws Exception {
        ControlPausaSimulacion control = new ControlPausaSimulacion();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> espera = executor.submit(() -> control.esperarActivo(250));
            Thread.sleep(70);

            control.pausar();
            long tiempoPausado = control.ahoraNanos();
            Thread.sleep(180);

            assertEquals(tiempoPausado, control.ahoraNanos());
            assertFalse(espera.isDone(), "El plazo no debe terminar mientras está pausado.");

            control.reanudar();
            assertTrue(espera.get(1, TimeUnit.SECONDS));
        } finally {
            control.finalizar();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void finalizarDespiertaLasTareasQueEsperanReanudar() throws Exception {
        ControlPausaSimulacion control = new ControlPausaSimulacion();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        control.pausar();
        try {
            Future<Boolean> espera = executor.submit(control::esperarActiva);
            Thread.sleep(40);

            control.finalizar();

            assertFalse(espera.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void vehiculoNoAvanzaMientrasLaSimulacionEstaPausada() throws Exception {
        GestorMetricas metricas = GestorMetricas.getInstancia();
        metricas.reiniciar(1);
        ControlPausaSimulacion control = new ControlPausaSimulacion();
        PuntoGeneracion origen = new PuntoGeneracion(0, 0, true, false);
        PuntoGeneracion destino = new PuntoGeneracion(0, 1, false, true);
        Vehiculo vehiculo = new Vehiculo("Pausa", java.util.List.of(origen, destino), 600, control);
        Thread hilo = new Thread(vehiculo);
        hilo.start();

        try {
            esperarHasta(origen::estaOcupada);
            control.pausar();
            Thread.sleep(500);

            assertSame(origen, vehiculo.getCeldaActual());
            assertTrue(hilo.isAlive());
            assertEquals(0, metricas.getTotalLlegados());

            control.reanudar();
            hilo.join(2000);
            assertFalse(hilo.isAlive());
            assertEquals(1, metricas.getTotalLlegados());
        } finally {
            control.finalizar();
            hilo.interrupt();
            hilo.join(1000);
        }
    }

    @Test
    void finalizarMientrasUnVehiculoEstaPausadoLoDetieneYLiberaLaCelda() throws Exception {
        GestorMetricas metricas = GestorMetricas.getInstancia();
        metricas.reiniciar(1);
        ControlPausaSimulacion control = new ControlPausaSimulacion();
        PuntoGeneracion origen = new PuntoGeneracion(0, 0, true, false);
        PuntoGeneracion destino = new PuntoGeneracion(0, 1, false, true);
        Vehiculo vehiculo = new Vehiculo("Finalizado", java.util.List.of(origen, destino), 2000, control);
        Thread hilo = new Thread(vehiculo);
        hilo.start();

        esperarHasta(origen::estaOcupada);
        control.pausar();
        control.finalizar();
        hilo.join(1000);

        assertFalse(hilo.isAlive());
        assertFalse(origen.estaOcupada());
        assertEquals(0, metricas.getTotalLlegados());
    }

    private void esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        fail("El vehículo no alcanzó el estado esperado a tiempo.");
    }
}
