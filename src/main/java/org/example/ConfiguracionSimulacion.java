package org.example;

import java.util.Map;

public class ConfiguracionSimulacion {
    // Valores por defecto establecidos originalmente en el proyecto
    private int cantidadVehiculos = 50; // Rango válido: 20 - 200
    private int duracionVerdeSeg = 5;
    private int duracionAmarilloSeg = 2;
    private int duracionRojoSeg = 1; // Pausa de seguridad en rojo antes de cambiar de eje
    private Map<String, ConfiguracionSemaforo> configuracionesSemaforo = Map.of();

    private boolean origenAleatorio = true;
    private int[] coordOrigen = null; // [fila, col]

    private boolean destinoAleatorio = true;
    private int[] coordDestino = null; // [fila, col]

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

    public boolean isOrigenAleatorio() { return origenAleatorio; }
    public void setOrigenAleatorio(boolean origenAleatorio) { this.origenAleatorio = origenAleatorio; }

    public int[] getCoordOrigen() { return coordOrigen; }
    public void setCoordOrigen(int[] coordOrigen) { this.coordOrigen = coordOrigen; }

    public boolean isDestinoAleatorio() { return destinoAleatorio; }
    public void setDestinoAleatorio(boolean destinoAleatorio) { this.destinoAleatorio = destinoAleatorio; }

    public int[] getCoordDestino() { return coordDestino; }
    public void setCoordDestino(int[] coordDestino) { this.coordDestino = coordDestino; }

    public ConfiguracionSimulacion copiar() {
        ConfiguracionSimulacion copia = new ConfiguracionSimulacion();
        copia.setCantidadVehiculos(cantidadVehiculos);
        copia.setDuracionVerdeSeg(duracionVerdeSeg);
        copia.setDuracionAmarilloSeg(duracionAmarilloSeg);
        copia.setDuracionRojoSeg(duracionRojoSeg);
        copia.setConfiguracionesSemaforo(configuracionesSemaforo);
        copia.setOrigenAleatorio(origenAleatorio);
        copia.setCoordOrigen(coordOrigen == null ? null : coordOrigen.clone());
        copia.setDestinoAleatorio(destinoAleatorio);
        copia.setCoordDestino(coordDestino == null ? null : coordDestino.clone());
        return copia;
    }
}
