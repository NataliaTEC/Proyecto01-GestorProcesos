package minipc.gui;

import minipc.BCP;
import minipc.CPU;
import minipc.CargadorMemoria;
import minipc.Disco;
import minipc.EntradaIndice;
import minipc.EstadoProceso;
import minipc.GestorArchivo;
import minipc.Instruccion;
import minipc.Memoria;
import minipc.ProcesadorInstrucciones;
import minipc.Registro;
import minipc.ResultadoAnalisis;
import minipc.ResultadoCargaArchivo;
import minipc.config.Configuracion;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ventana principal (100% gráfica) de la Mini PC.
 *
 * Flujo de uso:
 *   1) El usuario pulsa "Cargar archivo": se abre la ventana de configuracion de memoria
 *      (valores de config.properties o, si no existe, los valores por defecto).
 *   2) Se eligen uno o varios .asm. Cada uno se valida por separado: los validos se guardan en el
 *      Disco (y se registran en su indice); los invalidos se rechazan con su lista de errores.
 *      El primer programa cargado se copia DEL DISCO a la Memoria y se crea su BCP.
 *   3) "Paso a paso" ejecuta una instrucción por clic.
 *      "Ejecutar" corre el programa completo, animando cada paso.
 *   4) "Limpiar" reinicia todo para cargar un nuevo programa.
 */
public class VentanaMiniPC extends JFrame {

    /** Color de fondo para las posiciones de memoria que ocupa el BCP (Kernel). */
    private static final Color COLOR_FILA_BCP = new Color(0xFF, 0xF3, 0xDC);

    /** Colores de las zonas del disco. */
    private static final Color COLOR_DISCO_INDICE = new Color(0xE3, 0xF2, 0xFD);
    private static final Color COLOR_DISCO_VIRTUAL = new Color(0xF1, 0xE8, 0xFB);

    // ---- Backend ----
    private final GestorArchivo gestorArchivo = new GestorArchivo();
    private final ProcesadorInstrucciones procesador = new ProcesadorInstrucciones();
    private final CargadorMemoria cargadorMemoria = new CargadorMemoria();

    private List<Instruccion> instrucciones;
    private Memoria memoria;
    private Disco disco;
    private BCP bcp;

    /** Nombre del programa (en disco) que esta cargado en memoria. */
    private String programaEnMemoria;
    private CPU cpu;

    /** Configuracion de memoria/disco vigente (leida de config.properties o por defecto). */
    private Configuracion configuracion = Configuracion.cargar();
    private Timer temporizadorEjecucion;

    // ---- Componentes ----
    private JButton botonEjecutar;
    private JButton botonPasoAPaso;
    private JButton botonLimpiar;
    private JButton botonCargarArchivo;

    private JLabel etiquetaMemoriaTotal;
    private JLabel etiquetaSO;
    private JLabel etiquetaUsuario;
    private JLabel etiquetaDisco;

    private DefaultTableModel modeloInstrucciones;
    private DefaultTableModel modeloMemoria;
    private DefaultTableModel modeloDisco;
    private JTable tablaInstrucciones;
    private JTable tablaMemoria;
    private JTable tablaDisco;
    private JLabel leyendaDisco;

    // Etiquetas del panel "BCP actual"
    private JLabel valorId;
    private JLabel valorEstado;
    private JLabel valorPrioridad;
    private JLabel valorPosicionBcp;
    private JLabel valorInicioMemoria;
    private JLabel valorFinMemoria;
    private JLabel valorPc;
    private JLabel valorIr;
    private JLabel valorAc;
    private JLabel valorAx;
    private JLabel valorBx;
    private JLabel valorCx;
    private JLabel valorDx;
    private JLabel valorInstrucciones;

    public VentanaMiniPC() {
        super("Gestor de Procesos - Proyecto 1");
        disco = crearDiscoVacio();
        construirInterfaz();
        cargarTablaDisco();
        actualizarEstadoBotones();
    }

    // ==================================================================
    // Construcción de la interfaz
    // ==================================================================

    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(EstiloUI.FONDO);
        setLayout(new BorderLayout(0, 0));

        add(construirBarraSuperior(), BorderLayout.NORTH);
        add(construirContenidoCentral(), BorderLayout.CENTER);

