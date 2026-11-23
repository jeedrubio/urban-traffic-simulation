package org.example;

import java.util.concurrent.ExecutionException;

public class GeneradorTrafico implements Runnable {
    private final ConfiguracionSimulacion config;
    private final GestorVehiculos gestorVehiculos;
    private final ControlPausaSimulacion controlPausa;

    public GeneradorTrafico(GestorVehiculos gestorVehiculos, ConfiguracionSimulacion config) {
        this(gestorVehiculos, config, null);
    }

    public GeneradorTrafico(
            GestorVehiculos gestorVehiculos,
            ConfiguracionSimulacion config,
            ControlPausaSimulacion controlPausa) {
        this.gestorVehiculos = gestorVehiculos;
        this.config = config;
        this.controlPausa = controlPausa;
    }

    @Override
    public void run() {
        int vehiculosGenerados = 0;
        try {
            while (!Thread.currentThread().isInterrupted()
                    && vehiculosGenerados < config.getCantidadVehiculos()) {
                if (controlPausa == null) {
                    Thread.sleep(2000);
                } else if (!controlPausa.esperarActivo(2000)) {
                    break;
                }
                if (controlPausa != null && !controlPausa.esperarActiva()) {
                    break;
                }
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
