package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comparte el cálculo de rutas, la asignación de identificadores y el arranque
 * de vehículos entre el generador automático y los controles de la simulación.
 */
public class GestorVehiculos {
    private final Mapa mapa;
    private final List<Vehiculo> vehiculosVisuales;
    private final GestorMetricas metricas;
    private final ControlPausaSimulacion controlPausa;
    private final AtomicInteger secuenciaIds = new AtomicInteger();
    private final List<Thread> hilosVehiculos = new CopyOnWriteArrayList<>();
    private boolean cerrado;
    private final ExecutorService calculadorRutas = Executors.newCachedThreadPool(tarea -> {
        Thread hilo = new Thread(tarea, "CalculadorRutas");
        hilo.setDaemon(true);
        return hilo;
    });

    public GestorVehiculos(Mapa mapa, List<Vehiculo> vehiculosVisuales, GestorMetricas metricas) {
        this(mapa, vehiculosVisuales, metricas, null);
    }

    public GestorVehiculos(
            Mapa mapa,
            List<Vehiculo> vehiculosVisuales,
            GestorMetricas metricas,
            ControlPausaSimulacion controlPausa) {
        this.mapa = mapa;
        this.vehiculosVisuales = vehiculosVisuales;
        this.metricas = metricas;
        this.controlPausa = controlPausa;
    }

    public synchronized CompletableFuture<Boolean> crearVehiculoAleatorio(boolean adicional) {
        if (cerrado) {
            return CompletableFuture.completedFuture(false);
        }
        if (adicional && !metricas.registrarVehiculoAdicionalEsperado()) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                boolean creado = false;
                try {
                    if (estaCerrado()) {
                        return false;
                    }
                    List<PuntoGeneracion> spawns = mapa.getPuntosSpawn();
                    List<PuntoGeneracion> destinos = mapa.getPuntosDestino();
                    List<ParRuta> rutasPosibles = new ArrayList<>();
                    for (PuntoGeneracion origen : spawns) {
                        for (PuntoGeneracion destino : destinos) {
                            if (origen == destino) {
                                continue;
                            }
                            List<Celda> ruta = mapa.calcularRutaDijkstra(origen, destino);
                            if (ruta != null && ruta.size() >= 2) {
                                rutasPosibles.add(new ParRuta(ruta));
                            }
                        }
                    }

                    Collections.shuffle(rutasPosibles);
                    for (ParRuta ruta : rutasPosibles) {
                        if (iniciarVehiculo(ruta.celdas())) {
                            creado = true;
                            return true;
                        }
                    }
                    return false;
                } finally {
                    if (adicional && !creado) {
                        cancelarReservaSiAbierto();
                    }
                }
            }, calculadorRutas);
        } catch (RejectedExecutionException error) {
            if (adicional) {
                cancelarReservaSiAbierto();
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    public synchronized CompletableFuture<Boolean> crearVehiculoEnRuta(
            PuntoGeneracion origen,
            PuntoGeneracion destino,
            boolean adicional) {
        if (cerrado) {
            return CompletableFuture.completedFuture(false);
        }
        if (adicional && !metricas.registrarVehiculoAdicionalEsperado()) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                boolean creado = false;
                try {
                    if (estaCerrado()) {
                        return false;
                    }
                    if (origen == null || destino == null
                            || !origen.esSpawn()
                            || !destino.esDestino()
                            || origen == destino) {
                        return false;
                    }
                    List<Celda> ruta = mapa.calcularRutaDijkstra(origen, destino);
                    if (ruta == null || ruta.size() < 2) {
                        return false;
                    }
                    creado = iniciarVehiculo(ruta);
                    return creado;
                } finally {
                    if (adicional && !creado) {
                        cancelarReservaSiAbierto();
                    }
                }
            }, calculadorRutas);
        } catch (RejectedExecutionException error) {
            if (adicional) {
                cancelarReservaSiAbierto();
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<List<PuntoGeneracion>> obtenerDestinosAlcanzables(PuntoGeneracion origen) {
        synchronized (this) {
            if (cerrado) {
                return CompletableFuture.completedFuture(List.of());
            }
            try {
                return CompletableFuture.supplyAsync(() -> {
                    if (origen == null || !origen.esSpawn()) {
                        return List.of();
                    }
                    List<PuntoGeneracion> destinos = new ArrayList<>();
                    for (PuntoGeneracion destino : mapa.getPuntosDestino()) {
                        if (destino == origen) {
                            continue;
                        }
                        List<Celda> ruta = mapa.calcularRutaDijkstra(origen, destino);
                        if (ruta != null && ruta.size() >= 2) {
                            destinos.add(destino);
                        }
                    }
                    return List.copyOf(destinos);
                }, calculadorRutas);
            } catch (RejectedExecutionException error) {
                return CompletableFuture.completedFuture(List.of());
            }
        }
    }

    private synchronized boolean iniciarVehiculo(List<Celda> ruta) {
        if (cerrado) {
            return false;
        }
        String id = "V" + secuenciaIds.incrementAndGet();
        Vehiculo nuevo = new Vehiculo(id, ruta, 900, controlPausa);
        vehiculosVisuales.add(nuevo);
        Thread hiloVehiculo = new Thread(nuevo, "Vehiculo-" + nuevo.getId());
        hilosVehiculos.add(hiloVehiculo);
        hiloVehiculo.start();
        return true;
    }

    private synchronized boolean estaCerrado() {
        return cerrado;
    }

    private synchronized void cancelarReservaSiAbierto() {
        if (!cerrado) {
            metricas.cancelarVehiculoAdicionalEsperado();
        }
    }

    public synchronized void detener() {
        if (cerrado) {
            return;
        }
        cerrado = true;
        calculadorRutas.shutdown();
        hilosVehiculos.forEach(Thread::interrupt);
    }

    public void esperarDetencion() throws InterruptedException {
        while (!calculadorRutas.awaitTermination(1, TimeUnit.DAYS)) {
            // Las tareas de cálculo son finitas; sigue esperando a que terminen.
        }
        for (Thread hilo : hilosVehiculos) {
            hilo.join();
        }
    }

    private record ParRuta(List<Celda> celdas) { }
}
