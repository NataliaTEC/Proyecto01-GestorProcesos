package minipc;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gestiona los archivos .asm del computador real y su paso al disco de la Mini PC:
 *   1) Abre una ventana grafica (JFileChooser) para seleccionar UNO O VARIOS archivos .asm.
 *   2) Lee el contenido de cada archivo linea por linea.
 *   3) Valida cada archivo POR SEPARADO con {@link ProcesadorInstrucciones}.
 *   4) Escribe en el {@link Disco} los archivos validos (y los registra en el indice);
 *      los invalidos se rechazan con su lista de errores (archivo + linea).
 * @author Natalia Granados Rosales
 */
public class GestorArchivo {

    public static final String EXTENSION = ".asm";

    private final ProcesadorInstrucciones procesador;

    public GestorArchivo() {
        this.procesador = new ProcesadorInstrucciones();
    }

    public GestorArchivo(ProcesadorInstrucciones procesador) {
        this.procesador = procesador;
    }

    // ------------------------------------------------------------------
    // Seleccion de archivos
    // ------------------------------------------------------------------

    /**
     * Abre la ventana de seleccion de archivos (filtrada a .asm) permitiendo elegir VARIOS a la vez
     * (con Ctrl o Shift). Devuelve la lista elegida, o una lista vacia si el usuario cancelo.
     * @param componentePadre componente sobre el cual centrar el dialogo
     */
    public List<File> seleccionarArchivos(Component componentePadre) {
        JFileChooser selector = new JFileChooser(new File(System.getProperty("user.dir")));
        selector.setDialogTitle("Seleccione uno o varios archivos ensamblador (*.asm)");
        selector.setFileFilter(new FileNameExtensionFilter("Archivos ensamblador (*.asm)", "asm"));
        selector.setAcceptAllFileFilterUsed(false);
        selector.setMultiSelectionEnabled(true);

        int opcion = selector.showOpenDialog(componentePadre);
        if (opcion != JFileChooser.APPROVE_OPTION) {
            return Collections.emptyList(); // el usuario cancelo
        }
        File[] elegidos = selector.getSelectedFiles();
        if (elegidos.length == 0 && selector.getSelectedFile() != null) {
            elegidos = new File[]{selector.getSelectedFile()};
        }
        return Arrays.asList(elegidos);
    }

    /** Lee todas las lineas de texto de un archivo (UTF-8; si no lo es, se intenta con ISO-8859-1). */
    public List<String> leerLineas(File archivo) throws IOException {
        try {
            return Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            // archivos guardados con acentos en ANSI (Bloc de notas antiguo)
            return Files.readAllLines(archivo.toPath(), StandardCharsets.ISO_8859_1);
        }
    }

    // ------------------------------------------------------------------
    // Carga al disco
    // ------------------------------------------------------------------

    /**
     * Valida cada archivo por separado y guarda en el disco los que son validos.
     * Un archivo invalido NO detiene la carga de los demas.
     * @return un resultado por archivo, en el mismo orden recibido.
     */
    public List<ResultadoCargaArchivo> cargarEnDisco(List<File> archivos, Disco disco) {
        List<ResultadoCargaArchivo> resultados = new ArrayList<>();
        for (File archivo : archivos) {
            resultados.add(cargarEnDisco(archivo, disco));
        }
        return resultados;
    }

    /** Valida un archivo y, si es valido, lo guarda en el disco. */
    public ResultadoCargaArchivo cargarEnDisco(File archivo, Disco disco) {
        String nombre = archivo.getName();

        // 1) Verificaciones del archivo en si
        if (!nombre.toLowerCase().endsWith(EXTENSION)) {
            return ResultadoCargaArchivo.rechazado(nombre, Collections.singletonList(
                    nombre + ": la extensión debe ser " + EXTENSION + "."));
        }
        if (!archivo.isFile() || !archivo.canRead()) {
            return ResultadoCargaArchivo.rechazado(nombre, Collections.singletonList(
                    nombre + ": el archivo no existe o no se puede leer."));
        }

        List<String> lineas;
        try {
            lineas = leerLineas(archivo);
        } catch (IOException ex) {
            return ResultadoCargaArchivo.rechazado(nombre, Collections.singletonList(
                    nombre + ": error al leer el archivo (" + ex.getMessage() + ")."));
        }

        // 2) Validacion del programa
        ResultadoAnalisis analisis = procesador.analizarPrograma(lineas);
        if (!analisis.esValido()) {
            return ResultadoCargaArchivo.rechazado(nombre, analisis.getTodosLosErrores(nombre));
        }

        // 3) Escritura en el disco: una instruccion normalizada por posicion
        List<String> contenido = new ArrayList<>();
        for (Instruccion instruccion : analisis.getInstrucciones()) {
            contenido.add(instruccion.getTextoNormalizado());
        }
        try {
            EntradaIndice entrada = disco.guardarArchivo(nombre, contenido);
            return ResultadoCargaArchivo.cargado(nombre, entrada, analisis.getInstrucciones(),
                    analisis.getAdvertencias(nombre));
        } catch (IllegalStateException ex) {
            return ResultadoCargaArchivo.rechazado(nombre, Collections.singletonList(nombre + ": " + ex.getMessage()));
        }
    }

    /**
     * Lee un programa que ya esta en el disco y lo vuelve a analizar, para cargarlo en memoria
     * a partir del disco (no del archivo original).
     */
    public List<Instruccion> leerProgramaDelDisco(String nombre, Disco disco) {
        ResultadoAnalisis analisis = procesador.analizarPrograma(disco.leerArchivo(nombre));
        if (!analisis.esValido()) {
            throw new IllegalStateException("El programa \"" + nombre + "\" del disco no es válido: "
                    + analisis.getTodosLosErrores(nombre).get(0));
        }
        return analisis.getInstrucciones();
    }
}