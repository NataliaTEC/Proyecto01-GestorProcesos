package minipc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static minipc.TipoOperando.DESPLAZAMIENTO;
import static minipc.TipoOperando.INTERRUPCION;
import static minipc.TipoOperando.NUMERO;
import static minipc.TipoOperando.REGISTRO;

/**
 * Operadores (mnemonicos) del mini ensamblador.
 *
 * Cada operador conoce:
 *   - su PESO: segundos de CPU que tarda en ejecutarse (para INT depende de la interrupcion).
 *   - sus FORMAS validas: las combinaciones de operandos que acepta. Un operador puede tener
 *     varias formas, por ejemplo MOV acepta "registro, registro" o "registro, numero".
 *
 * @author Natalia Granados Rosales
 */
public enum Operador {

    LOAD(2, "Carga el valor del registro en el AC",
            forma(REGISTRO)),
    STORE(2, "Almacena el valor del AC en el registro",
            forma(REGISTRO)),
    MOV(1, "Copia un registro o un número al registro destino",
            forma(REGISTRO, REGISTRO),
            forma(REGISTRO, NUMERO)),
    ADD(3, "Suma al AC el valor del registro",
            forma(REGISTRO)),
    SUB(3, "Resta al AC el valor del registro",
            forma(REGISTRO)),
    INC(1, "Incrementa en 1 el AC o el registro indicado",
            forma(),
            forma(REGISTRO)),
    DEC(1, "Decrementa en 1 el AC o el registro indicado",
            forma(),
            forma(REGISTRO)),
    SWAP(1, "Intercambia los valores de dos registros",
            forma(REGISTRO, REGISTRO)),
    INT(Operador.PESO_SEGUN_INTERRUPCION, "Ejecuta una interrupción (20H, 10H, 09H o 21H)",
            forma(INTERRUPCION)),
    JMP(2, "Salta según el desplazamiento",
            forma(DESPLAZAMIENTO)),
    CMP(2, "Compara dos registros (activa la bandera de igualdad)",
            forma(REGISTRO, REGISTRO)),
    JE(2, "Salta según el desplazamiento si la última comparación fue igual",
            forma(DESPLAZAMIENTO)),
    JNE(2, "Salta según el desplazamiento si la última comparación fue distinta",
            forma(DESPLAZAMIENTO)),
    PARAM(3, "Guarda en la pila de 1 a 3 parámetros numéricos",
            forma(NUMERO),
            forma(NUMERO, NUMERO),
            forma(NUMERO, NUMERO, NUMERO)),
    PUSH(1, "Guarda en la pila el valor del registro",
            forma(REGISTRO)),
    POP(1, "Saca el último valor de la pila y lo guarda en el registro",
            forma(REGISTRO));

    /** Marca para INT: el peso no es del operador sino de la interrupcion (ver {@link Interrupcion}). */
    public static final int PESO_SEGUN_INTERRUPCION = 0;

    private final int peso;
    private final String descripcion;
    private final List<List<TipoOperando>> formas;

    Operador(int peso, String descripcion, TipoOperando[]... formas) {
        this.peso = peso;
        this.descripcion = descripcion;
        List<List<TipoOperando>> lista = new ArrayList<>();
        for (TipoOperando[] forma : formas) {
            lista.add(Collections.unmodifiableList(Arrays.asList(forma)));
        }
        this.formas = Collections.unmodifiableList(lista);
    }

    /** Ayuda para declarar una forma: forma(REGISTRO, NUMERO). Sin argumentos = sin operandos. */
    private static TipoOperando[] forma(TipoOperando... tipos) {
        return tipos;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /**
     * Peso fijo del operador (segundos de CPU). Para INT el peso depende de la interrupcion:
     * use {@link Instruccion#getPeso()}, que lo resuelve para cada instruccion.
     */
    public int getPeso() {
        return peso;
    }

    public boolean tienePesoFijo() {
        return peso != PESO_SEGUN_INTERRUPCION;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Formas de operandos validas para este operador (cada forma es una lista de tipos). */
    public List<List<TipoOperando>> getFormas() {
        return formas;
    }

    /** Indica si alguna de sus formas tiene exactamente esa cantidad de operandos. */
    public boolean aceptaCantidad(int cantidad) {
        for (List<TipoOperando> forma : formas) {
            if (forma.size() == cantidad) {
                return true;
            }
        }
        return false;
    }

    /** Indica si es una instruccion de salto (JMP, JE o JNE). */
    public boolean esSalto() {
        return this == JMP || this == JE || this == JNE;
    }

    /**
     * Texto con la sintaxis de todas sus formas, para mensajes de error.
     * Ej: MOV -> "MOV registro, registro  |  MOV registro, número"
     */
    public String sintaxis() {
        StringBuilder texto = new StringBuilder();
        for (List<TipoOperando> forma : formas) {
            if (texto.length() > 0) {
                texto.append("  |  ");
            }
            texto.append(name());
            for (int i = 0; i < forma.size(); i++) {
                texto.append(i == 0 ? " " : ", ").append(nombreCorto(forma.get(i)));
            }
        }
        return texto.toString();
    }

    /** Texto con las cantidades de operandos aceptadas: "2", "0 o 1", "de 1 a 3". */
    public String cantidadesAceptadas() {
        int minimo = Integer.MAX_VALUE;
        int maximo = Integer.MIN_VALUE;
        for (List<TipoOperando> forma : formas) {
            minimo = Math.min(minimo, forma.size());
            maximo = Math.max(maximo, forma.size());
        }
        if (minimo == maximo) {
            return String.valueOf(minimo);
        }
        return (maximo - minimo == 1) ? minimo + " o " + maximo : "de " + minimo + " a " + maximo;
    }

    private static String nombreCorto(TipoOperando tipo) {
        switch (tipo) {
            case REGISTRO:
                return "registro";
            case NUMERO:
                return "número";
            case DESPLAZAMIENTO:
                return "+/-desplazamiento";
            case INTERRUPCION:
                return "código";
            default:
                return tipo.name().toLowerCase();
        }
    }

    // ------------------------------------------------------------------
    // Conversion desde texto
    // ------------------------------------------------------------------

    /** Traduce un texto (mnemonico) a su Operador. Acepta mayusculas/minusculas. Devuelve null si no es reconocido. */
    public static Operador fromTexto(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return Operador.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Lista de todos los operadores validos, para mensajes de error. */
    public static String listaParaMensajes() {
        StringBuilder texto = new StringBuilder();
        for (Operador operador : values()) {
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append(operador.name());
        }
        return texto.toString();
    }
}