package org.example;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// En este caso se ha utilizado un ThreadPool para el calculo de las rutas, se hace uso de cuantos hilos se tengan
//disponibles y se guardan, para que el calculo de rutas (proceso exhaustivo) sea dinámico y no tenga
// que estar construyendo y eliminando hilos
//Ademas los generadores tienen dos espacios, uno donde generan el vehiculo y el otro donde
//reciben al carro de destino

public class GeneradorTrafico implements Runnable {
    private final Mapa mapa;
    private final List<Vehiculo> vehiculosVisuales;
    private final Random rand = new Random();
    private int idContador = 1;

    private final ExecutorService calculadorRutas = Executors.newCachedThreadPool();

    public GeneradorTrafico(Mapa mapa, List<Vehiculo> vehiculosVisuales) {
        this.mapa = mapa;
        this.vehiculosVisuales = vehiculosVisuales;
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
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(2000);  // Se duerme 2 segundos, es decir que se ejecutara cada 3 segundos este código, es decir, generará un vehículo

                List<PuntoGeneracion> spawns = mapa.getPuntosSpawn();
                if (spawns.size() >= 2) { //Si existen por lo menos 2 puntos de generación, elige uno al azar para generar el vehículo
                    PuntoGeneracion origen = spawns.get(rand.nextInt(spawns.size()));
                    PuntoGeneracion destinoTemp;

                    do {
                        destinoTemp = spawns.get(rand.nextInt(spawns.size()));  //Elige un destino que sea diferente al origen, para que calculle la ruta.
                    } while (origen == destinoTemp);

                    final PuntoGeneracion origenFinal = origen;
                    final PuntoGeneracion destinoFinal = destinoTemp;


                    // Delegar el algoritmo de Dijsktra al ThreadPool en paralelo
                    calculadorRutas.submit(() -> {
                        // Usa los datos para calcular una ruta con Dijsktra
                        List<Celda> rutaOptima = mapa.calcularRutaDijkstra(origenFinal, destinoFinal);

                        if (rutaOptima != null && rutaOptima.size() >= 2) {
                            Celda primeraCalle = rutaOptima.get(1);
                            String dirSalida = calcularDireccionSalida(origenFinal, primeraCalle);

                            // Confirmar que ni el Spawn ni la calle frente a él estén llenos/ocupados
                            synchronized (origenFinal) {
                                if (origenFinal.estaOcupada() || !primeraCalle.tieneEspacioEntrada(dirSalida)) {
                                    return; // No genera el auto hasta que se libere el espacio
                                }

                                String idVehiculo = "V" + idContador++;  //Guarda los datos del id dell vehiculo, su origen y destino
                                // esto con FINAL para que sean invariables y no cambien .
                                Vehiculo nuevoAuto = new Vehiculo(idVehiculo, rutaOptima, 900);
                                vehiculosVisuales.add(nuevoAuto);
                                new Thread(nuevoAuto).start();
                            }
                        }
                    });
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            calculadorRutas.shutdown();
        }
    }
}