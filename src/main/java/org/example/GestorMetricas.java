package org.example;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recolector thread-safe de métricas de la simulación (patrón Singleton).
 * Evita condiciones de carrera usando CopyOnWriteArrayList y contadores atómicos.
 */
public class GestorMetricas {

    private static final GestorMetricas INSTANCIA = new GestorMetricas();

    private final List<RegistroVehiculo> registros = new CopyOnWriteArrayList<>();
    private final List<Interseccion> intersecciones = new CopyOnWriteArrayList<>();
    private final AtomicInteger contadorLlegadas = new AtomicInteger(0);
    private final AtomicBoolean reporteFinalEmitido = new AtomicBoolean(false);

    private volatile int totalVehiculosEsperados = 0;
    private volatile Runnable alFinalizarSimulacion;

    private GestorMetricas() { }

    public static GestorMetricas getInstancia() {
        return INSTANCIA;
    }

    public synchronized void reiniciar(int totalVehiculos) {
        registros.clear();
        intersecciones.clear();
        contadorLlegadas.set(0);
        reporteFinalEmitido.set(false);
        this.totalVehiculosEsperados = totalVehiculos;
        this.alFinalizarSimulacion = null;
    }

    /**
     * Incluye un vehículo adicional solicitado durante la simulación.
     * Devuelve false si el reporte final ya se emitió.
     */
    public synchronized boolean registrarVehiculoAdicionalEsperado() {
        if (reporteFinalEmitido.get() || totalVehiculosEsperados <= 0) {
            return false;
        }
        totalVehiculosEsperados++;
        return true;
    }

    /**
     * Libera una reserva de vehículo adicional que no pudo crearse.
     */
    public void cancelarVehiculoAdicionalEsperado() {
        Runnable callback = null;
        boolean emitirReporte = false;
        synchronized (this) {
            if (reporteFinalEmitido.get()
                    || totalVehiculosEsperados <= contadorLlegadas.get()) {
                return;
            }
            totalVehiculosEsperados--;
            if (contadorLlegadas.get() >= totalVehiculosEsperados
                    && reporteFinalEmitido.compareAndSet(false, true)) {
                emitirReporte = true;
                callback = alFinalizarSimulacion;
            }
        }
        if (emitirReporte) {
            imprimirReporteConsola();
            if (callback != null) {
                callback.run();
            }
        }
    }

    public boolean estaFinalizada() {
        return reporteFinalEmitido.get();
    }

    public void setAlFinalizarSimulacion(Runnable callback) {
        this.alFinalizarSimulacion = callback;
    }

    public void registrarInterseccion(Interseccion interseccion) {
        if (interseccion != null && !intersecciones.contains(interseccion)) {
            intersecciones.add(interseccion);
        }
    }

    public void registrarLlegada(Vehiculo v) {
        Runnable callback = null;
        boolean emitirReporte = false;
        synchronized (this) {
            int orden = contadorLlegadas.incrementAndGet();
            RegistroVehiculo registro = new RegistroVehiculo(
                    orden,
                    v.getId(),
                    v.getCoordenadaOrigen(),
                    v.getCoordenadaDestino(),
                    v.getTiempoTotalMs(),
                    v.getTiempoEsperaSemaforoMs(),
                    v.getPorcentajeEspera()
            );
            registros.add(registro);

            // El alta de autos y la última llegada se serializan para evitar finalizar
            // mientras se está aceptando un vehículo adicional.
            if (totalVehiculosEsperados > 0
                    && orden >= totalVehiculosEsperados
                    && reporteFinalEmitido.compareAndSet(false, true)) {
                emitirReporte = true;
                callback = alFinalizarSimulacion;
            }
        }
        emitirReporteFinal(emitirReporte, callback);
    }

    private void emitirReporteFinal(boolean emitirReporte, Runnable callback) {
        if (emitirReporte) {
            imprimirReporteConsola();
            if (callback != null) {
                callback.run();
            }
        }
    }

    public List<RegistroVehiculo> getRegistros() { return registros; }
    public List<Interseccion> getIntersecciones() { return intersecciones; }
    public int getTotalLlegados() { return contadorLlegadas.get(); }
    public int getTotalEsperados() { return totalVehiculosEsperados; }

    public RegistroVehiculo getPrimeroEnLlegar() {
        return registros.stream()
                .min(Comparator.comparingInt(RegistroVehiculo::getOrdenLlegada))
                .orElse(null);
    }

    public double getTiempoPromedioViajeSeg() {
        if (registros.isEmpty()) return 0.0;
        long sumaMs = 0;
        for (RegistroVehiculo r : registros) {
            sumaMs += r.getTiempoTotalMs();
        }
        return (sumaMs / (double) registros.size()) / 1000.0;
    }

