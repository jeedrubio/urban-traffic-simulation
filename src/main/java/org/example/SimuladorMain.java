package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import java.util.Locale;

public class SimuladorMain extends Application {

    private final int[][] diseñoCiudad = {
            // GLOSARIO
            // 0 -> ZonaNoAccesible
            // 1 -> CalleUnSentido: NORTE
            // 2 -> CalleUnSentido: SUR
            // 3 -> CalleUnSentido: ESTE
            // 4 -> CalleUnSentido: OESTE
            // 5 -> CalleDobleSentido: HORIZONTAL
            // 6 -> CalleDobleSentido: VERTICAL
            // 7 -> Interseccion (Semáforo)
            // 8 -> PuntoGeneracion (Spawn / Destino)
            {0, 0, 0, 0, 8, 0, 0, 0, 0},
            {0, 7, 3, 3, 7, 3, 3, 7, 0},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {8, 7, 5, 5, 7, 5, 5, 7, 8},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {0, 7, 4, 4, 7, 4, 4, 7, 0},
            {0, 0, 0, 0, 8, 0, 0, 0, 0}
    };

    @Override
    public void start(Stage primaryStage) {
        mostrarMenuConfiguracion(primaryStage);
    }

    private void mostrarMenuConfiguracion(Stage stage) {
        // Extraer dinámicamente las coordenadas de los PuntoGeneracion (celdas con valor 8) de la matriz
        List<int[]> coordenadasSpawns = obtenerCoordenadasSpawns(diseñoCiudad);

        VBox contenedor = new VBox(14);
        contenedor.setPadding(new Insets(22));
        contenedor.setStyle("-fx-background-color: #232730;");

        Label titulo = new Label("Configuración de Simulación de Tráfico");
        titulo.setFont(Font.font("System", FontWeight.BOLD, 18));
        titulo.setTextFill(Color.WHITE);

        Label subtitulo = new Label("Deja los campos vacíos para usar los valores establecidos por defecto.");
        subtitulo.setTextFill(Color.LIGHTGRAY);
        subtitulo.setFont(Font.font("System", 12));

        // 1. Cantidad de vehículos (20 - 200)
        TextField txtVehiculos = new TextField();
        txtVehiculos.setPromptText("Número de vehículos");

        // 2. Duración de las luces de los semáforos (segundos)
        TextField txtVerde = new TextField();
        txtVerde.setPromptText("Verde");

        TextField txtAmarillo = new TextField();
        txtAmarillo.setPromptText("Amarillo");

        TextField txtRojo = new TextField();
        txtRojo.setPromptText("Rojo");

        HBox boxSemaforos = new HBox(10, txtVerde, txtAmarillo, txtRojo);

        // 3. Punto de partida
        ComboBox<String> cmbModoOrigen = new ComboBox<>();
        cmbModoOrigen.getItems().addAll("Aleatorio", "Elegir punto");
        cmbModoOrigen.setValue("Aleatorio");

        ComboBox<String> cmbCoordOrigen = new ComboBox<>();
        for (int[] c : coordenadasSpawns) {
            cmbCoordOrigen.getItems().add(formatearCoordenada(c));
        }
        if (!cmbCoordOrigen.getItems().isEmpty()) {
            cmbCoordOrigen.getSelectionModel().select(0);
        }
        cmbCoordOrigen.setDisable(true);
        cmbModoOrigen.setOnAction(e -> cmbCoordOrigen.setDisable("Aleatorio".equals(cmbModoOrigen.getValue())));

        HBox boxOrigen = new HBox(10, cmbModoOrigen, cmbCoordOrigen);

        // 4. Punto de llegada
        ComboBox<String> cmbModoDestino = new ComboBox<>();
        cmbModoDestino.getItems().addAll("Aleatorio", "Elegir punto");
        cmbModoDestino.setValue("Aleatorio");

        ComboBox<String> cmbCoordDestino = new ComboBox<>();
        for (int[] c : coordenadasSpawns) {
            cmbCoordDestino.getItems().add(formatearCoordenada(c));
        }
        if (cmbCoordDestino.getItems().size() > 1) {
            cmbCoordDestino.getSelectionModel().select(1);
        }
        cmbCoordDestino.setDisable(true);
        cmbModoDestino.setOnAction(e -> cmbCoordDestino.setDisable("Aleatorio".equals(cmbModoDestino.getValue())));

        HBox boxDestino = new HBox(10, cmbModoDestino, cmbCoordDestino);

        // Mensaje de validación
        Label lblError = new Label();
        lblError.setTextFill(Color.SALMON);
        lblError.setWrapText(true);

        // Botón de inicio
        Button btnIniciar = new Button("Iniciar Simulación");
        btnIniciar.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18;");

        HBox boxBotones = new HBox(12, btnIniciar);
        boxBotones.setAlignment(Pos.CENTER_RIGHT);

        // Validar que haya mínimo 2 PuntoGeneracion en el mapa
        if (coordenadasSpawns.size() < 2) {
            lblError.setText("Error: El mapa debe tener al menos 2 Spawns para iniciar.");
            btnIniciar.setDisable(true);
        }

        btnIniciar.setOnAction(e -> {
            lblError.setText("");
            if (coordenadasSpawns.size() < 2) {
                lblError.setText("Error: Se requieren al menos 2 Spawns para iniciar.");
                return;
            }

            ConfiguracionSimulacion config = new ConfiguracionSimulacion();

            try {
                // Leer cantidad de vehículos (si está vacío, conserva valor por defecto)
                if (!txtVehiculos.getText().trim().isEmpty()) {
                    int cant = Integer.parseInt(txtVehiculos.getText().trim());
                    if (cant < 20 || cant > 200) {
                        lblError.setText("La cantidad de vehículos debe ser un entero entre 20 y 200.");
                        return;
                    }
                    config.setCantidadVehiculos(cant);
                }

                // Leer tiempos de semáforo (si están vacíos, conserva valores por defecto)
                if (!txtVerde.getText().trim().isEmpty()) {
                    int verde = Integer.parseInt(txtVerde.getText().trim());
                    if (verde <= 0) {
                        lblError.setText("La duración de la luz VERDE debe ser mayor a 0 segundos.");
                        return;
                    }
                    config.setDuracionVerdeSeg(verde);
                }

                if (!txtAmarillo.getText().trim().isEmpty()) {
                    int amarillo = Integer.parseInt(txtAmarillo.getText().trim());
                    if (amarillo <= 0) {
                        lblError.setText("La duración de la luz AMARILLO debe ser mayor a 0 segundos.");
                        return;
                    }
                    config.setDuracionAmarilloSeg(amarillo);
                }

                if (!txtRojo.getText().trim().isEmpty()) {
                    int rojo = Integer.parseInt(txtRojo.getText().trim());
                    if (rojo <= 0) {
                        lblError.setText("La duración de la luz ROJO debe ser mayor a 0 segundos.");
                        return;
                    }
                    config.setDuracionRojoSeg(rojo);
                }

                // Leer origen
                boolean origenFijo = !"Aleatorio".equals(cmbModoOrigen.getValue());
                config.setOrigenAleatorio(!origenFijo);
                if (origenFijo) {
                    int idxOrigen = cmbCoordOrigen.getSelectionModel().getSelectedIndex();
                    if (idxOrigen < 0) {
                        lblError.setText("Selecciona una coordenada válida para el punto de partida.");
                        return;
                    }
                    config.setCoordOrigen(coordenadasSpawns.get(idxOrigen));
                }

                // Leer destino
                boolean destinoFijo = !"Aleatorio".equals(cmbModoDestino.getValue());
                config.setDestinoAleatorio(!destinoFijo);
                if (destinoFijo) {
                    int idxDestino = cmbCoordDestino.getSelectionModel().getSelectedIndex();
                    if (idxDestino < 0) {
                        lblError.setText("Selecciona una coordenada válida para el punto de llegada.");
                        return;
                    }
                    config.setCoordDestino(coordenadasSpawns.get(idxDestino));
                }

                // Validar que origen y destino fijos no sean el mismo punto
                if (origenFijo && destinoFijo
                        && config.getCoordOrigen()[0] == config.getCoordDestino()[0]
                        && config.getCoordOrigen()[1] == config.getCoordDestino()[1]) {
                    lblError.setText("El punto de llegada no puede ser el mismo que el punto de partida.");
                    return;
                }

                iniciarSimulacion(stage, config);

            } catch (NumberFormatException ex) {
                lblError.setText("Ingresa únicamente números enteros válidos en los campos numéricos.");
            }
        });

        contenedor.getChildren().addAll(
                titulo,
                subtitulo,
                crearEtiqueta("Cantidad de vehículos (20 - 200):"),
                txtVehiculos,
                crearEtiqueta("Duración de luces del semáforo (segundos):"),
                boxSemaforos,
                crearEtiqueta("Punto de partida (Spawn):"),
                boxOrigen,
                crearEtiqueta("Punto de llegada (Destino):"),
                boxDestino,
                lblError,
                boxBotones
        );

        Scene menuScene = new Scene(contenedor, 480, 460);
        stage.setTitle("Menú Inicial - Simulador de Tráfico");
        stage.setScene(menuScene);
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();
    }