        setMinimumSize(new Dimension(1240, 640));
        setSize(1420, 720);
        setLocationRelativeTo(null);
    }

    /** Fila de botones + resumen de la configuracion de memoria, en la parte superior. */
    private JPanel construirBarraSuperior() {
        JPanel contenedor = new JPanel();
        contenedor.setLayout(new javax.swing.BoxLayout(contenedor, javax.swing.BoxLayout.Y_AXIS));
        contenedor.setBackground(EstiloUI.FONDO);
        contenedor.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 20));

        // Fila 1: acciones de ejecución (izquierda) y "Cargar archivo" (derecha)
        JPanel filaSuperior = new JPanel(new BorderLayout());
        filaSuperior.setBackground(EstiloUI.FONDO);

        JPanel filaAcciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filaAcciones.setBackground(EstiloUI.FONDO);

        botonEjecutar = EstiloUI.crearBotonPrimario("Ejecutar", EstiloUI.PRIMARIO, EstiloUI.PRIMARIO_HOVER);
        botonPasoAPaso = EstiloUI.crearBotonSecundario("Paso a paso");
        botonLimpiar = EstiloUI.crearBotonPrimario("Limpiar", EstiloUI.PELIGRO, EstiloUI.PELIGRO_HOVER);

        botonEjecutar.addActionListener(e -> ejecutarProgramaCompleto());
        botonPasoAPaso.addActionListener(e -> ejecutarUnPaso());
        botonLimpiar.addActionListener(e -> limpiarTodo());

        filaAcciones.add(botonEjecutar);
        filaAcciones.add(botonPasoAPaso);
        filaAcciones.add(botonLimpiar);

        JPanel filaCarga = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filaCarga.setBackground(EstiloUI.FONDO);
        botonCargarArchivo = EstiloUI.crearBotonSecundario("Cargar archivos (.asm)");
        botonCargarArchivo.addActionListener(e -> cargarArchivos());
        filaCarga.add(botonCargarArchivo);

        filaSuperior.add(filaAcciones, BorderLayout.WEST);
        filaSuperior.add(filaCarga, BorderLayout.EAST);

        // Fila 2: resumen de la configuracion de memoria vigente (se cambia desde la ventana de configuracion)
        JPanel filaMemoria = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filaMemoria.setBackground(EstiloUI.FONDO);

        etiquetaMemoriaTotal = EstiloUI.crearEtiquetaSecundaria("");
        etiquetaMemoriaTotal.setIcon(EstiloUI.iconoMemoria(EstiloUI.TEXTO_SECUNDARIO));
        etiquetaMemoriaTotal.setIconTextGap(6);

        etiquetaSO = EstiloUI.crearEtiquetaSecundaria("");
        etiquetaSO.setIcon(EstiloUI.iconoSistema(EstiloUI.TEXTO_SECUNDARIO));
        etiquetaSO.setIconTextGap(6);

        etiquetaUsuario = EstiloUI.crearEtiquetaSecundaria("");
        etiquetaUsuario.setIcon(EstiloUI.iconoUsuario(EstiloUI.TEXTO_SECUNDARIO));
        etiquetaUsuario.setIconTextGap(6);

        etiquetaDisco = EstiloUI.crearEtiquetaSecundaria("");

        actualizarEtiquetaDivisionMemoria();

        filaMemoria.add(etiquetaMemoriaTotal);
        filaMemoria.add(Box.createHorizontalStrut(18));
        filaMemoria.add(etiquetaSO);
        filaMemoria.add(Box.createHorizontalStrut(14));
        filaMemoria.add(etiquetaUsuario);
        filaMemoria.add(Box.createHorizontalStrut(18));
        filaMemoria.add(etiquetaDisco);

        contenedor.add(filaSuperior);
        contenedor.add(filaMemoria);

        return contenedor;
    }

    /** Zona central: BCP actual | tabla de instrucciones | tabla de memoria | tabla de disco. */
    private JPanel construirContenidoCentral() {
        JPanel contenedor = new JPanel(new GridBagLayout());
        contenedor.setBackground(EstiloUI.FONDO);
        contenedor.setBorder(BorderFactory.createEmptyBorder(6, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 14);
        gbc.gridy = 0;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0; // ancho fijo: el espacio sobrante se reparte entre las tablas
        contenedor.add(construirPanelBcp(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.28;
        contenedor.add(construirPanelInstrucciones(), gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.36;
        contenedor.add(construirPanelMemoria(), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.36;
        gbc.insets = new Insets(0, 0, 0, 0);
        contenedor.add(construirPanelDisco(), gbc);

        return contenedor;
    }

    private JPanel construirPanelInstrucciones() {
        modeloInstrucciones = new DefaultTableModel(new Object[]{"Posición", "Instrucción"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaInstrucciones = new JTable(modeloInstrucciones);
        EstiloUI.aplicarEstiloTabla(tablaInstrucciones);
        tablaInstrucciones.getColumnModel().getColumn(0).setMaxWidth(90);

        return envolverEnTarjeta("Instrucciones", tablaInstrucciones);
    }

    private JPanel construirPanelMemoria() {
        modeloMemoria = new DefaultTableModel(new Object[]{"Posición", "Valor en memoria"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaMemoria = new JTable(modeloMemoria);
        EstiloUI.aplicarEstiloTabla(tablaMemoria);
        tablaMemoria.getColumnModel().getColumn(0).setMaxWidth(90);
        aplicarRendererMemoria();

        return envolverEnTarjeta("Memoria", tablaMemoria);
    }

    /**
     * Renderer de la tabla de memoria: pinta de un color distinto las posiciones del Kernel
     * donde esta guardado el BCP, para distinguirlas del codigo del programa.
     */
    private void aplicarRendererMemoria() {
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if (esFilaDelBcp(row)) {
                        c.setBackground(COLOR_FILA_BCP);
                    } else {
                        c.setBackground(row % 2 == 0 ? EstiloUI.PANEL : EstiloUI.FONDO);
                    }
                }
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return c;
            }
        };
        for (int i = 0; i < tablaMemoria.getColumnCount(); i++) {
            tablaMemoria.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    /** Indica si una posicion de memoria pertenece al BCP guardado en el Kernel. */
    private boolean esFilaDelBcp(int posicion) {
        if (bcp == null) {
            return false;
        }
        int inicio = bcp.getPosicionBCP();
        return posicion >= inicio && posicion < inicio + BCP.TAMANO_EN_MEMORIA;
    }

    /** Tabla del disco: posicion y valor, con un color por zona (indice, archivos, memoria virtual). */
    private JPanel construirPanelDisco() {
        modeloDisco = new DefaultTableModel(new Object[]{"Posición", "Valor en disco"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaDisco = new JTable(modeloDisco);
        EstiloUI.aplicarEstiloTabla(tablaDisco);
        tablaDisco.getColumnModel().getColumn(0).setMaxWidth(90);
        aplicarRendererDisco();

        JPanel tarjeta = envolverEnTarjeta("Disco", tablaDisco);

        leyendaDisco = EstiloUI.crearEtiquetaSecundaria("");
        leyendaDisco.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        tarjeta.add(leyendaDisco, BorderLayout.SOUTH);
        return tarjeta;
    }

    private void aplicarRendererDisco() {
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if (disco != null && disco.esIndice(row)) {
                        c.setBackground(COLOR_DISCO_INDICE);
                    } else if (disco != null && disco.esMemoriaVirtual(row)) {
                        c.setBackground(COLOR_DISCO_VIRTUAL);
                    } else {
                        c.setBackground(row % 2 == 0 ? EstiloUI.PANEL : EstiloUI.FONDO);
                    }
                }
                setToolTipText(descripcionZonaDisco(row));
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return c;
            }
        };
        for (int i = 0; i < tablaDisco.getColumnCount(); i++) {
            tablaDisco.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    /** Texto del tooltip de una fila del disco: zona y, si aplica, a que archivo pertenece. */
    private String descripcionZonaDisco(int posicion) {
        if (disco == null) {
            return null;
        }
        if (disco.esIndice(posicion)) {
            EntradaIndice entrada = EntradaIndice.desdeTexto(posicion, disco.leer(posicion));
            return entrada == null ? "Índice de archivos (entrada libre)" : "Índice: " + entrada.getNombre() + " → posiciones " + entrada.getInicio() + "-" + entrada.getFin();
        }
        if (disco.esMemoriaVirtual(posicion)) {
            return "Memoria virtual";
        }
        for (EntradaIndice entrada : disco.listarArchivos()) {
            if (posicion >= entrada.getInicio() && posicion <= entrada.getFin()) {
                return entrada.getNombre() + " (línea " + (posicion - entrada.getInicio() + 1) + " de " + entrada.getTamano() + ")";
            }
        }
        return "Zona de archivos (libre)";
    }

    /** Panel derecho tipo "ficha" con los campos del BCP actual. */
    private JPanel construirPanelBcp() {
        JPanel tarjeta = crearTarjetaBase();
        tarjeta.setLayout(new BorderLayout());

        JLabel titulo = EstiloUI.crearTitulo("BCP actual");
        titulo.setBorder(BorderFactory.createEmptyBorder(4, 4, 14, 4));
        tarjeta.add(titulo, BorderLayout.NORTH);

        JPanel campos = new JPanel(new GridBagLayout());
        campos.setBackground(EstiloUI.PANEL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.insets = new Insets(5, 4, 5, 4);
        int fila = 0;

        valorId = new JLabel();
        valorEstado = new JLabel();
        valorPrioridad = new JLabel();
        valorPosicionBcp = new JLabel();
        valorInicioMemoria = new JLabel();
        valorFinMemoria = new JLabel();
        valorPc = new JLabel();
        valorIr = new JLabel();
        valorAc = new JLabel();
        valorAx = new JLabel();
        valorBx = new JLabel();
        valorCx = new JLabel();
        valorDx = new JLabel();
        valorInstrucciones = new JLabel();

        fila = agregarCampoBcp(campos, gbc, fila, "ID", valorId);
        fila = agregarCampoBcp(campos, gbc, fila, "Estado", valorEstado);
        fila = agregarCampoBcp(campos, gbc, fila, "Prioridad", valorPrioridad);
        fila = agregarCampoBcp(campos, gbc, fila, "Posición BCP", valorPosicionBcp);
        fila = agregarCampoBcp(campos, gbc, fila, "Inicio memoria", valorInicioMemoria);
        fila = agregarCampoBcp(campos, gbc, fila, "Fin memoria", valorFinMemoria);
        fila = agregarSeparadorBcp(campos, gbc, fila);
        fila = agregarCampoBcp(campos, gbc, fila, "PC", valorPc);
        fila = agregarCampoBcp(campos, gbc, fila, "IR", valorIr);
        fila = agregarCampoBcp(campos, gbc, fila, "AC", valorAc);
        fila = agregarSeparadorBcp(campos, gbc, fila);
        fila = agregarCampoBcp(campos, gbc, fila, "AX", valorAx);
        fila = agregarCampoBcp(campos, gbc, fila, "BX", valorBx);
        fila = agregarCampoBcp(campos, gbc, fila, "CX", valorCx);
        fila = agregarCampoBcp(campos, gbc, fila, "DX", valorDx);
        fila = agregarSeparadorBcp(campos, gbc, fila);
        agregarCampoBcp(campos, gbc, fila, "Instrucciones", valorInstrucciones);

        // relleno inferior para que los campos queden pegados arriba
        gbc.gridy = fila + 10;
        gbc.weighty = 1;
        campos.add(javax.swing.Box.createVerticalGlue(), gbc);

        JScrollPane scroll = new JScrollPane(campos);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        tarjeta.add(scroll, BorderLayout.CENTER);

        // ancho fijo minimo para que las tablas no aplasten el panel
        tarjeta.setMinimumSize(new Dimension(250, 100));
        tarjeta.setPreferredSize(new Dimension(250, 100));

        limpiarPanelBcp();
        return tarjeta;
    }

    private int agregarCampoBcp(JPanel contenedor, GridBagConstraints gbc, int fila, String etiqueta, JLabel valor) {
        JPanel filaPanel = new JPanel(new BorderLayout());
        filaPanel.setBackground(EstiloUI.PANEL);

        JLabel nombreCampo = EstiloUI.crearEtiquetaSecundaria(etiqueta);
        valor.setFont(EstiloUI.FUENTE_MONO_BOLD);
        valor.setForeground(EstiloUI.TEXTO);
        valor.setHorizontalAlignment(SwingConstants.RIGHT);

        filaPanel.add(nombreCampo, BorderLayout.WEST);
        filaPanel.add(valor, BorderLayout.EAST);

        gbc.gridy = fila;
        contenedor.add(filaPanel, gbc);
        return fila + 1;
    }

    private int agregarSeparadorBcp(JPanel contenedor, GridBagConstraints gbc, int fila) {
        JPanel linea = new JPanel();
        linea.setBackground(EstiloUI.BORDE);
        linea.setPreferredSize(new Dimension(10, 1));
        gbc.gridy = fila;
        gbc.insets = new Insets(8, 4, 8, 4);
        contenedor.add(linea, gbc);
        gbc.insets = new Insets(5, 4, 5, 4);
        return fila + 1;
    }

    /** Envuelve un componente (tabla) en una "tarjeta" blanca con título, estilo tarjeta moderna. */
    private JPanel envolverEnTarjeta(String titulo, Component contenido) {
        JPanel tarjeta = crearTarjetaBase();
        tarjeta.setLayout(new BorderLayout());

        JLabel etiquetaTitulo = EstiloUI.crearTitulo(titulo);
        etiquetaTitulo.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));
        tarjeta.add(etiquetaTitulo, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(contenido);
        scroll.setBorder(BorderFactory.createLineBorder(EstiloUI.BORDE));
        tarjeta.add(scroll, BorderLayout.CENTER);

        return tarjeta;
    }

    private JPanel crearTarjetaBase() {
        JPanel tarjeta = new JPanel();
        tarjeta.setBackground(EstiloUI.PANEL);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(EstiloUI.BORDE), BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        return tarjeta;
    }

    // ==================================================================
    // Acciones
    // ==================================================================

    /**
     * Carga uno o varios archivos .asm:
     *   1) Ventana de configuracion (editable solo si no hay nada cargado).
     *   2) Seleccion multiple de archivos.
     *   3) Cada archivo se valida por separado; los validos se guardan en el disco.
     *   4) Resumen: cuales se cargaron (y donde) y cuales se rechazaron (con sus errores).
     *   5) Si no hay un programa en memoria, se copia el primero cargado DEL DISCO a la memoria.
     */
    private void cargarArchivos() {
        // 1) Configuracion de memoria
        boolean sinNadaCargado = memoria == null && disco.estaVacio();
        Configuracion elegida = new DialogoConfiguracion(this, configuracion, sinNadaCargado).mostrar();
        if (elegida == null) {
            return; // el usuario cancelo la configuracion
        }
        configuracion = elegida;
        if (sinNadaCargado) {
            disco = crearDiscoVacio(); // el disco toma el tamano configurado
            cargarTablaDisco();
        }
        actualizarEtiquetaDivisionMemoria();
        // 2) Seleccion de archivos
        List<File> archivos = gestorArchivo.seleccionarArchivos(this);
        if (archivos.isEmpty()) {
            return; // el usuario cancelo
        }
        // 3) Validacion y escritura en el disco (cada archivo por separado)
        List<ResultadoCargaArchivo> resultados = gestorArchivo.cargarEnDisco(archivos, disco);
        cargarTablaDisco();
        // 4) Si no hay programa en memoria, se carga el primero que entro al disco
        String mensajeMemoria = null;
        for (ResultadoCargaArchivo resultado : resultados) {
            if (resultado.isCargado() && memoria == null) {
                mensajeMemoria = cargarProgramaEnMemoria(resultado.getNombreArchivo());
                break;
            }
        }
        // 5) Resumen para el usuario
        mostrarResumenDeCarga(resultados, mensajeMemoria);
        actualizarEstadoBotones();
    }

    /**
     * Copia un programa del disco a la memoria principal y crea su BCP.
     * @return texto para el resumen (exito o motivo del error).
     */
    private String cargarProgramaEnMemoria(String nombre) {
        try {
            List<Instruccion> nuevasInstrucciones = gestorArchivo.leerProgramaDelDisco(nombre, disco);

            Memoria nuevaMemoria = new Memoria(configuracion.getMemoriaPrincipal());
            BCP nuevoBcp = cargadorMemoria.cargar(nuevasInstrucciones, nuevaMemoria, 1);
            CPU nuevaCpu = new CPU(nuevaMemoria);

            // si todo salio bien, recien aqui se reemplaza el estado actual
            this.instrucciones = nuevasInstrucciones;
            this.memoria = nuevaMemoria;
            this.bcp = nuevoBcp;
            this.cpu = nuevaCpu;
            this.programaEnMemoria = nombre;

            cargarTablaInstrucciones();
            cargarTablaMemoria();
            actualizarPanelBcp();
            resaltarFilaActual();
            return "\"" + nombre + "\" se copió del disco a la memoria (posiciones " + nuevoBcp.getLimiteInferior() + "-" + nuevoBcp.getLimiteSuperior() + ") y está listo para ejecutarse.";
        } catch (RuntimeException ex) {
            return "\"" + nombre + "\" quedó en el disco, pero no se pudo cargar en memoria: " + ex.getMessage();
        }
    }

    /** Muestra que archivos se cargaron (y en que posiciones del disco) y cuales se rechazaron. */
    private void mostrarResumenDeCarga(List<ResultadoCargaArchivo> resultados, String mensajeMemoria) {
        List<String> cargados = new ArrayList<>();
        List<String> rechazados = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        for (ResultadoCargaArchivo resultado : resultados) {
            if (resultado.isCargado()) {
                EntradaIndice entrada = resultado.getEntrada();
                cargados.add(resultado.getNombreArchivo() + " → disco, posiciones " + entrada.getInicio() + "-" + entrada.getFin() + " (índice en la posición " + entrada.getPosicionIndice() + ")");
                advertencias.addAll(resultado.getAdvertencias());
            } else {
                rechazados.add(resultado.getNombreArchivo() + " (" + resultado.getErrores().size() + " error(es)):");
                for (String error : resultado.getErrores()) {
                    rechazados.add("    – " + error);
                }
            }
        }

        StringBuilder texto = new StringBuilder();
        texto.append("Archivos cargados en el disco: ").append(cargados.size()).append(" de ").append(resultados.size()).append("\n");
        for (String linea : cargados) {
            texto.append("  ✔ ").append(linea).append("\n");
        }
        if (!rechazados.isEmpty()) {
            texto.append("\nArchivos rechazados:\n");
            for (String linea : rechazados) {
                texto.append(linea.startsWith("    ") ? linea : "  ✘ " + linea).append("\n");
            }
        }
        if (!advertencias.isEmpty()) {
            texto.append("\nAdvertencias:\n");
            for (String advertencia : advertencias) {
                texto.append("  • ").append(advertencia).append("\n");
            }
        }
        if (mensajeMemoria != null) {
            texto.append("\n").append(mensajeMemoria).append("\n");
        } else if (!cargados.isEmpty() && programaEnMemoria != null) {
            texto.append("\nEn memoria continúa \"").append(programaEnMemoria).append("\"; los programas nuevos quedan guardados en el disco.\n");
        }

        int tipo = rechazados.isEmpty() ? JOptionPane.INFORMATION_MESSAGE
                : (cargados.isEmpty() ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE);
        JOptionPane.showMessageDialog(this, crearAreaDeMensaje(texto.toString().trim()), "Resultado de la carga", tipo);
    }

    /** Texto con ajuste de linea y barra de desplazamiento, para mensajes largos. */
    private JScrollPane crearAreaDeMensaje(String texto) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(texto);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(EstiloUI.FUENTE_BASE);
        area.setBackground(EstiloUI.PANEL);
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, Math.min(380, 70 + 22 * texto.split("\n").length)));
        scroll.setBorder(BorderFactory.createLineBorder(EstiloUI.BORDE));
        return scroll;
    }

    private void ejecutarUnPaso() {
        if (bcp == null || cpu == null) {
            return;
        }
        if (!bcp.tieneInstruccionesPendientes()) {
            JOptionPane.showMessageDialog(this, "El programa ya finalizó.", "Fin de la ejecución", JOptionPane.INFORMATION_MESSAGE);
            actualizarEstadoBotones();
            return;
        }

        try {
            cpu.ejecutarUnPaso(bcp);
            actualizarPanelBcp();
            actualizarBcpEnTablaMemoria();
            resaltarFilaActual();
        } catch (RuntimeException ex) {
            mostrarErrorDeEjecucion(ex);
        }

        actualizarEstadoBotones();
        if (!bcp.tieneInstruccionesPendientes()) {
            JOptionPane.showMessageDialog(this, "Programa finalizado.\nAC final = " + bcp.getAc(), "Ejecución completa", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void ejecutarProgramaCompleto() {
        if (bcp == null || cpu == null) {
            return;
        }
        if (!bcp.tieneInstruccionesPendientes()) {
            JOptionPane.showMessageDialog(this, "El programa ya finalizó.", "Fin de la ejecución", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        fijarBotonesDurante(false); // deshabilita botones mientras corre la animación

        temporizadorEjecucion = new Timer(450, evento -> {
            if (bcp.tieneInstruccionesPendientes()) {
                try {
                    cpu.ejecutarUnPaso(bcp);
                    actualizarPanelBcp();
                    actualizarBcpEnTablaMemoria();
                    resaltarFilaActual();
                } catch (RuntimeException ex) {
                    ((Timer) evento.getSource()).stop();
                    fijarBotonesDurante(true);
                    actualizarEstadoBotones();
                    mostrarErrorDeEjecucion(ex);
                    return;
                }
            } else {
                ((Timer) evento.getSource()).stop();
                actualizarBcpEnTablaMemoria();
                fijarBotonesDurante(true);
                actualizarEstadoBotones();
                JOptionPane.showMessageDialog(this, "Programa finalizado.\nAC final = " + bcp.getAc(), "Ejecución completa", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        temporizadorEjecucion.start();
    }

    private void mostrarErrorDeEjecucion(RuntimeException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error de ejecución", JOptionPane.ERROR_MESSAGE);
    }

    private void limpiarTodo() {
        if (temporizadorEjecucion != null && temporizadorEjecucion.isRunning()) {
            temporizadorEjecucion.stop();
        }

        instrucciones = null;
        memoria = null;
        bcp = null;
        cpu = null;
        programaEnMemoria = null;
        disco = crearDiscoVacio();

        modeloInstrucciones.setRowCount(0);
        modeloMemoria.setRowCount(0);
        cargarTablaDisco();
        limpiarPanelBcp();

        actualizarEtiquetaDivisionMemoria();
        actualizarEstadoBotones();
    }

    // ==================================================================
    // Actualización de la interfaz
    // ==================================================================

    private void cargarTablaInstrucciones() {
        modeloInstrucciones.setRowCount(0);
        for (Instruccion instruccion : instrucciones) {
            modeloInstrucciones.addRow(new Object[]{instruccion.getPosicionMemoria(), instruccion.getTextoNormalizado()});
        }
    }

    private void cargarTablaMemoria() {
        modeloMemoria.setRowCount(0);
        // se muestra exactamente lo que quedo guardado en cada posicion de la memoria
        for (int posicion = 0; posicion < memoria.getTamanoTotal(); posicion++) {
            modeloMemoria.addRow(new Object[]{posicion, memoria.leer(posicion)});
        }
        actualizarBcpEnTablaMemoria();
    }

    /** Disco vacio con el tamano de la configuracion vigente. */
    private Disco crearDiscoVacio() {
        return new Disco(configuracion.getMemoriaSecundaria(), configuracion.getTamanoIndiceDisco(),
                configuracion.getMemoriaVirtual());
    }

    /** Vuelve a llenar la tabla del disco con lo que hay en cada posicion, y actualiza la leyenda de zonas. */
    private void cargarTablaDisco() {
        modeloDisco.setRowCount(0);
        for (int posicion = 0; posicion < disco.getTamanoTotal(); posicion++) {
            modeloDisco.addRow(new Object[]{posicion, disco.leer(posicion)});
        }
        leyendaDisco.setText("<html>"
                + cuadroColor(COLOR_DISCO_INDICE) + " Índice &nbsp;&nbsp;"
                + cuadroColor(EstiloUI.FONDO) + " Archivos &nbsp;&nbsp;"
                + cuadroColor(COLOR_DISCO_VIRTUAL) + " Memoria virtual<br>"
                + disco.getCantidadArchivos() + " de " + disco.getTamanoIndice() + " archivos &nbsp;·&nbsp; "
                + disco.getEspacioLibreArchivos() + " posiciones libres</html>");
        leyendaDisco.setToolTipText("Índice 0-" + (disco.getTamanoIndice() - 1) + ", archivos "
                + disco.getInicioArchivos() + "-" + disco.getFinArchivos() + ", memoria virtual "
                + disco.getInicioMemoriaVirtual() + "-" + (disco.getTamanoTotal() - 1));
    }

    /** Cuadrito de color para la leyenda (HTML). */
    private String cuadroColor(Color color) {
        return String.format("<span style='background-color:#%02x%02x%02x;'>&nbsp;&nbsp;&nbsp;&nbsp;</span>",
                color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Refresca las filas del Kernel donde esta guardado el BCP (desde la posicion 0).
     * Los valores se LEEN DE LA MEMORIA (no del objeto BCP), para mostrar lo que realmente
     * quedo almacenado en el espacio de Kernel.
     */
    private void actualizarBcpEnTablaMemoria() {
        if (memoria == null || bcp == null) {
            return;
        }
        int inicio = bcp.getPosicionBCP();
        for (int campo = 0; campo < BCP.TAMANO_EN_MEMORIA; campo++) {
            int posicion = inicio + campo;
            String valorEnMemoria = memoria.leer(posicion);
            modeloMemoria.setValueAt(BCP.describirCampo(campo, valorEnMemoria), posicion, 1);
        }
    }

    private void actualizarPanelBcp() {
        if (bcp == null) {
            limpiarPanelBcp();
            return;
        }
        valorId.setText(String.valueOf(bcp.getPid()));
        valorEstado.setText(bcp.getEstado().toString());
        valorEstado.setForeground(colorParaEstado(bcp.getEstado()));
        valorPrioridad.setText(String.valueOf(bcp.getPrioridad()));
        // rango de posiciones del Kernel que ocupa el BCP (ej: "0 - 12")
        valorPosicionBcp.setText(bcp.getPosicionBCP() + " - " + (bcp.getPosicionBCP() + BCP.TAMANO_EN_MEMORIA - 1));
        valorInicioMemoria.setText(String.valueOf(bcp.getLimiteInferior()));
        valorFinMemoria.setText(String.valueOf(bcp.getLimiteSuperior()));
        valorPc.setText(String.valueOf(bcp.getPc()));
        valorIr.setText(bcp.getIr().isEmpty() ? "-" : bcp.getIr());
        valorAc.setText(String.valueOf(bcp.getAc()));
        valorAx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.AX)));
        valorBx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.BX)));
        valorCx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.CX)));
        valorDx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.DX)));
        valorInstrucciones.setText(String.valueOf(bcp.getInstruccionesEjecutadas()));
    }

    private void limpiarPanelBcp() {
        JLabel[] etiquetas = {valorId, valorEstado, valorPrioridad, valorPosicionBcp, valorInicioMemoria,
                valorFinMemoria, valorPc, valorIr, valorAc, valorAx, valorBx, valorCx, valorDx, valorInstrucciones};
        for (JLabel etiqueta : etiquetas) {
            etiqueta.setText("-");
            etiqueta.setForeground(EstiloUI.TEXTO);
        }
    }

    private Color colorParaEstado(EstadoProceso estado) {
        switch (estado) {
            case EJECUTANDO:
                return EstiloUI.ESTADO_EJECUTANDO;
            case TERMINADO:
                return EstiloUI.ESTADO_TERMINADO;
            default:
                return EstiloUI.ESTADO_LISTO;
        }
    }

    /** Resalta, en ambas tablas, la fila correspondiente a la próxima instrucción a ejecutar (según el PC). */
    private void resaltarFilaActual() {
        if (bcp == null || instrucciones == null) {
            return;
        }
        int pcActual = bcp.getPc();

        int indiceInstruccion = -1;
        for (int i = 0; i < instrucciones.size(); i++) {
            if (instrucciones.get(i).getPosicionMemoria() == pcActual) {
                indiceInstruccion = i;
                break;
            }
        }

        if (indiceInstruccion >= 0) {
            tablaInstrucciones.setRowSelectionInterval(indiceInstruccion, indiceInstruccion);
            desplazarHastaFila(tablaInstrucciones, indiceInstruccion);
        } else {
            tablaInstrucciones.clearSelection();
        }

        if (pcActual >= 0 && pcActual < modeloMemoria.getRowCount()) {
            tablaMemoria.setRowSelectionInterval(pcActual, pcActual);
            desplazarHastaFila(tablaMemoria, pcActual);
        } else {
            tablaMemoria.clearSelection();
        }
    }

    private void desplazarHastaFila(JTable tabla, int fila) {
        tabla.scrollRectToVisible(tabla.getCellRect(fila, 0, true));
    }

    /** Muestra en la barra superior la distribucion de la configuracion vigente. */
    private void actualizarEtiquetaDivisionMemoria() {
        etiquetaMemoriaTotal.setText("Memoria: " + configuracion.getMemoriaPrincipal());
        etiquetaSO.setText("SO: " + configuracion.getTamanoAreaSO() + " (" + Memoria.PORCENTAJE_KERNEL + "%)");
        etiquetaUsuario.setText("Usuario: " + configuracion.getTamanoAreaUsuario() + " (" + (100 - Memoria.PORCENTAJE_KERNEL) + "%)");
        etiquetaDisco.setText("Disco: " + configuracion.getMemoriaSecundaria() + "  (índice: " + configuracion.getTamanoIndiceDisco() + ", memoria virtual fija: " + configuracion.getMemoriaVirtual() + ")");
    }

    private void actualizarEstadoBotones() {
        boolean hayPrograma = bcp != null;
        boolean puedeAvanzar = hayPrograma && bcp.tieneInstruccionesPendientes();

        botonEjecutar.setEnabled(puedeAvanzar);
        botonPasoAPaso.setEnabled(puedeAvanzar);
        botonLimpiar.setEnabled(hayPrograma || (disco != null && !disco.estaVacio()));
        botonCargarArchivo.setEnabled(true);
    }

    /** Deshabilita/habilita los botones mientras corre la animación de "Ejecutar". */
    private void fijarBotonesDurante(boolean habilitado) {
        botonPasoAPaso.setEnabled(habilitado && bcp != null && bcp.tieneInstruccionesPendientes());
        botonEjecutar.setEnabled(habilitado && bcp != null && bcp.tieneInstruccionesPendientes());
        botonLimpiar.setEnabled(habilitado);
        botonCargarArchivo.setEnabled(habilitado);
    }
}