    public double getPorcentajePromedioEspera() {
        if (registros.isEmpty()) return 0.0;
        double suma = 0.0;
        for (RegistroVehiculo r : registros) {
            suma += r.getPorcentajeEspera();
        }
        return suma / registros.size();
    }

    public Interseccion getInterseccionMasCongestionada() {
        return intersecciones.stream()
                .max(Comparator.comparingInt(Interseccion::getMaxCongestion))
                .orElse(null);
    }

    public void imprimirReporteConsola() {
        System.out.println("\n===============================================================================");
        System.out.println("                 REPORTE FINAL DE METRICAS DE SIMULACION                       ");
        System.out.println("===============================================================================");

        RegistroVehiculo primero = getPrimeroEnLlegar();
        if (primero != null) {
            System.out.printf(Locale.US, "Vehículo que llegó primero : %s (%.2f s) | Ruta: %s -> %s%n",
                    primero.getId(), primero.getTiempoTotalSeg(), primero.getOrigen(), primero.getDestino());
        }
        System.out.printf(Locale.US, "Vehículos completados      : %d / %d%n",
                getTotalLlegados(), totalVehiculosEsperados);
        System.out.printf(Locale.US, "Tiempo promedio de viaje   : %.2f s%n", getTiempoPromedioViajeSeg());
        System.out.printf(Locale.US, "Espera promedio semáforos  : %.1f%% del viaje detenido%n", getPorcentajePromedioEspera());

        Interseccion masCongestionada = getInterseccionMasCongestionada();
        if (masCongestionada != null) {
            System.out.printf("Mayor congestión detectada : Intersección %s (Pico de %d vehículos en espera)%n",
                    masCongestionada.getCoordenadaTexto(), masCongestionada.getMaxCongestion());
        }

        System.out.println("\n--- TABLA DE RESULTADOS POR VEHÍCULO (ORDEN DE LLEGADA) ---");
        System.out.printf("%-6s %-8s %-18s %-14s %-16s %-12s%n",
                "Orden", "ID", "Origen -> Destino", "T. Total (s)", "Espera Sem. (s)", "% Detenido");
        System.out.println("-------------------------------------------------------------------------------");
        for (RegistroVehiculo r : registros) {
            System.out.printf(Locale.US, "%-6d %-8s %-18s %-14.2f %-16.2f %-11.1f%%%n",
                    r.getOrdenLlegada(),
                    r.getId(),
                    r.getOrigen() + "->" + r.getDestino(),
                    r.getTiempoTotalSeg(),
                    r.getTiempoEsperaSeg(),
                    r.getPorcentajeEspera());
        }

        System.out.println("\n--- CONGESTIÓN POR INTERSECCIÓN ---");
        System.out.printf("%-16s %-18s %-18s %-15s%n",
                "Intersección", "En Espera Actual", "Congestión Máx.", "Total Cruces");
        System.out.println("-------------------------------------------------------------------------------");
        for (Interseccion inter : intersecciones) {
            System.out.printf("%-16s %-18d %-18d %-15d%n",
                    inter.getCoordenadaTexto(),
                    inter.getVehiculosEnEspera(),
                    inter.getMaxCongestion(),
                    inter.getTotalCruces());
        }
        System.out.println("===============================================================================\n");
    }

    public static class RegistroVehiculo {
        private final int ordenLlegada;
        private final String id;
        private final String origen;
        private final String destino;
        private final long tiempoTotalMs;
        private final long tiempoEsperaSemaforoMs;
        private final double porcentajeEspera;

        public RegistroVehiculo(int ordenLlegada, String id, String origen, String destino,
                                long tiempoTotalMs, long tiempoEsperaSemaforoMs, double porcentajeEspera) {
            this.ordenLlegada = ordenLlegada;
            this.id = id;
            this.origen = origen;
            this.destino = destino;
            this.tiempoTotalMs = tiempoTotalMs;
            this.tiempoEsperaSemaforoMs = tiempoEsperaSemaforoMs;
            this.porcentajeEspera = porcentajeEspera;
        }

        public int getOrdenLlegada() { return ordenLlegada; }
        public String getId() { return id; }
        public String getOrigen() { return origen; }
        public String getDestino() { return destino; }
        public long getTiempoTotalMs() { return tiempoTotalMs; }
        public long getTiempoEsperaSemaforoMs() { return tiempoEsperaSemaforoMs; }
        public double getTiempoTotalSeg() { return tiempoTotalMs / 1000.0; }
        public double getTiempoEsperaSeg() { return tiempoEsperaSemaforoMs / 1000.0; }
        public double getPorcentajeEspera() { return porcentajeEspera; }
    }
}