    private void iniciarSimulacion(Stage stage, ConfiguracionSimulacion config) {
        // Reiniciar el gestor ANTES de crear el mapa para que registre todas las intersecciones
        GestorMetricas gestor = GestorMetricas.getInstancia();
        gestor.reiniciar(config.getCantidadVehiculos());

        Mapa miCiudad = new Mapa(diseñoCiudad, config);

        if (miCiudad.getPuntosSpawn().size() < 2) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Se requieren al menos 2 Spawns para iniciar.");
            alert.showAndWait();
            return;
        }

        ComparadorRendimiento comparador = new ComparadorRendimiento(miCiudad, 500);
        comparador.ejecutarPrueba();

        List<Vehiculo> vehiculos = new CopyOnWriteArrayList<>();

        PanelCiudadFX panelCiudad = new PanelCiudadFX(miCiudad, vehiculos);
        BorderPane root = new BorderPane(panelCiudad);

        // Panel lateral de monitoreo de métricas en tiempo real
        VBox panelMetricas = new VBox(12);
        panelMetricas.setPadding(new Insets(16));
        panelMetricas.setPrefWidth(250);
        panelMetricas.setStyle("-fx-background-color: #1e222a; -fx-border-color: #3a3f4b; -fx-border-width: 0 0 0 1;");

