package org.example;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class SemaforoCambioManualTest {
    @Test
    void elAvanceManualOmiteLaEsperaVerdePeroRespetaAmarilloYRojo() throws Exception {
        Semaforo semaforo = new Semaforo(List.of(
                new FaseSemaforo(Set.of("NORTE", "SUR"), 30),
                new FaseSemaforo(Set.of("ESTE", "OESTE"), 30)
        ), 120L, 120L);
        Thread controlador = new Thread(semaforo, "TestSemaforoManual");
        controlador.setDaemon(true);
        controlador.start();

        try {
            esperarEstado(semaforo, "NORTE", "VERDE");
            CompletableFuture<Integer> cambio = semaforo.solicitarAvanceManual();

            esperarEstado(semaforo, "NORTE", "AMARILLO");
            assertFalse(cambio.isDone(), "La fase nueva debe esperar a que termine amarillo y rojo.");
            CompletableFuture<Integer> cambioDuranteAmarillo = semaforo.solicitarAvanceManual();

            esperarCondicion(() -> todosEnRojo(semaforo));
            assertFalse(cambio.isDone(), "La fase nueva no debe activarse antes de completar rojo.");
            assertFalse(cambioDuranteAmarillo.isDone(),
                    "Un clic durante amarillo debe quedar asociado a la transición actual.");

            assertEquals(1, cambio.get(2, TimeUnit.SECONDS));
            assertEquals(1, cambioDuranteAmarillo.get(2, TimeUnit.SECONDS));
            assertEquals("VERDE", semaforo.getEstadoPorDireccion("ESTE"));
            assertEquals("VERDE", semaforo.getEstadoPorDireccion("OESTE"));
            assertEquals("ROJO", semaforo.getEstadoPorDireccion("NORTE"));
        } finally {
            controlador.interrupt();
            controlador.join(1000);
        }
    }

    @Test
    void unaConfiguracionDeUnaSolaFaseNoPuedeAvanzar() {
        Semaforo semaforo = new Semaforo(
                List.of(new FaseSemaforo(Set.of("NORTE", "SUR"), 5)),
                1,
                1);

        assertEquals(-1, semaforo.solicitarAvanceManual().join());
    }

    @Test
    void laFaseSiguienteRecuperaSuDuracionConfigurada() throws Exception {
        Semaforo semaforo = new Semaforo(List.of(
                new FaseSemaforo(Set.of("NORTE", "SUR"), 30),
                new FaseSemaforo(Set.of("ESTE", "OESTE"), 1)
        ), 80L, 80L);
        Thread controlador = new Thread(semaforo, "TestSemaforoReanudado");
        controlador.setDaemon(true);
        controlador.start();

        try {
            esperarEstado(semaforo, "NORTE", "VERDE");
            CompletableFuture<Integer> cambio = semaforo.solicitarAvanceManual();
            assertEquals(1, cambio.get(2, TimeUnit.SECONDS));
            assertEquals("VERDE", semaforo.getEstadoPorDireccion("ESTE"));

            Thread.sleep(100);
            assertEquals("VERDE", semaforo.getEstadoPorDireccion("ESTE"));
            esperarEstado(semaforo, "ESTE", "AMARILLO");
        } finally {
            controlador.interrupt();
            controlador.join(1000);
        }
    }

    private void esperarEstado(Semaforo semaforo, String direccion, String estado)
            throws InterruptedException {
        esperarCondicion(() -> estado.equals(semaforo.getEstadoPorDireccion(direccion)));
    }

    private void esperarCondicion(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        fail("El semáforo no alcanzó el estado esperado a tiempo.");
    }

    private boolean todosEnRojo(Semaforo semaforo) {
        return "ROJO".equals(semaforo.getEstadoPorDireccion("NORTE"))
                && "ROJO".equals(semaforo.getEstadoPorDireccion("SUR"))
                && "ROJO".equals(semaforo.getEstadoPorDireccion("ESTE"))
                && "ROJO".equals(semaforo.getEstadoPorDireccion("OESTE"));
    }
}
