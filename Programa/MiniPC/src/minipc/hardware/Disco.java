package minipc.hardware;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Almacenamiento secundario (disco) de la Mini PC.
 *
 * Igual que la {@link Memoria}, cada posicion guarda un valor en forma de texto ("" = vacia).
 * El disco se divide en tres zonas: Indice, Archivos  y Memoria virtual
 *
 * El indice es la fuente de verdad: para saber que archivos hay y donde estan, se lee la zona de
 * indice del propio disco. Cada archivo ocupa un bloque contiguo de la zona de archivos, que se
 * asigna con la estrategia de primer ajuste (el primer hueco libre donde quepa).
 *
 * De cada programa se guardan sus instrucciones ya validadas y normalizadas (sin comentarios ni
 * lineas vacias), una por posicion, para no desperdiciar espacio y porque los saltos se cuentan
 * en instrucciones.
 *
 * Los tamanos vienen de {@link minipc.config.Configuracion}.
 * @author Natalia Granados Rosales
 */
public class Disco {

    private final int tamanoTotal;
    private final int tamanoIndice;
    private final int tamanoMemoriaVirtual;

    private final int inicioArchivos;
    private final int finArchivos;
    private final int inicioMemoriaVirtual;

    private final String[] datos;

    /**
     * @param tamanoTotal          posiciones totales del disco.
     * @param tamanoIndice         posiciones reservadas al inicio para el indice (una entrada por archivo).
     * @param tamanoMemoriaVirtual posiciones reservadas al final para la memoria virtual.
     * @throws IllegalArgumentException si las zonas no caben en el disco.
     */
    public Disco(int tamanoTotal, int tamanoIndice, int tamanoMemoriaVirtual) {
        if (tamanoIndice <= 0 || tamanoMemoriaVirtual <= 0 || tamanoIndice + tamanoMemoriaVirtual >= tamanoTotal) {
            throw new IllegalArgumentException("Tamaños de disco incoherentes: total " + tamanoTotal + ", índice "
                    + tamanoIndice + ", memoria virtual " + tamanoMemoriaVirtual + ".");
        }
        this.tamanoTotal = tamanoTotal;
        this.tamanoIndice = tamanoIndice;
        this.tamanoMemoriaVirtual = tamanoMemoriaVirtual;

        this.inicioArchivos = tamanoIndice;
        this.inicioMemoriaVirtual = tamanoTotal - tamanoMemoriaVirtual;
        this.finArchivos = inicioMemoriaVirtual - 1;

        this.datos = new String[tamanoTotal];
        formatear();
    }

    // ==================================================================
    // Archivos
    // ==================================================================

    /**
     * Guarda un archivo en el disco: escribe su contenido en un bloque contiguo de la zona de archivos
     * y registra la entrada en la primera posicion libre del indice.
     * @param nombre nombre del archivo (ej: "prog1.asm").
     * @param lineas contenido, una linea por posicion.
     * @return la entrada creada en el indice.
     * @throws IllegalStateException si el nombre no es valido, ya existe, el indice esta lleno o no hay espacio.
     */
    public EntradaIndice guardarArchivo(String nombre, List<String> lineas) {
        validarNombre(nombre);
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalStateException("El archivo \"" + nombre + "\" no tiene contenido para guardar.");
        }
        if (buscarArchivo(nombre) != null) {
            throw new IllegalStateException("Ya existe un archivo llamado \"" + nombre + "\" en el disco.");
        }

        int posicionIndice = buscarPosicionLibreIndice();
        if (posicionIndice < 0) {
            throw new IllegalStateException("El índice del disco está lleno (" + tamanoIndice
                    + " archivos como máximo): no se puede guardar \"" + nombre + "\".");
        }

        int inicio = buscarBloqueLibre(lineas.size());
        if (inicio < 0) {
            throw new IllegalStateException("No hay espacio contiguo en el disco para \"" + nombre + "\": necesita "
                    + lineas.size() + " posiciones y el mayor espacio libre es de " + mayorBloqueLibre() + ".");
        }

