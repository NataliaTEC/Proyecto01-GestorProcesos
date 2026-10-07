package minipc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de intentar cargar un archivo .asm al disco.
 *
 * Un archivo queda "cargado" si se pudo leer, su programa es valido y se guardo en el disco.
 * Si fue rechazado, {@link #getErrores()} explica por que; cada mensaje incluye el nombre del
 * archivo y, cuando aplica, la linea (ej: "prog1.asm, línea 4: ...").
 * @author Natalia Granados Rosales
 */
public class ResultadoCargaArchivo {

    private final String nombreArchivo;
    private final boolean cargado;
    private final List<String> errores;
    private final List<String> advertencias;
    private final EntradaIndice entrada;           // null si fue rechazado
    private final List<Instruccion> instrucciones; // vacia si fue rechazado

    private ResultadoCargaArchivo(String nombreArchivo, boolean cargado, List<String> errores, List<String> advertencias, EntradaIndice entrada, List<Instruccion> instrucciones) {
        this.nombreArchivo = nombreArchivo;
        this.cargado = cargado;
        this.errores = Collections.unmodifiableList(new ArrayList<>(errores));
        this.advertencias = Collections.unmodifiableList(new ArrayList<>(advertencias));
        this.entrada = entrada;
        this.instrucciones = Collections.unmodifiableList(new ArrayList<>(instrucciones));
    }

    public static ResultadoCargaArchivo cargado(String nombreArchivo, EntradaIndice entrada, List<Instruccion> instrucciones, List<String> advertencias) {
        return new ResultadoCargaArchivo(nombreArchivo, true, Collections.emptyList(), advertencias, entrada, instrucciones);
    }

    public static ResultadoCargaArchivo rechazado(String nombreArchivo, List<String> errores) {
        return new ResultadoCargaArchivo(nombreArchivo, false, errores, Collections.emptyList(), null, Collections.emptyList());
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public boolean isCargado() {
        return cargado;
    }

    public List<String> getErrores() {
        return errores;
    }

    public List<String> getAdvertencias() {
        return advertencias;
    }

    /** Entrada del indice del disco donde quedo registrado (null si fue rechazado). */
    public EntradaIndice getEntrada() {
        return entrada;
    }

    public List<Instruccion> getInstrucciones() {
        return instrucciones;
    }
}
