package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comparte el cálculo de rutas, la asignación de identificadores y el arranque
 * de vehículos entre el generador automático y los controles de la simulación.
 */
public class GestorVehiculos {
    private final Mapa mapa;
    private final List<Vehiculo> vehiculosVisuales;
    private final GestorMetricas metricas;
    private final AtomicInteger secuenciaIds = new AtomicInteger();
    private final ExecutorService calculadorRutas = Executors.newCachedThreadPool(tarea -> {
        Thread hilo = new Thread(tarea, "CalculadorRutas");
        hilo.setDaemon(true);
        return hilo;
    });

    public GestorVehiculos(Mapa mapa, List<Vehiculo> vehiculosVisuales, GestorMetricas metricas) {
        this.mapa = mapa;
        this.vehiculosVisuales = vehiculosVisuales;
        this.metricas = metricas;
    }

    public CompletableFuture<Boolean> crearVehiculoAleatorio(boolean adicional) {
        if (adicional && !metricas.registrarVehiculoAdicionalEsperado()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            boolean creado = false;
            try {
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
                    metricas.cancelarVehiculoAdicionalEsperado();
                }
            }
        }, calculadorRutas);
    }

    public CompletableFuture<Boolean> crearVehiculoEnRuta(
            PuntoGeneracion origen,
            PuntoGeneracion destino,
            boolean adicional) {
        if (adicional && !metricas.registrarVehiculoAdicionalEsperado()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            boolean creado = false;
            try {
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
                    metricas.cancelarVehiculoAdicionalEsperado();
                }
            }
        }, calculadorRutas);
    }

    public CompletableFuture<List<PuntoGeneracion>> obtenerDestinosAlcanzables(PuntoGeneracion origen) {
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
    }

    private boolean iniciarVehiculo(List<Celda> ruta) {
        Vehiculo nuevo = new Vehiculo("V" + secuenciaIds.incrementAndGet(), ruta, 900);
        vehiculosVisuales.add(nuevo);
        Thread hiloVehiculo = new Thread(nuevo, "Vehiculo-" + nuevo.getId());
        hiloVehiculo.start();
        return true;
    }

    private record ParRuta(List<Celda> celdas) { }
}