        for (int i = 0; i < lineas.size(); i++) {
            datos[inicio + i] = lineas.get(i) == null ? "" : lineas.get(i);
        }
        EntradaIndice entrada = new EntradaIndice(posicionIndice, nombre, inicio, lineas.size());
        datos[posicionIndice] = entrada.aTextoIndice();
        return entrada;
    }

    /** Busca un archivo por nombre en el indice (sin distinguir mayusculas). Devuelve null si no existe. */
    public EntradaIndice buscarArchivo(String nombre) {
        if (nombre == null) {
            return null;
        }
        for (EntradaIndice entrada : listarArchivos()) {
            if (entrada.getNombre().equalsIgnoreCase(nombre.trim())) {
                return entrada;
            }
        }
        return null;
    }

    /** Lee el contenido de un archivo, una linea por posicion. */
    public List<String> leerArchivo(String nombre) {
        EntradaIndice entrada = buscarArchivo(nombre);
        if (entrada == null) {
            throw new IllegalStateException("No existe el archivo \"" + nombre + "\" en el disco.");
        }
        return new ArrayList<>(Arrays.asList(datos).subList(entrada.getInicio(), entrada.getFin() + 1));
    }

    /** Elimina un archivo: borra su contenido y su entrada del indice, liberando el espacio. */
    public void eliminarArchivo(String nombre) {
        EntradaIndice entrada = buscarArchivo(nombre);
        if (entrada == null) {
            throw new IllegalStateException("No existe el archivo \"" + nombre + "\" en el disco.");
        }
        Arrays.fill(datos, entrada.getInicio(), entrada.getFin() + 1, "");
        datos[entrada.getPosicionIndice()] = "";
    }

    /** Archivos registrados en el indice, en el orden de sus posiciones de indice. */
    public List<EntradaIndice> listarArchivos() {
        List<EntradaIndice> entradas = new ArrayList<>();
        for (int posicion = 0; posicion < tamanoIndice; posicion++) {
            EntradaIndice entrada = EntradaIndice.desdeTexto(posicion, datos[posicion]);
            if (entrada != null) {
                entradas.add(entrada);
            }
        }
        return entradas;
    }

    public int getCantidadArchivos() {
        return listarArchivos().size();
    }

    /** Indica si el disco no tiene ningun archivo ni datos en memoria virtual. */
    public boolean estaVacio() {
        for (String valor : datos) {
            if (!valor.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ==================================================================
    // Memoria virtual
    // ==================================================================

    /** Escribe un valor en la zona de memoria virtual (las ultimas posiciones del disco). */
    public void escribirEnMemoriaVirtual(int posicion, String valor) {
        if (!esMemoriaVirtual(posicion)) {
            throw new SecurityException("La posición " + posicion + " del disco no pertenece a la memoria virtual ("
                    + inicioMemoriaVirtual + "-" + (tamanoTotal - 1) + ").");
        }
        datos[posicion] = valor == null ? "" : valor;
    }

    /** Deja vacia toda la zona de memoria virtual. */
    public void limpiarMemoriaVirtual() {
        Arrays.fill(datos, inicioMemoriaVirtual, tamanoTotal, "");
    }

    // ==================================================================
    // Lectura general y zonas
    // ==================================================================

    /** Lee el valor de cualquier posicion del disco ("" si esta vacia). */
    public String leer(int posicion) {
        if (posicion < 0 || posicion >= tamanoTotal) {
            throw new IndexOutOfBoundsException("Posición " + posicion + " fuera del disco (0-" + (tamanoTotal - 1) + ").");
        }
        return datos[posicion];
    }

    public boolean esIndice(int posicion) {
        return posicion >= 0 && posicion < tamanoIndice;
    }

    public boolean esAreaArchivos(int posicion) {
        return posicion >= inicioArchivos && posicion <= finArchivos;
    }

    public boolean esMemoriaVirtual(int posicion) {
        return posicion >= inicioMemoriaVirtual && posicion < tamanoTotal;
    }

    /** Borra todo el contenido del disco (indice, archivos y memoria virtual). */
    public final void formatear() {
        Arrays.fill(datos, "");
    }

    // ==================================================================
    // Espacio libre (primer ajuste)
    // ==================================================================

    /** Posiciones libres en la zona de archivos (sumando todos los huecos). */
    public int getEspacioLibreArchivos() {
        int libres = 0;
        for (int[] hueco : huecosLibres()) {
            libres += hueco[1];
        }
        return libres;
    }

    /** Primera posicion de un hueco donde quepan "cantidad" posiciones seguidas, o -1 si no hay. */
    private int buscarBloqueLibre(int cantidad) {
        for (int[] hueco : huecosLibres()) {
            if (hueco[1] >= cantidad) {
                return hueco[0];
            }
        }
        return -1;
    }

    private int mayorBloqueLibre() {
        int mayor = 0;
        for (int[] hueco : huecosLibres()) {
            mayor = Math.max(mayor, hueco[1]);
        }
        return mayor;
    }

    /** Huecos libres de la zona de archivos como pares {inicio, tamano}, calculados a partir del indice. */
    private List<int[]> huecosLibres() {
        List<EntradaIndice> ocupados = listarArchivos();
        Collections.sort(ocupados, Comparator.comparingInt(EntradaIndice::getInicio));

        List<int[]> huecos = new ArrayList<>();
        int actual = inicioArchivos;
        for (EntradaIndice entrada : ocupados) {
            if (entrada.getInicio() > actual) {
                huecos.add(new int[]{actual, entrada.getInicio() - actual});
            }
            actual = Math.max(actual, entrada.getFin() + 1);
        }
        if (actual <= finArchivos) {
            huecos.add(new int[]{actual, finArchivos - actual + 1});
        }
        return huecos;
    }

    private int buscarPosicionLibreIndice() {
        for (int posicion = 0; posicion < tamanoIndice; posicion++) {
            if (datos[posicion].isEmpty()) {
                return posicion;
            }
        }
        return -1;
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalStateException("El nombre del archivo no puede estar vacío.");
        }
        if (nombre.contains(EntradaIndice.SEPARADOR)) {
            throw new IllegalStateException("El nombre \"" + nombre + "\" no puede contener el carácter \""
                    + EntradaIndice.SEPARADOR + "\" (se usa como separador en el índice).");
        }
    }

    // ==================================================================
    // Getters
    // ==================================================================

    public int getTamanoTotal() {
        return tamanoTotal;
    }

    public int getTamanoIndice() {
        return tamanoIndice;
    }

    public int getTamanoMemoriaVirtual() {
        return tamanoMemoriaVirtual;
    }

    public int getInicioArchivos() {
        return inicioArchivos;
    }

    public int getFinArchivos() {
        return finArchivos;
    }

    public int getInicioMemoriaVirtual() {
        return inicioMemoriaVirtual;
    }
}