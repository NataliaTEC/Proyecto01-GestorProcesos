package minipc.hardware;

/**
 * Una entrada del indice de archivos del {@link Disco}.
 *
 * Cada entrada ocupa UNA posicion de la zona de indice y se guarda como texto con el formato:
 *     nombre|inicio|tamano        (ej: "prog1.asm|26|12")
 * donde "inicio" es la primera posicion del disco donde esta el contenido del archivo y
 * "tamano" la cantidad de posiciones (lineas) que ocupa.
 * @author Natalia Granados Rosales
 */
public class EntradaIndice {

    /** Separador de los campos dentro de la entrada. Por eso un nombre de archivo no puede contenerlo. */
    public static final String SEPARADOR = "|";

    private final int posicionIndice;
    private final String nombre;
    private final int inicio;
    private final int tamano;

    public EntradaIndice(int posicionIndice, String nombre, int inicio, int tamano) {
        this.posicionIndice = posicionIndice;
        this.nombre = nombre;
        this.inicio = inicio;
        this.tamano = tamano;
    }

    /** Texto que se escribe en la posicion del indice: "nombre|inicio|tamano". */
    public String aTextoIndice() {
        return nombre + SEPARADOR + inicio + SEPARADOR + tamano;
    }

    /**
     * Interpreta el texto de una posicion del indice.
     * @return la entrada, o null si la posicion esta vacia o no tiene el formato esperado.
     */
    public static EntradaIndice desdeTexto(int posicionIndice, String texto) {
        if (texto == null || texto.isEmpty()) {
            return null;
        }
        String[] partes = texto.split("\\|");
        if (partes.length != 3) {
            return null;
        }
        try {
            return new EntradaIndice(posicionIndice, partes[0], Integer.parseInt(partes[1]), Integer.parseInt(partes[2]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Posicion del disco (dentro de la zona de indice) donde esta guardada esta entrada. */
    public int getPosicionIndice() {
        return posicionIndice;
    }

    public String getNombre() {
        return nombre;
    }

    /** Primera posicion del disco con el contenido del archivo. */
    public int getInicio() {
        return inicio;
    }

    /** Cantidad de posiciones que ocupa el contenido. */
    public int getTamano() {
        return tamano;
    }

    /** Ultima posicion del disco con el contenido del archivo. */
    public int getFin() {
        return inicio + tamano - 1;
    }

    @Override
    public String toString() {
        return nombre + " (posiciones " + inicio + "-" + getFin() + ")";
    }
}
