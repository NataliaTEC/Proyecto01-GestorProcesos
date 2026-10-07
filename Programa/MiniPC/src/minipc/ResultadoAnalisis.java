package minipc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de analizar un programa .asm completo.
 *
 * Contiene:
 *   - las instrucciones (validas e invalidas), una por linea con contenido.
 *   - errores generales del programa, que no pertenecen a una sola linea (ej: archivo vacio).
 *   - advertencias: situaciones que no impiden cargar el programa pero conviene revisar
 *     (ej: no termina con INT 20H).
 *
 * Un programa es valido si todas sus instrucciones son validas y no tiene errores generales.
 * @author Natalia Granados Rosales
 */
public class ResultadoAnalisis {

    private final List<Instruccion> instrucciones;
    private final List<String> erroresGenerales;
    private final List<String> advertencias;

    public ResultadoAnalisis(List<Instruccion> instrucciones, List<String> erroresGenerales, List<String> advertencias) {
        this.instrucciones = Collections.unmodifiableList(new ArrayList<>(instrucciones));
        this.erroresGenerales = Collections.unmodifiableList(new ArrayList<>(erroresGenerales));
        this.advertencias = Collections.unmodifiableList(new ArrayList<>(advertencias));
    }

    public List<Instruccion> getInstrucciones() {
        return instrucciones;
    }

    public List<String> getErroresGenerales() {
        return erroresGenerales;
    }

    public List<String> getAdvertencias() {
        return advertencias;
    }

    public boolean tieneAdvertencias() {
        return !advertencias.isEmpty();
    }

    /** Indica si el programa se puede cargar en memoria. */
    public boolean esValido() {
        if (!erroresGenerales.isEmpty()) {
            return false;
        }
        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Todos los errores en texto, listos para mostrar: primero los generales y luego los de cada
     * linea ("Línea 4: ..."), en el orden del archivo.
     */
    public List<String> getTodosLosErrores() {
        List<String> errores = new ArrayList<>(erroresGenerales);
        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                errores.add("Línea " + instruccion.getNumeroLinea() + ": " + instruccion.getMensajeError());
            }
        }
        return errores;
    }

    /**
     * Igual que {@link #getTodosLosErrores()}, pero cada mensaje indica el archivo:
     * "prog1.asm, línea 4: ..." para errores de una linea y "prog1.asm: ..." para errores generales.
     */
    public List<String> getTodosLosErrores(String nombreArchivo) {
        List<String> errores = new ArrayList<>();
        for (String error : erroresGenerales) {
            errores.add(nombreArchivo + ": " + error);
        }
        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                errores.add(nombreArchivo + ", línea " + instruccion.getNumeroLinea() + ": " + instruccion.getMensajeError());
            }
        }
        return errores;
    }

    /** Las advertencias, cada una precedida por el nombre del archivo. */
    public List<String> getAdvertencias(String nombreArchivo) {
        List<String> resultado = new ArrayList<>();
        for (String advertencia : advertencias) {
            resultado.add(nombreArchivo + ": " + advertencia);
        }
        return resultado;
    }
}