        Label lblTituloMetricas = new Label("Métricas en Vivo");
        lblTituloMetricas.setFont(Font.font("System", FontWeight.BOLD, 16));
        lblTituloMetricas.setTextFill(Color.WHITE);

        Label lblLlegados = crearDatoPanel("Llegados: 0 / " + config.getCantidadVehiculos());
        Label lblPrimero = crearDatoPanel("Primero: Esperando...");
        Label lblPromedio = crearDatoPanel("Tiempo prom.: 0.00 s");
        Label lblEspera = crearDatoPanel("Espera semáf.: 0.0%");
        Label lblCongestion = crearDatoPanel("Congestión máx.: 0");
        Label lblEstado = crearDatoPanel("Estado: Simulando...");
        lblEstado.setTextFill(Color.LIGHTGREEN);

        Button btnVerTabla = new Button("Ver Tabla de Métricas");
        btnVerTabla.setMaxWidth(Double.MAX_VALUE);
        btnVerTabla.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
        btnVerTabla.setOnAction(e -> VentanaMetricasFX.mostrar(gestor));

        panelMetricas.getChildren().addAll(
                lblTituloMetricas,
                new Separator(),
                lblEstado,
                lblLlegados,
                lblPrimero,
                lblPromedio,
                lblEspera,
                lblCongestion,
                new Separator(),
                btnVerTabla
        );
        root.setRight(panelMetricas);

        // Lógica para actualizar el panel lateral
        Runnable refrescarPanelLateral = () -> {
            lblLlegados.setText("Llegados: " + gestor.getTotalLlegados() + " / " + gestor.getTotalEsperados());
            lblPromedio.setText(String.format(Locale.US, "Tiempo prom.: %.2f s", gestor.getTiempoPromedioViajeSeg()));
            lblEspera.setText(String.format(Locale.US, "Espera semáf.: %.1f%%", gestor.getPorcentajePromedioEspera()));

            GestorMetricas.RegistroVehiculo primero = gestor.getPrimeroEnLlegar();
            if (primero != null) {
                lblPrimero.setText(String.format(Locale.US, "Primero: %s (%.2f s)", primero.getId(), primero.getTiempoTotalSeg()));
            }

            Interseccion maxInter = gestor.getInterseccionMasCongestionada();
            if (maxInter != null && maxInter.getMaxCongestion() > 0) {
                lblCongestion.setText("Congestión máx.: " + maxInter.getMaxCongestion() + " en " + maxInter.getCoordenadaTexto());
            }
        };

        // Actualizador visual cada 500 ms para el panel lateral
        Timeline actualizadorMetricas = new Timeline(new KeyFrame(Duration.millis(500), e -> refrescarPanelLateral.run()));
        actualizadorMetricas.setCycleCount(Timeline.INDEFINITE);
        actualizadorMetricas.play();

        // Callback automático cuando el último vehículo llega a su destino
        gestor.setAlFinalizarSimulacion(() -> Platform.runLater(() -> {
            refrescarPanelLateral.run(); // Fuerza la actualización final a 20/20 antes de detener el timer
            lblEstado.setText("Estado: Finalizada");
            lblEstado.setTextFill(Color.GOLD);
            actualizadorMetricas.stop();
            VentanaMetricasFX.mostrar(gestor);
        }));

        Scene scene = new Scene(root);
        stage.setTitle("Simulador de Tráfico JavaFX - (" + config.getCantidadVehiculos() + " vehículos)");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();

        GeneradorTrafico generador = new GeneradorTrafico(miCiudad, vehiculos, config);
        Thread hiloGenerador = new Thread(generador);
        hiloGenerador.setDaemon(true);
        hiloGenerador.start();
    }

    private Label crearDatoPanel(String texto) {
        Label lbl = new Label(texto);
        lbl.setTextFill(Color.LIGHTGRAY);
        lbl.setFont(Font.font("System", 13));
        lbl.setWrapText(true);
        return lbl;
    }

    private List<int[]> obtenerCoordenadasSpawns(int[][] matriz) {
        List<int[]> lista = new ArrayList<>();
        for (int i = 0; i < matriz.length; i++) {
            for (int j = 0; j < matriz[0].length; j++) {
                if (matriz[i][j] == 8) {
                    lista.add(new int[]{i, j});
                }
            }
        }
        return lista;
    }

    private String formatearCoordenada(int[] coord) {
        return "(" + coord[0] + ", " + coord[1] + ") - Fila " + coord[0] + ", Col " + coord[1];
    }

    private Label crearEtiqueta(String texto) {
        Label lbl = new Label(texto);
        lbl.setTextFill(Color.WHITE);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        return lbl;
    }

    public static void main(String[] args) {
        launch(args);
    }
}