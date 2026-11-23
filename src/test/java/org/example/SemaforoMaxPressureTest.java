package org.example;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class SemaforoMaxPressureTest {
    @Test
    void cambiaAlGrupoConMayorPresionDespuesDelVerdeMinimoYCompletaDespeje() throws Exception {
        Semaforo semaforo = semaforoDinamico(120, 140, 140, 1000);
        AtomicInteger presionNorte = new AtomicInteger(10);
        AtomicInteger presionEste = new AtomicInteger(0);
        semaforo.establecerEvaluadorPresion(fase ->
                fase.direccionesAbiertas().contains("NORTE")
                        ? presionNorte.get()
                        : presionEste.get());
        Thread controlador = iniciar(semaforo);

        try {
            esperarEstado(semaforo, "NORTE", "VERDE");
            presionNorte.set(0);
            presionEste.set(10);
            Thread.sleep(50);
            assertEquals("VERDE", semaforo.getEstadoPorDireccion("NORTE"),
                    "Una presión mayor no debe cortar el verde mínimo.");

            esperarEstado(semaforo, "NORTE", "AMARILLO");
            assertEquals("ROJO", semaforo.getEstadoPorDireccion("ESTE"));

            esperarCondicion(() -> todosEnRojo(semaforo));
            Thread.sleep(40);
            assertEquals("ROJO", semaforo.getEstadoPorDireccion("ESTE"),
                    "El grupo siguiente debe esperar el intervalo completo de rojo.");

            esperarEstado(semaforo, "ESTE", "VERDE");
            assertEquals("ROJO", semaforo.getEstadoPorDireccion("NORTE"));
        } finally {
            detener(controlador);
        }
    }

    @Test
    void usaLaColaDelMovimientoParaSeleccionarLaFaseDeMayorPresion() throws Exception {
        Semaforo semaforo = semaforoDinamico(600, 60, 60, 2000);
        Interseccion interseccion = new Interseccion(1, 2, semaforo);
        PuntoGeneracion origen = new PuntoGeneracion(1, 0);
        CalleUnSentido entradaEste = new CalleUnSentido(1, 1, "ESTE");
        CalleUnSentido salidaEste = new CalleUnSentido(1, 3, "ESTE");
        CalleUnSentido entradaNorte = new CalleUnSentido(2, 2, "NORTE");
        CalleUnSentido salidaNorte = new CalleUnSentido(0, 2, "NORTE");
        interseccion.configurarCeldasAdyacentes(Map.of(
                "OESTE", entradaEste,
                "ESTE", salidaEste,
                "SUR", entradaNorte,
                "NORTE", salidaNorte));
        Vehiculo bloqueoAguasAbajo = new Vehiculo("V-BLOQUEO", List.of(), 20);
        salidaEste.entrar(bloqueoAguasAbajo);

        Thread controlador = iniciar(semaforo);
        Vehiculo vehiculo = new Vehiculo(
                "V-ESTE",
                List.of(origen, entradaEste, interseccion, salidaEste),
                20);
        Thread hiloVehiculo = new Thread(vehiculo, "TestVehiculoMaxPressure");
        hiloVehiculo.setDaemon(true);

        try {
            esperarEstado(semaforo, "NORTE", "VERDE");
            hiloVehiculo.start();
            esperarCondicion(() -> interseccion.getVehiculosEnEspera() > 0);
            assertEquals(0, interseccion.calcularPresion(
                    FaseSemaforo.conDuracionDinamica(Set.of("ESTE"))));
            assertEquals(-1, interseccion.calcularPresion(
                    FaseSemaforo.conDuracionDinamica(Set.of("NORTE"))));
            esperarEstado(semaforo, "ESTE", "VERDE");
        } finally {
            hiloVehiculo.interrupt();
            hiloVehiculo.join(1000);
            detener(controlador);
            salidaEste.salir(bloqueoAguasAbajo);
        }
    }

    @Test
    void enEmpateMantieneElVerdeHastaElMaximoYDespuesRotaDeFase() throws Exception {
        Semaforo semaforo = semaforoDinamico(40, 30, 30, 280);
        semaforo.establecerEvaluadorPresion(fase -> 0);
        Thread controlador = iniciar(semaforo);

        try {
            esperarEstado(semaforo, "NORTE", "VERDE");
            esperarEstado(semaforo, "NORTE", "AMARILLO");
            esperarEstado(semaforo, "ESTE", "VERDE");
        } finally {
            detener(controlador);
        }
    }

    @Test
    void elAvanceManualTambienTienePrioridadEnModoDinamico() throws Exception {
        Semaforo semaforo = semaforoDinamico(500, 80, 80, 1500);
        semaforo.establecerEvaluadorPresion(fase -> 0);
        Thread controlador = iniciar(semaforo);

        try {
            esperarEstado(semaforo, "NORTE", "VERDE");
            var cambio = semaforo.solicitarAvanceManual();
            esperarEstado(semaforo, "NORTE", "AMARILLO");
            assertFalse(cambio.isDone());
            esperarCondicion(() -> todosEnRojo(semaforo));
            assertEquals(1, cambio.get(2, TimeUnit.SECONDS));
            assertEquals("VERDE", semaforo.getEstadoPorDireccion("ESTE"));
        } finally {
            detener(controlador);
        }
    }

    @Test
    void cuentaOcupacionAguasAbajoSoloEnLaDireccionDeLaCalle() throws Exception {
        CalleUnSentido calle = new CalleUnSentido(0, 0, "ESTE");
        Vehiculo vehiculo = new Vehiculo("V1", List.of(), 10);

        calle.entrar(vehiculo);
        try {
            assertEquals(1, calle.contarVehiculosEnDireccion("ESTE"));
            assertEquals(0, calle.contarVehiculosEnDireccion("OESTE"));
        } finally {
            calle.salir(vehiculo);
        }
        assertEquals(0, calle.contarVehiculosEnDireccion("ESTE"));
    }

    @Test
    void cuentaOcupacionEnElCarrilCorrectoDeUnaCalleDeDobleSentido() throws Exception {
        CalleDobleSentido calle = new CalleDobleSentido(0, 0, "HORIZONTAL");
        Vehiculo vehiculo = new Vehiculo("V-ESTE", List.of(), 10);

        calle.entrar(vehiculo);
        try {
            assertEquals(1, calle.contarVehiculosEnDireccion("ESTE"));
            assertEquals(0, calle.contarVehiculosEnDireccion("OESTE"));
        } finally {
            calle.salir(vehiculo);
        }
    }

    private Semaforo semaforoDinamico(long minVerdeMs, long amarilloMs, long rojoMs, long maxVerdeMs) {
        return new Semaforo(List.of(
                FaseSemaforo.conDuracionDinamica(Set.of("NORTE")),
                FaseSemaforo.conDuracionDinamica(Set.of("ESTE"))
        ), amarilloMs, rojoMs, minVerdeMs, maxVerdeMs);
    }

    private Thread iniciar(Semaforo semaforo) {
        Thread controlador = new Thread(semaforo, "TestSemaforoMaxPressure");
        controlador.setDaemon(true);
        controlador.start();
        return controlador;
    }

    private void detener(Thread controlador) throws InterruptedException {
        controlador.interrupt();
        controlador.join(1000);
        assertFalse(controlador.isAlive(), "El controlador debe detenerse al interrumpirlo.");
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
        return List.of("NORTE", "SUR", "ESTE", "OESTE").stream()
                .allMatch(direccion -> "ROJO".equals(semaforo.getEstadoPorDireccion(direccion)));
    }
}
