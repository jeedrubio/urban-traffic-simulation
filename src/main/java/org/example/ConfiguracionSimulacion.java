package org.example;

import java.util.Map;

public class ConfiguracionSimulacion {
    // Valores por defecto establecidos originalmente en el proyecto
    private int cantidadVehiculos = 50; // Rango válido: 20 - 200
    private int duracionVerdeSeg = 5;
    private int duracionAmarilloSeg = 2;
    private int duracionRojoSeg = 1; // Pausa de seguridad en rojo antes de cambiar de eje
    private Map<String, ConfiguracionSemaforo> configuracionesSemaforo = Map.of();

    public int getCantidadVehiculos() { return cantidadVehiculos; }
    public void setCantidadVehiculos(int cantidadVehiculos) { this.cantidadVehiculos = cantidadVehiculos; }

    public int getDuracionVerdeSeg() { return duracionVerdeSeg; }
    public void setDuracionVerdeSeg(int duracionVerdeSeg) { this.duracionVerdeSeg = duracionVerdeSeg; }

    public int getDuracionAmarilloSeg() { return duracionAmarilloSeg; }
    public void setDuracionAmarilloSeg(int duracionAmarilloSeg) { this.duracionAmarilloSeg = duracionAmarilloSeg; }

    public int getDuracionRojoSeg() { return duracionRojoSeg; }
    public void setDuracionRojoSeg(int duracionRojoSeg) { this.duracionRojoSeg = duracionRojoSeg; }

    public Map<String, ConfiguracionSemaforo> getConfiguracionesSemaforo() {
        return configuracionesSemaforo;
    }
    public void setConfiguracionesSemaforo(Map<String, ConfiguracionSemaforo> configuracionesSemaforo) {
        this.configuracionesSemaforo = configuracionesSemaforo == null
                ? Map.of()
                : Map.copyOf(configuracionesSemaforo);
    }

    public ConfiguracionSimulacion copiar() {
        ConfiguracionSimulacion copia = new ConfiguracionSimulacion();
        copia.setCantidadVehiculos(cantidadVehiculos);
        copia.setDuracionVerdeSeg(duracionVerdeSeg);
        copia.setDuracionAmarilloSeg(duracionAmarilloSeg);
        copia.setDuracionRojoSeg(duracionRojoSeg);
        copia.setConfiguracionesSemaforo(configuracionesSemaforo);
        return copia;
    }
}
