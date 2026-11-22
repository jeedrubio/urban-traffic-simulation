package org.example;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneradorTrafico implements Runnable {
    private final Mapa mapa;
    private final List<Vehiculo> vehiculosVisuales;
    private final ConfiguracionSimulacion config;
    private final Random rand = new Random();
    private final AtomicInteger vehiculosGenerados = new AtomicInteger(0);

    private final ExecutorService calculadorRutas = Executors.newCachedThreadPool();

    public GeneradorTrafico(Mapa mapa, List<Vehiculo> vehiculosVisuales) {
        this(mapa, vehiculosVisuales, new ConfiguracionSimulacion());
    }

    public GeneradorTrafico(Mapa mapa, List<Vehiculo> vehiculosVisuales, ConfiguracionSimulacion config) {
        this.mapa = mapa;
        this.vehiculosVisuales = vehiculosVisuales;
        this.config = config;
    }

    private String calcularDireccionSalida(Celda origen, Celda siguiente) {
        if (siguiente.x < origen.x) return "NORTE";
        if (siguiente.x > origen.x) return "SUR";
        if (siguiente.y > origen.y) return "ESTE";
        return "OESTE";
    }

    @Override
    public void run() {
        try {
            List<PuntoGeneracion> spawns = mapa.getPuntosSpawn();
            if (spawns.size() < 2) {
                System.err.println("Error: Se requieren al menos 2 puntos de generación (PuntoGeneracion).");
                return;
            }

            while (!Thread.currentThread().isInterrupted()
                    && vehiculosGenerados.get() < config.getCantidadVehiculos()) {

                Thread.sleep(2000);

                // Determinar punto de partida (fijo o aleatorio)
                PuntoGeneracion origen;
                if (!config.isOrigenAleatorio() && config.getCoordOrigen() != null) {
                    origen = mapa.buscarSpawnPorCoordenada(config.getCoordOrigen()[0], config.getCoordOrigen()[1]);
                } else {
                    // Si el destino es fijo, evitar elegir ese mismo punto como origen
                    PuntoGeneracion destinoFijo = (!config.isDestinoAleatorio() && config.getCoordDestino() != null)
                            ? mapa.buscarSpawnPorCoordenada(config.getCoordDestino()[0], config.getCoordDestino()[1])
                            : null;
                    do {
                        origen = spawns.get(rand.nextInt(spawns.size()));
                    } while (origen == destinoFijo);
                }

                // Determinar punto de llegada (fijo o aleatorio, siempre distinto al origen)
                PuntoGeneracion destinoTemp;
                if (!config.isDestinoAleatorio() && config.getCoordDestino() != null) {
                    destinoTemp = mapa.buscarSpawnPorCoordenada(config.getCoordDestino()[0], config.getCoordDestino()[1]);
                } else {
                    do {
                        destinoTemp = spawns.get(rand.nextInt(spawns.size()));
                    } while (origen == destinoTemp);
                }

                if (origen == null || destinoTemp == null || origen == destinoTemp) {
                    continue;
                }

                final PuntoGeneracion origenFinal = origen;
                final PuntoGeneracion destinoFinal = destinoTemp;

                calculadorRutas.submit(() -> {
                    List<Celda> rutaOptima = mapa.calcularRutaDijkstra(origenFinal, destinoFinal);

                    if (rutaOptima != null && rutaOptima.size() >= 2) {
                        Celda primeraCalle = rutaOptima.get(1);
                        String dirSalida = calcularDireccionSalida(origenFinal, primeraCalle);

                        synchronized (origenFinal) {
                            if (vehiculosGenerados.get() >= config.getCantidadVehiculos()) {
                                return;
                            }
                            if (origenFinal.estaOcupada() || !primeraCalle.tieneEspacioEntrada(dirSalida)) {
                                return; // Espera al siguiente ciclo si el spawn o la calle inicial están llenos
                            }

                            int numeroActual = vehiculosGenerados.incrementAndGet();
                            String idVehiculo = "V" + numeroActual;
                            Vehiculo nuevoAuto = new Vehiculo(idVehiculo, rutaOptima, 900);
                            vehiculosVisuales.add(nuevoAuto);
                            new Thread(nuevoAuto).start();
                        }
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            calculadorRutas.shutdown();
        }
    }
}