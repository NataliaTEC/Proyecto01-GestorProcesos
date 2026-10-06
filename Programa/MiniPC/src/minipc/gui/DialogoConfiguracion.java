package minipc.gui;

import minipc.BCP;
import minipc.config.Configuracion;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.DefaultFormatter;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;

/**
 * Ventana modal para configurar la memoria de la Mini PC.
 *
 * Permite configurar:
 *
 * - Memoria principal (se divide siempre 25% SO y 75% usuario).
 * - Memoria secundaria (disco).
 *
 * La memoria virtual se muestra, pero es fija (64) y no se puede modificar.
 * El indice de archivos ocupa siempre el 5% inicial del disco (minimo 10 entradas),
 * por lo que su tamano se recalcula cada vez que cambia el tamano del disco.
 *
 * La ventana adapta su tamaño al espacio disponible de la pantalla.
 * Cuando el contenido no cabe verticalmente, solamente la zona central
 * utiliza desplazamiento, manteniendo visibles el encabezado y los botones.
 */
public class DialogoConfiguracion extends JDialog {

    // ================================================================
    // COLORES DEL DIALOGO
    // ================================================================

    private static final Color COLOR_ERROR = new Color(0xC6, 0x28, 0x28);
    private static final Color FONDO_ERROR = new Color(0xFD, 0xEC, 0xEC);
    private static final Color FONDO_INFO = new Color(0xEE, 0xF4, 0xFF);
    private static final Color FONDO_AVISO = new Color(0xFF, 0xF6, 0xE0);

    /** Color utilizado para representar la memoria de usuario en la barra de distribucion. */
    private static final Color COLOR_USUARIO = new Color(0xD8, 0xE4, 0xFF);

    // ================================================================
    // CONFIGURACION
    // ================================================================

    private final Configuracion configuracionInicial;

    /**
     * Indica si la configuracion puede modificarse.
     *
     * Si ya existe un programa cargado, los valores solamente
     * pueden visualizarse.
     */
    private final boolean editable;

    // ================================================================
    // COMPONENTES
    // ================================================================

    private JSpinner spinnerPrincipal;
    private JSpinner spinnerSecundaria;
    private JLabel valorSO;
    private JLabel valorUsuario;
    private JLabel porcentajeSO;
    private JLabel porcentajeUsuario;
    private JLabel rangoSO;
    private JLabel rangoUsuario;
    private JLabel detalleIndice;
    private JLabel detalleArchivos;
    private JLabel detalleVirtual;
    private JTextArea textoErrores;
    private JButton botonAceptar;
    private JButton botonDefecto;
    private BarraDistribucionMemoria barraMemoria;

    /**
     * Resultado del dialogo.
     *
     * Si es null, significa que el usuario cancelo.
     */
    private Configuracion resultado;

    // ================================================================
    // CONSTRUCTOR
    // ================================================================

    public DialogoConfiguracion(Frame propietario, Configuracion actual, boolean editable) {
        super(propietario, "Configuración de memoria", true);
        this.configuracionInicial = actual != null ? actual : Configuracion.porDefecto();
        this.editable = editable;
        construirInterfaz();
        recalcular();
    }

    // ================================================================
    // MOSTRAR
    // ================================================================

    /**
     * Muestra la ventana y devuelve la configuracion seleccionada.
     *
     * @return configuracion seleccionada o null si se cancelo.
     */
    public Configuracion mostrar() {
        setVisible(true);
        return resultado;
    }

    // ================================================================
    // CONSTRUCCION GENERAL
    // ================================================================

    private void construirInterfaz() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        /* Permite cerrar el dialogo con ESC. */
        getRootPane().registerKeyboardAction(
                e -> cancelar(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // ------------------------------------------------------------
        // CONTENEDOR PRINCIPAL
        // ------------------------------------------------------------

        JPanel raiz = new JPanel(new BorderLayout(0, 16));
        raiz.setBackground(EstiloUI.FONDO);
        raiz.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));

        // ------------------------------------------------------------
        // ENCABEZADO
        // ------------------------------------------------------------

        raiz.add(construirEncabezado(), BorderLayout.NORTH);

        // ------------------------------------------------------------
        // CONTENIDO CENTRAL
        // ------------------------------------------------------------

