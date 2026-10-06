package minipc.config;

import minipc.BCP;
import minipc.Memoria;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Configuracion de los recursos de la Mini PC: memoria principal y almacenamiento secundario (disco).
 *
 * Los tamanos configurables no quedan en el codigo: se leen y se guardan en el archivo externo
 * "config.properties" (ubicado en la carpeta desde donde se ejecuta la aplicacion). Si el archivo no
 * existe o tiene valores incorrectos, se usan los valores por defecto:
 *   memoria.principal  = 256
 *   memoria.secundaria = 512
 *
 * @author Natalia Granados Rosales
 */
public class Configuracion {

    // ------------------------------------------------------------------
    // Archivo y claves
    // ------------------------------------------------------------------
    public static final String NOMBRE_ARCHIVO = "config.properties";

    private static final String CLAVE_MEMORIA_PRINCIPAL = "memoria.principal";
    private static final String CLAVE_MEMORIA_SECUNDARIA = "memoria.secundaria";
    /** Clave antigua: si aparece en el archivo se ignora, porque la memoria virtual es fija. */
    private static final String CLAVE_MEMORIA_VIRTUAL = "memoria.virtual";

    // ------------------------------------------------------------------
    // Valores por defecto
    // ------------------------------------------------------------------
    public static final int DEFECTO_MEMORIA_PRINCIPAL = 256;
    public static final int DEFECTO_MEMORIA_SECUNDARIA = 512;

    // ------------------------------------------------------------------
    // Caracteristicas fijas del diseno de la computadora
    // ------------------------------------------------------------------

    /** Memoria virtual: tamano fijo (no configurable), ubicada al final del disco. */
    public static final int MEMORIA_VIRTUAL = 64;

    /**
     * Cantidad maxima de procesos activos (en memoria principal) al mismo tiempo. Se pueden cargar mas
     * programas, pero los que excedan este limite esperan en la lista de trabajos hasta que finalice uno.
     */
    public static final int MAX_PROCESOS_ACTIVOS = 5;

    /** Posiciones al inicio del Kernel para datos generales del SO (punteros a listas, contadores, etc.). */
    public static final int TAMANO_ENCABEZADO_SO = 4;

    /**
     * Porcentaje del disco reservado al inicio para el indice de archivos (una entrada por posicion).
     * Al igual que el Kernel en la memoria principal, el indice crece junto con el disco.
     */
    public static final int PORCENTAJE_INDICE_DISCO = 5;

    /** Entradas minimas del indice, sin importar el tamano del disco. */
    public static final int MINIMO_INDICE_DISCO = 10;

    // ------------------------------------------------------------------
    // Limites para la validacion
    // ------------------------------------------------------------------
    public static final int MINIMO_AREA_ARCHIVOS = 32;
    public static final int MAXIMO_MEMORIA_PRINCIPAL = 4096;
    public static final int MAXIMO_MEMORIA_SECUNDARIA = 8192;

    private final int memoriaPrincipal;
    private final int memoriaSecundaria;

    /** Avisos generados al leer el archivo (archivo inexistente, valores invalidos, etc.). */
    private final List<String> advertenciasDeCarga;

    public Configuracion(int memoriaPrincipal, int memoriaSecundaria) {
        this(memoriaPrincipal, memoriaSecundaria, Collections.emptyList());
    }

    private Configuracion(int memoriaPrincipal, int memoriaSecundaria, List<String> advertencias) {
        this.memoriaPrincipal = memoriaPrincipal;
        this.memoriaSecundaria = memoriaSecundaria;
        this.advertenciasDeCarga = Collections.unmodifiableList(new ArrayList<>(advertencias));
    }

    /** Configuracion con los valores por defecto. */
    public static Configuracion porDefecto() {
        return new Configuracion(DEFECTO_MEMORIA_PRINCIPAL, DEFECTO_MEMORIA_SECUNDARIA);
    }

    // ------------------------------------------------------------------
    // Lectura / escritura del archivo
    // ------------------------------------------------------------------

    /** Ruta del archivo de configuracion (en la carpeta de trabajo de la aplicacion). */
    public static Path rutaArchivo() {
        return Paths.get(NOMBRE_ARCHIVO).toAbsolutePath();
    }

    /**
     * Lee la configuracion desde config.properties.
     * Nunca falla: si el archivo no existe, no se puede leer o tiene valores incorrectos, devuelve los
     * valores por defecto y deja el motivo en getAdvertenciasDeCarga().
     */
    public static Configuracion cargar() {
        Path ruta = rutaArchivo();
        List<String> advertencias = new ArrayList<>();

        if (!Files.exists(ruta)) {
            advertencias.add("No se encontró " + NOMBRE_ARCHIVO + ": se muestran los valores por defecto.");
            return new Configuracion(DEFECTO_MEMORIA_PRINCIPAL, DEFECTO_MEMORIA_SECUNDARIA, advertencias);
        }

        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(ruta)) {
            propiedades.load(entrada);
        } catch (IOException ex) {
            advertencias.add("No se pudo leer " + NOMBRE_ARCHIVO + " (" + ex.getMessage() + "): se usan los valores por defecto.");
            return new Configuracion(DEFECTO_MEMORIA_PRINCIPAL, DEFECTO_MEMORIA_SECUNDARIA, advertencias);
        }

