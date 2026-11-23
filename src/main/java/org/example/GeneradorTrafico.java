package org.example;

import java.util.concurrent.ExecutionException;

public class GeneradorTrafico implements Runnable {
    private final ConfiguracionSimulacion config;
    private final GestorVehiculos gestorVehiculos;

    public GeneradorTrafico(GestorVehiculos gestorVehiculos, ConfiguracionSimulacion config) {
        this.gestorVehiculos = gestorVehiculos;
        this.config = config;
    }

    @Override
    public void run() {
        int vehiculosGenerados = 0;
        try {
            while (!Thread.currentThread().isInterrupted()
                    && vehiculosGenerados < config.getCantidadVehiculos()) {
                Thread.sleep(2000);
                try {
                    if (gestorVehiculos.crearVehiculoAleatorio(false).get()) {
                        vehiculosGenerados++;
                    }
                } catch (ExecutionException e) {
                    System.err.println("No se pudo crear un vehículo automático: " + e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
