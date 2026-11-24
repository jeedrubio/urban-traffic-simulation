package org.example;

import java.util.concurrent.TimeUnit;

/**
 * Coordinador compartido para pausar, reanudar y terminar una ejecución.
 * El reloj simulado deja de avanzar mientras el control está pausado. Todos
 * los cambios de estado y las esperas se sincronizan con este objeto.
 */
public final class ControlPausaSimulacion {
    // El estado y los acumulados se leen y modifican bajo el monitor de esta instancia.
    private boolean pausada;
    private boolean finalizada;
    private long inicioPausaNanos;
    private long tiempoPausadoNanos;

    public synchronized void pausar() {
        if (!pausada && !finalizada) {
            pausada = true;
            inicioPausaNanos = System.nanoTime();
            // Despierta tareas que necesitan volver a comprobar el estado de la simulación.
            notifyAll();
        }
    }

    public synchronized void reanudar() {
        if (pausada && !finalizada) {
            // Se descuenta el intervalo completo para conservar el tiempo restante de cada espera.
            tiempoPausadoNanos += System.nanoTime() - inicioPausaNanos;
            pausada = false;
            notifyAll();
        }
    }

    public synchronized void finalizar() {
        if (!finalizada) {
            if (pausada) {
                // También se contabiliza la pausa abierta para dejar el reloj simulado estable.
                tiempoPausadoNanos += System.nanoTime() - inicioPausaNanos;
            }
            pausada = false;
            finalizada = true;
            // Las tareas bloqueadas deben salir al terminar, no quedarse esperando una reanudación.
            notifyAll();
        }
    }

    public synchronized boolean estaPausada() {
        return pausada;
    }

    public synchronized boolean estaFinalizada() {
        return finalizada;
    }

    /** Devuelve el instante del reloj simulado, descontando todo el tiempo pausado. */
    public synchronized long ahoraNanos() {
        long ahora = System.nanoTime();
        long pausaActual = pausada ? ahora - inicioPausaNanos : 0;
        return ahora - tiempoPausadoNanos - pausaActual;
    }

    public synchronized long ahoraMillis() {
        return TimeUnit.NANOSECONDS.toMillis(ahoraNanos());
    }

    /**
     * Espera hasta reanudar. Devuelve false si la simulación fue terminada.
     */
    public synchronized boolean esperarActiva() throws InterruptedException {
        while (pausada && !finalizada) {
            wait();
        }
        return !finalizada;
    }

    /**
     * Ejecuta una acción mientras la ejecución no haya terminado. La exclusión
     * evita que un vehículo registre su llegada después de cerrar la simulación.
     */
    synchronized boolean ejecutarSiNoFinalizada(Runnable accion) {
        if (finalizada) {
            return false;
        }
        accion.run();
        return true;
    }

    /**
     * Espera una duración de tiempo simulado, conservando el tiempo restante
     * mientras la simulación está pausada.
     */
    public synchronized boolean esperarActivo(long duracionMs) throws InterruptedException {
        if (duracionMs < 0) {
            throw new IllegalArgumentException("La duración no puede ser negativa.");
        }
        // El plazo se calcula con tiempo simulado, por lo que la pausa no consume duración.
        long limite = ahoraNanos() + TimeUnit.MILLISECONDS.toNanos(duracionMs);
        while (!finalizada) {
            while (pausada && !finalizada) {
                wait();
            }
            if (finalizada) {
                return false;
            }

            long restante = limite - ahoraNanos();
            if (restante <= 0) {
                return true;
            }
            TimeUnit.NANOSECONDS.timedWait(
                    this,
                    Math.min(restante, TimeUnit.MILLISECONDS.toNanos(100)));
        }
        return false;
    }
}