        JPanel cuerpo = construirCuerpo();
        JScrollPane scroll = new JScrollPane(cuerpo);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        /* No queremos desplazamiento horizontal. */
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        /* El desplazamiento vertical solamente aparece cuando la pantalla no tiene suficiente altura. */
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        raiz.add(scroll, BorderLayout.CENTER);

        // ------------------------------------------------------------
        // BOTONES
        // ------------------------------------------------------------

        /* Los botones estan fuera del JScrollPane. Por eso siempre permanecen visibles. */
        raiz.add(construirBotones(), BorderLayout.SOUTH);

        setContentPane(raiz);

        // ------------------------------------------------------------
        // TAMAÑO RESPONSIVE
        // ------------------------------------------------------------

        ajustarTamanoResponsive();

        /* Permite al usuario ajustar manualmente la ventana si su pantalla o escalado lo requiere. */
        setResizable(true);

        setLocationRelativeTo(getOwner());
    }

    // ================================================================
    // TAMAÑO RESPONSIVE
    // ================================================================

    /**
     * Calcula un tamaño apropiado segun el espacio disponible
     * en la pantalla actual.
     *
     * No utiliza directamente la resolucion completa porque
     * MaximumWindowBounds descuenta elementos como la barra
     * de tareas del sistema operativo.
     */
    private void ajustarTamanoResponsive() {
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int anchoPantalla = pantalla.width;
        int altoPantalla = pantalla.height;

        /* Primero permitimos que Swing calcule el tamaño natural requerido por los componentes. */
        pack();
        Dimension natural = getPreferredSize();

        // ------------------------------------------------------------
        // ANCHO
        // ------------------------------------------------------------

        /*
         * 680 px es el ancho ideal para este dialogo.
         *
         * Nunca utilizamos mas del 92 % del ancho disponible.
         */
        int anchoIdeal = 680;
        int anchoMaximo = (int) (anchoPantalla * 0.92);
        int ancho = Math.min(anchoIdeal, anchoMaximo);

        /* En una pantalla suficientemente grande evitamos que el dialogo quede demasiado angosto. */
        if (anchoPantalla >= 600) {
            ancho = Math.max(560, ancho);
        }

        /* Dejamos al menos 20 px de margen respecto al area disponible. */
        ancho = Math.min(ancho, Math.max(320, anchoPantalla - 20));

        // ------------------------------------------------------------
        // ALTURA
        // ------------------------------------------------------------

        /* La altura ideal es la que realmente necesita todo el contenido. */
        int alturaNatural = natural.height;

        /* Nunca utilizamos mas del 90 % de la altura disponible. */
        int alturaMaxima = (int) (altoPantalla * 0.90);
        int altura = Math.min(alturaNatural, alturaMaxima);

        /* Altura minima cuando existe suficiente espacio. */
        if (altoPantalla >= 520) {
            altura = Math.max(480, altura);
        }

        /* Dejamos margen respecto a los bordes superior e inferior. */
        altura = Math.min(altura, Math.max(380, altoPantalla - 20));

        // ------------------------------------------------------------
        // APLICAR
        // ------------------------------------------------------------

        setSize(ancho, altura);

        /*
         * Tamaño minimo manual.
         *
         * Tambien se adapta a pantallas muy pequeñas.
         */
        int anchoMinimo = Math.min(520, ancho);
        int alturaMinima = Math.min(430, altura);
        setMinimumSize(new Dimension(anchoMinimo, alturaMinima));
    }

    // ================================================================
    // ENCABEZADO
    // ================================================================

    private JPanel construirEncabezado() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(EstiloUI.FONDO);

        JLabel titulo = EstiloUI.crearTituloVentana("Configuración de memoria");
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitulo = EstiloUI.crearEtiquetaSecundaria(
                "Define cómo se distribuirán los recursos de memoria del MiniPC.");
        subtitulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(titulo);
        panel.add(Box.createVerticalStrut(5));
        panel.add(subtitulo);

        // ------------------------------------------------------------
        // ADVERTENCIAS DE CONFIGURACION
        // ------------------------------------------------------------

        List<String> avisos = configuracionInicial.getAdvertenciasDeCarga();
        if (!avisos.isEmpty()) {
            panel.add(Box.createVerticalStrut(12));
            JTextArea cajaAvisos = crearCajaMensaje(String.join("\n", avisos), FONDO_AVISO, EstiloUI.TEXTO);
            cajaAvisos.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cajaAvisos);
        }

        // ------------------------------------------------------------
        // MODO SOLO LECTURA
        // ------------------------------------------------------------

        if (!editable) {
            panel.add(Box.createVerticalStrut(12));
            JTextArea avisoLectura = crearCajaMensaje(
                    "Hay un programa cargado en memoria. "
                            + "Para modificar estos valores primero debe "
                            + "presionar \"Limpiar\" en la ventana principal.",
                    FONDO_AVISO,
                    EstiloUI.TEXTO);
            avisoLectura.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(avisoLectura);
        }

        return panel;
    }

    // ================================================================
    // CUERPO
    // ================================================================

    private JPanel construirCuerpo() {
        JPanel cuerpo = new JPanel();
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));
        cuerpo.setBackground(EstiloUI.FONDO);

        JPanel tarjetaPrincipal = construirTarjetaMemoriaPrincipal();
        JPanel tarjetaDisco = construirTarjetaDisco();

        JTextArea informacionProcesos = crearCajaMensaje(
                "Máximo de procesos activos simultáneamente: "
                        + Configuracion.MAX_PROCESOS_ACTIVOS
                        + ". Los programas adicionales permanecen "
                        + "en espera hasta que exista espacio disponible.",
                FONDO_INFO,
                EstiloUI.TEXTO);

        textoErrores = crearCajaMensaje("", FONDO_ERROR, COLOR_ERROR);

        tarjetaPrincipal.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjetaDisco.setAlignmentX(Component.LEFT_ALIGNMENT);
        informacionProcesos.setAlignmentX(Component.LEFT_ALIGNMENT);
        textoErrores.setAlignmentX(Component.LEFT_ALIGNMENT);

        cuerpo.add(tarjetaPrincipal);
        cuerpo.add(Box.createVerticalStrut(14));
        cuerpo.add(tarjetaDisco);
        cuerpo.add(Box.createVerticalStrut(14));
        cuerpo.add(informacionProcesos);
        cuerpo.add(Box.createVerticalStrut(10));
        cuerpo.add(textoErrores);

        /* Este borde derecho evita que los componentes queden pegados a la barra de desplazamiento. */
        cuerpo.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 8));

        return cuerpo;
    }

    // ================================================================
    // TARJETA MEMORIA PRINCIPAL
    // ================================================================

    private JPanel construirTarjetaMemoriaPrincipal() {
        JPanel tarjeta = EstiloUI.crearTarjeta();
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));
        tarjeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // ------------------------------------------------------------
        // TITULO DE SECCION
        // ------------------------------------------------------------

        JLabel seccion = EstiloUI.crearEtiquetaSeccion("Memoria principal");
        seccion.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.add(seccion);
        tarjeta.add(Box.createVerticalStrut(14));

        // ------------------------------------------------------------
        // TAMAÑO TOTAL
        // ------------------------------------------------------------

        spinnerPrincipal = crearSpinner(
                configuracionInicial.getMemoriaPrincipal(),
                Configuracion.MAXIMO_MEMORIA_PRINCIPAL);

        JPanel filaTotal = crearFilaCampo(
                "Tamaño total",
                "Se divide siempre 25% SO y 75% usuario.",
                spinnerPrincipal);
        filaTotal.setAlignmentX(Component.LEFT_ALIGNMENT);

        tarjeta.add(filaTotal);
        tarjeta.add(Box.createVerticalStrut(16));
        tarjeta.add(crearSeparador());
        tarjeta.add(Box.createVerticalStrut(15));

        // ------------------------------------------------------------
        // DISTRIBUCION
        // ------------------------------------------------------------

        JLabel distribucion = EstiloUI.crearTitulo("Distribución de memoria");
        distribucion.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.add(distribucion);
        tarjeta.add(Box.createVerticalStrut(13));

        // ------------------------------------------------------------
        // INDICADORES SO / USUARIO
        // ------------------------------------------------------------

        JPanel indicadores = new JPanel(new GridBagLayout());
        indicadores.setBackground(EstiloUI.PANEL);
        indicadores.setAlignmentX(Component.LEFT_ALIGNMENT);
        indicadores.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        indicadores.add(construirIndicadorSO(), gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 24, 0, 0);
        indicadores.add(construirIndicadorUsuario(), gbc);

        tarjeta.add(indicadores);
        tarjeta.add(Box.createVerticalStrut(17));

        // ------------------------------------------------------------
        // BARRA GRAFICA
        // ------------------------------------------------------------

        barraMemoria = new BarraDistribucionMemoria();
        barraMemoria.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.add(barraMemoria);
        tarjeta.add(Box.createVerticalStrut(8));

        // ------------------------------------------------------------
        // PORCENTAJES
        // ------------------------------------------------------------

        JPanel filaPorcentajes = new JPanel(new BorderLayout());
        filaPorcentajes.setBackground(EstiloUI.PANEL);

        porcentajeSO = EstiloUI.crearEtiquetaSecundaria("");
        porcentajeUsuario = EstiloUI.crearEtiquetaSecundaria("");

        filaPorcentajes.add(porcentajeSO, BorderLayout.WEST);
        filaPorcentajes.add(porcentajeUsuario, BorderLayout.EAST);
        filaPorcentajes.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaPorcentajes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        tarjeta.add(filaPorcentajes);
        tarjeta.add(Box.createVerticalStrut(12));

        // ------------------------------------------------------------
        // RANGOS
        // ------------------------------------------------------------

        rangoSO = crearDetalle();
        rangoUsuario = crearDetalle();

        rangoSO.setIcon(EstiloUI.iconoSistema(EstiloUI.PRIMARIO));
        rangoSO.setIconTextGap(7);
        rangoUsuario.setIcon(EstiloUI.iconoUsuario(EstiloUI.TEXTO_SECUNDARIO));
        rangoUsuario.setIconTextGap(7);
        rangoSO.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangoUsuario.setAlignmentX(Component.LEFT_ALIGNMENT);

        tarjeta.add(rangoSO);
        tarjeta.add(Box.createVerticalStrut(5));
        tarjeta.add(rangoUsuario);

        return tarjeta;
    }

    // ================================================================
    // INDICADOR SISTEMA OPERATIVO
    // ================================================================

    private JPanel construirIndicadorSO() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(EstiloUI.PANEL);

        JLabel nombre = new JLabel("Sistema Operativo");
        nombre.setFont(EstiloUI.FUENTE_ETIQUETA);
        nombre.setForeground(EstiloUI.TEXTO_SECUNDARIO);
        nombre.setIcon(EstiloUI.iconoSistema(EstiloUI.PRIMARIO));
        nombre.setIconTextGap(7);

        valorSO = new JLabel("-");
        valorSO.setFont(EstiloUI.FUENTE_NUMERO);
        valorSO.setForeground(EstiloUI.TEXTO);

        nombre.setAlignmentX(Component.LEFT_ALIGNMENT);
        valorSO.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(nombre);
        panel.add(Box.createVerticalStrut(4));
        panel.add(valorSO);

        return panel;
    }

    // ================================================================
    // INDICADOR USUARIO
    // ================================================================

    private JPanel construirIndicadorUsuario() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(EstiloUI.PANEL);

        JLabel nombre = new JLabel("Usuario");
        nombre.setFont(EstiloUI.FUENTE_ETIQUETA);
        nombre.setForeground(EstiloUI.TEXTO_SECUNDARIO);
        nombre.setIcon(EstiloUI.iconoUsuario(EstiloUI.TEXTO_SECUNDARIO));
        nombre.setIconTextGap(7);

        valorUsuario = new JLabel("-");
        valorUsuario.setFont(EstiloUI.FUENTE_NUMERO);
        valorUsuario.setForeground(EstiloUI.TEXTO);

        nombre.setAlignmentX(Component.LEFT_ALIGNMENT);
        valorUsuario.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(nombre);
        panel.add(Box.createVerticalStrut(4));
        panel.add(valorUsuario);

        return panel;
    }

    // ================================================================
    // TARJETA ALMACENAMIENTO SECUNDARIO
    // ================================================================

    private JPanel construirTarjetaDisco() {
        JPanel tarjeta = EstiloUI.crearTarjeta();
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));
        tarjeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel seccion = EstiloUI.crearEtiquetaSeccion("Almacenamiento secundario");
        seccion.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.add(seccion);
        tarjeta.add(Box.createVerticalStrut(14));

        // ------------------------------------------------------------
        // SPINNERS
        // ------------------------------------------------------------

        spinnerSecundaria = crearSpinner(
                configuracionInicial.getMemoriaSecundaria(),
                Configuracion.MAXIMO_MEMORIA_SECUNDARIA);


        JPanel filaDisco = crearFilaCampo(
                "Tamaño del disco",
                "Capacidad total. El índice ocupa el "
                        + Configuracion.PORCENTAJE_INDICE_DISCO + "% inicial.",
                spinnerSecundaria);

        JPanel filaVirtual = crearFilaCampo(
                "Memoria virtual",
                "Valor fijo del sistema (no configurable).",
                crearCampoFijo(Configuracion.MEMORIA_VIRTUAL));

        filaDisco.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaVirtual.setAlignmentX(Component.LEFT_ALIGNMENT);

        tarjeta.add(filaDisco);
        tarjeta.add(Box.createVerticalStrut(10));
        tarjeta.add(filaVirtual);
        tarjeta.add(Box.createVerticalStrut(15));
        tarjeta.add(crearSeparador());
        tarjeta.add(Box.createVerticalStrut(13));

        // ------------------------------------------------------------
        // DISTRIBUCION DEL DISCO
        // ------------------------------------------------------------

        JLabel tituloDistribucion = EstiloUI.crearTitulo("Distribución del disco");
        tituloDistribucion.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjeta.add(tituloDistribucion);
        tarjeta.add(Box.createVerticalStrut(10));

        detalleIndice = crearDetalle();
        detalleArchivos = crearDetalle();
        detalleVirtual = crearDetalle();

        detalleIndice.setAlignmentX(Component.LEFT_ALIGNMENT);
        detalleArchivos.setAlignmentX(Component.LEFT_ALIGNMENT);
        detalleVirtual.setAlignmentX(Component.LEFT_ALIGNMENT);

        tarjeta.add(detalleIndice);
        tarjeta.add(Box.createVerticalStrut(5));
        tarjeta.add(detalleArchivos);
        tarjeta.add(Box.createVerticalStrut(5));
        tarjeta.add(detalleVirtual);

        return tarjeta;
    }

    // ================================================================
    // FILAS DE CAMPOS
    // ================================================================

    /**
     * Construye una fila formada por:
     *
     * titulo
     * descripcion
     * spinner numerico
     */
    private JPanel crearFilaCampo(String titulo, String descripcion, JComponent campo) {
        JPanel fila = new JPanel(new BorderLayout(15, 0));
        fila.setBackground(EstiloUI.PANEL);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

        JPanel textos = new JPanel();
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        textos.setBackground(EstiloUI.PANEL);

        JLabel etiquetaTitulo = new JLabel(titulo);
        etiquetaTitulo.setFont(EstiloUI.FUENTE_BASE.deriveFont(Font.BOLD));
        etiquetaTitulo.setForeground(EstiloUI.TEXTO);

        JLabel etiquetaDescripcion = EstiloUI.crearEtiquetaSecundaria(descripcion);

        etiquetaTitulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        etiquetaDescripcion.setAlignmentX(Component.LEFT_ALIGNMENT);

        textos.add(etiquetaTitulo);
        textos.add(Box.createVerticalStrut(3));
        textos.add(etiquetaDescripcion);

        fila.add(textos, BorderLayout.CENTER);
        fila.add(campo, BorderLayout.EAST);

        return fila;
    }

    // ================================================================
    // SPINNERS
    // ================================================================

    private JSpinner crearSpinner(int valorInicial, int maximo) {
        int valor = Math.max(1, Math.min(valorInicial, maximo));

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(valor, 1, maximo, 1));
        spinner.setFont(EstiloUI.FUENTE_MONO);
        spinner.setPreferredSize(new Dimension(120, 34));
        spinner.setMinimumSize(new Dimension(100, 34));

        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
        spinner.setEditor(editor);

        JFormattedTextField campo = editor.getTextField();
        campo.setHorizontalAlignment(SwingConstants.LEFT);
        campo.setFont(EstiloUI.FUENTE_MONO);
        campo.setForeground(EstiloUI.TEXTO);
        campo.setBackground(Color.WHITE);
        campo.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        if (campo.getFormatter() instanceof DefaultFormatter) {
            ((DefaultFormatter) campo.getFormatter()).setCommitsOnValidEdit(true);
        }

        campo.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);

        spinner.addChangeListener(e -> recalcular());
        spinner.setEnabled(editable);

        return spinner;
    }

    /**
     * Campo de solo lectura para valores fijos del sistema (ej: memoria virtual).
     * Tiene el mismo tamano que los spinners para que las filas queden alineadas,
     * pero no se puede editar ni recibe el foco.
     */
    private JComponent crearCampoFijo(int valor) {
        JTextField campo = new JTextField(String.valueOf(valor));
        campo.setEditable(false);
        campo.setFocusable(false);
        campo.setFont(EstiloUI.FUENTE_MONO);
        campo.setForeground(EstiloUI.TEXTO_SECUNDARIO);
        campo.setBackground(EstiloUI.FONDO);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EstiloUI.BORDE),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        campo.setPreferredSize(new Dimension(120, 34));
        campo.setMinimumSize(new Dimension(100, 34));
        campo.setToolTipText("La memoria virtual es fija y no se puede modificar.");
        return campo;
    }

    // ================================================================
    // BOTONES
    // ================================================================

    private JPanel construirBotones() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(EstiloUI.FONDO);

        // ------------------------------------------------------------
        // VALORES POR DEFECTO
        // ------------------------------------------------------------

        botonDefecto = EstiloUI.crearBotonSecundario("Valores por defecto");
        botonDefecto.addActionListener(e -> restaurarValoresPorDefecto());
        botonDefecto.setEnabled(editable);

        // ------------------------------------------------------------
        // CANCELAR
        // ------------------------------------------------------------

        JButton botonCancelar = EstiloUI.crearBotonSecundario("Cancelar");
        botonCancelar.addActionListener(e -> cancelar());

        // ------------------------------------------------------------
        // ACEPTAR
        // ------------------------------------------------------------

        botonAceptar = EstiloUI.crearBotonPrimario(
                editable ? "Guardar y continuar" : "Continuar",
                EstiloUI.PRIMARIO,
                EstiloUI.PRIMARIO_HOVER);
        botonAceptar.addActionListener(e -> aceptar());

        getRootPane().setDefaultButton(botonAceptar);

        // ------------------------------------------------------------
        // CONTENEDOR DERECHO
        // ------------------------------------------------------------

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        derecha.setBackground(EstiloUI.FONDO);
        derecha.add(botonCancelar);
        derecha.add(botonAceptar);

        panel.add(botonDefecto, BorderLayout.WEST);
        panel.add(derecha, BorderLayout.EAST);

        return panel;
    }

    // ================================================================
    // COMPONENTES AUXILIARES
    // ================================================================

    /** Etiqueta utilizada para mostrar rangos de memoria. */
    private JLabel crearDetalle() {
        JLabel etiqueta = EstiloUI.crearEtiquetaSecundaria("");
        etiqueta.setFont(EstiloUI.FUENTE_MONO.deriveFont(12f));
        return etiqueta;
    }

    /** Separador horizontal. */
    private JPanel crearSeparador() {
        JPanel linea = new JPanel();
        linea.setBackground(EstiloUI.BORDE);
        linea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        linea.setPreferredSize(new Dimension(100, 1));
        linea.setMinimumSize(new Dimension(1, 1));
        return linea;
    }

    /**
     * Crea una caja de texto para informacion,
     * advertencias y errores.
     */
    private JTextArea crearCajaMensaje(String texto, Color fondo, Color colorTexto) {
        JTextArea area = new JTextArea(texto);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(EstiloUI.FUENTE_ETIQUETA);
        area.setForeground(colorTexto);
        area.setBackground(fondo);
        area.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setColumns(50);

        /* Evita que BoxLayout limite innecesariamente el ancho del componente. */
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        return area;
    }

    // ================================================================
    // LEER FORMULARIO
    // ================================================================

    /**
     * Construye una configuracion utilizando los valores
     * actuales de los spinners.
     */
    private Configuracion leerFormulario() {
        return new Configuracion(
                (Integer) spinnerPrincipal.getValue(),
                (Integer) spinnerSecundaria.getValue());
    }

    // ================================================================
    // RECALCULAR
    // ================================================================

    /**
     * Recalcula toda la informacion visual cada vez
     * que cambia alguno de los campos.
     */
    private void recalcular() {
        /*
         * Durante la construccion inicial algunos componentes
         * todavia pueden no existir.
         */
        if (botonAceptar == null || valorSO == null || barraMemoria == null) {
            return;
        }

        Configuracion config = leerFormulario();
        int memoriaTotal = config.getMemoriaPrincipal();
        int areaSO = config.getTamanoAreaSO();
        int areaUsuario = Math.max(config.getTamanoAreaUsuario(), 0);

        // ------------------------------------------------------------
        // VALORES PRINCIPALES
        // ------------------------------------------------------------

        valorSO.setText(areaSO + " posiciones");
        valorUsuario.setText(areaUsuario + " posiciones");

        // ------------------------------------------------------------
        // PORCENTAJES
        // ------------------------------------------------------------

        double porcentajeSistema = memoriaTotal > 0 ? (areaSO * 100.0) / memoriaTotal : 0.0;
        double porcentajeUser = memoriaTotal > 0 ? (areaUsuario * 100.0) / memoriaTotal : 0.0;

        porcentajeSO.setText(String.format("SO %.1f%%", porcentajeSistema));
        porcentajeUsuario.setText(String.format("Usuario %.1f%%", porcentajeUser));

        // ------------------------------------------------------------
        // RANGO DEL SISTEMA OPERATIVO
        // ------------------------------------------------------------

        if (areaSO > 0) {
            rangoSO.setText("SO: posiciones 0 - " + (areaSO - 1)
                    + "   (encabezado " + Configuracion.TAMANO_ENCABEZADO_SO + " + "
                    + Configuracion.MAX_PROCESOS_ACTIVOS + " BCP × " + BCP.TAMANO_EN_MEMORIA
                    + " = " + Configuracion.getEspacioRequeridoSO() + ")");
        } else {
            rangoSO.setText("SO: sin espacio asignado");
        }

        // ------------------------------------------------------------
        // RANGO DE USUARIO
        // ------------------------------------------------------------

        if (areaUsuario > 0) {
            rangoUsuario.setText("Usuario: posiciones " + areaSO + " - " + (memoriaTotal - 1));
        } else {
            rangoUsuario.setText("Usuario: sin espacio disponible");
        }

        // ------------------------------------------------------------
        // BARRA VISUAL
        // ------------------------------------------------------------

        barraMemoria.setValores(areaSO, areaUsuario);

        // ------------------------------------------------------------
        // ALMACENAMIENTO SECUNDARIO
        // ------------------------------------------------------------

        /* El indice ya no es fijo: depende del tamano del disco (5%, minimo 10). */
        int tamanoIndice = config.getTamanoIndiceDisco();
        int finIndice = tamanoIndice - 1;
        int inicioVirtual = config.getMemoriaSecundaria() - config.getMemoriaVirtual();
        int areaArchivos = Math.max(config.getTamanoAreaArchivosDisco(), 0);

        /* Todas las etiquetas tienen 23 caracteres para que las columnas queden alineadas. */
        detalleIndice.setText(
                "Índice de archivos " + String.format("%-4s", Configuracion.PORCENTAJE_INDICE_DISCO + "%")
                        + tamanoIndice
                        + rango(0, finIndice));
        detalleIndice.setToolTipText(
                "Una entrada por archivo (nombre + dirección): máximo "
                        + tamanoIndice + " archivos en el disco.");

        detalleArchivos.setText(
                "Archivos               "
                        + areaArchivos
                        + rango(tamanoIndice, inicioVirtual - 1));

        detalleVirtual.setText(
                "Memoria virtual        "
                        + config.getMemoriaVirtual()
                        + rango(inicioVirtual, config.getMemoriaSecundaria() - 1));

        // ------------------------------------------------------------
        // VALIDACION
        // ------------------------------------------------------------

        List<String> errores = config.validar();

        if (errores.isEmpty()) {
            textoErrores.setText("");
            textoErrores.setVisible(false);
        } else {
            textoErrores.setText("• " + String.join("\n• ", errores));
            textoErrores.setVisible(true);
        }

        botonAceptar.setEnabled(errores.isEmpty());

        /*
         * IMPORTANTE:
         *
         * No utilizamos pack() aqui.
         *
         * Si se utilizara pack() cada vez que cambia un spinner,
         * la ventana modificaria su tamaño constantemente y se
         * perderia el comportamiento responsive.
         */
        revalidate();
        repaint();
    }

    // ================================================================
    // RANGOS
    // ================================================================

    /** Genera el texto correspondiente a un rango. */
    private String rango(int inicio, int fin) {
        if (inicio >= 0 && fin >= inicio) {
            return "   posiciones " + inicio + " - " + fin;
        }
        return "";
    }

    // ================================================================
    // RESTAURAR VALORES
    // ================================================================

    private void restaurarValoresPorDefecto() {
        if (!editable) {
            return;
        }

        spinnerPrincipal.setValue(Configuracion.DEFECTO_MEMORIA_PRINCIPAL);
        spinnerSecundaria.setValue(Configuracion.DEFECTO_MEMORIA_SECUNDARIA);

        recalcular();
    }

    // ================================================================
    // ACEPTAR
    // ================================================================

    private void aceptar() {
        /*
         * Si el dialogo esta en modo lectura,
         * simplemente devolvemos la configuracion actual.
         */
        if (!editable) {
            resultado = configuracionInicial;
            dispose();
            return;
        }

        // ------------------------------------------------------------
        // CONFIRMAR EDICION DE LOS SPINNERS
        // ------------------------------------------------------------

        JSpinner[] spinners = {spinnerPrincipal, spinnerSecundaria};

        for (JSpinner spinner : spinners) {
            try {
                spinner.commitEdit();
            } catch (java.text.ParseException ex) {
                /*
                 * Si el contenido escrito no es valido,
                 * Swing conserva el ultimo valor valido.
                 */
            }
        }

        // ------------------------------------------------------------
        // VALIDAR
        // ------------------------------------------------------------

        Configuracion config = leerFormulario();
        List<String> errores = config.validar();

        if (!errores.isEmpty()) {
            recalcular();
            return;
        }

        // ------------------------------------------------------------
        // GUARDAR
        // ------------------------------------------------------------

        try {
            config.guardar();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo guardar "
                            + Configuracion.NOMBRE_ARCHIVO
                            + ":\n"
                            + ex.getMessage()
                            + "\n\n"
                            + "La configuración se usará "
                            + "solo durante esta sesión.",
                    "Configuración",
                    JOptionPane.WARNING_MESSAGE);
        }

        resultado = config;
        dispose();
    }

    // ================================================================
    // CANCELAR
    // ================================================================

    private void cancelar() {
        resultado = null;
        dispose();
    }

    // ================================================================
    // BARRA VISUAL DE MEMORIA
    // ================================================================

    /**
     * Componente visual que representa la division de la memoria
     * principal entre el Sistema Operativo y el usuario.
     */
    private static class BarraDistribucionMemoria extends JComponent {

        private int areaSO;
        private int areaUsuario;

        BarraDistribucionMemoria() {
            setPreferredSize(new Dimension(500, 18));
            setMinimumSize(new Dimension(200, 18));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            setToolTipText("Distribución de la memoria principal");
        }

        /** Actualiza los valores representados. */
        public void setValores(int areaSO, int areaUsuario) {
            this.areaSO = Math.max(areaSO, 0);
            this.areaUsuario = Math.max(areaUsuario, 0);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int ancho = getWidth();
            int alto = getHeight();
            int total = areaSO + areaUsuario;

            if (ancho <= 0 || alto <= 0) {
                g2.dispose();
                return;
            }

            // --------------------------------------------------------
            // FONDO
            // --------------------------------------------------------

            g2.setColor(EstiloUI.BORDE);
            g2.fillRoundRect(0, 0, ancho, alto, 10, 10);

            // --------------------------------------------------------
            // DISTRIBUCION
            // --------------------------------------------------------

            if (total > 0) {
                int anchoSO = (int) Math.round(ancho * (areaSO / (double) total));
                anchoSO = Math.max(0, Math.min(anchoSO, ancho));

                /* Primero pintamos toda la barra como memoria de usuario. */
                g2.setColor(COLOR_USUARIO);
                g2.fillRoundRect(0, 0, ancho, alto, 10, 10);

                /* Luego pintamos la parte correspondiente al Sistema Operativo. */
                if (anchoSO > 0) {
                    g2.setColor(EstiloUI.PRIMARIO);
                    g2.fillRoundRect(0, 0, anchoSO, alto, 10, 10);

                    /* Corrige el borde redondeado interno cuando existen ambas areas. */
                    if (anchoSO < ancho) {
                        int correccion = Math.min(8, anchoSO);
                        g2.fillRect(Math.max(0, anchoSO - correccion), 0, correccion, alto);
                    }
                }
            }

            // --------------------------------------------------------
            // BORDE EXTERIOR
            // --------------------------------------------------------

            g2.setColor(EstiloUI.BORDE);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, ancho - 1, alto - 1, 10, 10);

            g2.dispose();
        }
    }
}