        int principal = leerEntero(propiedades, CLAVE_MEMORIA_PRINCIPAL, DEFECTO_MEMORIA_PRINCIPAL, advertencias);
        int secundaria = leerEntero(propiedades, CLAVE_MEMORIA_SECUNDARIA, DEFECTO_MEMORIA_SECUNDARIA, advertencias);

        String virtualEnArchivo = propiedades.getProperty(CLAVE_MEMORIA_VIRTUAL);
        if (virtualEnArchivo != null && !virtualEnArchivo.trim().equals(String.valueOf(MEMORIA_VIRTUAL))) {
            advertencias.add("La memoria virtual es fija (" + MEMORIA_VIRTUAL + "): se ignora el valor \""
                    + virtualEnArchivo.trim() + "\" del archivo.");
        }

        Configuracion leida = new Configuracion(principal, secundaria);
        List<String> errores = leida.validar();
        if (!errores.isEmpty()) {
            advertencias.add("Los valores de " + NOMBRE_ARCHIVO + " no son válidos (" + errores.get(0) + "). Se usan los valores por defecto.");
            return new Configuracion(DEFECTO_MEMORIA_PRINCIPAL, DEFECTO_MEMORIA_SECUNDARIA, advertencias);
        }
        return new Configuracion(principal, secundaria, advertencias);
    }

    /** Lee un entero del archivo; si falta o no es numerico, usa el valor por defecto y lo anota. */
    private static int leerEntero(Properties propiedades, String clave, int valorPorDefecto, List<String> advertencias) {
        String texto = propiedades.getProperty(clave);
        if (texto == null) {
            advertencias.add("Falta \"" + clave + "\" en " + NOMBRE_ARCHIVO + ": se usa " + valorPorDefecto + ".");
            return valorPorDefecto;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException ex) {
            advertencias.add("\"" + clave + "\" tiene un valor no numérico (\"" + texto.trim() + "\"): se usa " + valorPorDefecto + ".");
            return valorPorDefecto;
        }
    }

    /**
     * Guarda esta configuracion en config.properties (crea el archivo si no existe).
     * Solo se guardan los valores configurables (memoria principal y disco).
     * @throws IllegalStateException si la configuracion no es valida.
     * @throws IOException si no se puede escribir el archivo.
     */
    public void guardar() throws IOException {
        List<String> errores = validar();
        if (!errores.isEmpty()) {
            throw new IllegalStateException("No se puede guardar una configuración inválida: " + errores.get(0));
        }

        Properties propiedades = new Properties();
        propiedades.setProperty(CLAVE_MEMORIA_PRINCIPAL, String.valueOf(memoriaPrincipal));
        propiedades.setProperty(CLAVE_MEMORIA_SECUNDARIA, String.valueOf(memoriaSecundaria));

        try (OutputStream salida = Files.newOutputStream(rutaArchivo())) {
            propiedades.store(salida, "Configuracion de la Mini PC (tamanos en posiciones). "
                    + "La memoria virtual es fija en " + MEMORIA_VIRTUAL + ".");
        }
    }

    // ------------------------------------------------------------------
    // Validacion
    // ------------------------------------------------------------------

    /** Devuelve la lista de errores de la configuracion (vacia si es valida). */
    public List<String> validar() {
        List<String> errores = new ArrayList<>();

        // ---- Memoria principal: el 25% del Kernel debe alcanzar para el SO ----
        if (memoriaPrincipal > MAXIMO_MEMORIA_PRINCIPAL) {
            errores.add("La memoria principal (" + memoriaPrincipal + ") supera el máximo permitido ("
                    + MAXIMO_MEMORIA_PRINCIPAL + ").");
        } else if (getTamanoAreaSO() < getEspacioRequeridoSO()) {
            errores.add("La memoria principal (" + memoriaPrincipal + ") no alcanza para el área del SO: su "
                    + Memoria.PORCENTAJE_KERNEL + "% son " + getTamanoAreaSO() + " posiciones y el SO necesita "
                    + getEspacioRequeridoSO() + " (encabezado de " + TAMANO_ENCABEZADO_SO + " + "
                    + MAX_PROCESOS_ACTIVOS + " BCP de " + BCP.TAMANO_EN_MEMORIA + "). Mínimo: "
                    + getMinimoMemoriaPrincipal() + ".");
        }

        // ---- Disco: debe contener indice + archivos + memoria virtual ----
        int minimoSecundaria = getMinimoMemoriaSecundaria();
        if (memoriaSecundaria > MAXIMO_MEMORIA_SECUNDARIA) {
            errores.add("El almacenamiento secundario (" + memoriaSecundaria + ") supera el máximo permitido ("
                    + MAXIMO_MEMORIA_SECUNDARIA + ").");
        } else if (memoriaSecundaria < minimoSecundaria) {
            errores.add("La memoria virtual (" + MEMORIA_VIRTUAL + ") no cabe en el disco (" + memoriaSecundaria
                    + "): el disco necesita " + getTamanoIndiceDisco() + " posiciones para el índice ("
                    + PORCENTAJE_INDICE_DISCO + "%, mínimo " + MINIMO_INDICE_DISCO + "), "
                    + MINIMO_AREA_ARCHIVOS + " para archivos y " + MEMORIA_VIRTUAL
                    + " para la memoria virtual. Mínimo: " + minimoSecundaria + ".");
        }

        return errores;
    }

    public boolean esValida() {
        return validar().isEmpty();
    }

    // ------------------------------------------------------------------
    // Distribucion calculada de la memoria y del disco
    // ------------------------------------------------------------------

    /** Posiciones de memoria principal del Kernel (SO): siempre el 25% del total. */
    public int getTamanoAreaSO() {
        return Memoria.calcularTamanoKernel(memoriaPrincipal);
    }

    /** Posiciones de memoria principal para los programas de usuario: siempre el 75% restante. */
    public int getTamanoAreaUsuario() {
        return memoriaPrincipal - getTamanoAreaSO();
    }

    /** Posiciones que el SO necesita dentro del Kernel: encabezado + un BCP por proceso activo. */
    public static int getEspacioRequeridoSO() {
        return TAMANO_ENCABEZADO_SO + MAX_PROCESOS_ACTIVOS * BCP.TAMANO_EN_MEMORIA;
    }

    /** Menor memoria principal cuyo 25% alcanza para el SO. */
    public static int getMinimoMemoriaPrincipal() {
        int minimo = getEspacioRequeridoSO() * 100 / Memoria.PORCENTAJE_KERNEL;
        while (Memoria.calcularTamanoKernel(minimo) < getEspacioRequeridoSO()) {
            minimo++;
        }
        return minimo;
    }

    /**
     * Posiciones del indice para un disco dado: el 5% redondeado hacia arriba,
     * pero nunca menos de MINIMO_INDICE_DISCO entradas.
     */
    public static int calcularTamanoIndice(int memoriaSecundaria) {
        int porPorcentaje = (Math.max(memoriaSecundaria, 0) * PORCENTAJE_INDICE_DISCO + 99) / 100; // techo sin double
        return Math.max(MINIMO_INDICE_DISCO, porPorcentaje);
    }

    /** Posiciones del indice de este disco (ocupa las posiciones 0 .. getTamanoIndiceDisco()-1). */
    public int getTamanoIndiceDisco() {
        return calcularTamanoIndice(memoriaSecundaria);
    }

    /**
     * Menor disco que alcanza para el indice, un espacio minimo de archivos y la memoria virtual.
     * Como el indice depende del tamano del disco, se busca el primer tamano que cumpla.
     */
    public static int getMinimoMemoriaSecundaria() {
        int minimo = MINIMO_INDICE_DISCO + MINIMO_AREA_ARCHIVOS + MEMORIA_VIRTUAL;
        while (minimo < calcularTamanoIndice(minimo) + MINIMO_AREA_ARCHIVOS + MEMORIA_VIRTUAL) {
            minimo++;
        }
        return minimo;
    }

    /** Posiciones del disco disponibles para guardar archivos (sin el indice ni la memoria virtual). */
    public int getTamanoAreaArchivosDisco() {
        return memoriaSecundaria - getTamanoIndiceDisco() - MEMORIA_VIRTUAL;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public int getMemoriaPrincipal() {
        return memoriaPrincipal;
    }

    public int getMemoriaSecundaria() {
        return memoriaSecundaria;
    }

    /** La memoria virtual es fija; se expone para que el resto del sistema no dependa de la constante. */
    public int getMemoriaVirtual() {
        return MEMORIA_VIRTUAL;
    }

    public List<String> getAdvertenciasDeCarga() {
        return advertenciasDeCarga;
    }

    @Override
    public String toString() {
        return "Configuracion[principal=" + memoriaPrincipal + ", secundaria=" + memoriaSecundaria
                + ", indice=" + getTamanoIndiceDisco() + ", virtual=" + MEMORIA_VIRTUAL + " (fija)]";
    }
}