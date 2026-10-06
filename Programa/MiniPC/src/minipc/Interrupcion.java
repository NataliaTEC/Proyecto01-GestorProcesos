package minipc;

/**
 * Codigos de interrupcion validos para la instruccion INT, con su peso (segundos de CPU).
 *
 *   INT 20H : finaliza el programa                                  peso 2
 *   INT 10H : imprime en pantalla el valor de DX                    peso 2
 *   INT 09H : entrada del teclado (0-255) que se guarda en DX       peso variable (espera al usuario)
 *   INT 21H : manejo de archivos segun AH (crear, abrir, leer,      peso 5 escribir, eliminar); el nombre va en DX y el contenido en AL
 * @author Natalia Granados Rosales
 */
public enum Interrupcion {

    FIN_PROGRAMA("20H", 2, "Finaliza el programa"),
    IMPRIMIR_PANTALLA("10H", 2, "Imprime en pantalla el valor de DX"),
    ENTRADA_TECLADO("09H", Interrupcion.PESO_VARIABLE, "Lee un valor del teclado (0-255) y lo guarda en DX"),
    MANEJO_ARCHIVOS("21H", 5, "Manejo de archivos según el valor de AH");

    /** Indica que el tiempo de la instruccion no es fijo (depende de cuando responda el usuario). */
    public static final int PESO_VARIABLE = -1;

    private final String codigo;
    private final int peso;
    private final String descripcion;

    Interrupcion(String codigo, int peso, String descripcion) {
        this.codigo = codigo;
        this.peso = peso;
        this.descripcion = descripcion;
    }

    /** Codigo tal como se escribe en el ensamblador (ej: "20H"). */
    public String getCodigo() {
        return codigo;
    }

    /** Segundos de CPU que toma la interrupcion, o PESO_VARIABLE para INT 09H. */
    public int getPeso() {
        return peso;
    }

    public boolean tienePesoVariable() {
        return peso == PESO_VARIABLE;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Traduce un texto ("20H", "20h") a su Interrupcion. Devuelve null si no es un codigo valido. */
    public static Interrupcion fromTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim().toUpperCase();
        for (Interrupcion interrupcion : values()) {
            if (interrupcion.codigo.equals(t)) {
                return interrupcion;
            }
        }
        return null;
    }

    /** Lista legible de los codigos validos para mensajes: "20H, 10H, 09H o 21H". */
    public static String listaParaMensajes() {
        Interrupcion[] todas = values();
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < todas.length; i++) {
            if (i > 0) {
                texto.append(i == todas.length - 1 ? " o " : ", ");
            }
            texto.append(todas[i].codigo);
        }
        return texto.toString();
    }
}