package minipc;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Se encarga de Gestiona el archivo .asm desde disco:
 *   1) Abre una ventana gráfica (JFileChooser) para que el usuario busque y seleccione el archivo .asm que desee.
 *   2) Lee el contenido del archivo linea por linea.
 *   3) Envía esas lineas al ProcesadorInstrucciones para validarlas y procesarlas.
 * @author Natalia Granados Rosales
 */
public class GestorArchivo {

    private final ProcesadorInstrucciones procesador;

    public GestorArchivo() {
        this.procesador = new ProcesadorInstrucciones();
    }

    public GestorArchivo(ProcesadorInstrucciones procesador) {
        this.procesador = procesador;
    }

    /**
     * Abre la ventana de seleccion de archivos, filtrada para mostrar solo archivos .asm. Devuelve el File elegido, o null si el
     * usuario cancelo el dialogo.
     * @param componentePadre componente sobre el cual centrar el diálogo
     */
    public File seleccionarArchivo(Component componentePadre) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccione el archivo ensamblador (*.asm)");
        selector.setFileFilter(new FileNameExtensionFilter("Archivos ensamblador (*.asm)", "asm"));
        selector.setAcceptAllFileFilterUsed(false);

        int opcion = selector.showOpenDialog(componentePadre);
        if (opcion == JFileChooser.APPROVE_OPTION) {
            return selector.getSelectedFile();
        }
        return null; // el usuario cancelo
    }

    /** Lee todas las lineas de texto de un archivo. */
    public List<String> leerLineas(File archivo) throws IOException {
        return Files.readAllLines(archivo.toPath());
    }

    /**
     * Flujo completo: abre el selector de archivos, lee el .asm elegido y lo valida con ProcesadorInstrucciones.
     * @return la lista de Instruccion resultantes, o null si el usuario cancelo la seleccion del archivo.
     * @throws IOException si ocurre un error leyendo el archivo.
     */
    public List<Instruccion> cargarYProcesar(Component componentePadre) throws IOException {
        File archivo = seleccionarArchivo(componentePadre);
        if (archivo == null) {
            return null; // cancelado por el usuario
        }
        List<String> lineas = leerLineas(archivo);
        return procesador.procesarPrograma(lineas);
    }

    /** Variante directa: procesa un archivo ya conocido (sin abrir la ventanita) */
    public List<Instruccion> cargarYProcesar(File archivo) throws IOException {
        List<String> lineas = leerLineas(archivo);
        return procesador.procesarPrograma(lineas);
    }
}