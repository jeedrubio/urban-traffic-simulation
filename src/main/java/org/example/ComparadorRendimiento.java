package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class ComparadorRendimiento {
    private Mapa mapa;
    private int numeroVehiculos;

    public ComparadorRendimiento(Mapa mapa, int numeroVehiculos) {
        this.mapa = mapa;
        this.numeroVehiculos = numeroVehiculos;
    }

    public void ejecutarPrueba() {
        System.out.println("--- INICIANDO BENCHMARK DE RUTAS (" + numeroVehiculos + " vehículos) ---");
        List<PuntoGeneracion> spawns = mapa.getPuntosSpawn();
        List<PuntoGeneracion> destinosDisponibles = mapa.getPuntosDestino();
        List<ParRuta> paresAlcanzables = new ArrayList<>();
        for (PuntoGeneracion origen : spawns) {
            for (PuntoGeneracion destino : destinosDisponibles) {
                if (origen != destino && mapa.calcularRutaDijkstra(origen, destino) != null) {
                    paresAlcanzables.add(new ParRuta(origen, destino));
                }
            }
        }
        if (paresAlcanzables.isEmpty()) {
            System.out.println("No hay rutas alcanzables entre puntos de generación y destinos.");
            return;
        }

        // 1. Preparar pares aleatorios de origen y destino
        Random rand = new Random();
        List<PuntoGeneracion> origenes = new ArrayList<>();
        List<PuntoGeneracion> destinos = new ArrayList<>();

        for (int i = 0; i < numeroVehiculos; i++) {
            ParRuta par = paresAlcanzables.get(rand.nextInt(paresAlcanzables.size()));
            origenes.add(par.origen());
            destinos.add(par.destino());
        }

        // 2. EJECUCIÓN SECUENCIAL
        long inicioSecuencial = System.currentTimeMillis();
        for (int i = 0; i < numeroVehiculos; i++) {
            mapa.calcularRutaDijkstra(origenes.get(i), destinos.get(i));
        }
        long finSecuencial = System.currentTimeMillis();
        long tiempoSecuencial = finSecuencial - inicioSecuencial;

        // 3. EJECUCIÓN PARALELA (Thread Pool)
        long inicioParalelo = System.currentTimeMillis();
        // Usamos la cantidad de hilos lógicos disponibles en el procesador de tu computadora
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Callable<List<Celda>>> tareas = new ArrayList<>();
        for (int i = 0; i < numeroVehiculos; i++) {
            final PuntoGeneracion orig = origenes.get(i);
            final PuntoGeneracion dest = destinos.get(i);
            tareas.add(() -> mapa.calcularRutaDijkstra(orig, dest)); // Asignamos la tarea
        }

        try {
            // invokeAll lanza todas las peticiones al Thread Pool al mismo tiempo y espera que terminen
            executor.invokeAll(tareas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown(); // Cerramos el pool
        }

        long finParalelo = System.currentTimeMillis();
        long tiempoParalelo = finParalelo - inicioParalelo;

        // 4. Mostrar Resultados de la Comparativa
        System.out.println("Tiempos de Planificación de Rutas (Dijkstra):");
        System.out.println(" > Ejecución Secuencial: " + tiempoSecuencial + " ms");
        System.out.println(" > Ejecución Paralela: " + tiempoParalelo + " ms");
        System.out.println("---------------------------------------------------------");
    }

    private record ParRuta(PuntoGeneracion origen, PuntoGeneracion destino) {}
}
