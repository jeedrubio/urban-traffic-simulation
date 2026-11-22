package org.example;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.Locale;

/**
 * Ventana JavaFX para visualizar el resumen final y tablas de métricas.
 */
public class VentanaMetricasFX {

    public static void mostrar(GestorMetricas gestor) {
        Stage stage = new Stage();
        stage.setTitle("Resultados y Métricas de la Simulación");

        VBox root = new VBox(12);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: #232730;");

        Label titulo = new Label("Reporte de Métricas - Simulador de Tráfico");
        titulo.setFont(Font.font("System", FontWeight.BOLD, 18));
        titulo.setTextFill(Color.WHITE);

        // Panel de resumen superior
        VBox resumenBox = new VBox(5);
        resumenBox.setPadding(new Insets(12));
        resumenBox.setStyle("-fx-background-color: #2d323e; -fx-background-radius: 6;");

        Label lblPrimero = crearDatoLabel();
        Label lblPromedio = crearDatoLabel();
        Label lblEspera = crearDatoLabel();
        Label lblCongestion = crearDatoLabel();
        Label lblCompletados = crearDatoLabel();
        resumenBox.getChildren().addAll(lblCompletados, lblPrimero, lblPromedio, lblEspera, lblCongestion);

        // Pestaña 1: Tabla de Vehículos
        TableView<GestorMetricas.RegistroVehiculo> tablaVehiculos = new TableView<>();
        tablaVehiculos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<GestorMetricas.RegistroVehiculo, String> colOrden = new TableColumn<>("Llegada #");
        colOrden.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getOrdenLlegada())));

        TableColumn<GestorMetricas.RegistroVehiculo, String> colId = new TableColumn<>("Vehículo");
        colId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));

        TableColumn<GestorMetricas.RegistroVehiculo, String> colRuta = new TableColumn<>("Ruta (Origen -> Destino)");
        colRuta.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getOrigen() + " -> " + d.getValue().getDestino()));

        TableColumn<GestorMetricas.RegistroVehiculo, String> colTotal = new TableColumn<>("Tiempo Total (s)");
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "%.2f", d.getValue().getTiempoTotalSeg())));

        TableColumn<GestorMetricas.RegistroVehiculo, String> colEspera = new TableColumn<>("Espera Semáforos (s)");
        colEspera.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "%.2f", d.getValue().getTiempoEsperaSeg())));

        TableColumn<GestorMetricas.RegistroVehiculo, String> colPorcentaje = new TableColumn<>("% Detenido");
        colPorcentaje.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "%.1f%%", d.getValue().getPorcentajeEspera())));

        tablaVehiculos.getColumns().addAll(colOrden, colId, colRuta, colTotal, colEspera, colPorcentaje);

        // Pestaña 2: Tabla de Congestión por Intersección
        TableView<Interseccion> tablaIntersecciones = new TableView<>();
        tablaIntersecciones.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Interseccion, String> colCoord = new TableColumn<>("Intersección");
        colCoord.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCoordenadaTexto()));

        TableColumn<Interseccion, String> colEsperaActual = new TableColumn<>("Vehículos en Espera Actual");
        colEsperaActual.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getVehiculosEnEspera())));

        TableColumn<Interseccion, String> colMaxCongestion = new TableColumn<>("Congestión Máxima (Pico)");
        colMaxCongestion.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getMaxCongestion())));

        TableColumn<Interseccion, String> colCruces = new TableColumn<>("Total Vehículos Cruzados");
        colCruces.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getTotalCruces())));

        tablaIntersecciones.getColumns().addAll(colCoord, colEsperaActual, colMaxCongestion, colCruces);

        TabPane tabs = new TabPane();
        Tab tabVehiculos = new Tab("Vehículos (Orden de Llegada)", tablaVehiculos);
        tabVehiculos.setClosable(false);
        Tab tabIntersecciones = new Tab("Congestión por Intersección", tablaIntersecciones);
        tabIntersecciones.setClosable(false);
        tabs.getTabs().addAll(tabVehiculos, tabIntersecciones);

        Runnable cargarDatos = () -> {
            lblCompletados.setText("Vehículos completados: " + gestor.getTotalLlegados() + " / " + gestor.getTotalEsperados());

            GestorMetricas.RegistroVehiculo primero = gestor.getPrimeroEnLlegar();
            if (primero != null) {
                lblPrimero.setText(String.format(Locale.US,
                        "Vehículo que llegó primero: %s (%.2f s) | Ruta: %s -> %s",
                        primero.getId(), primero.getTiempoTotalSeg(), primero.getOrigen(), primero.getDestino()));
            } else {
                lblPrimero.setText("Vehículo que llegó primero: Ninguno todavía");
            }

            lblPromedio.setText(String.format(Locale.US, "Tiempo promedio de viaje: %.2f s", gestor.getTiempoPromedioViajeSeg()));
            lblEspera.setText(String.format(Locale.US, "Espera promedio en semáforos: %.1f%% del viaje detenido", gestor.getPorcentajePromedioEspera()));

            Interseccion maxInter = gestor.getInterseccionMasCongestionada();
            if (maxInter != null) {
                lblCongestion.setText("Mayor congestión: Intersección " + maxInter.getCoordenadaTexto()
                        + " (Máximo " + maxInter.getMaxCongestion() + " vehículos esperando)");
            } else {
                lblCongestion.setText("Mayor congestión: Sin datos");
            }

            tablaVehiculos.setItems(FXCollections.observableArrayList(gestor.getRegistros()));
            tablaIntersecciones.setItems(FXCollections.observableArrayList(gestor.getIntersecciones()));
        };

        cargarDatos.run();

        Button btnActualizar = new Button("Actualizar Datos");
        btnActualizar.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        btnActualizar.setOnAction(e -> cargarDatos.run());

        Button btnConsola = new Button("Imprimir en Consola");
        btnConsola.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        btnConsola.setOnAction(e -> gestor.imprimirReporteConsola());

        HBox botones = new HBox(10, btnActualizar, btnConsola);
        botones.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(titulo, resumenBox, tabs, botones);

        Scene scene = new Scene(root, 720, 560);
        stage.setScene(scene);
        stage.show();
    }

    private static Label crearDatoLabel() {
        Label lbl = new Label();
        lbl.setTextFill(Color.WHITE);
        lbl.setFont(Font.font("System", FontWeight.NORMAL, 13));
        return lbl;
    }
}